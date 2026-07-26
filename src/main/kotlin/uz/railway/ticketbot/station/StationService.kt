package uz.railway.ticketbot.station

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uz.railway.ticketbot.railway.RailwayProvider
import uz.railway.ticketbot.railway.RailwayStation
import java.text.Normalizer
import java.util.Locale

/**
 * Station name search backing the "qayerdan/qayerga" wizard steps (spec
 * sections 3.2/22). Searches railway.uz's own station directory live so
 * every station on the site is reachable, not just a small seed list;
 * results are cached into the local `stations` table both to serve
 * [findByCode] later (used when the user picks a station) and as an
 * offline fallback if railway.uz is unreachable.
 */
@Service
class StationService(
    private val repository: StationRepository,
    private val railwayProvider: RailwayProvider
) {
    private val log = LoggerFactory.getLogger(StationService::class.java)

    suspend fun search(query: String, limit: Int = 8): List<Station> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val live = try {
            railwayProvider.searchStations(trimmed)
        } catch (ex: Exception) {
            log.warn("Live station search for '{}' failed, falling back to local cache: {}", trimmed, ex.message)
            null
        }

        if (live != null) {
            if (live.isNotEmpty()) cacheStations(live)
            return live.take(limit).map { Station(code = it.code, name = prettyName(it.name)) }
        }

        val normalized = normalize(trimmed)
        return repository.searchByNormalizedName(normalized).take(limit).map { it.toDomain() }
    }

    fun findByCode(code: String): Station? = repository.findByCode(code)?.toDomain()

    private fun cacheStations(stations: List<RailwayStation>) {
        for (station in stations) {
            try {
                repository.upsert(station.code, prettyName(station.name), normalize(station.name))
            } catch (ex: Exception) {
                log.warn("Failed to cache station {} ({}): {}", station.code, station.name, ex.message)
            }
        }
    }

    companion object {
        fun normalize(input: String): String =
            Normalizer.normalize(input.trim().lowercase(Locale.forLanguageTag("uz")), Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}"), "")

        /** railway.uz returns station names upper-cased ("TASHKENT SOUTH") - show them Title Cased instead. */
        fun prettyName(raw: String): String =
            raw.trim().lowercase(Locale.forLanguageTag("uz")).split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.forLanguageTag("uz")) } }
    }
}
