package uz.railway.ticketbot.common.retry

import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import uz.railway.ticketbot.railway.exception.RailwayClientException
import uz.railway.ticketbot.railway.exception.RailwayTransientException
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class TransientErrorRetryTest {

    // Test 12 (spec section 27 / section 19): transient errors are retried with backoff, not surfaced immediately.
    @Test
    fun `retries transient errors up to the configured attempts then succeeds`() {
        val calls = AtomicInteger(0)
        val mono = Mono.defer {
            if (calls.incrementAndGet() < 3) Mono.error(RailwayTransientException("HTTP 503")) else Mono.just("ok")
        }.retryWhen(TransientErrorRetry.spec(4))

        StepVerifier.withVirtualTime { mono }
            .thenAwait(Duration.ofSeconds(20))
            .expectNext("ok")
            .verifyComplete()

        kotlin.test.assertEquals(3, calls.get())
    }

    @Test
    fun `gives up after max attempts and propagates the last error`() {
        val calls = AtomicInteger(0)
        val mono = Mono.defer {
            calls.incrementAndGet()
            Mono.error<String>(RailwayTransientException("HTTP 503"))
        }.retryWhen(TransientErrorRetry.spec(3))

        StepVerifier.withVirtualTime { mono }
            .thenAwait(Duration.ofSeconds(20))
            .expectError(RailwayTransientException::class.java)
            .verify()

        kotlin.test.assertEquals(3, calls.get())
    }

    @Test
    fun `non-transient errors are never retried`() {
        val calls = AtomicInteger(0)
        val mono = Mono.defer<String> {
            calls.incrementAndGet()
            Mono.error(RailwayClientException("HTTP 400"))
        }.retryWhen(TransientErrorRetry.spec(4))

        StepVerifier.create(mono)
            .expectError(RailwayClientException::class.java)
            .verify()

        kotlin.test.assertEquals(1, calls.get())
    }
}
