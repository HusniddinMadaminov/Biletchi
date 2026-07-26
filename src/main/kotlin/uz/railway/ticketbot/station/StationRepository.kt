package uz.railway.ticketbot.station

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional

interface StationRepository : JpaRepository<StationEntity, Long> {
    fun findByCode(code: String): StationEntity?

    @Query("SELECT s FROM StationEntity s WHERE s.isActive = true AND s.nameNormalized LIKE CONCAT('%', :query, '%') ORDER BY s.name")
    fun searchByNormalizedName(@Param("query") normalizedQuery: String): List<StationEntity>

    /** Caches a station returned by a live railway.uz search so future lookups (and findByCode) work offline. */
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO stations (code, name, name_normalized, is_active)
            VALUES (:code, :name, :nameNormalized, true)
            ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, name_normalized = EXCLUDED.name_normalized
        """,
        nativeQuery = true
    )
    fun upsert(@Param("code") code: String, @Param("name") name: String, @Param("nameNormalized") nameNormalized: String)
}
