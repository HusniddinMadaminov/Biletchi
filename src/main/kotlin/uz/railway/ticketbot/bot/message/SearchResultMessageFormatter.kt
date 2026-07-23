package uz.railway.ticketbot.bot.message

import org.springframework.stereotype.Component
import uz.railway.ticketbot.search.TicketSearchOutcome

/** Builds the initial search result message per spec sections 3.2 and 5. */
@Component
class SearchResultMessageFormatter {

    fun formatFound(outcome: TicketSearchOutcome): String {
        val request = outcome.request
        val sb = StringBuilder()
        sb.appendLine("✅ Eng yaqin pastki joy topildi")
        sb.appendLine()
        sb.appendLine("${request.fromStationName} → ${request.toStationName}")
        sb.appendLine("📅 ${FormatUtils.date(outcome.bestDate!!)}")
        for (offer in outcome.offers) {
            sb.appendLine()
            sb.appendLine("🚆 ${offer.trainNumber}")
            sb.appendLine("🕐 ${FormatUtils.time(offer.departureTime)}")
            sb.appendLine("🚃 ${offer.carType}, ${offer.carNumber}-vagon")
            sb.appendLine("💺 Pastki joylar: ${offer.lowerSeatNumbers.joinToString(", ")}")
            sb.append("💰 ${FormatUtils.price(offer.minimumPrice, offer.currency)}")
        }
        return sb.toString()
    }

    fun formatNotFound(outcome: TicketSearchOutcome): String {
        val request = outcome.request
        return buildString {
            appendLine("Hozircha pastki joy topilmadi.")
            appendLine()
            appendLine("${request.fromStationName} → ${request.toStationName}")
            appendLine("Tekshirilgan davr:")
            append("${FormatUtils.date(request.startDate)} — ${FormatUtils.date(request.endDate)}")
        }
    }
}
