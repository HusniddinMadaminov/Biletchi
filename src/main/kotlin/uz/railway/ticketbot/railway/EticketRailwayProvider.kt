package uz.railway.ticketbot.railway

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uz.railway.ticketbot.config.RailwayProperties
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.railway.dto.EticketTrainDetails
import uz.railway.ticketbot.railway.dto.EticketTrainSummary
import uz.railway.ticketbot.railway.mapper.EticketMapper
import uz.railway.ticketbot.search.NearestLowerSeatFinder
import uz.railway.ticketbot.search.SeatMode
import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchResult
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Implements the site's own search flow (verified from HAR captures):
 * first POST /api/v3/handbook/trains/list for the date/route, then - only
 * for trains that have free seats at all - POST /api/v1/handbook/trains to
 * get exact free seat numbers per car.
 */
@Component
class EticketRailwayProvider(
    private val client: EticketRailwayClient,
    private val mapper: EticketMapper,
    private val railwayProperties: RailwayProperties,
    botProperties: TicketBotProperties
) : RailwayProvider {

    private val log = LoggerFactory.getLogger(EticketRailwayProvider::class.java)
    private val finder = NearestLowerSeatFinder(botProperties.search.dateBatchSize)

    override suspend fun searchStations(query: String): List<RailwayStation> {
        val response = client.searchStations(query)
        if (response.error != null) {
            log.warn("railway.uz returned error for station search '{}': {}", query, response.error)
        }
        return response.data?.stations.orEmpty().map { RailwayStation(it.code, it.name) }
    }

    override suspend fun searchTrains(fromStationCode: String, toStationCode: String, date: LocalDate): List<RailwayTrain> =
        listTrains(fromStationCode, toStationCode, date).map { summary ->
            RailwayTrain(
                trainNumber = summary.number,
                trainName = summary.brand,
                fromStationCode = fromStationCode,
                toStationCode = toStationCode,
                date = date,
                departureTime = mapper.parseDateTime(summary.departureDate) ?: date.atStartOfDay(),
                arrivalTime = mapper.parseDateTime(summary.arrivalDate)
            )
        }

    override suspend fun getAvailableCars(train: RailwayTrain): List<RailwayCar> {
        val details = fetchTrainDetails(train.fromStationCode, train.toStationCode, train.date, train.trainNumber)
            ?: return emptyList()
        return details.carGroup.flatMap { group -> group.cars.map { car -> mapper.toDomainCar(group, car) } }
    }

    override suspend fun getAvailableSeats(train: RailwayTrain, car: RailwayCar): List<RailwaySeat> {
        val details = fetchTrainDetails(train.fromStationCode, train.toStationCode, train.date, train.trainNumber)
            ?: return emptyList()
        for (group in details.carGroup) {
            val match = group.cars.firstOrNull { it.number == car.carNumber }
            if (match != null) return mapper.toDomainSeats(group, match)
        }
        return emptyList()
    }

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

    /** All matching-seat offers for a single date that satisfy [filters] (spec section 5). */
    suspend fun searchOffersForDate(
        fromStationCode: String,
        fromStationName: String,
        toStationCode: String,
        toStationName: String,
        date: LocalDate,
        filters: TicketFilters
    ): List<TicketSearchResult> {
        val summaries = listTrains(fromStationCode, toStationCode, date)
            .filter { filters.trainNumbers.isEmpty() || it.number in filters.trainNumbers }
            // An empty cars list in the summary means the train is sold out - skip the details call.
            .filter { it.cars.isNotEmpty() }

        val offers = mutableListOf<TicketSearchResult>()
        for (summary in summaries) {
            val summaryDeparture = mapper.parseDateTime(summary.departureDate)
            if (summaryDeparture != null && !isWithinDepartureWindow(summaryDeparture, filters)) continue

            val details = fetchTrainDetails(fromStationCode, toStationCode, date, summary.number) ?: continue
            val departureTime = mapper.parseDateTime(details.departureDate) ?: summaryDeparture ?: date.atStartOfDay()
            if (!isWithinDepartureWindow(departureTime, filters)) continue

            for (group in details.carGroup) {
                val carType = mapper.carTypeLabel(group)
                if (filters.allowedCarTypes.isNotEmpty() && carType !in filters.allowedCarTypes) continue
                if (filters.maxPrice != null && group.tariff != null && group.tariff > filters.maxPrice) continue

                for (car in group.cars) {
                    // Business rule (spec section 2.1): odd seat number = lower berth.
                    // SeatMode.ANY skips that filter entirely - any free seat is a match.
                    val matchingSeats = when (filters.seatMode) {
                        SeatMode.LOWER -> car.places.filter { RailwaySeat.isLowerSeat(it) }
                        SeatMode.ANY -> car.places
                    }.sorted()
                    if (matchingSeats.isEmpty()) continue

                    offers += TicketSearchResult(
                        fromStationCode = fromStationCode,
                        fromStationName = fromStationName,
                        toStationCode = toStationCode,
                        toStationName = toStationName,
                        date = date,
                        trainNumber = details.number.ifBlank { summary.number },
                        trainName = details.brandName ?: details.brand ?: summary.brand,
                        departureTime = departureTime,
                        arrivalTime = mapper.parseDateTime(details.arrivalDate) ?: mapper.parseDateTime(summary.arrivalDate),
                        carType = carType,
                        carNumber = car.number,
                        seatNumbers = matchingSeats,
                        minimumPrice = group.tariff,
                        currency = "UZS",
                        purchaseUrl = buildPurchaseUrl(fromStationCode, toStationCode, date)
                    )
                }
            }
        }
        return offers.sortedWith(TicketSearchResult.DISPLAY_ORDER)
    }

    private suspend fun listTrains(fromStationCode: String, toStationCode: String, date: LocalDate): List<EticketTrainSummary> {
        val response = client.getTrainsList(fromStationCode, toStationCode, date)
        if (response.error != null) {
            log.warn("railway.uz returned error for train list {}->{} on {}: {}", fromStationCode, toStationCode, date, response.error)
        }
        return response.data?.directions?.forward?.trains ?: emptyList()
    }

    private suspend fun fetchTrainDetails(
        fromStationCode: String,
        toStationCode: String,
        date: LocalDate,
        trainNumber: String
    ): EticketTrainDetails? {
        val response = client.getTrainDetails(fromStationCode, toStationCode, date, trainNumber)
        if (response.error != null) {
            log.warn("railway.uz returned error for train {} on {}: {}", trainNumber, date, response.error)
        }
        return response.data?.train
    }

    private fun isWithinDepartureWindow(departureTime: LocalDateTime, filters: TicketFilters): Boolean {
        val time = departureTime.toLocalTime()
        val from = filters.departureTimeFrom
        val to = filters.departureTimeTo
        if (from != null && time.isBefore(from)) return false
        if (to != null && time.isAfter(to)) return false
        return true
    }

    private fun buildPurchaseUrl(fromStationCode: String, toStationCode: String, date: LocalDate): String =
        "${railwayProperties.baseUrl}/uz/home?from=$fromStationCode&to=$toStationCode&date=$date"
}
