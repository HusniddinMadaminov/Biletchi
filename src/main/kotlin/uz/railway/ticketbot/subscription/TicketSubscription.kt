package uz.railway.ticketbot.subscription

import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchResult
import java.time.Instant
import java.time.LocalDate

data class TicketSubscription(
    val id: Long,
    val telegramUserId: Long,
    val fromStationCode: String,
    val fromStationName: String,
    val toStationCode: String,
    val toStationName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val currentBestDate: LocalDate?,
    val currentBestResult: TicketSearchResult?,
    val currentBestResultFingerprint: String?,
    val filters: TicketFilters,
    val status: SubscriptionStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastCheckedAt: Instant?,
    val nextCheckAt: Instant?,
    val errorCount: Int,
    val lastError: String?
)
