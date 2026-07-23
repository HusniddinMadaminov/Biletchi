package uz.railway.ticketbot.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class TelegramUserService(
    private val repository: TelegramUserRepository,
    private val clock: Clock
) {
    @Transactional
    fun getOrCreate(telegramUserId: Long, chatId: Long, username: String?, firstName: String?, lastName: String?): TelegramUserEntity {
        val existing = repository.findByTelegramUserId(telegramUserId)
        if (existing != null) {
            existing.chatId = chatId
            existing.username = username
            existing.firstName = firstName
            existing.lastName = lastName
            existing.updatedAt = Instant.now(clock)
            return repository.save(existing)
        }
        val created = TelegramUserEntity(
            telegramUserId = telegramUserId,
            chatId = chatId,
            username = username,
            firstName = firstName,
            lastName = lastName
        )
        return repository.save(created)
    }

    @Transactional
    fun updateConversationState(telegramUserId: Long, state: ConversationState, context: String?) {
        val user = repository.findByTelegramUserId(telegramUserId) ?: return
        user.conversationState = state
        user.conversationContext = context
        user.updatedAt = Instant.now(clock)
        repository.save(user)
    }

    fun findByTelegramUserId(telegramUserId: Long): TelegramUserEntity? =
        repository.findByTelegramUserId(telegramUserId)
}
