package uz.railway.ticketbot.config

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication
import uz.railway.ticketbot.bot.TelegramUpdateHandler

@Component
class TelegramBotConfiguration(
    private val properties: TelegramBotProperties,
    private val updateHandler: TelegramUpdateHandler
) {
    private val log = LoggerFactory.getLogger(TelegramBotConfiguration::class.java)
    private var application: TelegramBotsLongPollingApplication? = null

    @PostConstruct
    fun start() {
        if (properties.token.isBlank()) {
            log.warn("telegram.bot.token is not configured; the Telegram bot will not start")
            return
        }
        val app = TelegramBotsLongPollingApplication()
        try {
            app.registerBot(properties.token, updateHandler)
            application = app
            log.info("Telegram bot started (long polling)")
        } catch (ex: Exception) {
            log.error("Failed to start Telegram bot", ex)
        }
    }

    @PreDestroy
    fun stop() {
        application?.close()
    }
}
