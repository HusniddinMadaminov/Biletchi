package uz.railway.ticketbot.railway

import java.math.BigDecimal

/**
 * A single seat as reported by the railway site.
 *
 * Business rule (must not change): odd seat numbers are "lower" berths,
 * even seat numbers are "upper" berths.
 */
data class RailwaySeat(
    val number: Int,
    val isAvailable: Boolean,
    val price: BigDecimal? = null,
    val currency: String? = null
) {
    val isLower: Boolean
        get() = isLowerSeat(number)

    companion object {
        fun isLowerSeat(seatNumber: Int): Boolean = seatNumber % 2 != 0
    }
}
