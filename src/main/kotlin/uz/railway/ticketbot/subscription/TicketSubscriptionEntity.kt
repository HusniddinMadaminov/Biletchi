package uz.railway.ticketbot.subscription

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "ticket_subscriptions")
class TicketSubscriptionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "telegram_user_id", nullable = false)
    var telegramUserId: Long,

    @Column(name = "from_station_code", nullable = false)
    var fromStationCode: String,

    @Column(name = "from_station_name", nullable = false)
    var fromStationName: String,

    @Column(name = "to_station_code", nullable = false)
    var toStationCode: String,

    @Column(name = "to_station_name", nullable = false)
    var toStationName: String,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,

    @Column(name = "current_best_date")
    var currentBestDate: LocalDate? = null,

    @Column(name = "current_result_json")
    var currentResultJson: String? = null,

    @Column(name = "current_fingerprint")
    var currentFingerprint: String? = null,

    @Column(name = "filters_json", nullable = false)
    var filtersJson: String = "{}",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "last_checked_at")
    var lastCheckedAt: Instant? = null,

    @Column(name = "next_check_at")
    var nextCheckAt: Instant? = null,

    @Column(name = "error_count", nullable = false)
    var errorCount: Int = 0,

    @Column(name = "last_error")
    var lastError: String? = null,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)
