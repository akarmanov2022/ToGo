package itmo.goto.web

import itmo.goto.generated.api.CommentsApiController
import itmo.goto.generated.api.CommentsApiService
import itmo.goto.generated.model.Comment
import itmo.goto.generated.model.CreateCommentRequest
import itmo.goto.generated.model.UserSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

@WebMvcTest(CommentsApiController::class)
@AutoConfigureMockMvc(addFilters = false)
class CommentRequestTest(@Autowired private val mvc: MockMvcTester) {
    @MockitoBean
    private lateinit var comments: CommentsApiService

    @Test
    fun `published comment answers created`() {
        val text = "Встречаемся у входа ${Random.nextInt()}"
        val event = UUID.randomUUID()
        given(comments.createEventComment(event, CreateCommentRequest(text))).willReturn(
            Comment(
                UUID.randomUUID(),
                UserSummary(UUID.randomUUID(), "user${Random.nextInt(1000)}"),
                text,
                OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )
        assertThat(publish(text, event))
            .withFailMessage("Published comment doesnt answer 201")
            .hasStatus(HttpStatus.CREATED)
    }

    @Test
    fun `blank comment gives validation error`() {
        assertThat(publish(" ".repeat(Random.nextInt(1, 20))))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Blank comment doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `blank comment is named in details`() {
        assertThat(publish("\n".repeat(Random.nextInt(1, 5)) + " ".repeat(Random.nextInt(1, 5))))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Blank comment isnt named in details")
            .isEqualTo("text")
    }

    @Test
    fun `comment of one thousand and one characters gives validation error`() {
        assertThat(publish(long()))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Comment of 1001 characters doesnt give VALIDATION_ERROR")
            .isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `comment of one thousand and one characters is named in details`() {
        assertThat(publish(long()))
            .bodyJson()
            .extractingPath("$.details[0].field")
            .withFailMessage("Comment of 1001 characters isnt named in details")
            .isEqualTo("text")
    }

    private fun publish(text: String, event: UUID = UUID.randomUUID()) = mvc.post()
        .uri("/api/v1/events/{eventId}/comments", event)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""{"text":"${text.replace("\n", "\\n")}"}""")

    private fun long(): String = String(CharArray(1001) { ('а'..'я').random() })
}
