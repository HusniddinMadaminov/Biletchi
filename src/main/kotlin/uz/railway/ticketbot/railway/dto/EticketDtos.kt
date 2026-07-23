package uz.railway.ticketbot.railway.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/**
 * Wire format for eticket.railway.uz, reverse-engineered from real browser
 * traffic (HAR capture, 2026-07-23) against POST /api/v1/handbook/trains.
 * That endpoint returns, for one train on one date/route, every car group
 * with its per-car list of FREE seat numbers ("places") - which is exactly
 * what the lower-seat search needs.
 *
 * Request (verified):
 *   {"depDate":"2026-08-20","depStationCode":"2900790","arvStationCode":"2900000",
 *    "trainNumber":"056Ж","trainId":null}
 *
 * Response (verified): {"data":{"train":{...,"carGroup":[{"type","typeShow","tariff",
 *   "cars":[{"number","places":[...],"schema":{...},"seatDetail":{...}}]}],
 *   "departureDate":"20.08.2026 19:20",...},"route":{...}},"error":null}
 */
data class EticketTrainDetailsRequest(
    val depDate: String,          // ISO: "2026-08-20"
    val depStationCode: String,   // Express-3 code, e.g. "2900790"
    val arvStationCode: String,
    val trainNumber: String,      // e.g. "056Ж"
    val trainId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainDetailsResponse(
    val data: EticketTrainDetailsData? = null,
    val error: Any? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainDetailsData(
    val train: EticketTrainDetails? = null,
    val route: EticketRoute? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketRoute(
    val depStationName: String? = null,
    val depStationCode: String? = null,
    val arvStationName: String? = null,
    val arvStationCode: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainDetails(
    val number: String = "",
    val brandName: String? = null,
    val brand: String? = null,
    val type: String? = null,
    val vendorType: String? = null,
    val carGroup: List<EticketCarGroup> = emptyList(),
    val route: EticketTrainRouteNames? = null,
    val departureDate: String? = null,   // "20.08.2026 19:20"
    val arrivalDate: String? = null,     // "21.08.2026 08:22"
    val timeOnWay: String? = null,
    val trainId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainRouteNames(
    val depStationName: String? = null,
    val arvStationName: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketCarGroup(
    val type: String? = null,        // "Плацкартный"
    val typeShow: String? = null,    // "Sleeper"
    val tariff: BigDecimal? = null,  // per-seat tariff in UZS, e.g. 245140
    val cars: List<EticketCar> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketCar(
    val number: String = "",          // "09"
    val places: List<Int> = emptyList(), // FREE seat numbers
    val schema: EticketCarSchema? = null,
    val seatDetail: EticketSeatDetail? = null,
    val carId: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketCarSchema(
    val service: String? = null,     // "3П"
    val name: String? = null,        // "car-std-sleeping-carriage-54.svg"
    val seatsCount: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketSeatDetail(
    val lateralDn: Int? = null,
    val lateralUp: Int? = null,
    val freeComp: Int? = null,
    val down: Int? = null,
    val up: Int? = null
)
