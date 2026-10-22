package org.gameyfin.app.games.versions

import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.net.URI

/** A DLC (or add-on) of a store product. The ID is the store's own identifier for it. */
data class StoreDlcInfo(val dlcId: String, val title: String)

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
            val title = runCatching { parseSteamAppName(appDetails(id), id) }.getOrNull()
            StoreDlcInfo(id, title ?: "Steam app $id")
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
        StoreDlcInfo(id, title.take(MAX_TITLE_LENGTH))
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

/** Editions are not DLC: a complete edition is a separate product that bundles the base game. */
internal fun parseEpicDlc(root: JsonNode, offerId: String): List<StoreDlcInfo> =
    root.mapNotNull { offer ->
        val type = offer.path("offerType").asText("")
        if (type != "DLC" && type != "ADD_ON") return@mapNotNull null
        val id = offer.path("id").asText("").lowercase().takeIf { GameStore.EPIC.isValidId(it) && it != offerId }
            ?: return@mapNotNull null
        val title = offer.path("title").asText("").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        StoreDlcInfo(id, title.take(MAX_TITLE_LENGTH))
    }.distinctBy { it.dlcId }.sortedBy { it.title.lowercase() }

private const val MAX_TITLE_LENGTH = 255
