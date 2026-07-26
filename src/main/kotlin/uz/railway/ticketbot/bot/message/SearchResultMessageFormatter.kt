package uz.railway.ticketbot.bot.message

import org.springframework.stereotype.Component
import uz.railway.ticketbot.search.SeatMode
import uz.railway.ticketbot.search.TicketSearchOutcome

/** Builds the initial search result message per spec sections 3.2 and 5. */
@Component
class SearchResultMessageFormatter {

    fun formatFound(outcome: TicketSearchOutcome): String {
        val request = outcome.request
        val isLowerOnly = request.filters.seatMode == SeatMode.LOWER
        val sb = StringBuilder()
        sb.appendLine(if (isLowerOnly) "✅ Eng yaqin pastki joy topildi" else "✅ Eng yaqin bo'sh joy topildi")
        sb.appendLine()
        sb.appendLine("${request.fromStationName} → ${request.toStationName}")
        sb.appendLine("📅 ${FormatUtils.date(outcome.bestDate!!)}")
        for (offer in outcome.offers) {
            sb.appendLine()
            sb.appendLine("🚆 ${offer.trainNumber}")
            sb.appendLine("🕐 ${FormatUtils.time(offer.departureTime)}")
            sb.appendLine("🚃 ${offer.carType}, ${offer.carNumber}-vagon")
            sb.appendLine("💺 ${if (isLowerOnly) "Pastki joylar" else "Joylar"}: ${offer.seatNumbers.joinToString(", ")}")
            sb.append("💰 ${FormatUtils.price(offer.minimumPrice, offer.currency)}")
        }
        return sb.toString()
    }

    fun formatNotFound(outcome: TicketSearchOutcome): String {
        val request = outcome.request
        val isLowerOnly = request.filters.seatMode == SeatMode.LOWER
        return buildString {
            appendLine(if (isLowerOnly) "Hozircha pastki joy topilmadi." else "Hozircha bo'sh joy topilmadi.")
            appendLine()
            appendLine("${request.fromStationName} → ${request.toStationName}")
            appendLine("Tekshirilgan davr:")
            append("${FormatUtils.date(request.startDate)} — ${FormatUtils.date(request.endDate)}")
        }
    }
}
