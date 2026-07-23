package uz.railway.ticketbot.search

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uz.railway.ticketbot.railway.RailwayProvider

/**
 * Thin orchestration over [RailwayProvider.findNearestLowerSeat] (spec
 * section 4): the earliest date in the requested range with an available
 * lower seat, plus every matching offer on that date (spec section 5).
 * Later dates are never fetched once a match is found.
 */
@Service
class TicketSearchService(
    private val railwayProvider: RailwayProvider,
    private val searchLogRepository: SearchLogRepository
) {
    suspend fun findNearestLowerSeat(request: TicketSearchRequest): TicketSearchOutcome {
        val offers = railwayProvider.findNearestLowerSeat(
            fromStationCode = request.fromStationCode,
            fromStationName = request.fromStationName,
            toStationCode = request.toStationCode,
            toStationName = request.toStationName,
            startDate = request.startDate,
            endDate = request.endDate,
            filters = request.filters
        )
        val outcome = TicketSearchOutcome(request, offers)
        logSearch(outcome)
        return outcome
    }

    @Transactional
    fun logSearch(outcome: TicketSearchOutcome) {
        val request = outcome.request
        searchLogRepository.save(
            SearchLogEntity(
                telegramUserId = request.telegramUserId,
                fromStationCode = request.fromStationCode,
                toStationCode = request.toStationCode,
                startDate = request.startDate,
                endDate = request.endDate,
                foundDate = outcome.bestDate
            )
        )
    }
}
