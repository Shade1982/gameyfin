package org.gameyfin.app.games.versions

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreSearchTest {
    private val mapper = ObjectMapper()

    @Test
    fun `parses Steam search results and skips invalid entries`() {
        val results = parseSteamSearch(
            mapper.readTree(
                """{"total":3,"items":[
                    {"type":"app","name":"Stardew Valley","id":413150,"tiny_image":"https://example.test/a.jpg"},
                    {"type":"app","name":"","id":1},
                    {"type":"app","name":"No ID"}
                ]}"""
            )
        )

        assertEquals(listOf(StoreSearchResultDto("413150", "Stardew Valley", null, "https://example.test/a.jpg")), results)
    }

    @Test
    fun `parses GOG search results with release year`() {
        val results = parseGogSearch(
            mapper.readTree(
                """{"products":[
                    {"id":"1453375253","title":"Stardew Valley","releaseDate":"2016.02.26","coverVertical":"https://example.test/c.jpg"},
                    {"id":"abc","title":"Invalid"}
                ]}"""
            )
        )

        assertEquals(listOf(StoreSearchResultDto("1453375253", "Stardew Valley", "2016", "https://example.test/c.jpg")), results)
    }

    @Test
    fun `parses Epic search results preferring the tall offer image`() {
        val results = parseEpicSearch(
            mapper.readTree(
                """{"offers":[
                    {"id":"2AE6EDB2223C4F8C97E9839B5B6497BB","title":"Hades","seller":{"name":"Supergiant Games"},
                     "keyImages":[{"type":"Thumbnail","url":"https://example.test/t.jpg"},{"type":"OfferImageTall","url":"https://example.test/tall.jpg"}]},
                    {"id":"short","title":"Invalid"}
                ]}"""
            )
        )

        val hades = results.single()
        assertEquals("2ae6edb2223c4f8c97e9839b5b6497bb", hades.id)
        assertEquals("Supergiant Games", hades.subtitle)
        assertEquals("https://example.test/tall.jpg", hades.imageUrl)
    }

    @Test
    fun `results without usable fields are dropped`() {
        assertTrue(parseSteamSearch(mapper.readTree("{}")).isEmpty())
        assertNull(parseGogSearch(mapper.readTree("""{"products":[{"id":"1","title":"X"}]}""")).single().subtitle)
    }

    @Test
    fun `search service ignores short queries and delegates to the provider of the store`() {
        val steam = mockk<GameStoreSearchProvider> { every { store } returns GameStore.STEAM }
        val gog = mockk<GameStoreSearchProvider> { every { store } returns GameStore.GOG }
        every { gog.search("Stardew") } returns listOf(StoreSearchResultDto("1", "Stardew", null, null))
        val service = GameStoreSearchService(listOf(steam, gog))

        assertTrue(service.search(GameStore.GOG, " a ").isEmpty())
        assertEquals("1", service.search(GameStore.GOG, "  Stardew ").single().id)
        verify(exactly = 0) { steam.search(any()) }
    }

    @Test
    fun `search service fails when no provider exists for the store`() {
        val service = GameStoreSearchService(emptyList())

        assertFailsWith<IllegalStateException> { service.search(GameStore.EPIC, "Hades") }
    }

    @Test
    fun `title filter drops loosely related fuzzy matches`() {
        val results = listOf("Ova Magica", "Hades", "Hades II", "Outbreak Shades of Horror", "Curse of Pirates")
            .mapIndexed { index, title -> StoreSearchResultDto("$index", title, null, null) }

        assertEquals(listOf("Hades", "Hades II"), filterByTitleRelevance("Hades", results).map { it.title })
        assertTrue(filterByTitleRelevance("Stardew Valley", results).isEmpty())
        assertEquals(
            listOf("Hades II"),
            filterByTitleRelevance("hades: ii", results).map { it.title }
        )
    }

    @Test
    fun `title filter treats roman numerals and digits alike`() {
        val results = listOf("The Witcher 3: Wild Hunt", "Paper Sorcerer")
            .mapIndexed { index, title -> StoreSearchResultDto("$index", title, null, null) }

        assertEquals(listOf("The Witcher 3: Wild Hunt"), filterByTitleRelevance("Witcher III", results).map { it.title })
    }

    @Test
    fun `search queries lose punctuation but keep apostrophes`() {
        assertEquals("The Witcher 3 Wild Hunt Remastered", normalizeSearchQuery("The Witcher 3: Wild Hunt — Remastered"))
        assertEquals("Assassin's Creed", normalizeSearchQuery("  Assassin's Creed! "))
    }

    @Test
    fun `Epic cover images are requested as small thumbnails`() {
        assertEquals(
            "https://cdn1.epicgames.com/offer/x/cover-1?h=96&resize=1&w=72",
            epicThumbnailUrl("https://cdn1.epicgames.com/offer/x/cover-1")
        )
        assertEquals("https://example.test/tall.jpg", epicThumbnailUrl("https://example.test/tall.jpg"))
    }

    @Test
    fun `Steam search keeps base games and unknown types but drops DLC and soundtracks`() {
        val results = listOf("1", "2", "3", "4").map { StoreSearchResultDto(it, "T$it", null, null) }
        val types = mapOf("1" to "game", "2" to "dlc", "3" to "music")

        assertEquals(listOf("1", "4"), keepBaseGames(results) { types[it] }.map { it.id })
    }
}
