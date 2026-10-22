package org.gameyfin.app.games.versions

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

/**
 * DLC that is included in the installer(s) Gameyfin provides for a game, as offered by the store product of a link.
 * Entries are either picked by an admin or detected from installer file names.
 */
@Entity
@Table(
    name = "game_included_dlc",
    uniqueConstraints = [UniqueConstraint(columnNames = ["link_id", "dlc_id"])]
)
class GameIncludedDlc(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(name = "link_id", nullable = false)
    val linkId: Long,

    @Column(name = "dlc_id", nullable = false)
    val dlcId: String,

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: LinkSource = LinkSource.MANUAL
)

interface GameIncludedDlcRepository : JpaRepository<GameIncludedDlc, Long> {
    fun findAllByLinkId(linkId: Long): List<GameIncludedDlc>

    fun findAllByLinkIdIn(linkIds: Collection<Long>): List<GameIncludedDlc>
}
