package uz.railway.ticketbot.search

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

fun sampleOffer(
    date: LocalDate,
    seatNumbers: List<Int> = listOf(9, 15),
    trainNumber: String = "058",
    departureTime: LocalTime = LocalTime.of(20, 15),
    price: BigDecimal = BigDecimal(346000)
): TicketSearchResult = TicketSearchResult(
    fromStationCode = "TASHKENT",
    fromStationName = "Toshkent",
    toStationCode = "URGANCH",
    toStationName = "Urganch",
    date = date,
    trainNumber = trainNumber,
    trainName = null,
    departureTime = LocalDateTime.of(date, departureTime),
    arrivalTime = null,
    carType = "Kupe",
    carNumber = "4",
    seatNumbers = seatNumbers,
    minimumPrice = price,
    currency = "UZS",
    purchaseUrl = "https://eticket.railway.uz/search"
)

fun sampleRequest(
    startDate: LocalDate,
    endDate: LocalDate
): TicketSearchRequest = TicketSearchRequest(
    telegramUserId = 1L,
    fromStationCode = "TASHKENT",
    fromStationName = "Toshkent",
    toStationCode = "URGANCH",
    toStationName = "Urganch",
    startDate = startDate,
    endDate = endDate
)
