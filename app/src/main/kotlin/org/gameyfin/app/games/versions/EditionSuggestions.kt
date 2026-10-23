package org.gameyfin.app.games.versions

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * Suggests edition names for a store product. Only GOGDB lists the editions of a product by name,
 * for the other stores editions are separate products and the label is suggested from the product title.
 */
@Service
class EditionSuggestionService {
    private class Cached(val fetchedAt: Long, val names: List<String>)

    private val cache = ConcurrentHashMap<String, Cached>()

    fun suggest(store: GameStore, storeAppId: String): List<String> {
        if (store != GameStore.GOG) return emptyList()
        val id = store.parseId(storeAppId) ?: return emptyList()
        val now = System.nanoTime()
        cache[id]?.takeIf { now - it.fetchedAt < CACHE_TTL.toNanos() }?.let { return it.names }
        return try {
            val product = StoreVersionHttp.getJson(URI.create("https://www.gogdb.org/data/products/$id/product.json"))
            parseGogDbEditions(product).also { cache[id] = Cached(now, it) }
        } catch (e: Exception) {
            log.debug(e) { "Could not look up the editions of GOG product $id" }
            emptyList()
        }
    }

    companion object {
        private val CACHE_TTL = Duration.ofMinutes(10)
    }
}

private val STANDARD_EDITION_NAME = Regex("standard( edition)?", RegexOption.IGNORE_CASE)

/** Edition names of a GOG product, without the standard edition, which is the unlabeled base product. */
internal fun parseGogDbEditions(product: JsonNode): List<String> {
    val editions = product.path("editions")
    if (!editions.isArray) return emptyList()
    return (0 until editions.size())
        .map { editions.get(it).path("name").asText("").trim() }
        .filter { it.isNotEmpty() && it.length <= GameStoreLinkService.MAX_EDITION_LENGTH && !STANDARD_EDITION_NAME.matches(it) }
        .distinct()
        .take(20)
}
