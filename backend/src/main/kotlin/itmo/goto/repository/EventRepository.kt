package itmo.goto.repository

import itmo.goto.model.Event
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface EventRepository :
    JpaRepository<Event, UUID>,
    JpaSpecificationExecutor<Event> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = ?1")
    fun locked(id: UUID): Event?

    @Query(
        "SELECT e.id FROM Event e WHERE (e.state = 'recruiting' AND e.registrationClosesAt <= ?1) " +
            "OR (e.state = 'registration_closed' AND e.endsAt <= ?1)",
    )
    fun due(now: Instant): List<UUID>
}
