package org.gameyfin.app.games.versions

import io.mockk.*
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.pluginapi.tasks.TaskContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionRefreshTaskTest {
    private val registry = mockk<TaskRegistry>(relaxed = true)
    private val links = mockk<GameStoreLinkRepository>()
    private val cache = mockk<StoreVersionCacheService>()
    private val context = mockk<TaskContext>(relaxed = true)

    private fun task(vararg providers: GameVersionProvider) =
        VersionRefreshTask(registry, links, cache, providers.toList())

    @Test
    fun `refreshes and caches versions for every link`() {
        val gog = mockk<GameVersionProvider>()
        val versions = listOf(StoreVersionInfo("build-1", label = "1.0"))
        every { gog.store } returns GameStore.GOG
        every { gog.fetchVersions("123") } returns versions
        every { links.findAll() } returns listOf(
            GameStoreLink(id = 1, gameId = 10, store = GameStore.GOG, storeAppId = "123")
        )
        every { cache.update(1, versions, any()) } just Runs

        val result = task(gog).execute(context)

        assertEquals("Refreshed versions for 1 store link(s)", result.summary)
        verify { cache.update(1, versions, any()) }
        verify { context.throwIfCancelled() }
    }

    @Test
    fun `refreshes a single link on demand`() {
        val steam = mockk<GameVersionProvider>()
        val versions = listOf(StoreVersionInfo("build-2", label = "2.0"))
        every { steam.store } returns GameStore.STEAM
        every { steam.fetchVersions("100") } returns versions
        every { links.findById(5) } returns java.util.Optional.of(
            GameStoreLink(id = 5, gameId = 10, store = GameStore.STEAM, storeAppId = "100")
        )
        every { cache.update(5, versions, any()) } just Runs

        task(steam).refreshLink(5)

        verify { cache.update(5, versions, any()) }
    }

    @Test
    fun `refreshing an unknown link fails`() {
        every { links.findById(99) } returns java.util.Optional.empty()

        assertFailsWith<NoSuchElementException> { task().refreshLink(99) }
    }

    @Test
    fun `reports failed refresh as task failure and preserves other link processing`() {
        val steam = mockk<GameVersionProvider>()
        val gog = mockk<GameVersionProvider>()
        val versions = listOf(StoreVersionInfo("gog-build", label = "1.0"))
        every { steam.store } returns GameStore.STEAM
        every { steam.fetchVersions("100") } throws IllegalStateException("source unavailable")
        every { gog.store } returns GameStore.GOG
        every { gog.fetchVersions("200") } returns versions
        every { links.findAll() } returns listOf(
            GameStoreLink(id = 1, gameId = 10, store = GameStore.STEAM, storeAppId = "100"),
            GameStoreLink(id = 2, gameId = 10, store = GameStore.GOG, storeAppId = "200")
        )
        every { cache.update(2, versions, any()) } just Runs

        val error = assertFailsWith<IllegalStateException> { task(steam, gog).execute(context) }

        assertEquals("Version refresh updated 1 of 2 links; 1 failed", error.message)
        verify { cache.update(2, versions, any()) }
        verify(exactly = 0) { cache.update(1, any(), any()) }
    }
}
