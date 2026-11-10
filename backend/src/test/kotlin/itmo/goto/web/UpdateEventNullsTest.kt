package itmo.goto.web

import itmo.goto.generated.api.EventsApiController
import itmo.goto.generated.api.EventsApiService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.util.UUID
import kotlin.random.Random

@WebMvcTest(EventsApiController::class)
@AutoConfigureMockMvc(addFilters = false)
class UpdateEventNullsTest(@Autowired private val mvc: MockMvcTester) {
    @MockitoBean
    private lateinit var events: EventsApiService

    @Test
    fun `null next to a real field gives validation error`() {
        assertThat(
            mvc.patch().uri("/api/v1/events/${UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"Событие ${Random.nextInt()}","icon":null}"""),
        )
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Null next to a real field doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `null field is named in details`() {
        val field = listOf("title", "description", "registrationClosesAt", "icon", "minUsers", "maxUsers", "category")
            .random()
        assertThat(
            mvc.patch().uri("/api/v1/events/${UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content("""{"$field":null}"""),
        )
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Null field isnt named in details")
            .isEqualTo(field)
    }
}
