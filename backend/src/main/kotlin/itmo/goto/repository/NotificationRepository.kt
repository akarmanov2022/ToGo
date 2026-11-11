package itmo.goto.repository

import itmo.goto.model.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun existsByUserIdAndEventIdAndKind(user: UUID, event: UUID, kind: String): Boolean

    fun deleteByUserIdAndEventIdAndKindInAndSentAtIsNull(user: UUID, event: UUID, kinds: Collection<String>)

    @Query(
        value = """
            SELECT * FROM notifications
            WHERE sent_at IS NULL AND due_at <= ?1 AND attempts < 3
            ORDER BY due_at
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true,
    )
    fun due(now: Instant): List<Notification>
}
