package uz.railway.ticketbot.railway

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import uz.railway.ticketbot.railway.dto.EticketTrainDetailsResponse
import uz.railway.ticketbot.railway.mapper.EticketMapper
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Parses the REAL response of POST /api/v1/handbook/trains captured from
 * live eticket.railway.uz browser traffic (2026-07-23, Urgench -> Tashkent,
 * train 056Ж) and verifies that the seat data the whole bot depends on -
 * free seat numbers per car, and the odd-number = lower berth rule - comes
 * through correctly.
 */
class EticketTrainDetailsParsingTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val mapper = EticketMapper()

    private fun load(): EticketTrainDetailsResponse =
        javaClass.getResourceAsStream("/eticket/handbook-trains-response.json")!!.use {
            objectMapper.readValue(it, EticketTrainDetailsResponse::class.java)
        }

    @Test
    fun `parses the captured real response into typed DTOs`() {
        val response = load()

        assertNull(response.error)
        val train = assertNotNull(response.data?.train)
        assertEquals("056Ж", train.number)
        assertEquals(1, train.carGroup.size)

        val group = train.carGroup.first()
        assertEquals("Sleeper", group.typeShow)
        assertEquals(BigDecimal(245140), group.tariff)
        assertEquals(listOf("09", "10", "11", "12", "13", "14"), group.cars.map { it.number })

        val route = assertNotNull(response.data?.route)
        assertEquals("2900790", route.depStationCode)
        assertEquals("2900000", route.arvStationCode)
    }

    @Test
    fun `free seats come through and odd numbers are identified as lower berths`() {
        val train = load().data!!.train!!
        val car11 = train.carGroup.first().cars.first { it.number == "11" }

        assertEquals(33, car11.places.size)

        val lowerSeats = car11.places.filter { RailwaySeat.isLowerSeat(it) }.sorted()
        assertEquals(listOf(5, 35, 37, 39, 41, 43, 45, 47, 51, 53), lowerSeats)
    }

    @Test
    fun `car 09 has lower seats even though the site classifies most as lateral`() {
        val train = load().data!!.train!!
        val car09 = train.carGroup.first().cars.first { it.number == "09" }

        val lowerSeats = car09.places.filter { RailwaySeat.isLowerSeat(it) }.sorted()
        assertEquals(listOf(37, 39, 41, 43, 45, 47, 49), lowerSeats)
    }

    @Test
    fun `departure and arrival timestamps parse from the dotted format`() {
        val train = load().data!!.train!!

        assertEquals(LocalDateTime.of(2026, 8, 20, 19, 20), mapper.parseDateTime(train.departureDate))
        assertEquals(LocalDateTime.of(2026, 8, 21, 8, 22), mapper.parseDateTime(train.arrivalDate))
    }
}
