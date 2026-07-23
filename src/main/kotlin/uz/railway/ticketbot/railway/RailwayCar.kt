package uz.railway.ticketbot.railway

import java.math.BigDecimal

data class RailwayCar(
    val carNumber: String,
    val carType: String,
    val freeSeatsCount: Int,
    val minimumPrice: BigDecimal? = null,
    val currency: String? = null
)
