package org.gameyfin.app.games.versions

import java.time.Instant

/**
 * Stores for which Gameyfin can track versions of a game.
 *
 * Version tracking is deliberately independent of metadata plugins: stores are linked to games by the admin
 * and versions come from sources that are built into Gameyfin and vetted by the maintainers.
 */
enum class GameStore(private val idPattern: Regex, private val urlPatterns: List<Regex>) {
    STEAM(
        Regex("^\\d{1,20}$"),
        listOf(Regex("store\\.steampowered\\.com/app/(\\d{1,20})"), Regex("steamdb\\.info/app/(\\d{1,20})"))
    ),
    GOG(
        Regex("^\\d{1,20}$"),
        listOf(Regex("gogdb\\.org/product/(\\d{1,20})"))
    ),
    EPIC(
        Regex("^[0-9a-f]{32}$"),
        listOf(Regex("egdata\\.app/offers/([0-9a-fA-F]{32})"))
    );

    fun isValidId(id: String) = idPattern.matches(id)

    /** Accepts a plain product ID or a URL of a well-known site and returns the normalized product ID. */
    fun parseId(input: String): String? {
        val trimmed = input.trim()
        val candidate = urlPatterns.firstNotNullOfOrNull { it.find(trimmed)?.groupValues?.get(1) } ?: trimmed
        val normalized = if (this == EPIC) candidate.lowercase() else candidate
        return normalized.takeIf(::isValidId)
    }
}

/**
 * Retrieves versions/builds for a product on a game store.
 *
 * Providers must use a vetted, fixed endpoint for their store and return versions ordered newest first.
 * The label is user-facing; build IDs and branch names are opaque identifiers and must not be parsed as semver.
 */
interface GameVersionProvider {

    val store: GameStore

    fun fetchVersions(storeAppId: String): List<StoreVersionInfo>
}

data class StoreVersionInfo(
    val buildId: String,
    val branch: String = "",
    val label: String,
    val releasedAt: Instant? = null
)
