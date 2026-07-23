package uz.railway.ticketbot.search

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalTime

data class TicketFilters(
    val trainNumbers: Set<String> = emptySet(),
    val allowedCarTypes: Set<String> = emptySet(),
    val departureTimeFrom: LocalTime? = null,
    val departureTimeTo: LocalTime? = null,
    val maxPrice: BigDecimal? = null
) {
    companion object {
        val NONE = TicketFilters()
    }

    /** Stable hash used as part of the monitoring de-duplication search key. */
    fun stableHash(): String {
        val raw = listOf(
            trainNumbers.sorted().joinToString(","),
            allowedCarTypes.sorted().joinToString(","),
            departureTimeFrom?.toString().orEmpty(),
            departureTimeTo?.toString().orEmpty(),
            maxPrice?.toPlainString().orEmpty()
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
