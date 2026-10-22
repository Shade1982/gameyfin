package org.gameyfin.app.games.versions

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

internal object StoreVersionHttp {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val mapper = ObjectMapper()

    fun getJson(uri: URI): JsonNode = send(
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "Gameyfin")
            .GET()
            .build()
    )

    fun postJson(uri: URI, body: String): JsonNode = send(
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Gameyfin")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
    )

    private fun send(request: HttpRequest): JsonNode {
        val uri = request.uri()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Version source returned HTTP ${response.statusCode()} for $uri"
        }
        check(response.body().isNotBlank()) { "Version source returned an empty response for $uri" }
        return mapper.readTree(response.body())
            ?: throw IllegalStateException("Version source returned invalid JSON for $uri")
    }
}

/** Prefers GOG's official content-system endpoint and uses GOGDB when the official response is incomplete. */
@Component
class GogVersionProvider : GameVersionProvider {
    override val store = GameStore.GOG

    override fun fetchVersions(storeAppId: String): List<StoreVersionInfo> {
        require(NUMERIC_PRODUCT_ID.matches(storeAppId)) { "Invalid GOG product ID: '$storeAppId'" }

        var officialFailure: Exception? = null
        try {
            val uri = URI.create("$BASE_URL/products/$storeAppId/os/windows/builds?generation=2")
            val page = parseGogBuildPage(StoreVersionHttp.getJson(uri))
            if (page.versions.isNotEmpty() && page.itemCount == page.totalCount) {
                return page.versions.sortedByNewest()
            }
            val incompleteResponse = IllegalStateException(
                "GOG returned ${page.itemCount} builds out of ${page.totalCount}; using the GOGDB archive"
            )
            officialFailure = incompleteResponse
            log.debug { incompleteResponse.message }
        } catch (e: Exception) {
            officialFailure = e
            log.warn(e) { "GOG's official version endpoint failed for product $storeAppId; trying the GOGDB archive" }
        }

        try {
            val uri = URI.create("$GOGDB_BASE_URL/data/products/$storeAppId/product.json")
            val versions = parseGogDbProduct(StoreVersionHttp.getJson(uri))
            check(versions.isNotEmpty()) { "GOGDB returned no usable Windows generation 2 builds" }
            return versions.sortedByNewest()
        } catch (fallbackFailure: Exception) {
            officialFailure?.let(fallbackFailure::addSuppressed)
            throw IllegalStateException(
                "Could not retrieve GOG versions from either GOG or GOGDB for product $storeAppId",
                fallbackFailure
            )
        }
    }

    companion object {
        private const val BASE_URL = "https://content-system.gog.com"
        private const val GOGDB_BASE_URL = "https://www.gogdb.org"
        private val NUMERIC_PRODUCT_ID = Regex("^\\d{1,20}$")
    }
}

/** Unofficial, maintainer-vetted Steam source. Steam only exposes branch build IDs, not user-facing versions. */
@Component
class SteamVersionProvider : GameVersionProvider {
    override val store = GameStore.STEAM

    override fun fetchVersions(storeAppId: String): List<StoreVersionInfo> {
        require(NUMERIC_APP_ID.matches(storeAppId)) { "Invalid Steam app ID: '$storeAppId'" }

        val root = StoreVersionHttp.getJson(URI.create("$BASE_URL/$storeAppId"))
        return parseSteamVersions(root, storeAppId)
    }

    companion object {
        private const val BASE_URL = "https://api.steamcmd.net/v1/info"
        private val NUMERIC_APP_ID = Regex("^\\d{1,20}$")
    }
}

/**
 * Unofficial, maintainer-vetted Epic source: the community catalog egdata.app, because Epic itself only exposes
 * build data to authenticated launcher clients. The product ID is the Epic offer ID.
 */
@Component
class EpicVersionProvider : GameVersionProvider {
    override val store = GameStore.EPIC

    override fun fetchVersions(storeAppId: String): List<StoreVersionInfo> {
        require(GameStore.EPIC.isValidId(storeAppId)) { "Invalid Epic offer ID: '$storeAppId'" }

        val root = StoreVersionHttp.getJson(URI.create("$BASE_URL/$storeAppId/builds"))
        return parseEpicBuilds(root)
    }

    companion object {
        private const val BASE_URL = "https://api.egdata.app/offers"
    }
}

internal data class GogBuildPage(
    val totalCount: Int,
    val itemCount: Int,
    val versions: List<StoreVersionInfo>
)

private fun List<StoreVersionInfo>.sortedByNewest() =
    distinctBy { it.buildId to it.branch }
        .sortedWith(compareByDescending<StoreVersionInfo> { it.releasedAt }.thenByDescending { it.buildId })

internal fun parseGogBuildPage(page: JsonNode): GogBuildPage {
    val items = page.path("items")
    check(items.isArray) { "GOG version response is missing the items array" }
    val totalCount = page.path("total_count").asInt(-1)
    check(totalCount >= 0) { "GOG version response is missing total_count" }

    val versions = items.mapNotNull { item ->
        val buildId = item.path("build_id").asText().trim()
        val label = item.path("version_name").asText().trim()
        if (buildId.isEmpty() || label.isEmpty()) return@mapNotNull null
        StoreVersionInfo(
            buildId = buildId,
            branch = item.path("branch").asText(""),
            label = label,
            releasedAt = item.path("date_published").asText()
                .takeIf { it.isNotBlank() }
                ?.let { OffsetDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx")).toInstant() }
        )
    }
    return GogBuildPage(totalCount, items.size(), versions)
}

internal fun parseGogDbProduct(product: JsonNode): List<StoreVersionInfo> {
    val builds = product.path("builds")
    check(builds.isArray) { "GOGDB product response is missing the builds array" }

    return builds.mapNotNull { build ->
        if (build.path("os").asText() != "windows" || build.path("generation").asInt() != 2) {
            return@mapNotNull null
        }
        val buildId = build.path("id").asText().trim()
        val label = build.path("version").asText().trim()
        if (buildId.isEmpty() || label.isEmpty()) return@mapNotNull null

        StoreVersionInfo(
            buildId = buildId,
            branch = build.path("branch").asText(""),
            label = label,
            releasedAt = build.path("date_published").asText()
                .takeIf { it.isNotBlank() }
                ?.let(OffsetDateTime::parse)
                ?.toInstant()
        )
    }
}

internal fun parseSteamVersions(root: JsonNode, appId: String): List<StoreVersionInfo> {
    val branches = root.path("data").path(appId).path("depots").path("branches")
    check(branches.isObject) { "Steam version response is missing depot branches for app $appId" }

    return branches.properties().mapNotNull { (branchName, branchInfo) ->
        val buildId = branchInfo.path("buildid").asText().trim()
        if (buildId.isEmpty()) return@mapNotNull null

        val normalizedBranch = if (branchName == "public") "" else branchName
        val releasedAt = branchInfo.path("timebuildupdated").asLong(-1)
            .takeIf { it > 0 }
            ?.let(Instant::ofEpochSecond)
            ?: branchInfo.path("timeupdated").asLong(-1)
                .takeIf { it > 0 }
                ?.let(Instant::ofEpochSecond)
        val date = releasedAt?.let {
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(it)
        }
        val branchLabel = if (normalizedBranch.isEmpty()) "Public" else normalizedBranch

        StoreVersionInfo(
            buildId = buildId,
            branch = normalizedBranch,
            label = buildString {
                append("$branchLabel build $buildId")
                if (date != null) append(" ($date)")
            },
            releasedAt = releasedAt
        )
    }.sortedWith(compareByDescending<StoreVersionInfo> { it.releasedAt }.thenByDescending { it.buildId })
}

/**
 * Maps egdata.app builds to versions. Only Windows builds on the production ("Live") label are used, unless there
 * are none. Epic has no release dates, so the time egdata first saw a build is used instead.
 */
internal fun parseEpicBuilds(root: JsonNode): List<StoreVersionInfo> {
    check(root.isArray) { "Epic version response is not an array of builds" }

    val windows = root.filter { it.path("platform").asText().equals("Windows", ignoreCase = true) }
    val live = windows.filter { it.path("labelName").asText().startsWith("Live", ignoreCase = true) }

    return (live.ifEmpty { windows }).mapNotNull { build ->
        val buildVersion = build.path("buildVersion").asText().trim()
        if (buildVersion.isEmpty()) return@mapNotNull null

        val label = build.path("labelName").asText().removeSuffix("-Windows").trim()
        val branch = if (label.equals("Live", ignoreCase = true)) "" else label
        val firstSeen = build.path("firstSeenAt").asText()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        Triple(buildVersion, branch, firstSeen)
    }.groupBy { (buildVersion, branch) -> buildVersion to branch }
        .map { (key, builds) ->
            val (buildVersion, branch) = key
            val releasedAt = builds.mapNotNull { it.third }.minOrNull()
            val date = releasedAt?.let {
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(it)
            }
            StoreVersionInfo(
                buildId = buildVersion,
                branch = branch,
                label = buildString {
                    if (branch.isNotEmpty()) append("$branch: ")
                    append(buildVersion)
                    if (date != null) append(" ($date)")
                },
                releasedAt = releasedAt
            )
        }.sortedByNewest()
}
