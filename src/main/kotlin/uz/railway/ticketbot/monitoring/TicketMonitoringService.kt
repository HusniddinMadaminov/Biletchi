package uz.railway.ticketbot.monitoring

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.notification.NotificationService
import uz.railway.ticketbot.railway.RailwayProvider
import uz.railway.ticketbot.railway.exception.RailwayException
import uz.railway.ticketbot.subscription.SubscriptionStatus
import uz.railway.ticketbot.subscription.TicketSubscription
import uz.railway.ticketbot.subscription.TicketSubscriptionService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Implements spec section 7: on every scheduler tick (interval configured
 * via ticketbot.monitoring.interval-cron / interval-minutes), for each
 * ACTIVE subscription, only the window from startDate up to
 * (currentBestDate - 1 day) - or the whole startDate..endDate range while
 * nothing has been found yet, per section 11 - is rescanned. A notification
 * only ever fires for a date strictly earlier than the current best
 * (section 8); reaching currentBestDate == startDate, or letting the whole
 * window lapse, ends monitoring (section 10).
 */
@Service
class TicketMonitoringService(
    private val lockService: MonitoringLockService,
    private val railwayProvider: RailwayProvider,
    private val subscriptionService: TicketSubscriptionService,
    private val notificationService: NotificationService,
    private val properties: TicketBotProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(TicketMonitoringService::class.java)
    private val checkInterval get() = Duration.ofMinutes(properties.monitoring.intervalMinutes)

    suspend fun checkDueSubscriptions(batchSize: Int) {
        val due = lockService.claimDueSubscriptions(batchSize)
        for (subscription in due) {
            try {
                checkSubscription(subscription)
            } catch (ex: Exception) {
                // A single subscription's failure must never block the rest of the batch (spec section 13).
                log.error("Monitoring check failed for subscription {}: {}", subscription.id, ex.message, ex)
                val now = Instant.now(clock)
                subscriptionService.recordCheckError(subscription.id, ex.message ?: ex.toString(), now, now.plus(checkInterval))
            }
        }
    }

    suspend fun checkSubscription(subscription: TicketSubscription) {
        if (subscription.status != SubscriptionStatus.ACTIVE) return

        val today = LocalDate.now(clock)

        val currentBestDate = subscription.currentBestDate
        if (currentBestDate != null && !currentBestDate.isAfter(today)) {
            // The previously found date has arrived or passed; there is nothing meaningful left to watch for.
            completeOrExpire(subscription, today)
            return
        }
        if (currentBestDate == null && today.isAfter(subscription.endDate)) {
            subscriptionService.markExpired(subscription.id)
            return
        }

        val effectiveStartDate = maxOf(subscription.startDate, today)
        val monitoringEndDate = currentBestDate?.minusDays(1) ?: subscription.endDate

        if (effectiveStartDate.isAfter(monitoringEndDate)) {
            completeOrExpire(subscription, today)
            return
        }

        val newOffers = try {
            railwayProvider.findNearestLowerSeat(
                fromStationCode = subscription.fromStationCode,
                fromStationName = subscription.fromStationName,
                toStationCode = subscription.toStationCode,
                toStationName = subscription.toStationName,
                startDate = effectiveStartDate,
                endDate = monitoringEndDate,
                filters = subscription.filters
            )
        } catch (ex: RailwayException) {
            val now = Instant.now(clock)
            log.warn("railway.uz error while monitoring subscription {}: {}", subscription.id, ex.message)
            subscriptionService.recordCheckError(subscription.id, ex.message ?: ex.toString(), now, now.plus(checkInterval))
            return
        }

        val now = Instant.now(clock)
        subscriptionService.markChecked(subscription.id, now, now.plus(checkInterval))

        if (newOffers.isEmpty()) return

        val newBestDate = newOffers.first().date
        val isEarlier = currentBestDate == null || newBestDate.isBefore(currentBestDate)
        if (!isEarlier) return

        notificationService.sendEarlierSeatFound(subscription, currentBestDate, newOffers)
        subscriptionService.applyEarlierResult(subscription.id, newBestDate, newOffers)
    }

    private fun completeOrExpire(subscription: TicketSubscription, today: LocalDate) {
        val currentBestDate = subscription.currentBestDate
        if (currentBestDate != null && currentBestDate == subscription.startDate) {
            subscriptionService.markCompleted(subscription.id)
        } else if (today.isAfter(subscription.endDate) || (currentBestDate != null && !currentBestDate.isAfter(today))) {
            subscriptionService.markExpired(subscription.id)
        } else {
            subscriptionService.markCompleted(subscription.id)
        }
    }
}
