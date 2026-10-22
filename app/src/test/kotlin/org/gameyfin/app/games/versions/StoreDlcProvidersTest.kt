package org.gameyfin.app.games.versions

import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreDlcProvidersTest {
    private val mapper = ObjectMapper()

    @Test
    fun `parses GOG DLC and drops invalid entries`() {
        val root = mapper.readTree(
            """{"expanded_dlcs":[{"id":2,"title":"B Pack"},{"id":1,"title":"A Pack"},{"id":"x","title":"Bad"},{"id":3,"title":" "},{"id":1,"title":"A Pack"}]}"""
        )

        assertEquals(listOf(StoreDlcInfo("1", "A Pack"), StoreDlcInfo("2", "B Pack")), parseGogDlc(root))
    }

    @Test
    fun `GOG response without DLC gives an empty list`() {
        assertTrue(parseGogDlc(mapper.readTree("""{"expanded_dlcs":[]}""")).isEmpty())
        assertTrue(parseGogDlc(mapper.readTree("""{}""")).isEmpty())
    }

    @Test
    fun `parses Steam DLC app IDs`() {
        val root = mapper.readTree("""{"10":{"success":true,"data":{"dlc":[20,30,20,"bad"]}}}""")

        assertEquals(listOf("20", "30"), parseSteamDlcIds(root, "10"))
    }

    @Test
    fun `Steam failures give no DLC and no name`() {
        val root = mapper.readTree("""{"10":{"success":false}}""")

        assertTrue(parseSteamDlcIds(root, "10").isEmpty())
        assertNull(parseSteamAppName(root, "10"))
    }

    @Test
    fun `parses Steam app name`() {
        val root = mapper.readTree("""{"20":{"success":true,"data":{"name":" Skyrim Anniversary Upgrade "}}}""")

        assertEquals("Skyrim Anniversary Upgrade", parseSteamAppName(root, "20"))
    }

    @Test
    fun `Epic keeps DLC and add-ons but not editions or other offers`() {
        val own = "a".repeat(32)
        val root = mapper.readTree(
            """
            [
              {"id":"${"b".repeat(32)}","title":"Map Pack","offerType":"ADD_ON"},
              {"id":"${"c".repeat(32)}","title":"Story DLC","offerType":"DLC"},
              {"id":"${"d".repeat(32)}","title":"Ultimate Edition","offerType":"EDITION"},
              {"id":"${"e".repeat(32)}","title":"Keys","offerType":"OTHERS"},
              {"id":"$own","title":"Itself","offerType":"DLC"}
            ]
            """.trimIndent()
        )

        assertEquals(
            listOf(StoreDlcInfo("b".repeat(32), "Map Pack"), StoreDlcInfo("c".repeat(32), "Story DLC")),
            parseEpicDlc(root, own)
        )
    }
}
