package uz.railway.ticketbot.bot.message

import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.subscription.SubscriptionStatus
import uz.railway.ticketbot.subscription.TicketSubscription
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class SubscriptionMessageFormatterTest {

    private val formatter = SubscriptionMessageFormatter()
    private val today = LocalDate.of(2026, 8, 7)

    private fun baseSubscription(lastCheckedAt: Instant?) = TicketSubscription(
        id = 1L,
        telegramUserId = 42L,
        fromStationCode = "HAZARASP",
        fromStationName = "Hazarasp",
        toStationCode = "TASHKENT",
        toStationName = "Tashkent",
        startDate = today,
        endDate = today.plusDays(30),
        currentBestDate = today,
        currentBestResult = null,
        currentBestResultFingerprint = null,
        filters = TicketFilters.NONE,
        status = SubscriptionStatus.ACTIVE,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        lastCheckedAt = lastCheckedAt,
        nextCheckAt = null,
        errorCount = 0,
        lastError = null
    )

    // The user needs second-level precision to tell whether monitoring is actually running right now.
    // lastCheckedAt is stored as UTC Instant but must display in Uzbekistan's UTC+5 wall-clock time.
    @Test
    fun `shows last checked time down to the second in Tashkent time (UTC+5)`() {
        val checkedAtUtc = Instant.parse("2026-07-26T09:03:27Z")

        val text = formatter.format(baseSubscription(checkedAtUtc))

        assertTrue(text.contains("🔄 Oxirgi tekshiruv: 26.07.2026 14:03:27"))
    }

    // Previously this line was omitted entirely when null, leaving no way to tell the bot hadn't checked yet.
    @Test
    fun `shows an explicit not-yet-checked message when lastCheckedAt is null`() {
        val text = formatter.format(baseSubscription(null))

        assertTrue(text.contains("🔄 Oxirgi tekshiruv: hali tekshirilmagan"))
    }
}
