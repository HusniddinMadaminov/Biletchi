package uz.railway.ticketbot.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.generics.TelegramClient

@Configuration
class TelegramClientConfig(
    private val properties: TelegramBotProperties
) {
    @Bean
    fun telegramClient(): TelegramClient = OkHttpTelegramClient(properties.token)
}
