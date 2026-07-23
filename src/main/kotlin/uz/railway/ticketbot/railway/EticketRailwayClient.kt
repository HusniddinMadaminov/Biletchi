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
 * The captured requests carried a logged-in user's Bearer token (the site's
 * login endpoint requires reCAPTCHA, so the bot cannot log in by itself).
 * Whether these two endpoints also answer anonymously is not yet known - if
 * they do not, set RAILWAY_AUTH_TOKEN to a token obtained from a browser
 * session (1-hour expiry; a proper refresh-token flow is a follow-up).
 */
@Component
class EticketRailwayClient(
    private val webClient: WebClient,
    private val properties: RailwayProperties
) {
    private val log = LoggerFactory.getLogger(EticketRailwayClient::class.java)
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

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
        val mono = webClient.post()
            .uri(path)
            .headers { headers ->
                headers.set("Accept", "application/json")
                headers.set("device-type", "BROWSER")
                headers.set("Accept-Language", "en")
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

    private fun classifyError(status: HttpStatusCode): Exception {
        val code = status.value()
        return when {
            code == 401 || code == 403 -> RailwayAuthException("railway.uz auth error: HTTP $code")
            code == 429 || code in 500..599 -> RailwayTransientException("railway.uz transient error: HTTP $code")
            else -> RailwayClientException("railway.uz client error: HTTP $code")
        }
    }
}
