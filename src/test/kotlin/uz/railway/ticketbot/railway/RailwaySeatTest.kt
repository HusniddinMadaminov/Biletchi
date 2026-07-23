package uz.railway.ticketbot.railway

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class RailwaySeatTest {

    @ParameterizedTest
    @CsvSource(
        "1,true", "3,true", "5,true", "7,true", "9,true", "11,true", "13,true", "15,true",
        "2,false", "4,false", "6,false", "8,false", "10,false", "12,false", "14,false", "16,false"
    )
    fun `odd seat numbers are lower, even are upper`(seatNumber: Int, expectedLower: Boolean) {
        assertEquals(expectedLower, RailwaySeat.isLowerSeat(seatNumber))
        assertEquals(expectedLower, RailwaySeat(seatNumber, isAvailable = true).isLower)
    }

    // Test 4 (spec section 27): mixed seats 2,4,7,8,11 -> lower seats are 7 and 11.
    @org.junit.jupiter.api.Test
    fun `mixed seat list keeps only odd numbers as lower`() {
        val seats = listOf(2, 4, 7, 8, 11).map { RailwaySeat(it, isAvailable = true) }
        val lower = seats.filter { it.isLower }.map { it.number }
        assertEquals(listOf(7, 11), lower)
    }

    // Test 3: only even seats present -> no lower seat.
    @org.junit.jupiter.api.Test
    fun `all even seats yields no lower seat`() {
        val seats = listOf(2, 4, 8, 12).map { RailwaySeat(it, isAvailable = true) }
        assertEquals(emptyList(), seats.filter { it.isLower })
    }
}
