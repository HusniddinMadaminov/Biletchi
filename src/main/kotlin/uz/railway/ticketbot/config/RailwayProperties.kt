package uz.railway.ticketbot.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "railway")
data class RailwayProperties(
    val baseUrl: String = "https://eticket.railway.uz",
    val connectTimeoutMs: Long = 5000,
    val readTimeoutMs: Long = 10000,
    /**
     * Optional Bearer token for eticket.railway.uz. The captured browser
     * traffic carried a logged-in user's JWT (1-hour expiry); set this only
     * if the API turns out to reject anonymous requests.
     */
    val authToken: String? = null,
    val retry: Retry = Retry()
) {
    data class Retry(
        val maxAttempts: Int = 4,
        val initialBackoffMs: Long = 2000,
        val multiplier: Double = 2.0
    )
}
