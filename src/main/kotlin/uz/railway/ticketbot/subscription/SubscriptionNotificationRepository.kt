package uz.railway.ticketbot.subscription

import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionNotificationRepository : JpaRepository<SubscriptionNotificationEntity, Long> {
    fun existsBySubscriptionIdAndFingerprint(subscriptionId: Long, fingerprint: String): Boolean
}
