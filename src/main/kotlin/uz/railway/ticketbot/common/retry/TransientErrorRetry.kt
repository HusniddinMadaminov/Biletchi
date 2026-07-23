package uz.railway.ticketbot.common.retry

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import uz.railway.ticketbot.railway.exception.RailwayTransientException
import java.time.Duration

/**
 * Retry schedule for transient railway.uz errors (429/5xx/timeout/connection
 * reset): attempt immediately, then after 2s, 5s, 10s - at most
 * [maxAttempts] tries total. Non-transient errors (auth, 4xx) are never
 * retried here.
 */
object TransientErrorRetry {

    private val log = LoggerFactory.getLogger(TransientErrorRetry::class.java)
    private val DELAYS = listOf(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(10))

    fun spec(maxAttempts: Int): Retry {
        val attempts = maxAttempts.coerceIn(1, DELAYS.size)
        return Retry.from { signals ->
            signals.concatMap { signal ->
                val attempt = signal.totalRetries() + 1
                val failure = signal.failure()
                if (failure !is RailwayTransientException || attempt >= attempts) {
                    Mono.error(failure)
                } else {
                    val delay = DELAYS[attempt.toInt().coerceIn(0, DELAYS.size - 1)]
                    log.warn("Transient railway.uz error (attempt {}/{}), retrying in {}: {}", attempt, attempts, delay, failure.message)
                    Mono.delay(delay)
                }
            }
        }
    }
}
