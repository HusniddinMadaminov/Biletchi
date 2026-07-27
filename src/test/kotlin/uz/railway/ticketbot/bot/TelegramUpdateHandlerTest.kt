package uz.railway.ticketbot.bot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.objects.Chat
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.api.objects.message.Message
import uz.railway.ticketbot.bot.conversation.ConversationService
import uz.railway.ticketbot.user.TelegramUserService
import kotlin.test.Test

class TelegramUpdateHandlerTest {

    // Regression: an unhandled exception while processing an update (e.g. an SSL/network failure
    // reaching railway.uz, or here a simulated failure resolving the user) previously left the user
    // with silence - the message just seemed to vanish. It must now be told the bot is temporarily
    // unavailable instead.
    @Test
    fun `unhandled exception while processing a message notifies the user instead of failing silently`() {
        val userService = mockk<TelegramUserService>()
        every {
            userService.getOrCreate(any(), any(), any(), any(), any())
        } throws RuntimeException("simulated SSL handshake failure")
        val conversationService = mockk<ConversationService>(relaxed = true)
        val subscriptionCommandService = mockk<SubscriptionCommandService>(relaxed = true)
        val sender = mockk<TelegramMessageSender>(relaxed = true)
        val handler = TelegramUpdateHandler(userService, conversationService, subscriptionCommandService, sender)

        val chat = Chat.builder().id(555L).type("private").build()
        val from = User(42L, "Test", false)
        val message = Message.builder().messageId(1).chat(chat).from(from).text("salom").build()
        val update = Update()
        update.message = message

        handler.consume(update)

        verify(timeout = 2000) { sender.send(555L, match { it.contains("vaqtinchalik") }, null, null) }
    }
}
