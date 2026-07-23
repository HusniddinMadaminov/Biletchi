package uz.railway.ticketbot.notification

import org.springframework.stereotype.Component
import uz.railway.ticketbot.bot.message.FormatUtils
import uz.railway.ticketbot.search.TicketSearchResult
import uz.railway.ticketbot.subscription.TicketSubscription
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Builds the "earlier lower seat found" alert exactly as templated in spec section 8.1. */
@Component
class NotificationMessageFormatter {

    fun format(
        subscription: TicketSubscription,
        previousBestDate: LocalDate?,
        newBestOffers: List<TicketSearchResult>
    ): String {
        val primary = newBestOffers.first()
        val daysEarlier = previousBestDate?.let { ChronoUnit.DAYS.between(primary.date, it) }

        val sb = StringBuilder()
        sb.appendLine("🔔 Oldinroq pastki joy topildi!")
        sb.appendLine()
        sb.appendLine("${subscription.fromStationName} → ${subscription.toStationName}")
        sb.appendLine()
        if (previousBestDate != null) {
            sb.appendLine("Avvalgi eng yaqin sana:")
            sb.appendLine(FormatUtils.date(previousBestDate))
            sb.appendLine()
        }
        sb.appendLine("Yangi eng yaqin sana:")
        sb.appendLine(FormatUtils.date(primary.date))
        sb.appendLine()
        if (daysEarlier != null && daysEarlier > 0) {
            sb.appendLine("Siz $daysEarlier kun oldinroq jo'nashingiz mumkin.")
            sb.appendLine()
        }
        sb.appendLine("🚆 Poyezd: ${primary.trainNumber}")
        sb.appendLine("🕐 Jo'nash: ${FormatUtils.time(primary.departureTime)}")
        sb.appendLine("🚃 Vagon: ${primary.carType}, ${primary.carNumber}-vagon")
        sb.appendLine("💺 Pastki joylar: ${primary.lowerSeatNumbers.joinToString(", ")}")
        sb.append("💰 Narxi: ${FormatUtils.price(primary.minimumPrice, primary.currency)}")
        return sb.toString()
    }
}
