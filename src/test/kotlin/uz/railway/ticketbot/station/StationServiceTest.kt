package uz.railway.ticketbot.station

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import uz.railway.ticketbot.railway.RailwayProvider
import uz.railway.ticketbot.railway.RailwayStation
import kotlin.test.assertEquals

class StationServiceTest {

    private val repository: StationRepository = mockk(relaxed = true)
    private val railwayProvider: RailwayProvider = mockk()
    private val service = StationService(repository, railwayProvider)

    @Test
    fun `live results are title-cased and cached locally`() = runTest {
        coEvery { railwayProvider.searchStations("tash") } returns listOf(
            RailwayStation("2900000", "TASHKENT"),
            RailwayStation("2900002", "TASHKENT SOUTH")
        )

        val result = service.search("tash")

        assertEquals(listOf("Tashkent", "Tashkent South"), result.map { it.name })
        verify { repository.upsert("2900000", "Tashkent", "tashkent") }
        verify { repository.upsert("2900002", "Tashkent South", "tashkent south") }
    }

    @Test
    fun `falls back to the local cache when the live search fails`() = runTest {
        coEvery { railwayProvider.searchStations("tash") } throws RuntimeException("railway.uz unreachable")
        val cached = StationEntity(code = "2900000", name = "Tashkent", nameNormalized = "tashkent")
        every { repository.searchByNormalizedName("tash") } returns listOf(cached)

        val result = service.search("tash")

        assertEquals(listOf("Tashkent"), result.map { it.name })
    }

    @Test
    fun `blank query returns no results without calling the site`() = runTest {
        val result = service.search("   ")
        assertEquals(emptyList(), result)
    }
}
