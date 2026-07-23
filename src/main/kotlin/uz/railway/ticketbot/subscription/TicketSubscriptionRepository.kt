package uz.railway.ticketbot.subscription

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.time.Instant

interface TicketSubscriptionRepository : JpaRepository<TicketSubscriptionEntity, Long> {

    fun findByTelegramUserId(telegramUserId: Long): List<TicketSubscriptionEntity>

    fun findByIdAndTelegramUserId(id: Long, telegramUserId: Long): TicketSubscriptionEntity?

    /**
     * Distributed-lock-friendly page of subscriptions due for a monitoring
     * check (spec sections 16 and 20). PESSIMISTIC_WRITE + a Hibernate
     * "skip locked" hint means concurrent scheduler instances each grab a
     * disjoint batch instead of blocking on or double-processing the same
     * rows - equivalent to PostgreSQL's SELECT ... FOR UPDATE SKIP LOCKED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        """
        SELECT s FROM TicketSubscriptionEntity s
        WHERE s.status = uz.railway.ticketbot.subscription.SubscriptionStatus.ACTIVE
          AND (s.nextCheckAt IS NULL OR s.nextCheckAt <= :now)
        ORDER BY s.nextCheckAt ASC NULLS FIRST, s.id ASC
        """
    )
    fun findDueForMonitoring(@Param("now") now: Instant, pageable: Pageable): List<TicketSubscriptionEntity>
}
