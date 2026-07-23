package uz.railway.ticketbot.railway

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import uz.railway.ticketbot.railway.dto.EticketTrainsListResponse
import uz.railway.ticketbot.railway.mapper.EticketMapper
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Parses the REAL response of POST /api/v3/handbook/trains/list captured
 * from live eticket.railway.uz browser traffic (2026-07-23, Urgench ->
 * Tashkent, 20.08.2026): the per-date train list the whole search starts
 * from.
 */
class EticketTrainsListParsingTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val mapper = EticketMapper()

    private fun load(): EticketTrainsListResponse =
        javaClass.getResourceAsStream("/eticket/trains-list-response.json")!!.use {
            objectMapper.readValue(it, EticketTrainsListResponse::class.java)
        }

    @Test
    fun `parses all trains of the captured real response`() {
        val trains = assertNotNull(load().data?.directions?.forward).trains

        assertEquals(listOf("126Ч", "056Ж", "751М"), trains.map { it.number })
        assertEquals(
            LocalDateTime.of(2026, 8, 20, 19, 20),
            mapper.parseDateTime(trains.first { it.number == "056Ж" }.departureDate)
        )
    }

    @Test
    fun `car summaries carry free seat counts and tariffs`() {
        val trains = load().data!!.directions!!.forward!!.trains
        val train126 = trains.first { it.number == "126Ч" }

        assertEquals(listOf("Sleeper", "Coupe"), train126.cars.map { it.type })
        assertEquals(102, train126.cars.first { it.type == "Sleeper" }.freeSeats)
        assertEquals(BigDecimal(348550), train126.cars.first { it.type == "Coupe" }.tariffs.single().tariff)
    }

    @Test
    fun `sold-out train has an empty cars list so its details call can be skipped`() {
        val trains = load().data!!.directions!!.forward!!.trains
        assertTrue(trains.first { it.number == "751М" }.cars.isEmpty())
    }
}
