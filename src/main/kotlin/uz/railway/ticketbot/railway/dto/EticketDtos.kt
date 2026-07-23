package uz.railway.ticketbot.railway.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.math.BigDecimal

/**
 * Wire format for eticket.railway.uz, reverse-engineered from real browser
 * traffic (HAR captures, 2026-07-23). The site's search flow, in call
 * order, is:
 *
 *  1. POST /api/v1/auth/login  {"username","password"} + captcha-response
 *     header (reCAPTCHA) -> {"token" (1h), "refreshToken" (30d), ...}
 *  2. POST /api/v3/handbook/trains/list
 *     {"directions":{"forward":{"date":"2026-08-20","depStationCode":"2900790",
 *     "arvStationCode":"2900000"}}} -> all trains for the date/route, each
 *     with per-car-type freeSeats/tariff summaries
 *  3. POST /api/v1/handbook/trains {"depDate","depStationCode",
 *     "arvStationCode","trainNumber","trainId":null} -> one train's cars
 *     with exact FREE seat numbers ("places") - the lower-seat source of truth
 *
 * Both search endpoints (2 and 3) are verified against captured responses;
 * fixtures of those responses live under src/test/resources/eticket and are
 * parsed in tests.
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

// ---- POST /api/v3/handbook/trains/list (verified) ----

data class EticketTrainsListRequest(
    val directions: EticketTrainsListDirections
) {
    companion object {
        fun forward(date: String, depStationCode: String, arvStationCode: String) =
            EticketTrainsListRequest(
                EticketTrainsListDirections(
                    EticketTrainsListForward(date, depStationCode, arvStationCode)
                )
            )
    }
}

data class EticketTrainsListDirections(val forward: EticketTrainsListForward)

data class EticketTrainsListForward(
    val date: String,            // ISO: "2026-08-20"
    val depStationCode: String,
    val arvStationCode: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainsListResponse(
    val data: EticketTrainsListData? = null,
    val error: Any? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainsListData(
    val directions: EticketTrainsListDirectionsData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainsListDirectionsData(
    val forward: EticketTrainsListForwardData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainsListForwardData(
    val trains: List<EticketTrainSummary> = emptyList()
)

/**
 * One train row of the list response. An empty [cars] list means the train
 * has no free seats at all (seen for sold-out 751М in the capture), so the
 * per-train details call can be skipped entirely.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTrainSummary(
    val type: String? = null,          // "EX", "В-СКОР"
    val number: String = "",           // "056Ж"
    val departureDate: String? = null, // "20.08.2026 19:20"
    val arrivalDate: String? = null,
    val timeOnWay: String? = null,
    val brand: String? = null,         // "Passenger", "Jaloliddin Manguberdi"
    val originRoute: EticketTrainRouteNames? = null,
    val subRoute: EticketRoute? = null,
    val cars: List<EticketCarSummary> = emptyList(),
    val trainId: String? = null,
    val comment: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketCarSummary(
    val type: String? = null,          // "Sleeper", "Coupe"
    val freeSeats: Int = 0,
    val tariffs: List<EticketTariff> = emptyList(),
    val seatDetail: EticketSeatDetail? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EticketTariff(
    val classServiceType: String? = null, // "3П", "2К"
    val freeSeats: Int = 0,
    val tariff: BigDecimal? = null        // UZS
)
