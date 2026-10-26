package org.gameyfin.app.games.overrides

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Pins a metadata field of a game to a specific source, regardless of the plugin priorities.
 * Follows the source instead of storing a value, so a metadata refresh still picks up updated data.
 * If the pinned source no longer provides the field, the plugin priorities decide again.
 */
@Entity
@Table(
    name = "game_field_override",
    uniqueConstraints = [UniqueConstraint(columnNames = ["game_id", "field"])]
)
class GameFieldOverride(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(name = "game_id", nullable = false)
    val gameId: Long,

    @Column(name = "field", nullable = false)
    val field: String,

    @Column(name = "plugin_id", nullable = false)
    val pluginId: String,

    /** True if the value comes from the base game (supplement) instead of the game's own match */
    @Column(name = "base_game", nullable = false)
    val baseGame: Boolean = false
)

/** The source a field is pinned to. [baseGame] distinguishes a base game from the game's own match of the same plugin. */
data class FieldSource(val pluginId: String, val baseGame: Boolean) {
    /** Same format as the keys the metadata inspector uses for its sources */
    val key: String get() = "${if (baseGame) "base" else "primary"}:$pluginId"

    companion object {
        fun fromKey(key: String): FieldSource? {
            val role = key.substringBefore(':', "")
            val pluginId = key.substringAfter(':', "")
            if (pluginId.isBlank() || role !in setOf("primary", "base")) return null
            return FieldSource(pluginId, role == "base")
        }
    }
}

interface GameFieldOverrideRepository : JpaRepository<GameFieldOverride, Long> {
    fun findAllByGameId(gameId: Long): List<GameFieldOverride>

    fun deleteAllByGameId(gameId: Long)
}
