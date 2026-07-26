package uz.railway.ticketbot.bot.message

import org.springframework.stereotype.Component
import uz.railway.ticketbot.search.SeatMode
import uz.railway.ticketbot.search.TicketSearchOutcome

/** Builds the initial search result message per spec sections 3.2 and 5. */
@Component
class SearchResultMessageFormatter {

    /** Renders with Telegram HTML parse mode - callers must send this with parseMode = "HTML". */
    fun formatFound(outcome: TicketSearchOutcome): String {
        val request = outcome.request
        val isLowerOnly = request.filters.seatMode == SeatMode.LOWER
        val trains = outcome.offers.groupBy { it.trainNumber }.entries.toList()

        val sb = StringBuilder()
        sb.appendLine("✅ <b>${if (isLowerOnly) "Eng yaqin pastki joylar topildi" else "Eng yaqin bo'sh joylar topildi"}</b>")
        sb.appendLine()
        sb.appendLine("📍 <b>${FormatUtils.escapeHtml(request.fromStationName)} → ${FormatUtils.escapeHtml(request.toStationName)}</b>")
        sb.appendLine("📅 <b>${FormatUtils.date(outcome.bestDate!!)}</b>")

        trains.forEachIndexed { index, (trainNumber, offers) ->
            sb.appendLine()
            sb.appendLine("🚆 <b>${index + 1}-poyezd: ${FormatUtils.escapeHtml(trainNumber)}</b>")
            sb.appendLine("🕐 Jo'nash vaqti: <b>${FormatUtils.time(offers.first().departureTime)}</b>")
            sb.appendLine()
            for (offer in offers) {
                sb.appendLine("┌ 🚃 <b>${offer.carNumber}-vagon — ${FormatUtils.carTypeUz(offer.carType)}</b>")
                sb.appendLine("│ 💺 ${if (isLowerOnly) "Bo'sh pastki joylar" else "Bo'sh joylar"}: <b>${offer.seatNumbers.joinToString(", ")}</b>")
                sb.appendLine("│ 💰 Narxi: <b>${FormatUtils.price(offer.minimumPrice, offer.currency)}</b>")
                sb.appendLine("└────────────")
                sb.appendLine()
            }
            if (index < trains.lastIndex) {
                sb.appendLine("━━━━━━━━━━━━━━━━━━")
            }
        }
        sb.append("🎫 Chipta sotib olish uchun poyezd va vagonni tanlang.")
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
