package uz.railway.ticketbot.notification

import uz.railway.ticketbot.search.TicketSearchResult
import uz.railway.ticketbot.subscription.TicketSubscription
import java.time.LocalDate

interface NotificationService {
    /**
     * Sends the "earlier lower seat found" alert (spec section 8) and
     * records it for de-duplication/audit (spec section 9 / 21.2). Must be
     * called only when [newBestOffers]' date is strictly before
     * [previousBestDate] (or [previousBestDate] is null) - the caller
     * (TicketMonitoringService) is responsible for that check.
     */
    fun sendEarlierSeatFound(
        subscription: TicketSubscription,
        previousBestDate: LocalDate?,
        newBestOffers: List<TicketSearchResult>
    )
}
