package uz.railway.ticketbot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "telegram.bot")
data class TelegramBotProperties(
    val token: String = "",
    val username: String = ""
)
