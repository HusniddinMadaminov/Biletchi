package uz.railway.ticketbot.bot.message

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object FormatUtils {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

    fun date(value: LocalDate): String = value.format(DATE_FORMAT)

    fun time(value: LocalDateTime): String = value.format(TIME_FORMAT)

    fun price(amount: BigDecimal?, currency: String?): String {
        if (amount == null) return "noma'lum"
        val grouped = amount.toBigInteger().toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()
        val unit = when (currency?.uppercase()) {
            "UZS", null, "" -> "so'm"
            else -> currency
        }
        return "$grouped $unit"
    }
}
