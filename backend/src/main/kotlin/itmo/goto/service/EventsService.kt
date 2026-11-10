package itmo.goto.service

import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.Event
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventPage
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.ParticipantPage
import itmo.goto.generated.model.UpdateEventRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Serves the Events part of the contract by handing each operation to the class responsible for it.
 */
@Service
class EventsService(
    private val creation: EventCreationService,
    private val delegateService: EventDelegateService,
    private val participation: EventParticipationService,
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
    ): EventPage = throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Event listing is not implemented yet")

    override fun getEvent(eventId: UUID): Event =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Event details are not implemented yet")

    override fun updateEvent(eventId: UUID, updateEventRequest: UpdateEventRequest): Event =
        delegateService.change(eventId, updateEventRequest)

    override fun cancelEvent(eventId: UUID): Event = delegateService.cancel(eventId)

    override fun getEventParticipants(eventId: UUID, limit: Int, offset: Int): ParticipantPage =
        participation.participants(eventId, limit, offset)

    override fun joinEvent(eventId: UUID): Event = participation.join(eventId)

    override fun leaveEvent(eventId: UUID): Unit = participation.leave(eventId)
}
