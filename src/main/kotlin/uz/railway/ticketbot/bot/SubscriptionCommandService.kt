package uz.railway.ticketbot.bot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uz.railway.ticketbot.bot.conversation.ConversationService
import uz.railway.ticketbot.bot.menu.Menus
import uz.railway.ticketbot.bot.message.SubscriptionMessageFormatter
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.monitoring.TicketMonitoringService
import uz.railway.ticketbot.subscription.SubscriptionStatus
import uz.railway.ticketbot.subscription.TicketSubscriptionService
import uz.railway.ticketbot.user.TelegramUserEntity
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class SubscriptionCommandService(
    private val subscriptionService: TicketSubscriptionService,
    private val conversationService: ConversationService,
    private val monitoringService: TicketMonitoringService,
    private val formatter: SubscriptionMessageFormatter,
    private val sender: TelegramMessageSender,
    private val properties: TicketBotProperties,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(SubscriptionCommandService::class.java)

    fun listSubscriptions(user: TelegramUserEntity) {
        val subscriptions = subscriptionService.findForUser(user.telegramUserId)
            .filter { it.status == SubscriptionStatus.ACTIVE || it.status == SubscriptionStatus.PAUSED }
        if (subscriptions.isEmpty()) {
            sender.send(user.chatId, "Sizda hozircha faol kuzatuvlar yo'q.", Menus.mainMenu())
            return
        }
        for (subscription in subscriptions) {
            sender.send(
                user.chatId,
                formatter.format(subscription),
                Menus.subscriptionActions(subscription.id, subscription.status == SubscriptionStatus.ACTIVE)
            )
        }
    }

    /** Creates a subscription from the user's most recent search result (spec section 6). */
    fun createFromLastSearch(user: TelegramUserEntity) {
        val outcome = conversationService.readLastOutcome(user)
        if (outcome == null) {
            sender.send(user.chatId, "Avval yangi qidiruv boshlang.", Menus.mainMenu())
            return
        }
        val subscription = subscriptionService.create(user.telegramUserId, outcome)
        val message = if (subscription.status == SubscriptionStatus.COMPLETED) {
            "✅ Siz tanlagan eng birinchi sana uchun pastki joy topildi.\n\nKuzatuv yakunlandi."
        } else {
            "🔔 Kuzatuv yoqildi. Har ${properties.monitoring.intervalMinutes} daqiqada oldinroq sana borligini tekshirib turaman."
        }
        sender.send(user.chatId, message, Menus.mainMenu())
    }

    fun pause(user: TelegramUserEntity, subscriptionId: Long) {
        subscriptionService.pause(subscriptionId, user.telegramUserId)
        sender.send(user.chatId, "⏸ Kuzatuv pauza qilindi.")
    }

    fun resume(user: TelegramUserEntity, subscriptionId: Long) {
        subscriptionService.resume(subscriptionId, user.telegramUserId)
        sender.send(user.chatId, "▶️ Kuzatuv qayta yoqildi.")
    }

    fun delete(user: TelegramUserEntity, subscriptionId: Long) {
        subscriptionService.cancel(subscriptionId, user.telegramUserId)
        sender.send(user.chatId, "🗑 Kuzatuv o'chirildi.")
    }

    /** Manual "Hozir tekshirish" (spec section 24), rate-limited per subscription. */
    suspend fun checkNow(user: TelegramUserEntity, subscriptionId: Long) {
        val subscription = subscriptionService.findByIdForUser(subscriptionId, user.telegramUserId)
        if (subscription == null) {
            sender.send(user.chatId, "Kuzatuv topilmadi.")
            return
        }
        val cooldown = Duration.ofSeconds(properties.monitoring.manualCheckCooldownSeconds)
        val lastChecked = subscription.lastCheckedAt
        if (lastChecked != null && Duration.between(lastChecked, Instant.now(clock)) < cooldown) {
            sender.send(user.chatId, "Iltimos, biroz kuting - bu kuzatuv yaqinda tekshirilgan edi.")
            return
        }
        sender.send(user.chatId, "Tekshirilmoqda...")
        try {
            monitoringService.checkSubscription(subscription)
        } catch (ex: Exception) {
            log.error("Manual check failed for subscription {}: {}", subscriptionId, ex.message, ex)
            sender.send(user.chatId, "Tekshiruv vaqtida xatolik yuz berdi, birozdan so'ng qayta urinib ko'ring.")
            return
        }
        val refreshed = subscriptionService.findByIdForUser(subscriptionId, user.telegramUserId) ?: return
        sender.send(
            user.chatId,
            formatter.format(refreshed),
            Menus.subscriptionActions(refreshed.id, refreshed.status == SubscriptionStatus.ACTIVE)
        )
    }
}
