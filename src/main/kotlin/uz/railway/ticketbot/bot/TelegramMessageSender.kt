package uz.railway.ticketbot.bot

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.message.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import org.telegram.telegrambots.meta.generics.TelegramClient

sealed class InlineButton {
    abstract val label: String
    data class Callback(override val label: String, val data: String) : InlineButton()
    data class Url(override val label: String, val url: String) : InlineButton()
}

@Component
class TelegramMessageSender(
    private val telegramClient: TelegramClient
) {
    private val log = LoggerFactory.getLogger(TelegramMessageSender::class.java)

    fun send(chatId: Long, text: String, keyboard: ReplyKeyboard? = null, parseMode: String? = null): Message? {
        val message = SendMessage.builder()
            .chatId(chatId)
            .text(text)
            .apply { if (keyboard != null) replyMarkup(keyboard) }
            .apply { if (parseMode != null) parseMode(parseMode) }
            .build()
        return sendWithRetry(chatId, message, attempt = 1)
    }

    /**
     * Occasional SocketTimeoutExceptions/connection resets talking to api.telegram.org surface as a
     * plain TelegramApiException - worth a couple of quick retries, since giving up on the first blip
     * silently drops whatever message the user was mid-wizard waiting for. TelegramApiRequestException
     * (a definitive response from Telegram - bad chat_id, blocked by user, etc.) is never retried.
     */
    private fun sendWithRetry(chatId: Long, message: SendMessage, attempt: Int): Message? {
        return try {
            telegramClient.execute(message)
        } catch (ex: TelegramApiRequestException) {
            log.error("Failed to send Telegram message to chat {}: {}", chatId, ex.message, ex)
            null
        } catch (ex: Exception) {
            if (attempt < MAX_SEND_ATTEMPTS) {
                log.warn("Transient error sending Telegram message to chat {} (attempt {}/{}), retrying: {}", chatId, attempt, MAX_SEND_ATTEMPTS, ex.message)
                Thread.sleep(RETRY_DELAY_MILLIS * attempt)
                sendWithRetry(chatId, message, attempt + 1)
            } else {
                log.error("Failed to send Telegram message to chat {} after {} attempts: {}", chatId, attempt, ex.message, ex)
                null
            }
        }
    }

    companion object {
        private const val MAX_SEND_ATTEMPTS = 3
        private const val RETRY_DELAY_MILLIS = 750L

        fun inlineKeyboard(rows: List<List<InlineButton>>): InlineKeyboardMarkup {
            val keyboardRows = rows.map { row ->
                InlineKeyboardRow(
                    row.map { button ->
                        val builder = InlineKeyboardButton.builder().text(button.label)
                        when (button) {
                            is InlineButton.Callback -> builder.callbackData(button.data)
                            is InlineButton.Url -> builder.url(button.url)
                        }
                        builder.build()
                    }
                )
            }
            return InlineKeyboardMarkup.builder().keyboard(keyboardRows).build()
        }
    }
}
