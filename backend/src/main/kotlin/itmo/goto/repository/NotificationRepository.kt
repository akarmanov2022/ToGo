package itmo.goto.repository

import itmo.goto.model.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun existsByUserIdAndEventIdAndKind(user: UUID, event: UUID, kind: String): Boolean

    fun deleteByUserIdAndEventIdAndKindInAndSentAtIsNull(user: UUID, event: UUID, kinds: Collection<String>)

    @Query("SELECT n FROM Notification n WHERE n.sentAt IS NULL AND n.dueAt <= ?1 AND n.attempts < 3 ORDER BY n.dueAt")
    fun due(now: Instant): List<Notification>
}
