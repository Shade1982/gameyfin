package org.gameyfin.app.games.supplements

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

/**
 * A metadata provider ID that was manually attached to a game as a supplement (e.g. the base game of an edition).
 * Supplements only fill metadata fields that the game's own metadata leaves empty.
 * Kept apart from the game's own provider IDs, so a game can have a supplement from the same provider as its own match.
 */
@Entity
@Table(
    name = "game_supplement",
    uniqueConstraints = [UniqueConstraint(columnNames = ["game_id", "plugin_id"])]
)
class GameSupplement(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(name = "game_id", nullable = false)
    val gameId: Long,

    @Column(name = "plugin_id", nullable = false)
    val pluginId: String,

    @Column(name = "original_id", nullable = false)
    val originalId: String
)

interface GameSupplementRepository : JpaRepository<GameSupplement, Long> {
    fun findAllByGameId(gameId: Long): List<GameSupplement>

    fun deleteAllByGameId(gameId: Long)
}
