package itmo.goto.repository

import itmo.goto.model.Event
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface EventRepository : JpaRepository<Event, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = ?1")
    fun locked(id: UUID): Event?
}
