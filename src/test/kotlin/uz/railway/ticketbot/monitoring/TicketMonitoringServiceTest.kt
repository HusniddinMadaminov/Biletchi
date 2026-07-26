package uz.railway.ticketbot.monitoring

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.notification.NotificationService
import uz.railway.ticketbot.railway.RailwayProvider
import uz.railway.ticketbot.railway.exception.RailwayTransientException
import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.sampleOffer
import uz.railway.ticketbot.subscription.SubscriptionStatus
import uz.railway.ticketbot.subscription.TicketSubscription
import uz.railway.ticketbot.subscription.TicketSubscriptionService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals

class TicketMonitoringServiceTest {

    private val fixedToday = LocalDate.of(2026, 7, 25)
    private val clock = Clock.fixed(fixedToday.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    private lateinit var lockService: MonitoringLockService
    private lateinit var railwayProvider: RailwayProvider
    private lateinit var subscriptionService: TicketSubscriptionService
    private lateinit var notificationService: NotificationService
    private lateinit var service: TicketMonitoringService

    @BeforeEach
    fun setUp() {
        lockService = mockk()
        railwayProvider = mockk()
        subscriptionService = mockk(relaxed = true)
        notificationService = mockk(relaxed = true)
        service = TicketMonitoringService(lockService, railwayProvider, subscriptionService, notificationService, TicketBotProperties(), clock)
    }

    private fun subscription(
        id: Long = 1L,
        startDate: LocalDate = fixedToday,
        endDate: LocalDate = fixedToday.plusDays(30),
        currentBestDate: LocalDate? = fixedToday.plusDays(3)
    ) = TicketSubscription(
        id = id,
        telegramUserId = 100L,
        fromStationCode = "TASHKENT",
        fromStationName = "Toshkent",
        toStationCode = "URGANCH",
        toStationName = "Urganch",
        startDate = startDate,
        endDate = endDate,
        currentBestDate = currentBestDate,
        currentBestResult = null,
        currentBestResultFingerprint = null,
        filters = TicketFilters.NONE,
        status = SubscriptionStatus.ACTIVE,
        createdAt = Instant.now(clock),
        updatedAt = Instant.now(clock),
        lastCheckedAt = null,
        nextCheckAt = Instant.now(clock),
        errorCount = 0,
        lastError = null
    )

    // Test 5 (spec section 27): an earlier date is found during monitoring -> notify + persist new best date.
    @Test
    fun `earlier date found triggers notification and updates currentBestDate`() = runTest {
        val sub = subscription(currentBestDate = LocalDate.of(2026, 7, 28))
        val earlierDate = LocalDate.of(2026, 7, 26)
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } returns listOf(sampleOffer(earlierDate, listOf(9)))

        service.checkSubscription(sub)

        coVerify(exactly = 1) { notificationService.sendEarlierSeatFound(sub, LocalDate.of(2026, 7, 28), any()) }
        verify(exactly = 1) { subscriptionService.applyEarlierResult(sub.id, earlierDate, any()) }
    }

    // Test 7: a later (not earlier) date is found -> ignored, no notification, no update.
    @Test
    fun `later date found is ignored`() = runTest {
        val sub = subscription(currentBestDate = LocalDate.of(2026, 7, 28))
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } returns listOf(sampleOffer(LocalDate.of(2026, 7, 30), listOf(9)))

        service.checkSubscription(sub)

        coVerify(exactly = 0) { notificationService.sendEarlierSeatFound(any(), any(), any()) }
        verify(exactly = 0) { subscriptionService.applyEarlierResult(any(), any(), any()) }
    }

    // Test 6: same date as currentBestDate is never "earlier" -> no notification even if a new offer appears there.
    @Test
    fun `same date as currentBestDate never counts as earlier`() = runTest {
        val sub = subscription(currentBestDate = LocalDate.of(2026, 7, 28))
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } returns listOf(sampleOffer(LocalDate.of(2026, 7, 28), listOf(21)))

        service.checkSubscription(sub)

        coVerify(exactly = 0) { notificationService.sendEarlierSeatFound(any(), any(), any()) }
    }

    // Test 8: currentBestDate == startDate -> monitoring is already done, provider must not even be called.
    @Test
    fun `currentBestDate equal to startDate completes the subscription without querying`() = runTest {
        val sub = subscription(startDate = fixedToday, currentBestDate = fixedToday)

        service.checkSubscription(sub)

        coVerify(exactly = 0) { railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { subscriptionService.markCompleted(sub.id) }
    }

    // Test 9: startDate has passed -> effectiveStartDate is clamped to today, and the window end is
    // currentBestDate itself (inclusive), so that date's offer gets re-verified every cycle too.
    @Test
    fun `effective window is clamped to today and includes currentBestDate`() = runTest {
        val sub = subscription(
            startDate = LocalDate.of(2026, 7, 20),
            currentBestDate = LocalDate.of(2026, 7, 30)
        )
        val startSlot = slot<LocalDate>()
        val endSlot = slot<LocalDate>()
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), capture(startSlot), capture(endSlot), any())
        } returns listOf(sampleOffer(LocalDate.of(2026, 7, 30), listOf(9)))

        service.checkSubscription(sub)

        assertEquals(fixedToday, startSlot.captured)
        assertEquals(LocalDate.of(2026, 7, 30), endSlot.captured)
    }

    // Regression: the offer previously found on currentBestDate can be sold out by the next check.
    // Since currentBestDate is now part of the scanned window, that disappearance is detected (empty
    // result) and the subscription re-anchors forward to the next available date instead of keeping a
    // stale currentBestDate on display. This is strictly later than before, so it must never notify.
    @Test
    fun `best offer disappearing re-anchors forward to a new later date without notifying`() = runTest {
        val sub = subscription(currentBestDate = LocalDate.of(2026, 7, 28))
        val laterDate = LocalDate.of(2026, 8, 2)
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } returns emptyList() andThen listOf(sampleOffer(laterDate, listOf(9)))

        service.checkSubscription(sub)

        verify(exactly = 1) { subscriptionService.applyEarlierResult(sub.id, laterDate, any()) }
        coVerify(exactly = 0) { notificationService.sendEarlierSeatFound(any(), any(), any()) }
        verify(exactly = 0) { subscriptionService.clearBestResult(any()) }
    }

    // Regression: if nothing at all remains in the subscription's range after the best offer vanishes,
    // fall back to the "nothing found yet" state (spec section 11) rather than leaving a stale date.
    @Test
    fun `best offer disappearing with nothing left anywhere clears the best result`() = runTest {
        val sub = subscription(currentBestDate = LocalDate.of(2026, 7, 28))
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } returns emptyList()

        service.checkSubscription(sub)

        verify(exactly = 1) { subscriptionService.clearBestResult(sub.id) }
        verify(exactly = 0) { subscriptionService.applyEarlierResult(any(), any(), any()) }
    }

    // Regression: when currentBestDate is already the last day of the range, there is nothing to scan
    // forward into - the result must be cleared without an extra, pointless provider call.
    @Test
    fun `best offer disappearing at the end of the range clears result without an extra query`() = runTest {
        val sub = subscription(currentBestDate = fixedToday.plusDays(30), endDate = fixedToday.plusDays(30))
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } returns emptyList()

        service.checkSubscription(sub)

        coVerify(exactly = 1) { railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { subscriptionService.clearBestResult(sub.id) }
    }

    // Test 12: a railway.uz error must not crash the check or flip subscription status - just recorded for next cycle.
    @Test
    fun `railway error is recorded but does not mark the subscription as ERROR`() = runTest {
        val sub = subscription(currentBestDate = LocalDate.of(2026, 7, 28))
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } throws RailwayTransientException("HTTP 503")

        service.checkSubscription(sub)

        verify(exactly = 1) { subscriptionService.recordCheckError(sub.id, any(), any(), any()) }
        verify(exactly = 0) { subscriptionService.markExpired(any()) }
        coVerify(exactly = 0) { notificationService.sendEarlierSeatFound(any(), any(), any()) }
    }

    // Test 13 (spec section 13): a failure on one subscription must not stop the rest of the batch.
    @Test
    fun `a failing subscription does not stop the batch from processing the next one`() = runTest {
        val failing = subscription(id = 1L, currentBestDate = LocalDate.of(2026, 7, 28))
        val healthy = subscription(id = 2L, currentBestDate = LocalDate.of(2026, 7, 28))
        every { lockService.claimDueSubscriptions(any()) } returns listOf(failing, healthy)
        coEvery {
            railwayProvider.findNearestLowerSeat(any(), any(), any(), any(), any(), any(), any())
        } throws RailwayTransientException("boom") andThen listOf(sampleOffer(LocalDate.of(2026, 7, 26), listOf(9)))

        service.checkDueSubscriptions(50)

        verify(exactly = 1) { subscriptionService.recordCheckError(failing.id, any(), any(), any()) }
        verify(exactly = 1) { subscriptionService.applyEarlierResult(healthy.id, LocalDate.of(2026, 7, 26), any()) }
    }
}
