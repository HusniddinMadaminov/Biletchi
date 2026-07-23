package uz.railway.ticketbot.railway

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import kotlinx.coroutines.reactor.awaitSingle
import reactor.core.publisher.Mono
import uz.railway.ticketbot.common.retry.TransientErrorRetry
import uz.railway.ticketbot.config.RailwayProperties
import uz.railway.ticketbot.railway.dto.EticketCarListResponse
import uz.railway.ticketbot.railway.dto.EticketSeatListResponse
import uz.railway.ticketbot.railway.dto.EticketStationSearchResponse
import uz.railway.ticketbot.railway.dto.EticketTrainSearchResponse
import uz.railway.ticketbot.railway.exception.RailwayAuthException
import uz.railway.ticketbot.railway.exception.RailwayClientException
import uz.railway.ticketbot.railway.exception.RailwayTransientException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Thin HTTP layer over eticket.railway.uz. Endpoint paths/params below are
 * placeholders that MUST be verified against the real site before
 * production use; everything else in this codebase depends only on
 * [RailwayProvider], so fixing this file (and the DTOs/mapper it feeds) is
 * the only work required if the site's contract differs.
 */
@Component
class EticketRailwayClient(
    private val webClient: WebClient,
    private val properties: RailwayProperties
) {
    private val log = LoggerFactory.getLogger(EticketRailwayClient::class.java)
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun searchStations(query: String): EticketStationSearchResponse =
        get("/api/v1/stations", mapOf("query" to query), EticketStationSearchResponse::class.java)

    suspend fun searchTrains(fromStationCode: String, toStationCode: String, date: LocalDate): EticketTrainSearchResponse =
        get(
            "/api/v1/trains/search",
            mapOf(
                "from" to fromStationCode,
                "to" to toStationCode,
                "date" to date.format(dateFormatter)
            ),
            EticketTrainSearchResponse::class.java
        )

    suspend fun getCars(trainNumber: String, date: LocalDate): EticketCarListResponse =
        get(
            "/api/v1/trains/$trainNumber/cars",
            mapOf("date" to date.format(dateFormatter)),
            EticketCarListResponse::class.java
        )

    suspend fun getSeats(trainNumber: String, date: LocalDate, carNumber: String): EticketSeatListResponse =
        get(
            "/api/v1/trains/$trainNumber/cars/$carNumber/seats",
            mapOf("date" to date.format(dateFormatter)),
            EticketSeatListResponse::class.java
        )

    private suspend fun <T> get(path: String, params: Map<String, String>, responseType: Class<T>): T {
        val mono = webClient.get()
            .uri { builder ->
                val uriBuilder = builder.path(path)
                params.forEach { (key, value) -> uriBuilder.queryParam(key, value) }
                uriBuilder.build()
            }
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
