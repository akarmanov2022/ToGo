package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.domain.Violation
import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.Event
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventPage
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.ParticipantPage
import itmo.goto.generated.model.UpdateEventRequest
import itmo.goto.repository.Categorized
import itmo.goto.repository.Joinable
import itmo.goto.repository.Joined
import itmo.goto.repository.Staged
import itmo.goto.repository.StartingBefore
import itmo.goto.repository.StartingFrom
import itmo.goto.repository.Titled
import itmo.goto.web.ApiFailure
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Serves the Events part of the contract by handing each operation to the class responsible for it.
 *
 * The listing turns its query parameters into one filter per parameter before handing the search over.
 */
@Service
class EventsService(
    private val creation: EventCreationService,
    private val delegateService: EventDelegateService,
    private val participation: EventParticipationService,
    private val discovery: EventDiscoveryService,
    private val identity: Identity,
    private val clock: Clock,
) : EventsApiService {
    override fun createEvent(createEventRequest: CreateEventRequest): Event = creation.event(createEventRequest)

    override fun getEvents(
        query: String?,
        category: EventCategory?,
        startsFrom: OffsetDateTime?,
        startsTo: OffsetDateTime?,
        state: EventState?,
        participating: Boolean,
        hasFreePlaces: Boolean,
        limit: Int,
        offset: Int,
    ): EventPage {
        if (startsFrom != null && startsTo != null && startsFrom >= startsTo) {
            throw ApiFailure(listOf(Violation("startsFrom", "Начало диапазона должно быть раньше его конца")))
        }
        return discovery.events(
            listOfNotNull(
                query?.let(::Titled),
                category?.let(::Categorized),
                startsFrom?.let { StartingFrom(it.toInstant()) },
                startsTo?.let { StartingBefore(it.toInstant()) },
                state?.let(::Staged),
                Joined(identity.user()).takeIf { participating },
                Joinable(clock.instant()).takeIf { hasFreePlaces },
            ),
            limit,
            offset,
        )
    }

    override fun getEvent(eventId: UUID): Event = discovery.event(eventId)

    override fun updateEvent(eventId: UUID, updateEventRequest: UpdateEventRequest): Event =
        delegateService.change(eventId, updateEventRequest)

    override fun cancelEvent(eventId: UUID): Event = delegateService.cancel(eventId)

    override fun getEventParticipants(eventId: UUID, limit: Int, offset: Int): ParticipantPage =
        participation.participants(eventId, limit, offset)

    override fun joinEvent(eventId: UUID): Event = participation.join(eventId)

    override fun leaveEvent(eventId: UUID): Unit = participation.leave(eventId)
}
