package org.gameyfin.app.games.overrides

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameFieldOverrideService(
    private val repository: GameFieldOverrideRepository
) {
    companion object {
        /** The fields that can be pinned, named like the field sources stored on a game */
        val OVERRIDABLE_FIELDS = setOf(
            "title", "platforms", "release", "summary", "developers", "publishers", "userRating", "criticRating",
            "genres", "themes", "keywords", "features", "perspectives", "coverImage", "headerImage", "images",
            "videoUrls"
        )

        /** Parses and validates overrides sent by the client (field name to source key). */
        fun parse(raw: Map<String, String>?): Map<String, FieldSource> = raw.orEmpty().mapValues { (field, key) ->
            require(field in OVERRIDABLE_FIELDS) { "Field '$field' cannot be pinned to a source" }
            FieldSource.fromKey(key) ?: throw IllegalArgumentException("Invalid metadata source '$key'")
        }
    }

    /** Maps field name to the source it is pinned to. */
    fun get(gameId: Long): Map<String, FieldSource> =
        repository.findAllByGameId(gameId).associate { it.field to FieldSource(it.pluginId, it.baseGame) }

    /** Replaces all overrides of the game. An empty map removes them. */
    @Transactional
    fun replace(gameId: Long, overrides: Map<String, FieldSource>) {
        repository.deleteAllByGameId(gameId)
        repository.flush()
        repository.saveAll(overrides.map { (field, source) ->
            GameFieldOverride(gameId = gameId, field = field, pluginId = source.pluginId, baseGame = source.baseGame)
        })
    }
}
