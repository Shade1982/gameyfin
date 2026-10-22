package org.gameyfin.app.games.versions

import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StoreVersionProvidersTest {
    private val mapper = ObjectMapper()

    @Test
    fun `parses GOG versions and retains branch and release date`() {
        val page = mapper.readTree(
            """
            {
              "total_count": 2,
              "items": [
                {
                  "build_id": "build-2",
                  "version_name": "2.0b",
                  "branch": null,
                  "date_published": "2024-12-22T02:35:35+0000"
                },
                {
                  "build_id": "build-1",
                  "version_name": "COMPAT-1.9",
                  "branch": "compatibility",
                  "date_published": "2024-12-20T02:35:35+0000"
                }
              ]
            }
            """.trimIndent()
        )

        val result = parseGogBuildPage(page)

        assertEquals(2, result.totalCount)
        assertEquals(2, result.itemCount)
        assertEquals("", result.versions[0].branch)
        assertEquals("2.0b", result.versions[0].label)
        assertEquals(Instant.parse("2024-12-22T02:35:35Z"), result.versions[0].releasedAt)
        assertEquals("compatibility", result.versions[1].branch)
    }

    @Test
    fun `rejects incomplete GOG response`() {
        assertFailsWith<IllegalStateException> { parseGogBuildPage(mapper.readTree("""{"items": []}""")) }
    }

    @Test
    fun `parses GOGDB archive builds and filters to Windows generation 2`() {
        val product = mapper.readTree(
            """
            {
              "builds": [
                {
                  "id": 123,
                  "version": "1.2.0",
                  "os": "windows",
                  "generation": 2,
                  "branch": null,
                  "date_published": "2024-12-22T02:35:35+00:00"
                },
                {
                  "id": 456,
                  "version": "1.1.0",
                  "os": "osx",
                  "generation": 2,
                  "date_published": "2024-12-20T02:35:35+00:00"
                },
                {
                  "id": 789,
                  "version": "",
                  "os": "windows",
                  "generation": 1
                }
              ]
            }
            """.trimIndent()
        )

        val versions = parseGogDbProduct(product)

        assertEquals(1, versions.size)
        assertEquals("123", versions.single().buildId)
        assertEquals("1.2.0", versions.single().label)
        assertEquals("", versions.single().branch)
        assertEquals(Instant.parse("2024-12-22T02:35:35Z"), versions.single().releasedAt)
    }

    @Test
    fun `parses Steam public and named branches without treating build IDs as versions`() {
        val response = mapper.readTree(
            """
            {
              "data": {
                "413150": {
                  "depots": {
                    "branches": {
                      "public": {
                        "buildid": "16826371",
                        "timebuildupdated": "1734825749"
                      },
                      "legacy_1.6.8": {
                        "buildid": "14198583",
                        "timeupdated": "1730756892"
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()
        )

        val versions = parseSteamVersions(response, "413150")

        assertEquals(2, versions.size)
        val public = versions.single { it.branch.isEmpty() }
        assertEquals("16826371", public.buildId)
        assertEquals("Public build 16826371 (2024-12-22)", public.label)
        assertEquals(Instant.ofEpochSecond(1734825749), public.releasedAt)
        assertEquals("legacy_1.6.8", versions.single { it.branch.isNotEmpty() }.branch)
        assertEquals(GameStore.STEAM, SteamVersionProvider().store)
    }

    @Test
    fun `provider rejects nonnumeric product IDs before making requests`() {
        assertFailsWith<IllegalArgumentException> { GogVersionProvider().fetchVersions("1/other") }
        assertFailsWith<IllegalArgumentException> { SteamVersionProvider().fetchVersions("1?other") }
    }

    @Test
    fun `parses Epic builds using Windows live label and earliest sighting`() {
        val builds = mapper.readTree(
            """
            [
              {"appName":"Min","buildVersion":"38294-x64","labelName":"Live-Windows","platform":"Windows","firstSeenAt":"2025-12-15T10:00:00.000Z"},
              {"appName":"Min","buildVersion":"38294-x64","labelName":"Live-Windows","platform":"Windows","firstSeenAt":"2025-07-23T08:30:00.000Z"},
              {"appName":"Min","buildVersion":"30000-x64","labelName":"Live-Windows","platform":"Windows","firstSeenAt":"2025-01-02T00:00:00.000Z"},
              {"appName":"Test","buildVersion":"99999-x64","labelName":"-Windows","platform":"Windows","firstSeenAt":"2026-04-19T09:00:00.000Z"},
              {"appName":"Min","buildVersion":"1.0-mac","labelName":"Live-Mac","platform":"Mac","firstSeenAt":"2025-01-01T00:00:00.000Z"}
            ]
            """.trimIndent()
        )

        val versions = parseEpicBuilds(builds)

        assertEquals(listOf("38294-x64", "30000-x64"), versions.map { it.buildId })
        assertEquals("", versions.first().branch)
        assertEquals("38294-x64 (2025-07-23)", versions.first().label)
        assertEquals(Instant.parse("2025-07-23T08:30:00Z"), versions.first().releasedAt)
        assertEquals(GameStore.EPIC, EpicVersionProvider().store)
    }

    @Test
    fun `Epic falls back to other Windows builds when there is no live label`() {
        val builds = mapper.readTree(
            """[{"buildVersion":"7","labelName":"Beta-Windows","platform":"Windows","firstSeenAt":"2025-01-02T00:00:00Z"}]"""
        )

        val versions = parseEpicBuilds(builds)

        assertEquals("Beta", versions.single().branch)
        assertEquals("Beta: 7 (2025-01-02)", versions.single().label)
    }

    @Test
    fun `Epic provider rejects invalid offer IDs before making requests`() {
        assertFailsWith<IllegalArgumentException> { EpicVersionProvider().fetchVersions("../other") }
        assertFailsWith<IllegalArgumentException> { EpicVersionProvider().fetchVersions("123") }
    }

    @Test
    fun `store IDs are parsed from plain IDs and well-known URLs`() {
        assertEquals("413150", GameStore.STEAM.parseId(" 413150 "))
        assertEquals("413150", GameStore.STEAM.parseId("https://store.steampowered.com/app/413150/Stardew_Valley/"))
        assertEquals("1453375253", GameStore.GOG.parseId("https://www.gogdb.org/product/1453375253"))
        val offer = "2ae6edb2223c4f8c97e9839b5b6497bb"
        assertEquals(offer, GameStore.EPIC.parseId("https://egdata.app/offers/${offer.uppercase()}"))
        assertNull(GameStore.EPIC.parseId("413150"))
        assertNull(GameStore.STEAM.parseId("abc"))
        assertNull(GameStore.STEAM.parseId("1/../2"))
    }
}
