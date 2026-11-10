package itmo.goto.service

import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.Event
import itmo.goto.generated.model.EventPage
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.ParticipantPage
import itmo.goto.generated.model.UpdateEventRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.util.UUID

/** Business logic belongs here; this file is never overwritten by OpenAPI generation. */
@Service
class EventsService : EventsApiService {
    override fun createEvent(createEventRequest: CreateEventRequest): Event =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Event creation is not implemented yet")

    override fun getEvents(
        query: String?,
        categoryId: UUID?,
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
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Event update is not implemented yet")

    override fun cancelEvent(eventId: UUID): Event =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Event cancellation is not implemented yet")

    override fun getEventParticipants(eventId: UUID, limit: Int, offset: Int): ParticipantPage =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Participant listing is not implemented yet")

    override fun joinEvent(eventId: UUID): Event =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Joining an event is not implemented yet")

    override fun leaveEvent(eventId: UUID): Unit =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Leaving an event is not implemented yet")
}
