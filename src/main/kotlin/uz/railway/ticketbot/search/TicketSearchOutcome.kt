package uz.railway.ticketbot.search

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class TicketSearchOutcome(
    val request: TicketSearchRequest,
    val offers: List<TicketSearchResult>
) {
    @get:JsonIgnore
    val found: Boolean get() = offers.isNotEmpty()

    @get:JsonIgnore
    val bestDate: LocalDate? get() = offers.firstOrNull()?.date
}
