package uz.railway.ticketbot.railway

import com.fasterxml.jackson.databind.JsonNode
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
import uz.railway.ticketbot.railway.exception.RailwayAuthException
import uz.railway.ticketbot.railway.exception.RailwayClientException
import uz.railway.ticketbot.railway.exception.RailwayTransientException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * HTTP layer over eticket.railway.uz.
 *
 * [getTrainDetails] (POST /api/v1/handbook/trains) is VERIFIED against real
 * browser traffic (HAR capture, 2026-07-23): it returns every car of one
 * train with its free seat numbers. The captured request carried a Bearer
 * token of a logged-in user; whether the endpoint also answers anonymously
 * is not yet known - if it does not, set RAILWAY_AUTH_TOKEN.
 *
 * [searchTrainsRaw] (the per-date train list) is NOT yet verified - the HAR
 * only covered the seats page. Its path/body follow the shape commonly seen
 * for this site but must be confirmed with a HAR capture of the train
 * search page; until then the response is handled as raw JSON and parsed
 * defensively in [EticketRailwayProvider].
 */
@Component
class EticketRailwayClient(
    private val webClient: WebClient,
    private val properties: RailwayProperties
) {
    private val log = LoggerFactory.getLogger(EticketRailwayClient::class.java)
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val dottedDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

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

    suspend fun searchTrainsRaw(
        depStationCode: String,
        arvStationCode: String,
        date: LocalDate
    ): JsonNode = post(
        "/api/v2/trains/availability/space/between/stations",
        mapOf(
            "direction" to listOf(
                mapOf(
                    "depDate" to date.format(dottedDate),
                    "fullday" to true,
                    "type" to "Forward"
                )
            ),
            "stationFrom" to depStationCode,
            "stationTo" to arvStationCode,
            "detailNumPlaces" to 1,
            "showWithoutPlaces" to 0
        ),
        JsonNode::class.java
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
