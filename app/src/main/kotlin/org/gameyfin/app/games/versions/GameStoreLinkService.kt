package org.gameyfin.app.games.versions

import org.gameyfin.app.games.repositories.GameRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages which store products a game is linked to. Links are only created and removed by admins,
 * metadata plugins are never consulted, so the result does not depend on which plugins are enabled.
 */
@Service
class GameStoreLinkService(
    private val linkRepository: GameStoreLinkRepository,
    private val gameRepository: GameRepository
) {
    companion object {
        // Prefer stores that provide real version names over stores that only provide build IDs
        private val TRACKING_PREFERENCE = listOf(GameStore.GOG, GameStore.EPIC, GameStore.STEAM)
    }

    fun getLinks(gameId: Long): List<GameStoreLink> = linkRepository.findAllByGameId(gameId)

    /** [storeAppId] may be a plain product ID or the URL of a product page. */
    @Transactional
    fun addLink(gameId: Long, store: GameStore, storeAppId: String): GameStoreLink {
        val id = store.parseId(storeAppId)
            ?: throw IllegalArgumentException("Invalid ${store.name} product ID or URL: '$storeAppId'")
        if (!gameRepository.existsById(gameId)) throw NoSuchElementException("Game $gameId not found")

        val link = linkRepository.findByGameIdAndStoreAndStoreAppId(gameId, store, id)
            ?: linkRepository.save(GameStoreLink(gameId = gameId, store = store, storeAppId = id))
        ensureTrackedLink(gameId)
        return link
    }

    @Transactional
    fun removeLink(linkId: Long) {
        val link = linkRepository.findById(linkId).orElse(null) ?: return
        linkRepository.delete(link)
        linkRepository.flush()
        ensureTrackedLink(link.gameId)
    }

    @Transactional
    fun setTracked(linkId: Long) {
        val link = linkRepository.findById(linkId).orElseThrow { NoSuchElementException("Link $linkId not found") }
        linkRepository.findAllByGameId(link.gameId).forEach { it.tracked = it.id == link.id }
    }

    /** Makes sure exactly one link of the game is tracked (if it has any). */
    private fun ensureTrackedLink(gameId: Long) {
        val links = linkRepository.findAllByGameId(gameId)
        if (links.isEmpty() || links.count { it.tracked } == 1) return

        val tracked = links.firstOrNull { it.tracked }
            ?: links.sortedWith(compareBy({ TRACKING_PREFERENCE.indexOf(it.store) }, { it.id })).first()
        links.forEach { it.tracked = it === tracked }
        linkRepository.saveAll(links)
    }
}
