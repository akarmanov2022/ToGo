package itmo.goto.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

@WebMvcTest(ProbeController::class, MethodProbeController::class)
@AutoConfigureMockMvc(addFilters = false)
class ApiErrorHandlerTest(@Autowired private val mvc: MockMvcTester) {
    @Test
    fun `invalid body field answers bad request`() {
        assertThat(
            mvc.post().uri(
                "/probe/events",
            ).contentType(MediaType.APPLICATION_JSON).content(event(maximum = Random.nextInt(51, 999))),
        )
            .withFailMessage("Invalid body field doesnt answer 400")
            .hasStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `invalid body field is named in details`() {
        assertThat(
            mvc.post().uri(
                "/probe/events",
            ).contentType(MediaType.APPLICATION_JSON).content(event(maximum = Random.nextInt(51, 999))),
        )
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Invalid body field isnt named in details")
            .isEqualTo("maxUsers")
    }

    @Test
    fun `unknown body field gives validation error`() {
        assertThat(
            mvc.post().uri("/probe/events").contentType(MediaType.APPLICATION_JSON)
                .content(
                    event(
                        maximum = Random.nextInt(1, 50),
                    ).replaceFirst("{", """{"organizerId":"${UUID.randomUUID()}","""),
                ),
        )
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Unknown body field doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `missing required field is named in details`() {
        assertThat(
            mvc.post().uri("/probe/events").contentType(MediaType.APPLICATION_JSON)
                .content(
                    event(maximum = Random.nextInt(1, 50)).replace(Regex(""""registrationClosesAt":"[^"]+","""), ""),
                ),
        )
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Missing required field isnt named in details")
            .isEqualTo("registrationClosesAt")
    }

    @Test
    fun `malformed json gives validation error`() {
        assertThat(
            mvc.post().uri(
                "/probe/events",
            ).contentType(MediaType.APPLICATION_JSON).content("{\"title\": ${Random.nextInt()}"),
        )
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Malformed JSON doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `malformed identifier is named in details`() {
        assertThat(mvc.get().uri("/probe/events/{eventId}", "событие-${Random.nextInt()}"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Malformed identifier isnt named in details")
            .isEqualTo("eventId")
    }

    @Test
    fun `parameter out of range is named in details`() {
        assertThat(mvc.get().uri("/probe/page").param("limit", Random.nextInt(101, 9999).toString()))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Parameter out of range isnt named in details")
            .isEqualTo("limit")
    }

    @Test
    fun `invalid array element is named with its index`() {
        assertThat(
            mvc.post().uri("/probe/events").contentType(MediaType.APPLICATION_JSON)
                .content(
                    event(
                        maximum = Random.nextInt(1, 50),
                    ).replace(Regex(""""categoryIds":\["[^"]+"]"""), """"categoryIds":["id-${Random.nextInt()}"]"""),
                ),
        )
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Invalid array element isnt named with its index")
            .isEqualTo("categoryIds[0]")
    }

    @Test
    fun `missing parameter is named in details`() {
        assertThat(mvc.get().uri("/probe/page"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Missing parameter isnt named in details")
            .isEqualTo("limit")
    }

    @Test
    fun `parameter rejected by spring method validation is named in details`() {
        assertThat(mvc.get().uri("/probe/method/page").param("limit", Random.nextInt(101, 9999).toString()))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Parameter rejected by Spring method validation isnt named in details")
            .isEqualTo("limit")
    }

    @Test
    fun `unsupported method answers with its client error`() {
        assertThat(mvc.delete().uri("/probe/page").param("limit", Random.nextInt(1, 100).toString()))
            .withFailMessage("Unsupported method doesnt answer 405")
            .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
    }

    @Test
    fun `unsupported method gives validation error`() {
        assertThat(mvc.delete().uri("/probe/page").param("limit", Random.nextInt(1, 100).toString()))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Unsupported method doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `server error from spring keeps its status`() {
        assertThat(mvc.get().uri("/probe/unavailable"))
            .withFailMessage("Server error from Spring doesnt keep 501")
            .hasStatus(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `server error from spring gives internal error`() {
        assertThat(mvc.get().uri("/probe/unavailable"))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Server error from Spring doesnt give INTERNAL_ERROR")
            .isEqualTo("INTERNAL_ERROR")
    }

    @Test
    fun `forbidden failure answers forbidden`() {
        assertThat(mvc.get().uri("/probe/failures/FORBIDDEN"))
            .withFailMessage("FORBIDDEN failure doesnt answer 403")
            .hasStatus(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `missing event answers not found`() {
        assertThat(mvc.get().uri("/probe/failures/EVENT_NOT_FOUND"))
            .withFailMessage("EVENT_NOT_FOUND failure doesnt answer 404")
            .hasStatus(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `rule conflict answers conflict`() {
        assertThat(mvc.get().uri("/probe/failures/EVENT_FULL"))
            .withFailMessage("EVENT_FULL failure doesnt answer 409")
            .hasStatus(HttpStatus.CONFLICT)
    }

    @Test
    fun `rule conflict keeps its code`() {
        assertThat(mvc.get().uri("/probe/failures/ORGANIZER_CANNOT_LEAVE"))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Rule conflict doesnt keep its code")
            .isEqualTo("ORGANIZER_CANNOT_LEAVE")
    }

    @Test
    fun `unexpected failure answers internal error`() {
        assertThat(mvc.get().uri("/probe/crash").param("secret", "пароль-${Random.nextInt()}"))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Unexpected failure doesnt give INTERNAL_ERROR")
            .isEqualTo("INTERNAL_ERROR")
    }

    @Test
    fun `unexpected failure hides internal details`() {
        val secret = "пароль-${Random.nextInt()}"
        assertThat(mvc.get().uri("/probe/crash").param("secret", secret))
            .body()
            .asString()
            .withFailMessage("Unexpected failure exposes internal details")
            .doesNotContain(secret)
    }

    private fun event(maximum: Int): String {
        val closes = OffsetDateTime.now(ZoneOffset.UTC).plusDays(Random.nextLong(1, 30))
        return """
            {"title":"Событие ${Random.nextInt()}","location":"Место ${Random.nextInt()}",
             "registrationClosesAt":"$closes","startsAt":"${closes.plusHours(1)}","endsAt":"${closes.plusHours(3)}",
             "minUsers":1,"maxUsers":$maximum,"categoryIds":["${UUID.randomUUID()}"]}
        """.trimIndent().replace("\n", "")
    }
}
