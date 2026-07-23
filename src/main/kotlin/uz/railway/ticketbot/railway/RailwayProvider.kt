package uz.railway.ticketbot.railway

import uz.railway.ticketbot.search.TicketFilters
import uz.railway.ticketbot.search.TicketSearchResult
import java.time.LocalDate

/**
 * Boundary between the bot/monitoring domain and eticket.railway.uz. If the
 * site's endpoints change, only the implementation of this interface (and
 * its DTO/mapper layer) needs to change - callers never see railway.uz's
 * own request/response shapes.
 */
interface RailwayProvider {

    suspend fun searchTrains(
        fromStationCode: String,
        toStationCode: String,
        date: LocalDate
    ): List<RailwayTrain>

    suspend fun getAvailableCars(train: RailwayTrain): List<RailwayCar>

    suspend fun getAvailableSeats(train: RailwayTrain, car: RailwayCar): List<RailwaySeat>

    /**
     * Scans [startDate]..[endDate] (inclusive, ascending) and returns every
     * offer on the first date that has at least one available lower seat.
     * Returns an empty list if no date in the range has one.
     */
    suspend fun findNearestLowerSeat(
        fromStationCode: String,
        fromStationName: String,
        toStationCode: String,
        toStationName: String,
        startDate: LocalDate,
        endDate: LocalDate,
        filters: TicketFilters
    ): List<TicketSearchResult>
}
