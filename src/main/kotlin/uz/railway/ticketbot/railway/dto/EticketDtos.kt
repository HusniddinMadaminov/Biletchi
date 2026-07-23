package uz.railway.ticketbot.railway.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/**
 * Wire format for eticket.railway.uz. Field names here are best-effort and
 * MUST be verified/adjusted against the real API contract (e.g. by
 * inspecting the site's network requests) before going to production -
 * everything downstream of [uz.railway.ticketbot.railway.mapper.EticketMapper]
 * is isolated from this shape on purpose so that only this file and the
 * mapper need to change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketStationDto(
    val code: String,
    val name: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketStationSearchResponse(
    val stations: List<EticketStationDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainDto(
    val trainNumber: String,
    val trainName: String? = null,
    val fromStationCode: String,
    val toStationCode: String,
    val departureDate: String,
    val departureTime: String,
    val arrivalDate: String? = null,
    val arrivalTime: String? = null,
    val cars: List<EticketCarDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainSearchResponse(
    val trains: List<EticketTrainDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketCarDto(
    val carNumber: String,
    val carType: String,
    val freeSeatsCount: Int = 0,
    val minimumPrice: BigDecimal? = null,
    val currency: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketCarListResponse(
    val cars: List<EticketCarDto> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketSeatDto(
    val seatNumber: Int,
    val isFree: Boolean,
    val price: BigDecimal? = null,
    val currency: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketSeatListResponse(
    val seats: List<EticketSeatDto> = emptyList()
)
