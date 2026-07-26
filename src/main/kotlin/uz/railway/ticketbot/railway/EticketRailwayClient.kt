package uz.railway.ticketbot.railway

import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import reactor.core.publisher.Mono
import uz.railway.ticketbot.common.retry.TransientErrorRetry
import uz.railway.ticketbot.config.RailwayProperties
import uz.railway.ticketbot.railway.dto.EticketTrainDetailsRequest
import uz.railway.ticketbot.railway.dto.EticketTrainDetailsResponse
import uz.railway.ticketbot.railway.dto.EticketTrainsListRequest
import uz.railway.ticketbot.railway.dto.EticketTrainsListResponse
import uz.railway.ticketbot.railway.exception.RailwayAuthException
import uz.railway.ticketbot.railway.exception.RailwayClientException
import uz.railway.ticketbot.railway.exception.RailwayTransientException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * HTTP layer over eticket.railway.uz. Both endpoints below are VERIFIED
 * against real browser traffic (HAR captures, 2026-07-23):
 *
 *  - [getTrainsList]  POST /api/v3/handbook/trains/list - all trains for a
 *    date/route with per-car-type free seat summaries
 *  - [getTrainDetails] POST /api/v1/handbook/trains - one train's cars with
 *    exact free seat numbers
 *
 * The site fronts its API with Spring Security's cookie-based CSRF
 * protection: browser requests carry an XSRF-TOKEN cookie plus a matching
 * X-XSRF-TOKEN header, and POSTs without the pair are rejected with 403.
 * This client bootstraps the cookie with a GET to the site root, caches it,
 * sends the pair on every POST, and refreshes it once if a 403 slips
 * through (stale token).
 *
 * The captured traffic was also from a logged-in session (Bearer JWT). The
 * login endpoint requires reCAPTCHA, so the bot cannot log in by itself;
 * if the API turns out to require auth beyond CSRF, set RAILWAY_AUTH_TOKEN
 * to a token from a browser session (1-hour expiry; a refresh-token flow
 * is a follow-up).
 */
@Component
class EticketRailwayClient(
    private val webClient: WebClient,
    private val properties: RailwayProperties
) {
    private val log = LoggerFactory.getLogger(EticketRailwayClient::class.java)
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    @Volatile
    private var xsrfToken: String? = null

    suspend fun getTrainsList(
        depStationCode: String,
        arvStationCode: String,
        date: LocalDate
    ): EticketTrainsListResponse = post(
        "/api/v3/handbook/trains/list",
        EticketTrainsListRequest.forward(date.format(isoDate), depStationCode, arvStationCode),
        EticketTrainsListResponse::class.java
    )

    suspend fun getTrainDetails(
        depStationCode: String,
        arvStationCode: String,
        date: LocalDate,
        trainNumber: String
    ): EticketTrainDetailsResponse = post(
        "/api/v1/handbook/trains",
        EticketTrainDetailsRequest(
            depDate = date.format(isoDate),
            depStationCode = depStationCode,
            arvStationCode = arvStationCode,
            trainNumber = trainNumber
        ),
        EticketTrainDetailsResponse::class.java
    )

    private suspend fun <T> post(path: String, body: Any, responseType: Class<T>): T {
        val token = ensureXsrfToken()
        return try {
            doPost(path, body, responseType, token)
        } catch (ex: RailwayAuthException) {
            // A cached XSRF token may have gone stale - refresh it once and retry.
            log.info("Got auth error on {}, refreshing XSRF token and retrying once", path)
            xsrfToken = null
            val fresh = ensureXsrfToken()
            if (fresh == null || fresh == token) throw ex
            doPost(path, body, responseType, fresh)
        }
    }

    private suspend fun <T> doPost(path: String, body: Any, responseType: Class<T>, xsrf: String?): T {
        val mono = webClient.post()
            .uri(path)
            .headers { headers ->
                headers.set("Accept", "application/json")
                headers.set("device-type", "BROWSER")
                headers.set("Accept-Language", "en")
                headers.set("User-Agent", BROWSER_USER_AGENT)
                if (xsrf != null) {
                    headers.set("X-XSRF-TOKEN", xsrf)
                    headers.set("Cookie", "XSRF-TOKEN=$xsrf")
                }
                properties.authToken?.takeIf { it.isNotBlank() }?.let {
                    headers.setBearerAuth(it)
                }
            }
            .bodyValue(body)
            .retrieve()
            .onStatus({ it.isError }) { response -> Mono.error(classifyError(response.statusCode())) }
            .bodyToMono(responseType)
            .onErrorMap(WebClientRequestException::class.java) { RailwayTransientException("Network error calling railway.uz: ${it.message}", it) }
            .retryWhen(TransientErrorRetry.spec(properties.retry.maxAttempts))
            .doOnError { err -> log.error("railway.uz call failed: {} {}", path, err.message) }

        return mono.awaitSingle()
    }

    /**
     * Bootstraps (and caches) the XSRF-TOKEN cookie. Which response first
     * carries the Set-Cookie varies by deployment, so several candidate
     * pages are tried in order. If none sets it, a self-generated UUID is
     * used instead: the site's protection is the stateless double-submit
     * pattern (its 403 message is "An expected CSRF token cannot be
     * found"), where the server only checks that the cookie and the
     * X-XSRF-TOKEN header carry the same value - a value the client itself
     * minted is just as valid.
     */
    private suspend fun ensureXsrfToken(): String? {
        xsrfToken?.let { return it }
        for (path in XSRF_BOOTSTRAP_PATHS) {
            val fetched = fetchXsrfCookie(path)
            if (fetched != null) {
                xsrfToken = fetched
                log.info("Obtained XSRF token from railway.uz via GET {}", path)
                return fetched
            }
        }
        val generated = java.util.UUID.randomUUID().toString()
        xsrfToken = generated
        log.info("No XSRF-TOKEN cookie offered by railway.uz; using a self-generated double-submit token")
        return generated
    }

    private suspend fun fetchXsrfCookie(path: String): String? =
        webClient.get()
            .uri(path)
            .headers { headers ->
                headers.set("User-Agent", BROWSER_USER_AGENT)
                headers.set("Accept-Language", "en")
                headers.set("device-type", "BROWSER")
            }
            .exchangeToMono { response ->
                val cookie = response.cookies()["XSRF-TOKEN"]?.firstOrNull()?.value
                response.releaseBody().thenReturn(cookie ?: "")
            }
            .onErrorResume { ex ->
                log.warn("XSRF bootstrap GET {} failed: {}", path, ex.message)
                Mono.just("")
            }
            .awaitSingle()
            .takeIf { it.isNotBlank() }

    private fun classifyError(status: HttpStatusCode): Exception {
        val code = status.value()
        return when {
            code == 401 || code == 403 -> RailwayAuthException("railway.uz auth error: HTTP $code")
            code == 429 || code in 500..599 -> RailwayTransientException("railway.uz transient error: HTTP $code")
            else -> RailwayClientException("railway.uz client error: HTTP $code")
        }
    }

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        private val XSRF_BOOTSTRAP_PATHS = listOf("/", "/en/home", "/api/v1/line-runner")
    }
}
