package uz.railway.ticketbot.railway

import java.time.LocalDate
import java.time.LocalDateTime

data class RailwayTrain(
    val trainNumber: String,
    val trainName: String? = null,
    val fromStationCode: String,
    val toStationCode: String,
    val date: LocalDate,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime? = null
)
