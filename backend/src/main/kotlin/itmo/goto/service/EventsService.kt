package itmo.goto.service

import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.Event
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** Business logic belongs here; this file is never overwritten by OpenAPI generation. */
@Service
class EventsService : EventsApiService {
    override fun createEvent(createEventRequest: CreateEventRequest): Event =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Event creation is not implemented yet")

    override fun getEvents(): List<Event> =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Event listing is not implemented yet")
}
