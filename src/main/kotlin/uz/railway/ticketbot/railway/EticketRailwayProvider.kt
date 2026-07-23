package uz.railway.ticketbot.railway

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uz.railway.ticketbot.config.RailwayProperties
import uz.railway.ticketbot.config.TicketBotProperties
import uz.railway.ticketbot.railway.dto.EticketTrainDetails
import uz.railway.ticketbot.railway.mapper.EticketMapper
import uz.railway.ticketbot.search.NearestLowerSeatFinder
import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchResult
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class EticketRailwayProvider(
    private val client: EticketRailwayClient,
    private val mapper: EticketMapper,
    private val railwayProperties: RailwayProperties,
    botProperties: TicketBotProperties
) : RailwayProvider {

    private val log = LoggerFactory.getLogger(EticketRailwayProvider::class.java)
    private val finder = NearestLowerSeatFinder(botProperties.search.dateBatchSize)

    // Train numbers look like "056Ж" / "760Ф" - 3 digits plus an optional letter.
    // Used to tell train numbers apart from car numbers ("09") when defensively
    // parsing the not-yet-verified train-list response.
    private val trainNumberPattern = Regex("""^\d{3}\p{L}?$""")

    override suspend fun searchTrains(fromStationCode: String, toStationCode: String, date: LocalDate): List<RailwayTrain> {
        val trainNumbers = listTrainNumbers(fromStationCode, toStationCode, date)
        return trainNumbers.mapNotNull { number ->
            fetchTrainDetails(fromStationCode, toStationCode, date, number)?.let { details ->
                RailwayTrain(
                    trainNumber = details.number.ifBlank { number },
                    trainName = details.brandName ?: details.brand,
                    fromStationCode = fromStationCode,
                    toStationCode = toStationCode,
                    date = date,
                    departureTime = mapper.parseDateTime(details.departureDate) ?: date.atStartOfDay(),
                    arrivalTime = mapper.parseDateTime(details.arrivalDate)
                )
            }
        }
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

    /** All lower-seat offers for a single date that satisfy [filters], per spec section 5. */
    suspend fun searchOffersForDate(
        fromStationCode: String,
        fromStationName: String,
        toStationCode: String,
        toStationName: String,
        date: LocalDate,
        filters: TicketFilters
    ): List<TicketSearchResult> {
        // If the user pinned train numbers, the unverified train-list endpoint
        // can be skipped entirely - the verified per-train endpoint is enough.
        val trainNumbers = if (filters.trainNumbers.isNotEmpty()) {
            filters.trainNumbers.toList()
        } else {
            listTrainNumbers(fromStationCode, toStationCode, date)
        }

        val offers = mutableListOf<TicketSearchResult>()
        for (trainNumber in trainNumbers) {
            val details = fetchTrainDetails(fromStationCode, toStationCode, date, trainNumber) ?: continue
            val departureTime = mapper.parseDateTime(details.departureDate) ?: date.atStartOfDay()
            if (!isWithinDepartureWindow(departureTime, filters)) continue

            for (group in details.carGroup) {
                val carType = mapper.carTypeLabel(group)
                if (filters.allowedCarTypes.isNotEmpty() && carType !in filters.allowedCarTypes) continue
                if (filters.maxPrice != null && group.tariff != null && group.tariff > filters.maxPrice) continue

                for (car in group.cars) {
                    // Business rule (spec section 2.1): odd seat number = lower berth.
                    val lowerSeats = car.places.filter { RailwaySeat.isLowerSeat(it) }.sorted()
                    if (lowerSeats.isEmpty()) continue

                    offers += TicketSearchResult(
                        fromStationCode = fromStationCode,
                        fromStationName = fromStationName,
                        toStationCode = toStationCode,
                        toStationName = toStationName,
                        date = date,
                        trainNumber = details.number.ifBlank { trainNumber },
                        trainName = details.brandName ?: details.brand,
                        departureTime = departureTime,
                        arrivalTime = mapper.parseDateTime(details.arrivalDate),
                        carType = carType,
                        carNumber = car.number,
                        lowerSeatNumbers = lowerSeats,
                        minimumPrice = group.tariff,
                        currency = "UZS",
                        purchaseUrl = buildPurchaseUrl(fromStationCode, toStationCode, date)
                    )
                }
            }
        }
        return offers.sortedWith(TicketSearchResult.DISPLAY_ORDER)
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

    /**
     * Train numbers for a date/route via the not-yet-verified list endpoint.
     * The response shape is unconfirmed, so it is parsed defensively: every
     * "number" field anywhere in the tree that looks like a train number
     * (3 digits + optional letter) is collected. Once a HAR capture of the
     * train search page confirms the real shape, replace this with typed DTOs.
     */
    private suspend fun listTrainNumbers(fromStationCode: String, toStationCode: String, date: LocalDate): List<String> {
        val root: JsonNode = try {
            client.searchTrainsRaw(fromStationCode, toStationCode, date)
        } catch (ex: Exception) {
            log.warn("Train list request failed for {}->{} on {} ({}). If this persists, capture the train-search page traffic to verify the endpoint.", fromStationCode, toStationCode, date, ex.message)
            throw ex
        }
        val numbers = root.findValues("number")
            .mapNotNull { it.takeIf(JsonNode::isTextual)?.asText() }
            .filter { trainNumberPattern.matches(it) }
            .distinct()
        if (numbers.isEmpty()) {
            log.warn("No train numbers recognised in train-list response for {}->{} on {}; the endpoint shape may differ from the assumed one.", fromStationCode, toStationCode, date)
        }
        return numbers
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
