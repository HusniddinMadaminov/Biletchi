package uz.railway.ticketbot.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "telegram_users")
class TelegramUserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    var telegramUserId: Long,

    @Column(name = "chat_id", nullable = false)
    var chatId: Long,

    @Column(name = "username")
    var username: String? = null,

    @Column(name = "first_name")
    var firstName: String? = null,

    @Column(name = "last_name")
    var lastName: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_state", nullable = false)
    var conversationState: ConversationState = ConversationState.IDLE,

    @Column(name = "conversation_context")
    var conversationContext: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
