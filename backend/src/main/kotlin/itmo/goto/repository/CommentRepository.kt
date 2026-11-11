package itmo.goto.repository

import itmo.goto.model.Comment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CommentRepository : JpaRepository<Comment, UUID> {
    fun countByEventId(event: UUID): Long

    @Query(
        "SELECT c FROM Comment c JOIN FETCH c.author WHERE c.event.id = ?1 " +
            "ORDER BY c.createdAt, c.id LIMIT ?2 OFFSET ?3",
    )
    fun page(event: UUID, limit: Int, offset: Int): List<Comment>
}
