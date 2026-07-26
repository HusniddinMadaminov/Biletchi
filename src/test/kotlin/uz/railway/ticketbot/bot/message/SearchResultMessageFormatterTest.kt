package uz.railway.ticketbot.bot.message

import uz.railway.ticketbot.search.SeatMode
import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchOutcome
import uz.railway.ticketbot.search.sampleOffer
import uz.railway.ticketbot.search.sampleRequest
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertTrue

class SearchResultMessageFormatterTest {

    private val formatter = SearchResultMessageFormatter()
    private val today = LocalDate.of(2026, 8, 7)

    // Offers on the same train (same trainNumber) must render under one grouped header,
    // each vagon boxed separately - this is what makes multi-vagon results readable.
    @Test
    fun `groups offers from the same train under a single numbered header`() {
        val request = sampleRequest(today, today.plusDays(30)).copy(filters = TicketFilters(seatMode = SeatMode.ANY))
        val outcome = TicketSearchOutcome(
            request,
            listOf(
                sampleOffer(today, listOf(54), trainNumber = "058Ь", departureTime = LocalTime.of(20, 7)),
                sampleOffer(today, listOf(38, 46), trainNumber = "058Ь", departureTime = LocalTime.of(20, 7)),
                sampleOffer(today, listOf(36), trainNumber = "076Ф", departureTime = LocalTime.of(22, 35))
            )
        )

        val text = formatter.formatFound(outcome)

        assertTrue(text.contains("1-poyezd: 058Ь"))
        assertTrue(text.contains("2-poyezd: 076Ф"))
        assertTrue(text.contains("━━━━━━━━━━━━━━━━━━"))
        // Two boxed vagons under the first train, one under the second.
        assertTrue(text.split("┌ 🚃").size - 1 == 3)
        assertTrue(text.trim().endsWith("🎫 Chipta sotib olish uchun poyezd va vagonni tanlang."))
    }

    // The message uses HTML parse mode - dynamic station/train text must be escaped so it can't break the markup.
    @Test
    fun `escapes html-sensitive characters in station names`() {
        val request = sampleRequest(today, today.plusDays(30)).copy(
            fromStationName = "A & B <station>",
            filters = TicketFilters(seatMode = SeatMode.LOWER)
        )
        val outcome = TicketSearchOutcome(request, listOf(sampleOffer(today, listOf(9))))

        val text = formatter.formatFound(outcome)

        assertTrue(text.contains("A &amp; B &lt;station&gt;"))
        assertTrue(!text.contains("<station>"))
    }
}
