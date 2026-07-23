package uz.railway.ticketbot.user

enum class ConversationState {
    IDLE,
    WAITING_FROM_STATION,
    WAITING_TO_STATION,
    WAITING_START_DATE,
    WAITING_SEARCH_PERIOD,
    WAITING_FILTERS,
    WAITING_CONFIRMATION
}
