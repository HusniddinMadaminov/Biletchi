package uz.railway.ticketbot.search

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NearestLowerSeatFinderTest {

    private val finder = NearestLowerSeatFinder(batchSize = 4)

    // Test 2 (spec section 27): no lower seat on 25-27 Jul, found on 28 Jul -> stop there.
    @Test
    fun `stops at the first date with a lower seat and does not look further`() = runTest {
        val start = LocalDate.of(2026, 7, 25)
        val end = LocalDate.of(2026, 8, 24)
        val queried = mutableListOf<LocalDate>()

        val result = finder.find(start, end) { date ->
            queried += date
            if (date == LocalDate.of(2026, 7, 28)) listOf(sampleOffer(date, listOf(15))) else emptyList()
        }

        assertEquals(LocalDate.of(2026, 7, 28), result.first().date)
        assertTrue(queried.none { it.isAfter(LocalDate.of(2026, 7, 28)) }, "dates after the match must never be queried")
    }

    // Test 1: match on the very first (start) date.
    @Test
    fun `matches immediately on the start date`() = runTest {
        val start = LocalDate.of(2026, 7, 25)
        val result = finder.find(start, start.plusDays(30)) { date ->
            if (date == start) listOf(sampleOffer(date, listOf(9))) else emptyList()
        }
        assertEquals(start, result.first().date)
    }

    // Test 10: nothing found anywhere in the range.
    @Test
    fun `returns empty when no date in range matches`() = runTest {
        val start = LocalDate.of(2026, 7, 25)
        val end = LocalDate.of(2026, 8, 25)
        val result = finder.find(start, end) { emptyList() }
        assertTrue(result.isEmpty())
    }

    // A later date matching inside an earlier batch must not be beaten by a same-batch earlier date's absence.
    @Test
    fun `within a batch the earliest matching date wins even if later ones also match`() = runTest {
        val start = LocalDate.of(2026, 7, 25)
        val result = finder.find(start, start.plusDays(10)) { date ->
            when (date) {
                start.plusDays(1) -> listOf(sampleOffer(date, listOf(3)))
                start.plusDays(2) -> listOf(sampleOffer(date, listOf(5)))
                else -> emptyList()
            }
        }
        assertEquals(start.plusDays(1), result.first().date)
    }

    // Test 4/5 display ordering: multiple offers on the matched date are sorted per DISPLAY_ORDER.
    @Test
    fun `offers on the matched date are sorted by departure time then price`() = runTest {
        val start = LocalDate.of(2026, 7, 25)
        val late = sampleOffer(start, listOf(21, 27), trainNumber = "076", departureTime = java.time.LocalTime.of(22, 40), price = java.math.BigDecimal(275000))
        val early = sampleOffer(start, listOf(9, 15), trainNumber = "058", departureTime = java.time.LocalTime.of(20, 15), price = java.math.BigDecimal(346000))

        val result = finder.find(start, start) { listOf(late, early) }

        assertEquals(listOf("058", "076"), result.map { it.trainNumber })
    }
}
