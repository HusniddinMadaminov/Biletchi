package uz.railway.ticketbot.search

import java.time.LocalDate

data class TicketSearchRequest(
    val telegramUserId: Long,
    val fromStationCode: String,
    val fromStationName: String,
    val toStationCode: String,
    val toStationName: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val filters: TicketFilters = TicketFilters.NONE
) {
    init {
        require(!endDate.isBefore(startDate)) { "endDate must not be before startDate" }
    }
}
