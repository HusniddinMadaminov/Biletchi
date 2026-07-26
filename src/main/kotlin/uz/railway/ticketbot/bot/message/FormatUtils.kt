package uz.railway.ticketbot.bot.message

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object FormatUtils {
    /** Uzbekistan has a single fixed UTC+5 offset year-round (no DST), used for any timestamp shown to users. */
    val TASHKENT_ZONE: ZoneId = ZoneId.of("Asia/Tashkent")

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE_TIME_SECONDS_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

    private val CAR_TYPE_LABELS_UZ = mapOf(
        "sleeper" to "Platskart",
        "coupe" to "Kupe",
        "suite" to "SV",
        "general" to "Umumiy"
    )

    fun date(value: LocalDate): String = value.format(DATE_FORMAT)

    fun time(value: LocalDateTime): String = value.format(TIME_FORMAT)

    /** Second-level precision, e.g. for "last checked" timestamps so a user can tell the bot is actively monitoring. */
    fun dateTimeWithSeconds(value: LocalDateTime): String = value.format(DATE_TIME_SECONDS_FORMAT)

    fun carTypeUz(carType: String): String = CAR_TYPE_LABELS_UZ[carType.trim().lowercase()] ?: carType

    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

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
