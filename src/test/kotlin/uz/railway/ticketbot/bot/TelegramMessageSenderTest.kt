package uz.railway.ticketbot.bot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.meta.generics.TelegramClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TelegramMessageSenderTest {

    // Regression: api.telegram.org occasionally times out mid-request (SocketTimeoutException wrapped
    // as a plain TelegramApiException). Giving up on the first blip silently drops whatever message the
    // user was waiting for, so a couple of quick retries must happen before giving up.
    @Test
    fun `retries on a transient TelegramApiException and succeeds`() {
        val telegramClient = mockk<TelegramClient>()
        val sentMessage = mockk<Message>()
        every {
            telegramClient.execute(any<SendMessage>())
        } throws TelegramApiException("timeout", java.net.SocketTimeoutException("timeout")) andThenThrows
            TelegramApiException("timeout", java.net.SocketTimeoutException("timeout")) andThen sentMessage
        val sender = TelegramMessageSender(telegramClient)

        val result = sender.send(555L, "salom")

        assertEquals(sentMessage, result)
        verify(exactly = 3) { telegramClient.execute(any<SendMessage>()) }
    }

    // A definitive response from Telegram (bad chat_id, blocked by the user, etc.) is never retried -
    // retrying it would just waste time since the outcome can't change.
    @Test
    fun `does not retry a TelegramApiRequestException`() {
        val telegramClient = mockk<TelegramClient>()
        every {
            telegramClient.execute(any<SendMessage>())
        } throws TelegramApiRequestException("Forbidden: bot was blocked by the user")
        val sender = TelegramMessageSender(telegramClient)

        val result = sender.send(555L, "salom")

        assertNull(result)
        verify(exactly = 1) { telegramClient.execute(any<SendMessage>()) }
    }

    // After exhausting all retry attempts on a persistent transient failure, give up and return null
    // rather than retrying forever.
    @Test
    fun `gives up after exhausting retries on a persistent transient failure`() {
        val telegramClient = mockk<TelegramClient>()
        every {
            telegramClient.execute(any<SendMessage>())
        } throws TelegramApiException("timeout", java.net.SocketTimeoutException("timeout"))
        val sender = TelegramMessageSender(telegramClient)

        val result = sender.send(555L, "salom")

        assertNull(result)
        verify(exactly = 3) { telegramClient.execute(any<SendMessage>()) }
    }
}
