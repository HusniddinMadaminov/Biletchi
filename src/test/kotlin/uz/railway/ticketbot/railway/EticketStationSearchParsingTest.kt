package uz.railway.ticketbot.railway

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Test
import uz.railway.ticketbot.railway.dto.EticketStationSearchResponse
import kotlin.test.assertEquals

/**
 * Parses the REAL response of POST /api/v1/handbook/stations/list captured
 * live from eticket.railway.uz (2026-07-26, query "ta").
 */
class EticketStationSearchParsingTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `parses the captured real station search response`() {
        val response = javaClass.getResourceAsStream("/eticket/stations-search-response.json")!!.use {
            objectMapper.readValue(it, EticketStationSearchResponse::class.java)
        }

        val stations = response.data!!.stations
        assertEquals(
            listOf("TASHKENT", "TASHKENT SOUTH", "TASHGUZAR", "TASHKENT CENTRAL", "TAHIATASH"),
            stations.map { it.name }
        )
        assertEquals("2900000", stations.first { it.name == "TASHKENT" }.code)
    }
}
