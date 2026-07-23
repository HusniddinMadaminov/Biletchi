package uz.railway.ticketbot.search

import org.springframework.data.jpa.repository.JpaRepository

interface SearchLogRepository : JpaRepository<SearchLogEntity, Long>
