package org.gameyfin.app.games.versions

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.games.repositories.GameRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GameStoreLinkServiceTest {

    private val repository = mockk<GameStoreLinkRepository>()
    private val gameRepository = mockk<GameRepository>()
    private val service = GameStoreLinkService(repository, gameRepository)

    /** Fake in-memory table so that the repository behaves consistently across calls */
    private val stored = mutableListOf<GameStoreLink>()

    @BeforeEach
    fun setUp() {
        var nextId = 1L
        every { gameRepository.existsById(10) } returns true
        every { gameRepository.existsById(99) } returns false
        every { repository.findAllByGameId(any()) } answers { stored.filter { it.gameId == firstArg<Long>() } }
        every { repository.findByGameIdAndStoreAndStoreAppId(any(), any(), any()) } answers {
            stored.firstOrNull { it.gameId == firstArg<Long>() && it.store == secondArg() && it.storeAppId == thirdArg() }
        }
        every { repository.save(any<GameStoreLink>()) } answers {
            val link = firstArg<GameStoreLink>()
            if (link.id == null) {
                link.id = nextId++
                stored.add(link)
            }
            link
        }
        every { repository.saveAll(any<Iterable<GameStoreLink>>()) } answers { firstArg<Iterable<GameStoreLink>>().toList() }
        every { repository.findById(any()) } answers { Optional.ofNullable(stored.firstOrNull { it.id == firstArg<Long>() }) }
        every { repository.delete(any()) } answers { stored.remove(firstArg()) }
        every { repository.flush() } returns Unit
    }

    @Test
    fun `adds a link from a plain ID or a product URL and tracks the first one`() {
        val steam = service.addLink(10, GameStore.STEAM, "https://store.steampowered.com/app/413150/Stardew_Valley/")

        assertEquals("413150", steam.storeAppId)
        assertTrue(steam.tracked)
    }

    @Test
    fun `adding the same link twice does not create a duplicate`() {
        service.addLink(10, GameStore.GOG, "1453375253")
        service.addLink(10, GameStore.GOG, " 1453375253 ")

        assertEquals(1, stored.size)
    }

    @Test
    fun `a game can be linked to several stores independently of each other`() {
        service.addLink(10, GameStore.STEAM, "413150")
        service.addLink(10, GameStore.GOG, "1453375253")
        service.addLink(10, GameStore.EPIC, "2ae6edb2223c4f8c97e9839b5b6497bb")

        assertEquals(setOf(GameStore.STEAM, GameStore.GOG, GameStore.EPIC), stored.map { it.store }.toSet())
        assertEquals(1, stored.count { it.tracked })
        assertEquals(GameStore.STEAM, stored.single { it.tracked }.store)
    }

    @Test
    fun `rejects invalid IDs and unknown games`() {
        assertFailsWith<IllegalArgumentException> { service.addLink(10, GameStore.STEAM, "not-an-id") }
        assertFailsWith<IllegalArgumentException> { service.addLink(10, GameStore.EPIC, "413150") }
        assertFailsWith<NoSuchElementException> { service.addLink(99, GameStore.STEAM, "413150") }
        assertTrue(stored.isEmpty())
    }

    @Test
    fun `removing the tracked link promotes another one`() {
        val gog = service.addLink(10, GameStore.GOG, "1453375253")
        val steam = service.addLink(10, GameStore.STEAM, "413150")

        service.removeLink(gog.id!!)

        assertEquals(listOf(steam.id), stored.map { it.id })
        assertTrue(steam.tracked)
    }

    @Test
    fun `tracked link can be changed`() {
        val gog = service.addLink(10, GameStore.GOG, "1453375253")
        val steam = service.addLink(10, GameStore.STEAM, "413150")

        service.setTracked(steam.id!!)

        assertTrue(steam.tracked)
        assertEquals(false, gog.tracked)
    }

    @Test
    fun `edition label is trimmed, can be cleared and is length limited`() {
        val link = service.addLink(10, GameStore.GOG, "1453375253")

        service.setEdition(link.id!!, "  Anniversary Edition ")
        assertEquals("Anniversary Edition", link.edition)

        service.setEdition(link.id!!, "   ")
        assertEquals(null, link.edition)

        service.setEdition(link.id!!, "Standard Edition")
        assertEquals(null, link.edition)

        assertFailsWith<IllegalArgumentException> { service.setEdition(link.id!!, "x".repeat(101)) }
    }
}
