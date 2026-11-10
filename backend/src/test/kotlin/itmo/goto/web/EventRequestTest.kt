package itmo.goto.web

import itmo.goto.generated.api.EventsApiController
import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.model.EventCategory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

@WebMvcTest(EventsApiController::class)
@AutoConfigureMockMvc(addFilters = false)
class EventRequestTest(@Autowired private val mvc: MockMvcTester, @Autowired private val json: JsonMapper) {
    @MockitoBean
    private lateinit var events: EventsApiService

    @Test
    fun `creation without deadline names the deadline`() {
        assertThat(create(event() - "registrationClosesAt"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation without a deadline doesnt name registrationClosesAt")
            .isEqualTo("registrationClosesAt")
    }

    @Test
    fun `creation without description names the description`() {
        assertThat(create(event() - "description"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation without a description doesnt name description")
            .isEqualTo("description")
    }

    @Test
    fun `creation without category names the category`() {
        assertThat(create(event() - "category"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation without a category doesnt name category")
            .isEqualTo("category")
    }

    @Test
    fun `creation with unknown category names the category`() {
        assertThat(create(event() + ("category" to "SPORT${Random.nextInt(1000)}")))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with an unknown category doesnt name category")
            .isEqualTo("category")
    }

    @Test
    fun `creation with blank title names the title`() {
        assertThat(create(event() + ("title" to " ".repeat(Random.nextInt(1, 10)))))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a blank title doesnt name title")
            .isEqualTo("title")
    }

    @Test
    fun `creation with title of 101 characters names the title`() {
        assertThat(create(event() + ("title" to "а".repeat(101))))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a title of 101 characters doesnt name title")
            .isEqualTo("title")
    }

    @Test
    fun `creation with description of 501 characters names the description`() {
        assertThat(create(event() + ("description" to "о".repeat(501))))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a description of 501 characters doesnt name description")
            .isEqualTo("description")
    }

    @Test
    fun `creation with blank location names the location`() {
        assertThat(create(event() + ("location" to " ".repeat(Random.nextInt(1, 10)))))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a blank location doesnt name location")
            .isEqualTo("location")
    }

    @Test
    fun `creation with latitude beyond the pole names the latitude`() {
        assertThat(create(event() + ("latitude" to Random.nextDouble(90.001, 180.0)) + ("longitude" to 30.3)))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a latitude beyond the pole doesnt name latitude")
            .isEqualTo("latitude")
    }

    @Test
    fun `creation with longitude beyond the antimeridian names the longitude`() {
        assertThat(create(event() + ("latitude" to 59.9) + ("longitude" to -Random.nextDouble(180.001, 360.0))))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a longitude beyond the antimeridian doesnt name longitude")
            .isEqualTo("longitude")
    }

    @Test
    fun `creation with zero minimum names the minimum`() {
        assertThat(create(event() + ("minUsers" to 0)))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a zero minimum doesnt name minUsers")
            .isEqualTo("minUsers")
    }

    @Test
    fun `creation with maximum of 51 names the maximum`() {
        assertThat(create(event() + ("maxUsers" to 51)))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Creation with a maximum of 51 doesnt name maxUsers")
            .isEqualTo("maxUsers")
    }

    @Test
    fun `creation with organizer field gives validation error`() {
        assertThat(create(event() + ("organizerId" to UUID.randomUUID().toString())))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Creation with an organizerId field doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `change with empty description names the description`() {
        assertThat(change(mapOf("description" to "")))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Change with an empty description doesnt name description")
            .isEqualTo("description")
    }

    @Test
    fun `change with unknown category names the category`() {
        assertThat(change(mapOf("category" to "SPORT${Random.nextInt(1000)}")))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Change with an unknown category doesnt name category")
            .isEqualTo("category")
    }

    @Test
    fun `change with start gives validation error`() {
        assertThat(change(mapOf("startsAt" to OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString())))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Change with a startsAt field doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `listing with unknown category names the category`() {
        assertThat(mvc.get().uri("/api/v1/events?category=SPORT${Random.nextInt(1000)}"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Listing with an unknown category doesnt name category")
            .isEqualTo("category")
    }

    @Test
    fun `listing with limit above 100 names the limit`() {
        assertThat(mvc.get().uri("/api/v1/events?limit=${Random.nextInt(101, 10_000)}"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Listing with a limit above 100 doesnt name limit")
            .isEqualTo("limit")
    }

    @Test
    fun `listing with negative offset names the offset`() {
        assertThat(mvc.get().uri("/api/v1/events?offset=${Random.nextInt(-10_000, 0)}"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Listing with a negative offset doesnt name offset")
            .isEqualTo("offset")
    }

    @Test
    fun `details with malformed id give validation error`() {
        assertThat(mvc.get().uri("/api/v1/events/event-${Random.nextInt(1000)}"))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Details with a malformed id dont give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    private fun create(body: Map<String, Any>) =
        mvc.post().uri("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))

    private fun change(body: Map<String, Any>) = mvc.patch().uri("/api/v1/events/${UUID.randomUUID()}")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))

    private fun event(): Map<String, Any> {
        val closes = OffsetDateTime.now(ZoneOffset.UTC).plusDays(Random.nextLong(1, 20))
        return mapOf(
            "title" to "Событие ${Random.nextInt()}",
            "description" to "Описание ${Random.nextInt()}",
            "location" to "Место ${Random.nextInt()}",
            "registrationClosesAt" to closes.toString(),
            "startsAt" to closes.plusHours(1).toString(),
            "endsAt" to closes.plusHours(3).toString(),
            "minUsers" to 1,
            "maxUsers" to Random.nextInt(2, 51),
            "category" to EventCategory.entries.random().name,
        )
    }
}
