package uz.railway.ticketbot.search

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "search_logs")
class SearchLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "telegram_user_id")
    var telegramUserId: Long?,

    @Column(name = "from_station_code", nullable = false)
    var fromStationCode: String,

    @Column(name = "to_station_code", nullable = false)
    var toStationCode: String,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    var endDate: LocalDate,

    @Column(name = "found_date")
    var foundDate: LocalDate? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
