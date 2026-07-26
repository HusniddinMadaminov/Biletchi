package uz.railway.ticketbot.monitoring

import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.subscription.TicketSubscription
import uz.railway.ticketbot.subscription.TicketSubscriptionRepository
import uz.railway.ticketbot.subscription.TicketSubscriptionService
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Claims a batch of due subscriptions for this scheduler tick (spec
 * sections 16 and 20). [TicketSubscriptionRepository.findDueForMonitoring]
 * takes a PESSIMISTIC_WRITE + SKIP LOCKED lock for the duration of this
 * transaction; while it's held, nextCheckAt is advanced immediately so
 * other instances (or the next tick, if processing outlives the check
 * interval) never pick the same row up twice. The lock is released as soon
 * as this method returns - the slow, per-subscription railway.uz calls
 * happen afterwards, outside any database transaction.
 */
@Service
class MonitoringLockService(
    private val repository: TicketSubscriptionRepository,
    private val subscriptionService: TicketSubscriptionService,
    private val properties: TicketBotProperties,
    private val clock: Clock
) {
    @Transactional
    fun claimDueSubscriptions(batchSize: Int): List<TicketSubscription> {
        val now = Instant.now(clock)
        val due = repository.findDueForMonitoring(now, PageRequest.of(0, batchSize))
        val provisionalNext = now.plus(Duration.ofMinutes(properties.monitoring.intervalMinutes))
        due.forEach { it.nextCheckAt = provisionalNext }
        return due.map(subscriptionService::toDomain)
    }
}
