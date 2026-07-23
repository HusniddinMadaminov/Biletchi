package uz.railway.ticketbot.search

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TicketSearchResultFingerprintTest {

    private val date = LocalDate.of(2026, 7, 28)

    // Test 11 (spec section 27): identical offers must produce identical fingerprints, so
    // the dedup check (SubscriptionNotificationRepository.existsBySubscriptionIdAndFingerprint)
    // recognizes a repeat and skips sending a second notification.
    @Test
    fun `identical offers produce identical fingerprints`() {
        val a = sampleOffer(date, listOf(9, 15))
        val b = sampleOffer(date, listOf(15, 9)) // different seat order, same set
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test
    fun `different seat sets produce different fingerprints`() {
        val a = sampleOffer(date, listOf(9, 15))
        val b = sampleOffer(date, listOf(9, 21))
        assertNotEquals(a.fingerprint, b.fingerprint)
    }

    @Test
    fun `different dates produce different fingerprints`() {
        val a = sampleOffer(date, listOf(9))
        val b = sampleOffer(date.plusDays(1), listOf(9))
        assertNotEquals(a.fingerprint, b.fingerprint)
    }
}
