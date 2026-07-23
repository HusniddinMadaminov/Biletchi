package uz.railway.ticketbot.station

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StationRepository : JpaRepository<StationEntity, Long> {
    fun findByCode(code: String): StationEntity?

    @Query("SELECT s FROM StationEntity s WHERE s.isActive = true AND s.nameNormalized LIKE CONCAT('%', :query, '%') ORDER BY s.name")
    fun searchByNormalizedName(@Param("query") normalizedQuery: String): List<StationEntity>
}
