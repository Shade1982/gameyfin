package org.gameyfin.app.games.supplements

import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameSupplementService(
    private val repository: GameSupplementRepository
) {
    /** Maps plugin ID to the provider's ID for the game. */
    fun get(gameId: Long): Map<String, String> =
        repository.findAllByGameId(gameId).associate { it.pluginId to it.originalId }

    /** Replaces all supplements of the game. An empty collection removes the supplement. */
    @Transactional
    fun replace(gameId: Long, supplementIds: Collection<ExternalProviderIdDto>) {
        repository.deleteAllByGameId(gameId)
        repository.flush()
        repository.saveAll(supplementIds.map {
            GameSupplement(gameId = gameId, pluginId = it.pluginId, originalId = it.externalProviderId)
        })
    }
}
