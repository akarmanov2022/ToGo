package itmo.goto.service

import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.UpdateEventRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

class EventsServiceTest {
    @Test
    fun `event creation answers not implemented`() {
        val closes = OffsetDateTime.now(ZoneOffset.UTC).plusDays(Random.nextLong(1, 30))
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().createEvent(
                    CreateEventRequest(
                        "Событие ${Random.nextInt()}",
                        "Описание ${Random.nextInt()}",
                        "Место ${Random.nextInt()}",
                        closes.plusHours(1),
                        closes.plusHours(3),
                        closes,
                        1,
                        Random.nextInt(2, 51),
                        EventCategory.entries.random(),
                    ),
                )
            }?.statusCode,
        )
            .withFailMessage("Event creation doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `event listing answers not implemented`() {
        val from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(Random.nextLong(1, 30))
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().getEvents(
                    "запрос ${Random.nextInt()}",
                    EventCategory.entries.random(),
                    from,
                    from.plusDays(Random.nextLong(1, 30)),
                    EventState.entries.random(),
                    Random.nextBoolean(),
                    Random.nextBoolean(),
                    Random.nextInt(1, 100),
                    Random.nextInt(0, 1000),
                )
            }?.statusCode,
        )
            .withFailMessage("Event listing doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `event details answer not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().getEvent(UUID.randomUUID())
            }?.statusCode,
        )
            .withFailMessage("Event details dont answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `event update answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().updateEvent(
                    UUID.randomUUID(),
                    UpdateEventRequest(title = "Событие ${Random.nextInt()}"),
                )
            }?.statusCode,
        )
            .withFailMessage("Event update doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `event cancellation answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().cancelEvent(UUID.randomUUID())
            }?.statusCode,
        )
            .withFailMessage("Event cancellation doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `participant listing answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().getEventParticipants(UUID.randomUUID(), Random.nextInt(1, 100), Random.nextInt(0, 1000))
            }?.statusCode,
        )
            .withFailMessage("Participant listing doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `joining answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().joinEvent(UUID.randomUUID())
            }?.statusCode,
        )
            .withFailMessage("Joining doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `leaving answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                EventsService().leaveEvent(UUID.randomUUID())
            }?.statusCode,
        )
            .withFailMessage("Leaving doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
}
