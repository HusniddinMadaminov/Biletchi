package uz.railway.ticketbot.bot.menu

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import uz.railway.ticketbot.bot.InlineButton
import uz.railway.ticketbot.bot.TelegramMessageSender
import uz.railway.ticketbot.station.Station

object Menus {
    const val BTN_NEW_SEARCH = "🎫 Yangi chipta qidirish"
    const val BTN_MY_SUBSCRIPTIONS = "📋 Kuzatuvlarim"
    const val BTN_HELP = "❓ Yordam"

    fun mainMenu(): ReplyKeyboardMarkup {
        val rows = listOf(
            KeyboardRow(listOf(KeyboardButton(BTN_NEW_SEARCH))),
            KeyboardRow(listOf(KeyboardButton(BTN_MY_SUBSCRIPTIONS), KeyboardButton(BTN_HELP)))
        )
        return ReplyKeyboardMarkup.builder()
            .keyboard(rows)
            .resizeKeyboard(true)
            .build()
    }

    fun stationChoices(prefix: String, stations: List<Station>) =
        TelegramMessageSender.inlineKeyboard(
            stations.map { station -> listOf(InlineButton.Callback(station.name, "station:$prefix:${station.code}")) }
        )

    fun searchPeriodChoices() = TelegramMessageSender.inlineKeyboard(
        listOf(
            listOf(
                InlineButton.Callback("7 kun", "period:7"),
                InlineButton.Callback("15 kun", "period:15")
            ),
            listOf(
                InlineButton.Callback("30 kun", "period:30"),
                InlineButton.Callback("45 kun", "period:45")
            )
        )
    )

    fun confirmSearch() = TelegramMessageSender.inlineKeyboard(
        listOf(
            listOf(
                InlineButton.Callback("✅ Qidiruvni boshlash", "search:confirm"),
                InlineButton.Callback("❌ Bekor qilish", "search:cancel")
            )
        )
    )

    fun watchOffer() = TelegramMessageSender.inlineKeyboard(
        listOf(listOf(InlineButton.Callback("🔔 Oldinroq joylarni kuzatish", "sub:create")))
    )

    fun subscriptionActions(subscriptionId: Long, isActive: Boolean) = TelegramMessageSender.inlineKeyboard(
        listOf(
            listOf(
                InlineButton.Callback("🔍 Hozir tekshirish", "sub:check:$subscriptionId"),
                InlineButton.Callback(if (isActive) "⏸ Pauza" else "▶️ Davom ettirish", if (isActive) "sub:pause:$subscriptionId" else "sub:resume:$subscriptionId")
            ),
            listOf(InlineButton.Callback("🗑 O'chirish", "sub:delete:$subscriptionId"))
        )
    )
}
