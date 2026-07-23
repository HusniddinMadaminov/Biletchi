package uz.railway.ticketbot.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.railway.ticketbot.bot.InlineButton
import uz.railway.ticketbot.bot.TelegramMessageSender
import uz.railway.ticketbot.search.TicketSearchResult
import uz.railway.ticketbot.subscription.NotificationStatus
import uz.railway.ticketbot.subscription.SubscriptionNotificationEntity
import uz.railway.ticketbot.subscription.SubscriptionNotificationRepository
import uz.railway.ticketbot.subscription.TicketSubscription
import uz.railway.ticketbot.user.TelegramUserService
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@Service
class TelegramNotificationService(
    private val sender: TelegramMessageSender,
    private val formatter: NotificationMessageFormatter,
    private val userService: TelegramUserService,
    private val notificationRepository: SubscriptionNotificationRepository,
    private val clock: Clock
) : NotificationService {

    private val log = LoggerFactory.getLogger(TelegramNotificationService::class.java)

    @Transactional
    override fun sendEarlierSeatFound(
        subscription: TicketSubscription,
        previousBestDate: LocalDate?,
        newBestOffers: List<TicketSearchResult>
    ) {
        val primary = newBestOffers.first()

        // Spec section 9: never re-send an identical offer (same fingerprint) for this subscription.
        if (notificationRepository.existsBySubscriptionIdAndFingerprint(subscription.id, primary.fingerprint)) {
            log.debug("Skipping duplicate notification for subscription {} fingerprint {}", subscription.id, primary.fingerprint)
            return
        }

        val user = userService.findByTelegramUserId(subscription.telegramUserId)
        if (user == null) {
            log.warn("No Telegram user found for id {}, cannot send notification", subscription.telegramUserId)
            return
        }

        val text = formatter.format(subscription, previousBestDate, newBestOffers)
        val keyboard = TelegramMessageSender.inlineKeyboard(
            listOf(
                listOf(InlineButton.Url("🌐 Saytda xarid qilish", primary.purchaseUrl ?: "https://eticket.railway.uz")),
                listOf(InlineButton.Callback("⏸ Kuzatuvni to'xtatish", "sub:pause:${subscription.id}"))
            )
        )

        val message = sender.send(user.chatId, text, keyboard)

        notificationRepository.save(
            SubscriptionNotificationEntity(
                subscriptionId = subscription.id,
                resultDate = primary.date,
                fingerprint = primary.fingerprint,
                telegramMessageId = message?.messageId?.toLong(),
                sentAt = Instant.now(clock),
                status = if (message != null) NotificationStatus.SENT else NotificationStatus.FAILED,
                errorMessage = if (message == null) "Telegram send failed" else null
            )
        )
    }
}
