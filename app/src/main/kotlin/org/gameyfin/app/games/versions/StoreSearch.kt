package org.gameyfin.app.games.versions

import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** A product found on a store, so that admins can pick a product instead of typing its ID. */
data class StoreSearchResultDto(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?
)

/**
 * Searches a store's public catalog. Like the version providers, search providers use fixed, maintainer-vetted
 * endpoints and are completely independent of the metadata plugins.
 */
interface GameStoreSearchProvider {

    val store: GameStore

    fun search(query: String): List<StoreSearchResultDto>
}

private const val MAX_SEARCH_RESULTS = 10

private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

@Component
class SteamStoreSearchProvider : GameStoreSearchProvider {
    override val store = GameStore.STEAM

    override fun search(query: String): List<StoreSearchResultDto> {
        val uri = URI.create("$BASE_URL/?term=${encode(query)}&cc=us&l=en")
        return parseSteamSearch(StoreVersionHttp.getJson(uri))
    }

    companion object {
        private const val BASE_URL = "https://store.steampowered.com/api/storesearch"
    }
}

@Component
class GogStoreSearchProvider : GameStoreSearchProvider {
    override val store = GameStore.GOG

    override fun search(query: String): List<StoreSearchResultDto> {
        val filter = encode("like:$query")
        val uri = URI.create("$BASE_URL?query=$filter&productType=${encode("in:game,pack")}&limit=$MAX_SEARCH_RESULTS")
        return filterByTitleRelevance(query, parseGogSearch(StoreVersionHttp.getJson(uri)))
    }

    companion object {
        private const val BASE_URL = "https://catalog.gog.com/v1/catalog"
    }
}

@Component
class EpicStoreSearchProvider : GameStoreSearchProvider {
    override val store = GameStore.EPIC

    private val mapper = ObjectMapper()

    override fun search(query: String): List<StoreSearchResultDto> {
        val body = mapper.writeValueAsString(
            mapOf("title" to query, "offerType" to "BASE_GAME", "limit" to MAX_SEARCH_RESULTS)
        )
        return filterByTitleRelevance(query, parseEpicSearch(StoreVersionHttp.postJson(URI.create(SEARCH_URL), body)))
    }

    companion object {
        private const val SEARCH_URL = "https://api.egdata.app/search/v2/search"
    }
}

@Service
class GameStoreSearchService(private val providers: List<GameStoreSearchProvider>) {

    fun search(store: GameStore, query: String): List<StoreSearchResultDto> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        val provider = providers.singleOrNull { it.store == store }
            ?: throw IllegalStateException("No search provider is registered for $store")
        return provider.search(trimmed.take(100)).take(MAX_SEARCH_RESULTS)
    }
}

internal fun parseSteamSearch(root: JsonNode): List<StoreSearchResultDto> =
    root.path("items").mapNotNull { item ->
        val id = item.path("id").asString("").takeIf { GameStore.STEAM.isValidId(it) } ?: return@mapNotNull null
        val title = item.path("name").asString("").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        StoreSearchResultDto(id, title, null, item.path("tiny_image").asString("").takeIf { it.isNotBlank() })
    }.take(MAX_SEARCH_RESULTS)

internal fun parseGogSearch(root: JsonNode): List<StoreSearchResultDto> =
    root.path("products").mapNotNull { product ->
        val id = product.path("id").asString("").takeIf { GameStore.GOG.isValidId(it) } ?: return@mapNotNull null
        val title = product.path("title").asString("").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val year = product.path("releaseDate").asString("").take(4).takeIf { it.length == 4 }
        StoreSearchResultDto(id, title, year, product.path("coverVertical").asString("").takeIf { it.isNotBlank() })
    }.take(MAX_SEARCH_RESULTS)

internal fun parseEpicSearch(root: JsonNode): List<StoreSearchResultDto> =
    root.path("offers").mapNotNull { offer ->
        val id = offer.path("id").asString("").lowercase().takeIf { GameStore.EPIC.isValidId(it) }
            ?: return@mapNotNull null
        val title = offer.path("title").asString("").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val images = offer.path("keyImages")
        val image = listOf("OfferImageTall", "Thumbnail")
            .firstNotNullOfOrNull { type -> images.firstOrNull { it.path("type").asString("") == type } }
            ?.path("url")?.asString("")?.takeIf { it.isNotBlank() }
        StoreSearchResultDto(id, title, offer.path("seller").path("name").asString("").takeIf { it.isNotBlank() }, image)
    }.take(MAX_SEARCH_RESULTS)

private val ROMAN_NUMERALS = mapOf(
    "i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
    "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10"
)

private fun titleWords(value: String) =
    value.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }
        .map { ROMAN_NUMERALS[it] ?: it }

/**
 * The Epic (egdata) and GOG searches are fuzzy and return loosely related products when a game is not on the
 * store at all. Steam's search does not need this. A result is only kept if every word of the query is the start of a word in its title.
 */
internal fun filterByTitleRelevance(query: String, results: List<StoreSearchResultDto>): List<StoreSearchResultDto> {
    val queryWords = titleWords(query)
    if (queryWords.isEmpty()) return results
    return results.filter { result ->
        val words = titleWords(result.title)
        queryWords.all { queryWord -> words.any { it.startsWith(queryWord) } }
    }
}
