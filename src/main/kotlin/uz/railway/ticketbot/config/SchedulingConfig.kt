package uz.railway.ticketbot.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class SchedulingConfig(
    private val properties: TicketBotProperties
) {

    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = properties.scheduling.poolSize
        scheduler.setThreadNamePrefix("ticketbot-scheduler-")
        scheduler.initialize()
        return scheduler
    }
}
