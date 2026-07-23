package uz.railway.ticketbot.monitoring

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uz.railway.ticketbot.config.TicketBotProperties

@Component
class TicketMonitoringScheduler(
    private val monitoringService: TicketMonitoringService,
    private val properties: TicketBotProperties
) {
    private val log = LoggerFactory.getLogger(TicketMonitoringScheduler::class.java)

    @Scheduled(cron = "\${ticketbot.monitoring.interval-cron}")
    fun monitorSubscriptions() {
        runBlocking {
            try {
                monitoringService.checkDueSubscriptions(properties.monitoring.batchSize)
            } catch (ex: Exception) {
                log.error("Monitoring scheduler tick failed", ex)
            }
        }
    }
}
