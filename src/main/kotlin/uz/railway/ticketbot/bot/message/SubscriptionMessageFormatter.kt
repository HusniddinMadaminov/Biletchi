package uz.railway.ticketbot.bot.message

import org.springframework.stereotype.Component
import uz.railway.ticketbot.search.SeatMode
import uz.railway.ticketbot.subscription.SubscriptionStatus
import uz.railway.ticketbot.subscription.TicketSubscription

/** Builds the subscription card per spec section 23. */
@Component
class SubscriptionMessageFormatter {
    fun format(subscription: TicketSubscription): String = buildString {
        appendLine("📍 ${subscription.fromStationName} → ${subscription.toStationName}")
        appendLine("Joy turi: ${if (subscription.filters.seatMode == SeatMode.LOWER) "Pastki joylar" else "Istalgan bo'sh joy"}")
        if (subscription.currentBestDate != null) {
            appendLine("📅 Eng yaqin sana: ${FormatUtils.date(subscription.currentBestDate)}")
        } else {
            appendLine("📅 Eng yaqin sana: hali topilmadi")
        }
        if (subscription.lastCheckedAt != null) {
            appendLine("🔄 Oxirgi tekshiruv: ${FormatUtils.dateTimeWithSeconds(subscription.lastCheckedAt.atZone(FormatUtils.TASHKENT_ZONE).toLocalDateTime())}")
        } else {
            appendLine("🔄 Oxirgi tekshiruv: hali tekshirilmagan")
        }
        append("Holati: ${statusLabel(subscription.status)}")
    }

    private fun statusLabel(status: SubscriptionStatus): String = when (status) {
        SubscriptionStatus.ACTIVE -> "🟢 Faol"
        SubscriptionStatus.PAUSED -> "⏸ Pauzada"
        SubscriptionStatus.COMPLETED -> "✅ Yakunlangan"
        SubscriptionStatus.EXPIRED -> "⌛️ Muddati o'tgan"
        SubscriptionStatus.CANCELLED -> "🗑 Bekor qilingan"
        SubscriptionStatus.ERROR -> "⚠️ Xatolik"
    }
}
