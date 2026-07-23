package uz.railway.ticketbot.subscription

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

enum class NotificationStatus {
    SENT,
    FAILED
}

@Entity
@Table(name = "subscription_notifications")
class SubscriptionNotificationEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "subscription_id", nullable = false)
    var subscriptionId: Long,

    @Column(name = "result_date", nullable = false)
    var resultDate: LocalDate,

    @Column(name = "fingerprint", nullable = false)
    var fingerprint: String,

    @Column(name = "telegram_message_id")
    var telegramMessageId: Long? = null,

    @Column(name = "sent_at", nullable = false)
    var sentAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: NotificationStatus = NotificationStatus.SENT,

    @Column(name = "error_message")
    var errorMessage: String? = null
)
