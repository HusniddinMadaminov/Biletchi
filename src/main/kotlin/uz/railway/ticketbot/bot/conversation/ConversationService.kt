package uz.railway.ticketbot.bot.conversation

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uz.railway.ticketbot.bot.TelegramMessageSender
import uz.railway.ticketbot.bot.menu.Menus
import uz.railway.ticketbot.bot.message.SearchResultMessageFormatter
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchOutcome
import uz.railway.ticketbot.search.TicketSearchRequest
import uz.railway.ticketbot.search.TicketSearchService
import uz.railway.ticketbot.station.StationService
import uz.railway.ticketbot.user.ConversationState
import uz.railway.ticketbot.user.TelegramUserEntity
import uz.railway.ticketbot.user.TelegramUserService
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Drives the "Yangi chipta qidirish" wizard (spec section 3.2): from
 * station -> to station -> start date -> search period -> confirmation ->
 * search. Filters are skipped in this version (spec: "Birinchi versiyada
 * filtrlar majburiy emas").
 */
@Service
class ConversationService(
    private val userService: TelegramUserService,
    private val stationService: StationService,
    private val searchService: TicketSearchService,
    private val sender: TelegramMessageSender,
    private val resultFormatter: SearchResultMessageFormatter,
    private val objectMapper: ObjectMapper,
    private val properties: TicketBotProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(ConversationService::class.java)
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun sendWelcome(user: TelegramUserEntity) {
        userService.updateConversationState(user.telegramUserId, ConversationState.IDLE, null)
        sender.send(
            user.chatId,
            "Assalomu alaykum! Bu bot temiryo'l chiptalari orasidan pastki joylarni topishga yordam beradi.\n\nQuyidagi menyudan birini tanlang:",
            Menus.mainMenu()
        )
    }

    fun handleMainMenuText(user: TelegramUserEntity, text: String): Boolean {
        return when (text) {
            Menus.BTN_NEW_SEARCH -> {
                startNewSearch(user)
                true
            }
            Menus.BTN_HELP -> {
                sendHelp(user)
                true
            }
            else -> false
        }
    }

    private fun startNewSearch(user: TelegramUserEntity) {
        userService.updateConversationState(user.telegramUserId, ConversationState.WAITING_FROM_STATION, contextJson(ConversationContext()))
        sender.send(user.chatId, "Qayerdan jo'nashni istaysiz? Bekat nomini yozing (masalan: Toshkent):")
    }

    fun sendHelp(user: TelegramUserEntity) {
        sender.send(
            user.chatId,
            """
            ❓ Yordam

            Bu bot eticket.railway.uz saytidan siz belgilagan yo'nalish bo'yicha pastki (toq raqamli) joy mavjud bo'lgan eng yaqin sanani topadi va shu sanadan oldinroq joy bo'shasa, sizga xabar beradi.

            Bot chiptani o'zi sotib olmaydi va pasport, karta yoki parol kabi ma'lumotlarni saqlamaydi - xarid uchun sizni rasmiy saytga yo'naltiradi.

            Buyruqlar:
            /start - botni qayta boshlash
            """.trimIndent(),
            Menus.mainMenu()
        )
    }

    fun handleWizardText(user: TelegramUserEntity, state: ConversationState, text: String) {
        when (state) {
            ConversationState.WAITING_FROM_STATION -> promptStationChoice(user, "from", text)
            ConversationState.WAITING_TO_STATION -> promptStationChoice(user, "to", text)
            ConversationState.WAITING_START_DATE -> handleStartDate(user, text)
            else -> sender.send(user.chatId, "Iltimos, tugmalardan birini tanlang.")
        }
    }

    private fun promptStationChoice(user: TelegramUserEntity, prefix: String, query: String) {
        val matches = stationService.search(query)
        if (matches.isEmpty()) {
            sender.send(user.chatId, "Bekat topilmadi. Boshqa nom bilan qayta urinib ko'ring:")
            return
        }
        sender.send(user.chatId, "Mos bekatlardan birini tanlang:", Menus.stationChoices(prefix, matches))
    }

    fun handleStationSelected(user: TelegramUserEntity, prefix: String, code: String) {
        val station = stationService.findByCode(code)
        if (station == null) {
            sender.send(user.chatId, "Bekat topilmadi, qaytadan urinib ko'ring.")
            return
        }
        val context = readContext(user)
        if (prefix == "from") {
            val updated = context.copy(fromStationCode = station.code, fromStationName = station.name)
            userService.updateConversationState(user.telegramUserId, ConversationState.WAITING_TO_STATION, contextJson(updated))
            sender.send(user.chatId, "Qayerga borishni istaysiz? Bekat nomini yozing:")
        } else {
            val updated = context.copy(toStationCode = station.code, toStationName = station.name)
            userService.updateConversationState(user.telegramUserId, ConversationState.WAITING_START_DATE, contextJson(updated))
            sender.send(user.chatId, "Boshlang'ich sanani kiriting (kun.oy.yil, masalan: ${LocalDate.now(clock).plusDays(2).format(dateFormat)}):")
        }
    }

    private fun handleStartDate(user: TelegramUserEntity, text: String) {
        val date = try {
            LocalDate.parse(text.trim(), dateFormat)
        } catch (ex: DateTimeParseException) {
            sender.send(user.chatId, "Sana formati noto'g'ri. Iltimos, kun.oy.yil ko'rinishida kiriting (masalan: 25.07.2026):")
            return
        }
        val today = LocalDate.now(clock)
        if (date.isBefore(today)) {
            sender.send(user.chatId, "Boshlang'ich sana bugundan oldin bo'lishi mumkin emas. Qaytadan kiriting:")
            return
        }
        val context = readContext(user).copy(startDate = date)
        userService.updateConversationState(user.telegramUserId, ConversationState.WAITING_SEARCH_PERIOD, contextJson(context))
        sender.send(user.chatId, "Qidiruv davrini tanlang:", Menus.searchPeriodChoices())
    }

    fun handlePeriodSelected(user: TelegramUserEntity, days: Long) {
        val context = readContext(user).copy(periodDays = days)
        userService.updateConversationState(user.telegramUserId, ConversationState.WAITING_CONFIRMATION, contextJson(context))
        val endDate = context.startDate!!.plusDays(days)
        sender.send(
            user.chatId,
            """
            Qidiruvni tasdiqlang:

            ${context.fromStationName} → ${context.toStationName}
            Boshlang'ich sana: ${context.startDate.format(dateFormat)}
            Oxirgi sana: ${endDate.format(dateFormat)}
            """.trimIndent(),
            Menus.confirmSearch()
        )
    }

    fun handleSearchCancelled(user: TelegramUserEntity) {
        userService.updateConversationState(user.telegramUserId, ConversationState.IDLE, null)
        sender.send(user.chatId, "Qidiruv bekor qilindi.", Menus.mainMenu())
    }

    suspend fun handleSearchConfirmed(user: TelegramUserEntity): TicketSearchOutcome? {
        val context = readContext(user)
        val startDate = context.startDate
        val periodDays = context.periodDays ?: properties.search.defaultPeriodDays
        if (context.fromStationCode == null || context.toStationCode == null || startDate == null) {
            sender.send(user.chatId, "Qidiruv ma'lumotlari to'liq emas. Qaytadan boshlang.", Menus.mainMenu())
            userService.updateConversationState(user.telegramUserId, ConversationState.IDLE, null)
            return null
        }

        val request = TicketSearchRequest(
            telegramUserId = user.telegramUserId,
            fromStationCode = context.fromStationCode,
            fromStationName = context.fromStationName ?: context.fromStationCode,
            toStationCode = context.toStationCode,
            toStationName = context.toStationName ?: context.toStationCode,
            startDate = startDate,
            endDate = startDate.plusDays(periodDays),
            filters = TicketFilters.NONE
        )

        sender.send(user.chatId, "Qidirilmoqda, biroz kuting...")
        val outcome = searchService.findNearestLowerSeat(request)

        val text = if (outcome.found) resultFormatter.formatFound(outcome) else resultFormatter.formatNotFound(outcome)
        sender.send(user.chatId, text, Menus.watchOffer())

        // Kept around (state back to IDLE) so the "🔔 kuzatish" callback can build the subscription from it.
        userService.updateConversationState(user.telegramUserId, ConversationState.IDLE, objectMapper.writeValueAsString(outcome))
        return outcome
    }

    fun readLastOutcome(user: TelegramUserEntity): TicketSearchOutcome? =
        user.conversationContext?.let {
            try {
                objectMapper.readValue(it, TicketSearchOutcome::class.java)
            } catch (ex: Exception) {
                null
            }
        }

    private fun readContext(user: TelegramUserEntity): ConversationContext =
        user.conversationContext?.let {
            try {
                objectMapper.readValue(it, ConversationContext::class.java)
            } catch (ex: Exception) {
                ConversationContext()
            }
        } ?: ConversationContext()

    private fun contextJson(context: ConversationContext): String = objectMapper.writeValueAsString(context)
}
