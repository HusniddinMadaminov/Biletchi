package uz.railway.ticketbot.railway.mapper

import org.springframework.stereotype.Component
import uz.railway.ticketbot.railway.RailwayCar
import uz.railway.ticketbot.railway.RailwaySeat
import uz.railway.ticketbot.railway.dto.EticketCar
import uz.railway.ticketbot.railway.dto.EticketCarGroup
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class EticketMapper {

    // eticket.railway.uz returns timestamps as "20.08.2026 19:20"
    private val dottedDateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun parseDateTime(raw: String?): LocalDateTime? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { LocalDateTime.parse(it, dottedDateTime) }.getOrNull()
        }

    fun carTypeLabel(group: EticketCarGroup): String =
        group.typeShow ?: group.type ?: "Unknown"

    fun toDomainCar(group: EticketCarGroup, car: EticketCar): RailwayCar = RailwayCar(
        carNumber = car.number,
        carType = carTypeLabel(group),
        freeSeatsCount = car.places.size,
        minimumPrice = group.tariff,
        currency = "UZS"
    )

    fun toDomainSeats(group: EticketCarGroup, car: EticketCar): List<RailwaySeat> =
        car.places.map { seatNumber ->
            RailwaySeat(
                number = seatNumber,
                isAvailable = true,
                price = group.tariff,
                currency = "UZS"
            )
        }
}
