package uz.railway.ticketbot.search

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime

data class TicketSearchResult(
    val fromStationCode: String,
    val fromStationName: String,
    val toStationCode: String,
    val toStationName: String,
    val date: LocalDate,
    val trainNumber: String,
    val trainName: String?,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime?,
    val carType: String,
    val carNumber: String,
    val lowerSeatNumbers: List<Int>,
    val minimumPrice: BigDecimal?,
    val currency: String?,
    val purchaseUrl: String?
) {
    @get:JsonIgnore
    val fingerprint: String by lazy { buildFingerprint(this) }

    companion object {
        /**
         * Identity of a concrete offer: direction + date + train + departure time +
         * car + the exact set of lower seats. Used only to suppress duplicate
         * notifications for an unchanged offer; it never overrides the date-based
         * notification rule (see NotificationService).
         */
        fun buildFingerprint(result: TicketSearchResult): String {
            val raw = listOf(
                result.fromStationCode,
                result.toStationCode,
                result.date.toString(),
                result.trainNumber,
                result.departureTime.toString(),
                result.carType,
                result.carNumber,
                result.lowerSeatNumbers.sorted().joinToString(",")
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * Result comparator per spec section 5: date, then departure time, then
         * price, then train number, then car number, then lowest seat number.
         */
        val DISPLAY_ORDER: Comparator<TicketSearchResult> =
            compareBy<TicketSearchResult> { it.date }
                .thenBy { it.departureTime }
                .thenBy { it.minimumPrice ?: BigDecimal.ZERO }
                .thenBy { it.trainNumber }
                .thenBy { it.carNumber }
                .thenBy { it.lowerSeatNumbers.minOrNull() ?: Int.MAX_VALUE }
    }
}
