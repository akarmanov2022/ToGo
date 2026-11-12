package itmo.goto.repository

import itmo.goto.model.Rating
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RatingRepository : JpaRepository<Rating, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO event_ratings (event_id, user_id, score, created_at, updated_at)
            VALUES (?1, ?2, ?3, ?4, ?4)
        """,
        nativeQuery = true,
    )
    fun rate(event: UUID, user: UUID, score: Int, at: Instant)
}
