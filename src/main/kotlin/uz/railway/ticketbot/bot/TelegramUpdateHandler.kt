package uz.railway.ticketbot.bot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.message.Message
import uz.railway.ticketbot.bot.conversation.ConversationService
import uz.railway.ticketbot.bot.menu.Menus
import uz.railway.ticketbot.user.ConversationState
import uz.railway.ticketbot.user.TelegramUserEntity
import uz.railway.ticketbot.user.TelegramUserService

/**
 * Single entry point for all Telegram updates. Each update is dispatched
 * onto its own coroutine (bounded by Dispatchers.IO) so that one slow
 * railway.uz search never delays updates for other chats - the underlying
 * long-polling loop itself must stay non-blocking.
 */
@Component
class TelegramUpdateHandler(
    private val userService: TelegramUserService,
    private val conversationService: ConversationService,
    private val subscriptionCommandService: SubscriptionCommandService,
    private val sender: TelegramMessageSender
) : LongPollingSingleThreadUpdateConsumer {

    private val log = LoggerFactory.getLogger(TelegramUpdateHandler::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun consume(update: Update) {
        scope.launch {
            try {
                handle(update)
            } catch (ex: Exception) {
                log.error("Failed to process Telegram update {}: {}", update.updateId, ex.message, ex)
            }
        }
    }

    private suspend fun handle(update: Update) {
        when {
            update.hasCallbackQuery() -> handleCallback(update)
            update.hasMessage() && update.message.hasText() -> handleMessage(update.message)
            else -> Unit
        }
    }

    private fun resolveUser(chatId: Long, telegramUserId: Long, username: String?, firstName: String?, lastName: String?): TelegramUserEntity =
        userService.getOrCreate(telegramUserId, chatId, username, firstName, lastName)

    private suspend fun handleMessage(message: Message) {
        val from = message.from ?: return
        val user = resolveUser(message.chatId, from.id, from.userName, from.firstName, from.lastName)
        val text = message.text.trim()

        if (text == "/start") {
            conversationService.sendWelcome(user)
            return
        }

        if (conversationService.handleMainMenuText(user, text)) return

        when (user.conversationState) {
            ConversationState.IDLE -> {
                if (text == Menus.BTN_MY_SUBSCRIPTIONS) {
                    subscriptionCommandService.listSubscriptions(user)
                } else {
                    sender.send(user.chatId, "Menyudan birini tanlang yoki /start bosing.", Menus.mainMenu())
                }
            }
            else -> conversationService.handleWizardText(user, user.conversationState, text)
        }
    }

    private suspend fun handleCallback(update: Update) {
        val callback = update.callbackQuery
        val from = callback.from
        val chatId = callback.message?.chatId ?: return
        val user = resolveUser(chatId, from.id, from.userName, from.firstName, from.lastName)
        val data = callback.data ?: return
        val parts = data.split(":")

        when (parts.getOrNull(0)) {
            "station" -> if (parts.size >= 3) conversationService.handleStationSelected(user, parts[1], parts[2])
            "period" -> parts.getOrNull(1)?.toLongOrNull()?.let { conversationService.handlePeriodSelected(user, it) }
            "search" -> when (parts.getOrNull(1)) {
                "confirm" -> conversationService.handleSearchConfirmed(user)
                "cancel" -> conversationService.handleSearchCancelled(user)
            }
            "sub" -> handleSubscriptionCallback(user, parts)
        }
    }

    private suspend fun handleSubscriptionCallback(user: TelegramUserEntity, parts: List<String>) {
        val action = parts.getOrNull(1) ?: return
        val id = parts.getOrNull(2)?.toLongOrNull()
        when (action) {
            "create" -> subscriptionCommandService.createFromLastSearch(user)
            "pause" -> id?.let { subscriptionCommandService.pause(user, it) }
            "resume" -> id?.let { subscriptionCommandService.resume(user, it) }
            "delete" -> id?.let { subscriptionCommandService.delete(user, it) }
            "check" -> id?.let { subscriptionCommandService.checkNow(user, it) }
        }
    }
}
