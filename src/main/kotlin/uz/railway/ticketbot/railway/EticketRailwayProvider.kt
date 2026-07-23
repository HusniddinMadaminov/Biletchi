package uz.railway.ticketbot.railway

import org.springframework.stereotype.Component
import uz.railway.ticketbot.config.RailwayProperties
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.railway.mapper.EticketMapper
import uz.railway.ticketbot.search.NearestLowerSeatFinder
import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchResult
import java.time.LocalDate

@Component
class EticketRailwayProvider(
    private val client: EticketRailwayClient,
    private val mapper: EticketMapper,
    private val railwayProperties: RailwayProperties,
    botProperties: TicketBotProperties
) : RailwayProvider {

    private val finder = NearestLowerSeatFinder(botProperties.search.dateBatchSize)

    override suspend fun searchTrains(fromStationCode: String, toStationCode: String, date: LocalDate): List<RailwayTrain> =
        client.searchTrains(fromStationCode, toStationCode, date).trains.map(mapper::toDomain)

    override suspend fun getAvailableCars(train: RailwayTrain): List<RailwayCar> =
        client.getCars(train.trainNumber, train.date).cars.map(mapper::toDomain)

    override suspend fun getAvailableSeats(train: RailwayTrain, car: RailwayCar): List<RailwaySeat> =
        client.getSeats(train.trainNumber, train.date, car.carNumber).seats.map(mapper::toDomain)

    override suspend fun findNearestLowerSeat(
        fromStationCode: String,
        fromStationName: String,
        toStationCode: String,
        toStationName: String,
        startDate: LocalDate,
        endDate: LocalDate,
        filters: TicketFilters
    ): List<TicketSearchResult> = finder.find(startDate, endDate) { date ->
        searchOffersForDate(fromStationCode, fromStationName, toStationCode, toStationName, date, filters)
    }

    /** All lower-seat offers for a single date that satisfy [filters], per spec section 5. */
    suspend fun searchOffersForDate(
        fromStationCode: String,
        fromStationName: String,
        toStationCode: String,
        toStationName: String,
        date: LocalDate,
        filters: TicketFilters
    ): List<TicketSearchResult> {
        val trains = searchTrains(fromStationCode, toStationCode, date)
            .filter { train -> filters.trainNumbers.isEmpty() || train.trainNumber in filters.trainNumbers }
            .filter { train -> isWithinDepartureWindow(train, filters) }

        val offers = mutableListOf<TicketSearchResult>()
        for (train in trains) {
            val cars = getAvailableCars(train)
                .filter { car -> filters.allowedCarTypes.isEmpty() || car.carType in filters.allowedCarTypes }

            for (car in cars) {
                val lowerSeats = getAvailableSeats(train, car)
                    .filter { it.isAvailable && it.isLower }
                if (lowerSeats.isEmpty()) continue

                val minPrice = lowerSeats.mapNotNull { it.price }.minOrNull() ?: car.minimumPrice
                if (filters.maxPrice != null && minPrice != null && minPrice > filters.maxPrice) continue

                offers += TicketSearchResult(
                    fromStationCode = fromStationCode,
                    fromStationName = fromStationName,
                    toStationCode = toStationCode,
                    toStationName = toStationName,
                    date = date,
                    trainNumber = train.trainNumber,
                    trainName = train.trainName,
                    departureTime = train.departureTime,
                    arrivalTime = train.arrivalTime,
                    carType = car.carType,
                    carNumber = car.carNumber,
                    lowerSeatNumbers = lowerSeats.map { it.number }.sorted(),
                    minimumPrice = minPrice,
                    currency = lowerSeats.firstOrNull()?.currency ?: car.currency,
                    purchaseUrl = buildPurchaseUrl(fromStationCode, toStationCode, date)
                )
            }
        }
        return offers.sortedWith(TicketSearchResult.DISPLAY_ORDER)
    }

    private fun isWithinDepartureWindow(train: RailwayTrain, filters: TicketFilters): Boolean {
        val time = train.departureTime.toLocalTime()
        val from = filters.departureTimeFrom
        val to = filters.departureTimeTo
        if (from != null && time.isBefore(from)) return false
        if (to != null && time.isAfter(to)) return false
        return true
    }

    private fun buildPurchaseUrl(fromStationCode: String, toStationCode: String, date: LocalDate): String =
        "${railwayProperties.baseUrl}/search?from=$fromStationCode&to=$toStationCode&date=$date"
}
