package itmo.goto.web

import itmo.goto.generated.api.RatingsApiController
import itmo.goto.generated.api.RatingsApiService
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

@WebMvcTest(RatingsApiController::class)
@AutoConfigureMockMvc(addFilters = false)
class RatingRequestTest(@Autowired private val mvc: MockMvcTester) {
    @MockitoBean
    private lateinit var ratings: RatingsApiService

    @Test
    fun `zero score gives validation error`() {
        assertThat(rate("""{"score":0}"""))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Zero score doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `zero score is named in details`() {
        assertThat(rate("""{"score":0}"""))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Zero score isnt named in details")
            .isEqualTo("score")
    }

    @Test
    fun `score above five gives validation error`() {
        assertThat(rate("""{"score":${Random.nextInt(6, 1000)}}"""))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Score above five doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `score above five is named in details`() {
        assertThat(rate("""{"score":${Random.nextInt(6, 1000)}}"""))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Score above five isnt named in details")
            .isEqualTo("score")
    }

    @Test
    fun `fractional score gives validation error`() {
        assertThat(rate("""{"score":${Random.nextInt(1, 5)}.5}"""))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Fractional score doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `fractional score is named in details`() {
        assertThat(rate("""{"score":${Random.nextInt(1, 5)}.5}"""))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Fractional score isnt named in details")
            .isEqualTo("score")
    }

    @Test
    fun `missing score gives validation error`() {
        assertThat(rate("{}"))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Missing score doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `missing score is named in details`() {
        assertThat(rate("{}"))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Missing score isnt named in details")
            .isEqualTo("score")
    }

    @Test
    fun `unknown field gives validation error`() {
        assertThat(rate("""{"score":${Random.nextInt(1, 6)},"review":"Хорошо ${Random.nextInt()}"}"""))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Unknown field in a rating doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    private fun rate(body: String) = mvc.put()
        .uri("/api/v1/events/{eventId}/rating", UUID.randomUUID())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
}
