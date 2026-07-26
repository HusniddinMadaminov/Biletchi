package uz.railway.ticketbot.search

enum class SeatMode {
    /** Any available seat counts, odd or even. */
    ANY,

    /** Only odd-numbered (lower berth) seats count - the original spec rule. */
    LOWER
}
