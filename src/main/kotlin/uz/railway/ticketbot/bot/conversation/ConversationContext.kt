package uz.railway.ticketbot.bot.conversation

import java.time.LocalDate

/** Serialized into telegram_users.conversation_context while a /new-search wizard is in progress. */
data class ConversationContext(
    val fromStationCode: String? = null,
    val fromStationName: String? = null,
    val toStationCode: String? = null,
    val toStationName: String? = null,
    val startDate: LocalDate? = null,
    val periodDays: Long? = null
)
