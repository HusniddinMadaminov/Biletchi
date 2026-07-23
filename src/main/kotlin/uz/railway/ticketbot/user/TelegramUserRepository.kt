package uz.railway.ticketbot.user

import org.springframework.data.jpa.repository.JpaRepository

interface TelegramUserRepository : JpaRepository<TelegramUserEntity, Long> {
    fun findByTelegramUserId(telegramUserId: Long): TelegramUserEntity?
}
