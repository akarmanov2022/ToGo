package itmo.goto.repository

import itmo.goto.model.Event
import itmo.goto.model.Participation
import itmo.goto.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ParticipationRepository : JpaRepository<Participation, UUID> {
    fun existsByEventIdAndUserId(event: UUID, user: UUID): Boolean

    fun deleteByEventIdAndUserId(event: UUID, user: UUID)

    fun countByEventId(event: UUID): Long

    @Query("SELECT p.user FROM Participation p WHERE p.event.id = ?1")
    fun allMembers(event: UUID): List<User>

    @Query("SELECT p.user FROM Participation p WHERE p.event.id = ?1 AND p.user.id = ?2")
    fun member(event: UUID, user: UUID): User?

    @Query("SELECT p.user FROM Participation p WHERE p.event.id = ?1 ORDER BY p.user.id LIMIT ?2 OFFSET ?3")
    fun members(event: UUID, limit: Int, offset: Int): List<User>

    @Query(
        "SELECT p.event FROM Participation p WHERE p.user.id = ?1 " +
            "AND p.event.state IN ('recruiting', 'registration_closed')",
    )
    fun active(user: UUID): List<Event>

    @Query("SELECT p.event.id FROM Participation p WHERE p.user.id = ?1 AND p.event.id IN ?2")
    fun joined(user: UUID, events: Collection<UUID>): List<UUID>
}
