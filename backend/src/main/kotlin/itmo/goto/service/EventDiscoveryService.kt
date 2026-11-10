package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.EventPage
import itmo.goto.model.Event
import itmo.goto.repository.EventRepository
import itmo.goto.repository.ParticipationRepository
import itmo.goto.web.ApiFailure
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Window
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import itmo.goto.generated.model.Event as Card

/**
 * Finds events and shows one of them, always in their current state.
 *
 * Events past their deadline or end are brought up to date before the search, so filters see the same state
 * as the cards they return, even when the background processing is late.
 */
@Service
class EventDiscoveryService(
    private val events: EventRepository,
    private val participations: ParticipationRepository,
    private val deadlines: EventDeadlineService,
    private val identity: Identity,
    private val clock: Clock,
) {
    @Transactional
    fun events(filters: List<Specification<Event>>, limit: Int, offset: Int): EventPage {
        deadlines.settle()
        val filter = Specification.allOf(filters)
        val found = events.findBy<Event, Window<Event>>(filter) {
            it.sortBy(Sort.by("startsAt", "id"))
                .project("organizer")
                .limit(limit)
                .scroll(position(offset))
        }.content
        val joined = participations.joined(identity.user(), found.map { checkNotNull(it.id) }).toSet()
        return EventPage(
            found.map { it.card(identity.user(), it.id in joined) },
            events.count(filter),
            limit,
            offset,
        )
    }

    @Transactional
    fun event(id: UUID): Card {
        val event = events.locked(id) ?: throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")
        event.settle(event.meeting().current(clock.instant()))
        return event.card(identity.user(), participations.existsByEventIdAndUserId(id, identity.user()))
    }

    private fun position(offset: Int): ScrollPosition =
        if (offset == 0) ScrollPosition.offset() else ScrollPosition.offset(offset - 1L)
}
