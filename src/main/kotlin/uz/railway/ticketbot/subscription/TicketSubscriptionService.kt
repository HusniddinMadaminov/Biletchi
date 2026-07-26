package uz.railway.ticketbot.subscription

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchOutcome
import uz.railway.ticketbot.search.TicketSearchResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class TicketSubscriptionService(
    private val repository: TicketSubscriptionRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(TicketSubscriptionService::class.java)

    /**
     * Creates a subscription from an initial search outcome (spec section
     * 6 and 11). If a match was already found on [TicketSearchRequest.startDate]
     * itself, there is nothing earlier to monitor for, so the subscription
     * is created COMPLETED. Otherwise it is ACTIVE and, per section 11, a
     * "nothing found yet" outcome still yields a subscription - just one
     * with a null currentBestDate that watches the whole original range.
     */
    @Transactional
    fun create(
        telegramUserId: Long,
        outcome: TicketSearchOutcome
    ): TicketSubscription {
        val request = outcome.request
        val bestDate = outcome.bestDate
        val status = if (bestDate != null && bestDate == request.startDate) {
            SubscriptionStatus.COMPLETED
        } else {
            SubscriptionStatus.ACTIVE
        }
        val bestResult = outcome.offers.firstOrNull()

        val entity = TicketSubscriptionEntity(
            telegramUserId = telegramUserId,
            fromStationCode = request.fromStationCode,
            fromStationName = request.fromStationName,
            toStationCode = request.toStationCode,
            toStationName = request.toStationName,
            startDate = request.startDate,
            endDate = request.endDate,
            currentBestDate = bestDate,
            currentResultJson = bestResult?.let { objectMapper.writeValueAsString(it) },
            currentFingerprint = bestResult?.fingerprint,
            filtersJson = objectMapper.writeValueAsString(request.filters),
            status = status,
            nextCheckAt = if (status == SubscriptionStatus.ACTIVE) Instant.now(clock) else null
        )
        return toDomain(repository.save(entity))
    }

    fun findForUser(telegramUserId: Long): List<TicketSubscription> =
        repository.findByTelegramUserId(telegramUserId).map { toDomain(it) }

    fun findByIdForUser(id: Long, telegramUserId: Long): TicketSubscription? =
        repository.findByIdAndTelegramUserId(id, telegramUserId)?.let { toDomain(it) }

    @Transactional
    fun pause(id: Long, telegramUserId: Long): TicketSubscription? {
        val entity = repository.findByIdAndTelegramUserId(id, telegramUserId) ?: return null
        if (entity.status == SubscriptionStatus.ACTIVE) {
            entity.status = SubscriptionStatus.PAUSED
            entity.updatedAt = Instant.now(clock)
        }
        return toDomain(entity)
    }

    @Transactional
    fun resume(id: Long, telegramUserId: Long): TicketSubscription? {
        val entity = repository.findByIdAndTelegramUserId(id, telegramUserId) ?: return null
        if (entity.status == SubscriptionStatus.PAUSED) {
            entity.status = SubscriptionStatus.ACTIVE
            entity.nextCheckAt = Instant.now(clock)
            entity.updatedAt = Instant.now(clock)
        }
        return toDomain(entity)
    }

    @Transactional
    fun cancel(id: Long, telegramUserId: Long): TicketSubscription? {
        val entity = repository.findByIdAndTelegramUserId(id, telegramUserId) ?: return null
        entity.status = SubscriptionStatus.CANCELLED
        entity.updatedAt = Instant.now(clock)
        return toDomain(entity)
    }

    @Transactional
    fun findDueForMonitoring(now: Instant, batchSize: Int): List<TicketSubscription> =
        repository.findDueForMonitoring(now, PageRequest.of(0, batchSize)).map { toDomain(it) }

    @Transactional
    fun markChecked(id: Long, checkedAt: Instant, nextCheckAt: Instant) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.lastCheckedAt = checkedAt
        entity.nextCheckAt = nextCheckAt
        entity.updatedAt = checkedAt
    }

    /** Persists an earlier date found during monitoring (spec section 7.3 / 10). */
    @Transactional
    fun applyEarlierResult(id: Long, newBestDate: LocalDate, newBestOffers: List<TicketSearchResult>) {
        val entity = repository.findById(id).orElse(null) ?: return
        val bestResult = newBestOffers.firstOrNull()
        entity.currentBestDate = newBestDate
        entity.currentResultJson = bestResult?.let { objectMapper.writeValueAsString(it) }
        entity.currentFingerprint = bestResult?.fingerprint
        entity.updatedAt = Instant.now(clock)
        if (newBestDate == entity.startDate) {
            entity.status = SubscriptionStatus.COMPLETED
            entity.nextCheckAt = null
        }
    }

    @Transactional
    fun markCompleted(id: Long) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.status = SubscriptionStatus.COMPLETED
        entity.nextCheckAt = null
        entity.updatedAt = Instant.now(clock)
    }

    @Transactional
    fun markExpired(id: Long) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.status = SubscriptionStatus.EXPIRED
        entity.nextCheckAt = null
        entity.updatedAt = Instant.now(clock)
    }

    /**
     * A single subscription's failure must never stop other subscriptions
     * from being checked (spec section 13). Transient/auth railway.uz
     * errors (spec section 19, test 12) are recorded for observability but
     * deliberately do NOT flip status away from ACTIVE - the same window
     * is simply retried on the next 10-minute cycle.
     */
    @Transactional
    fun recordCheckError(id: Long, message: String, checkedAt: Instant, nextCheckAt: Instant) {
        val entity = repository.findById(id).orElse(null) ?: return
        entity.errorCount += 1
        entity.lastError = message
        entity.lastCheckedAt = checkedAt
        entity.nextCheckAt = nextCheckAt
        entity.updatedAt = checkedAt
    }

    fun readFilters(entity: TicketSubscriptionEntity): TicketFilters =
        try {
            objectMapper.readValue(entity.filtersJson, TicketFilters::class.java)
        } catch (ex: Exception) {
            log.warn("Failed to parse filters for subscription {}, falling back to defaults: {}", entity.id, ex.message)
            TicketFilters.NONE
        }

    fun readCurrentResult(entity: TicketSubscriptionEntity): TicketSearchResult? =
        entity.currentResultJson?.let {
            try {
                objectMapper.readValue(it, TicketSearchResult::class.java)
            } catch (ex: Exception) {
                // Cached display data only (currentBestDate/status carry the real state) - a stale or
                // incompatible blob must not break loading the rest of this subscription, or a whole
                // user's subscription list. The next monitoring/search cycle overwrites it anyway.
                log.warn("Failed to parse cached result for subscription {}: {}", entity.id, ex.message)
                null
            }
        }

    fun toDomain(entity: TicketSubscriptionEntity): TicketSubscription = TicketSubscription(
        id = requireNotNull(entity.id),
        telegramUserId = entity.telegramUserId,
        fromStationCode = entity.fromStationCode,
        fromStationName = entity.fromStationName,
        toStationCode = entity.toStationCode,
        toStationName = entity.toStationName,
        startDate = entity.startDate,
        endDate = entity.endDate,
        currentBestDate = entity.currentBestDate,
        currentBestResult = readCurrentResult(entity),
        currentBestResultFingerprint = entity.currentFingerprint,
        filters = readFilters(entity),
        status = entity.status,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        lastCheckedAt = entity.lastCheckedAt,
        nextCheckAt = entity.nextCheckAt,
        errorCount = entity.errorCount,
        lastError = entity.lastError
    )
}
