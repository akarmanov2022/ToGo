package itmo.goto.service

import itmo.goto.repository.EventRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.concurrent.TimeUnit

/**
 * Brings stored event states up to date once their registration deadline or end has come.
 *
 * Requests bring the event they touch up to date on their own, so a late or missed run never lets a stale
 * state through; this only keeps the stored states current between requests.
 */
@Service
class EventDeadlineService(private val events: EventRepository, private val clock: Clock) {
    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    @Transactional
    fun settle() {
        events.due(clock.instant()).forEach { id ->
            events.locked(id)?.let { it.settle(it.meeting().current(clock.instant())) }
        }
    }
}
