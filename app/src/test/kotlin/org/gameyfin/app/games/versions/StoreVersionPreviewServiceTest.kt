package org.gameyfin.app.games.versions

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StoreVersionPreviewServiceTest {
    private val provider = mockk<GameVersionProvider> { every { store } returns GameStore.GOG }
    private val statusService = mockk<GameVersionStatusService>()
    private val service = StoreVersionPreviewService(listOf(provider), statusService)

    private val versions = listOf(
        StoreVersionInfo("b2", "", "2.0"),
        StoreVersionInfo("b1", "", "1.0")
    )

    @Test
    fun `returns fetched versions and the index of the detected one`() {
        every { provider.fetchVersions("987") } returns versions
        every { statusService.detectInstalledValues(10, GameStore.GOG, "987") } returns listOf("b1")

        val result = service.preview(10, GameStore.GOG, "https://www.gogdb.org/product/987")

        assertEquals(listOf("2.0", "1.0"), result.versions.map { it.label })
        assertEquals(1, result.detectedIndex)
    }

    @Test
    fun `detection problems do not hide the versions`() {
        every { provider.fetchVersions("987") } returns versions
        every { statusService.detectInstalledValues(10, GameStore.GOG, "987") } throws IllegalStateException("boom")

        val result = service.preview(10, GameStore.GOG, "987")

        assertEquals(2, result.versions.size)
        assertNull(result.detectedIndex)
    }

    @Test
    fun `rejects invalid IDs without contacting the store`() {
        assertFailsWith<IllegalArgumentException> { service.preview(10, GameStore.GOG, "nope") }
        verify(exactly = 0) { provider.fetchVersions(any()) }
    }

    @Test
    fun `fails when no provider exists for the store`() {
        assertFailsWith<IllegalStateException> { service.preview(10, GameStore.STEAM, "123") }
    }
}
