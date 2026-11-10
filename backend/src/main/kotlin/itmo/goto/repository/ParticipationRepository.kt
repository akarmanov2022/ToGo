package itmo.goto.repository

import itmo.goto.model.Event
import itmo.goto.model.Participation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ParticipationRepository : JpaRepository<Participation, UUID> {
    fun existsByEventIdAndUserId(event: UUID, user: UUID): Boolean

    @Query(
        "SELECT p.event FROM Participation p WHERE p.user.id = ?1 " +
            "AND p.event.state IN ('recruiting', 'registration_closed')",
    )
    fun active(user: UUID): List<Event>
}
