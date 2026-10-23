package org.gameyfin.app.games.versions

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.net.URI

/** An extra (edition upgrade, DLC or bonus material) of a store product. The ID is the store's own identifier. */
data class StoreDlcInfo(val dlcId: String, val title: String, val kind: DlcKind = DlcKind.DLC)

/**
 * Lists the DLC a store knows for a product. Like the version providers, implementations use fixed,
 * maintainer-vetted endpoints and never depend on metadata plugins.
 */
interface GameDlcProvider {

    val store: GameStore

    fun fetchDlc(storeAppId: String): List<StoreDlcInfo>
}

/** Prefers the official GOG API, which lists DLC with titles in one request. */
@Component
class GogDlcProvider : GameDlcProvider {
    override val store = GameStore.GOG

    override fun fetchDlc(storeAppId: String): List<StoreDlcInfo> {
        require(store.isValidId(storeAppId)) { "Invalid GOG product ID: '$storeAppId'" }
        val root = StoreVersionHttp.getJson(URI.create("$BASE_URL/$storeAppId?expand=expanded_dlcs"))
        return parseGogDlc(root)
    }

    companion object {
        private const val BASE_URL = "https://api.gog.com/products"
    }
}

/**
 * Steam lists the DLC app IDs on the app page, the names require one request per DLC.
 * The number of DLC is capped to stay well below Steam's rate limits.
 */
@Component
class SteamDlcProvider : GameDlcProvider {
    override val store = GameStore.STEAM

    override fun fetchDlc(storeAppId: String): List<StoreDlcInfo> {
        require(store.isValidId(storeAppId)) { "Invalid Steam app ID: '$storeAppId'" }
        val ids = parseSteamDlcIds(appDetails(storeAppId), storeAppId).take(MAX_DLC)
        return ids.map { id ->
            val details = runCatching { appDetails(id) }.getOrNull()
            val title = details?.let { parseSteamAppName(it, id) }
            StoreDlcInfo(id, title ?: "Steam app $id", classifyExtra(title.orEmpty(), isBonus = details?.let { parseSteamAppType(it, id) } == "music"))
        }
    }

    private fun appDetails(appId: String): JsonNode =
        StoreVersionHttp.getJson(URI.create("$BASE_URL?appids=$appId&cc=us&l=english"))

    companion object {
        private const val BASE_URL = "https://store.steampowered.com/api/appdetails"
        private const val MAX_DLC = 80
    }
}

/** Unofficial, maintainer-vetted Epic source, see [EpicVersionProvider]. The product ID is the Epic offer ID. */
@Component
class EpicDlcProvider : GameDlcProvider {
    override val store = GameStore.EPIC

    override fun fetchDlc(storeAppId: String): List<StoreDlcInfo> {
        require(store.isValidId(storeAppId)) { "Invalid Epic offer ID: '$storeAppId'" }
        val root = StoreVersionHttp.getJson(URI.create("$BASE_URL/$storeAppId/related"))
        return parseEpicDlc(root, storeAppId)
    }

    companion object {
        private const val BASE_URL = "https://api.egdata.app/offers"
    }
}

internal fun parseGogDlc(root: JsonNode): List<StoreDlcInfo> =
    root.path("expanded_dlcs").mapNotNull { dlc ->
        val id = dlc.path("id").asText("").takeIf { GameStore.GOG.isValidId(it) } ?: return@mapNotNull null
        val title = dlc.path("title").asText("").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        StoreDlcInfo(id, title.take(MAX_TITLE_LENGTH), classifyExtra(title))
    }.distinctBy { it.dlcId }.sortedBy { it.title.lowercase() }

internal fun parseSteamDlcIds(root: JsonNode, appId: String): List<String> {
    val app = root.path(appId)
    if (!app.path("success").asBoolean(false)) return emptyList()
    return app.path("data").path("dlc")
        .mapNotNull { it.asText("").takeIf(GameStore.STEAM::isValidId) }
        .distinct()
}

internal fun parseSteamAppName(root: JsonNode, appId: String): String? {
    val app = root.path(appId)
    if (!app.path("success").asBoolean(false)) return null
    return app.path("data").path("name").asText("").trim().takeIf { it.isNotEmpty() }?.take(MAX_TITLE_LENGTH)
}

internal fun parseSteamAppType(root: JsonNode, appId: String): String? {
    val app = root.path(appId)
    if (!app.path("success").asBoolean(false)) return null
    return app.path("data").path("type").asText("").takeIf { it.isNotEmpty() }
}

private val BONUS_TITLE = Regex("soundtrack|\\bost\\b|original score|art ?book|wallpaper|making of|comic|\\bbonus\\b", RegexOption.IGNORE_CASE)
private val EDITION_TITLE = Regex("\\bupgrade\\b|\\bedition\\b", RegexOption.IGNORE_CASE)
private val EPIC_INTERNAL_TITLE = Regex("staging|modkit|audience", RegexOption.IGNORE_CASE)

/**
 * Stores do not tell us what an extra is, so this uses what they do provide (Steam's "music" type, Epic's
 * "EDITION" offer type) and falls back to words in the title. Admins can correct the result by hand.
 */
internal fun classifyExtra(title: String, isEdition: Boolean = false, isBonus: Boolean = false): DlcKind = when {
    isBonus || BONUS_TITLE.containsMatchIn(title) -> DlcKind.BONUS
    isEdition || EDITION_TITLE.containsMatchIn(title) -> DlcKind.EDITION_UPGRADE
    else -> DlcKind.DLC
}

/**
 * Epic lists editions next to DLC. They are offered as extras here because they can be an upgrade of the base
 * product. Other offer types (tools, keys, internal staging entries) are skipped.
 */
internal fun parseEpicDlc(root: JsonNode, offerId: String): List<StoreDlcInfo> =
    root.mapNotNull { offer ->
        val type = offer.path("offerType").asText("")
        if (type != "DLC" && type != "ADD_ON" && type != "EDITION") return@mapNotNull null
        val id = offer.path("id").asText("").lowercase().takeIf { GameStore.EPIC.isValidId(it) && it != offerId }
            ?: return@mapNotNull null
        val title = offer.path("title").asText("").trim().takeIf { it.isNotEmpty() && !EPIC_INTERNAL_TITLE.containsMatchIn(it) }
            ?: return@mapNotNull null
        StoreDlcInfo(id, title.take(MAX_TITLE_LENGTH), classifyExtra(title, isEdition = type == "EDITION"))
    }.distinctBy { it.dlcId }.sortedBy { it.title.lowercase() }

private const val MAX_TITLE_LENGTH = 255
