package org.gameyfin.app.games.versions

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

data class GameStoreLinkDto(
    val id: Long,
    val gameId: Long,
    val store: GameStore,
    val storeAppId: String,
    val tracked: Boolean
)

fun GameStoreLink.toDto() = GameStoreLinkDto(id!!, gameId, store, storeAppId, tracked)

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class GameStoreLinkEndpoint(private val linkService: GameStoreLinkService) {

    fun getLinks(gameId: Long): List<GameStoreLinkDto> = linkService.getLinks(gameId).map { it.toDto() }

    fun addLink(gameId: Long, store: GameStore, storeAppId: String): GameStoreLinkDto =
        linkService.addLink(gameId, store, storeAppId).toDto()

    fun removeLink(linkId: Long) = linkService.removeLink(linkId)

    fun setTracked(linkId: Long) = linkService.setTracked(linkId)
}
