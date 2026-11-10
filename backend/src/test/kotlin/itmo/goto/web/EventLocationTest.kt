package itmo.goto.web

import itmo.goto.generated.api.EventsApiController
import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.Event
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.UserSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

@WebMvcTest(EventsApiController::class)
@AutoConfigureMockMvc(addFilters = false)
class EventLocationTest(@Autowired private val mvc: MockMvcTester, @Autowired private val json: JsonMapper) {
    @MockitoBean
    private lateinit var events: EventsApiService

    @Test
    fun `created event is located at its address`() {
        val closes = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).plusDays(Random.nextLong(1, 20))
        val request = CreateEventRequest(
            "Событие ${Random.nextInt()}",
            "Описание ${Random.nextInt()}",
            "Место ${Random.nextInt()}",
            closes.plusHours(1),
            closes.plusHours(3),
            closes,
            1,
            Random.nextInt(2, 51),
            EventCategory.entries.random(),
        )
        val id = UUID.randomUUID()
        given(events.createEvent(request)).willReturn(
            Event(
                id,
                request.title,
                request.description,
                request.location,
                request.startsAt,
                request.endsAt,
                request.registrationClosesAt,
                EventState.recruiting,
                null,
                null,
                null,
                request.minUsers,
                request.maxUsers,
                UserSummary(UUID.randomUUID(), null),
                request.category,
                1,
                request.maxUsers - 1,
                true,
                true,
            ),
        )
        assertThat(
            mvc.post().uri("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)),
        )
            .headers()
            .withFailMessage("Created event isnt located at its address")
            .hasValue("Location", "/api/v1/events/$id")
    }
}
