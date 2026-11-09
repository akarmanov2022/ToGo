package itmo.goto.web

import itmo.goto.generated.api.EventsApiController
import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.Event
import itmo.goto.generated.model.EventPage
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.ParticipantPage
import itmo.goto.generated.model.UpdateEventRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.random.Random

@WebMvcTest(EventsApiController::class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RefusingEvents::class)
class ApiErrorHandlerTest(@Autowired private val mvc: MockMvcTester) {
    @Test
    fun `blank title gives validation error with title field`() {
        assertThat(mvc.post().uri("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(creation("   ", "")))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .withFailMessage("Blank title does not produce VALIDATION_ERROR naming the title field")
            .isLenientlyEqualTo("""{"code":"VALIDATION_ERROR","details":[{"field":"title"}]}""")
    }

    @Test
    fun `unknown field gives validation error`() {
        assertThat(
            mvc.post().uri("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                .content(creation("Пробежка ${Random.nextInt()}", ""","city":"Казань"""")),
        )
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .withFailMessage("Unknown request field does not produce VALIDATION_ERROR")
            .isLenientlyEqualTo("""{"code":"VALIDATION_ERROR","details":[{"field":"city"}]}""")
    }

    @Test
    fun `page size above limit gives validation error with limit field`() {
        assertThat(mvc.get().uri("/api/v1/events?limit=101"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .withFailMessage("Page size above the limit does not produce VALIDATION_ERROR naming the limit field")
            .isLenientlyEqualTo("""{"code":"VALIDATION_ERROR","details":[{"field":"limit"}]}""")
    }

    @Test
    fun `malformed identifier gives validation error with identifier field`() {
        assertThat(mvc.get().uri("/api/v1/events/not-a-uuid"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .withFailMessage("Malformed event identifier does not produce VALIDATION_ERROR naming the identifier")
            .isLenientlyEqualTo("""{"code":"VALIDATION_ERROR","details":[{"field":"eventId"}]}""")
    }

    @Test
    fun `refused access gives forbidden`() {
        assertThat(mvc.post().uri("/api/v1/events/${UUID.randomUUID()}/cancel"))
            .hasStatus(HttpStatus.FORBIDDEN)
            .bodyJson()
            .withFailMessage("Refused access does not produce FORBIDDEN")
            .isLenientlyEqualTo("""{"code":"FORBIDDEN","details":[]}""")
    }

    @Test
    fun `missing event gives not found`() {
        assertThat(mvc.get().uri("/api/v1/events/${UUID.randomUUID()}"))
            .hasStatus(HttpStatus.NOT_FOUND)
            .bodyJson()
            .withFailMessage("Missing event does not produce EVENT_NOT_FOUND")
            .isLenientlyEqualTo("""{"code":"EVENT_NOT_FOUND","details":[]}""")
    }

    @Test
    fun `broken rule gives conflict with its code`() {
        assertThat(mvc.put().uri("/api/v1/events/${UUID.randomUUID()}/participants/me"))
            .hasStatus(HttpStatus.CONFLICT)
            .bodyJson()
            .withFailMessage("Full event does not produce EVENT_FULL")
            .isLenientlyEqualTo("""{"code":"EVENT_FULL","details":[]}""")
    }

    @Test
    fun `unexpected failure gives internal error`() {
        assertThat(mvc.get().uri("/api/v1/events"))
            .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .bodyJson()
            .withFailMessage("Unexpected failure does not produce INTERNAL_ERROR")
            .isLenientlyEqualTo("""{"code":"INTERNAL_ERROR","details":[]}""")
    }

    @Test
    fun `unexpected failure hides its details`() {
        assertThat(mvc.get().uri("/api/v1/events"))
            .bodyText()
            .withFailMessage("Unexpected failure leaks its internal message to the client")
            .doesNotContain(RefusingEvents.SECRET)
    }

    private fun creation(title: String, extra: String): String {
        val start = OffsetDateTime.now().plusDays(Random.nextLong(2, 30))
        return """
            {"title":"$title","location":"Улица ${Random.nextInt(1, 300)}","startsAt":"$start",
            "endsAt":"${start.plusHours(2)}","registrationClosesAt":"${start.minusHours(1)}",
            "minUsers":2,"maxUsers":${Random.nextInt(2, 20)},"categoryIds":["${UUID.randomUUID()}"]$extra}
        """.trimIndent()
    }
}

/**
 * Events service that refuses every request in a fixed way, so the web layer can be checked in isolation.
 */
class RefusingEvents : EventsApiService {
    override fun cancelEvent(eventId: UUID): Event =
        throw ApiFailure(ApiError.Code.FORBIDDEN, "Отменить событие может только организатор")

    override fun createEvent(createEventRequest: CreateEventRequest): Event =
        throw IllegalStateException("Creation must not be reached in these tests")

    override fun getEvent(eventId: UUID): Event =
        throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")

    override fun getEventParticipants(eventId: UUID, limit: Int, offset: Int): ParticipantPage =
        throw IllegalStateException("Participant listing must not be reached in these tests")

    override fun getEvents(
        query: String?,
        categoryId: UUID?,
        startsFrom: OffsetDateTime?,
        startsTo: OffsetDateTime?,
        state: EventState?,
        participating: Boolean,
        limit: Int,
        offset: Int,
    ): EventPage = throw IllegalStateException(SECRET)

    override fun joinEvent(eventId: UUID): Event =
        throw ApiFailure(ApiError.Code.EVENT_FULL, "Все места заняты")

    override fun leaveEvent(eventId: UUID) =
        throw IllegalStateException("Leaving must not be reached in these tests")

    override fun updateEvent(eventId: UUID, updateEventRequest: UpdateEventRequest): Event =
        throw IllegalStateException("Update must not be reached in these tests")

    companion object {
        const val SECRET = "connection to 10.0.0.7 refused"
    }
}
