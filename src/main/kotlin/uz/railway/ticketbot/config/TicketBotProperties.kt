package uz.railway.ticketbot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ticketbot")
data class TicketBotProperties(
    val search: Search = Search(),
    val monitoring: Monitoring = Monitoring(),
    val scheduling: Scheduling = Scheduling()
) {
    data class Search(
        val defaultPeriodDays: Long = 30,
        val maxPeriodDays: Long = 60,
        val dateBatchSize: Int = 4
    )

    data class Monitoring(
        val intervalCron: String = "0 */10 * * * *",
        val batchSize: Int = 100,
        val manualCheckCooldownSeconds: Long = 120
    )

    data class Scheduling(
        val poolSize: Int = 8
    )
}
