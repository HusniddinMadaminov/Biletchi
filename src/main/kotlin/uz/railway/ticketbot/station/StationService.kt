package uz.railway.ticketbot.station

import org.springframework.stereotype.Service
import java.text.Normalizer
import java.util.Locale

@Service
class StationService(
    private val repository: StationRepository
) {
    fun search(query: String, limit: Int = 8): List<Station> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        return repository.searchByNormalizedName(normalized)
            .take(limit)
            .map { it.toDomain() }
    }

    fun findByCode(code: String): Station? = repository.findByCode(code)?.toDomain()

    companion object {
        fun normalize(input: String): String =
            Normalizer.normalize(input.trim().lowercase(Locale.forLanguageTag("uz")), Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}"), "")
    }
}
