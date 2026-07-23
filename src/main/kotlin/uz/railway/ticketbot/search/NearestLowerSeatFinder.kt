package uz.railway.ticketbot.search

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/**
 * Core "nearest date with a lower seat" algorithm (spec section 4 and 18):
 *
 *  - dates are scanned from [startDate] to [endDate], ascending, in small
 *    batches (size [batchSize]) to bound concurrent load on railway.uz
 *  - a batch is always fully awaited before it is inspected, so a
 *    fast-but-later date can never win over a slow-but-earlier one
 *  - as soon as any date in a batch has a match, the earliest such date in
 *    that batch is the answer and no further batches are fetched
 *  - a date "matches" when [fetchOffersForDate] returns a non-empty list
 *    for it (callers are expected to have already filtered for lower seats)
 *
 * Pure and side-effect free apart from [fetchOffersForDate], so it can be
 * unit tested without any HTTP involved.
 */
class NearestLowerSeatFinder(
    private val batchSize: Int = 4
) {
    suspend fun find(
        startDate: LocalDate,
        endDate: LocalDate,
        fetchOffersForDate: suspend (LocalDate) -> List<TicketSearchResult>
    ): List<TicketSearchResult> {
        require(batchSize > 0) { "batchSize must be positive" }
        if (endDate.isBefore(startDate)) return emptyList()

        var batchStart = startDate
        while (!batchStart.isAfter(endDate)) {
            val batchDates = generateSequence(batchStart) { it.plusDays(1) }
                .takeWhile { !it.isAfter(endDate) }
                .take(batchSize)
                .toList()

            val batchResults: List<Pair<LocalDate, List<TicketSearchResult>>> = coroutineScope {
                val deferredByDate: List<Pair<LocalDate, Deferred<List<TicketSearchResult>>>> =
                    batchDates.map { date -> date to async { fetchOffersForDate(date) } }
                deferredByDate.map { (date, deferred) -> date to deferred.await() }
            }

            val firstMatch = batchResults
                .filter { (_, offers) -> offers.isNotEmpty() }
                .minByOrNull { (date, _) -> date }

            if (firstMatch != null) {
                return firstMatch.second.sortedWith(TicketSearchResult.DISPLAY_ORDER)
            }

            batchStart = batchDates.last().plusDays(1)
        }

        return emptyList()
    }
}
