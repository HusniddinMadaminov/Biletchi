package uz.railway.ticketbot.railway

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uz.railway.ticketbot.config.RailwayProperties
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.railway.dto.EticketTrainDetailsResponse
import uz.railway.ticketbot.railway.dto.EticketTrainsListResponse
import uz.railway.ticketbot.railway.mapper.EticketMapper
import uz.railway.ticketbot.search.TicketFilters
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the full verified search flow (trains/list -> per-train
 * handbook/trains) against the two REAL captured responses and checks the
 * resulting lower-seat offers.
 */
class EticketRailwayProviderTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val date = LocalDate.of(2026, 8, 20)

    private lateinit var client: EticketRailwayClient
    private lateinit var provider: EticketRailwayProvider

    private fun <T> loadFixture(path: String, type: Class<T>): T =
        javaClass.getResourceAsStream(path)!!.use { objectMapper.readValue(it, type) }

    @BeforeEach
    fun setUp() {
        client = mockk()
        provider = EticketRailwayProvider(client, EticketMapper(), RailwayProperties(), TicketBotProperties())

        coEvery { client.getTrainsList("2900790", "2900000", date) } returns
            loadFixture("/eticket/trains-list-response.json", EticketTrainsListResponse::class.java)
        // The details fixture is the captured 056Ж response; serve it for any train number requested.
        coEvery { client.getTrainDetails("2900790", "2900000", date, any()) } returns
            loadFixture("/eticket/handbook-trains-response.json", EticketTrainDetailsResponse::class.java)
    }

    @Test
    fun `search follows list-then-details flow and returns lower-seat offers`() = runTest {
        val offers = provider.searchOffersForDate("2900790", "Urganch", "2900000", "Toshkent", date, TicketFilters.NONE)

        assertTrue(offers.isNotEmpty())
        val first = offers.first()
        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 20), first.departureTime)
        assertEquals("Sleeper", first.carType)
        // Car 09's lower (odd) seats from the captured places[] data.
        val car09 = offers.first { it.carNumber == "09" }
        assertEquals(listOf(37, 39, 41, 43, 45, 47, 49), car09.lowerSeatNumbers)
    }

    @Test
    fun `sold-out train from the list never triggers a details call`() = runTest {
        provider.searchOffersForDate("2900790", "Urganch", "2900000", "Toshkent", date, TicketFilters.NONE)

        // 751М has cars: [] in the captured list response - no details call for it.
        coVerify(exactly = 0) { client.getTrainDetails(any(), any(), any(), "751М") }
        coVerify(exactly = 1) { client.getTrainDetails(any(), any(), any(), "126Ч") }
        coVerify(exactly = 1) { client.getTrainDetails(any(), any(), any(), "056Ж") }
    }

    @Test
    fun `pinned train number filter narrows the details calls`() = runTest {
        val filters = TicketFilters(trainNumbers = setOf("056Ж"))

        provider.searchOffersForDate("2900790", "Urganch", "2900000", "Toshkent", date, filters)

        coVerify(exactly = 1) { client.getTrainDetails(any(), any(), any(), "056Ж") }
        coVerify(exactly = 0) { client.getTrainDetails(any(), any(), any(), "126Ч") }
    }
}
