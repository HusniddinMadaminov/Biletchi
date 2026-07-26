package uz.railway.ticketbot.bot.conversation

import uz.railway.ticketbot.search.SeatMode
import java.time.LocalDate

/** Serialized into telegram_users.conversation_context while a /new-search wizard is in progress. */
data class ConversationContext(
    val seatMode: SeatMode = SeatMode.LOWER,
    val fromStationCode: String? = null,
    val fromStationName: String? = null,
    val toStationCode: String? = null,
    val toStationName: String? = null,
    val startDate: LocalDate? = null,
    val periodDays: Long? = null
)
