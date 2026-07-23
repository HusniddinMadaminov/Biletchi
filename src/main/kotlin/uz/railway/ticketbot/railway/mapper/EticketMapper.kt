package uz.railway.ticketbot.railway.mapper

import org.springframework.stereotype.Component
import uz.railway.ticketbot.railway.RailwayCar
import uz.railway.ticketbot.railway.RailwaySeat
import uz.railway.ticketbot.railway.RailwayTrain
import uz.railway.ticketbot.railway.dto.EticketCarDto
import uz.railway.ticketbot.railway.dto.EticketSeatDto
import uz.railway.ticketbot.railway.dto.EticketTrainDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Component
class EticketMapper {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun toDomain(dto: EticketTrainDto): RailwayTrain {
        val date = LocalDate.parse(dto.departureDate, dateFormatter)
        val departure = LocalDateTime.of(date, LocalTime.parse(dto.departureTime, timeFormatter))
        val arrival = dto.arrivalTime?.let { time ->
            val arrivalDate = dto.arrivalDate?.let { LocalDate.parse(it, dateFormatter) } ?: date
            LocalDateTime.of(arrivalDate, LocalTime.parse(time, timeFormatter))
        }
        return RailwayTrain(
            trainNumber = dto.trainNumber,
            trainName = dto.trainName,
            fromStationCode = dto.fromStationCode,
            toStationCode = dto.toStationCode,
            date = date,
            departureTime = departure,
            arrivalTime = arrival
        )
    }

    fun toDomain(dto: EticketCarDto): RailwayCar = RailwayCar(
        carNumber = dto.carNumber,
        carType = dto.carType,
        freeSeatsCount = dto.freeSeatsCount,
        minimumPrice = dto.minimumPrice,
        currency = dto.currency
    )

    fun toDomain(dto: EticketSeatDto): RailwaySeat = RailwaySeat(
        number = dto.seatNumber,
        isAvailable = dto.isFree,
        price = dto.price,
        currency = dto.currency
    )
}
