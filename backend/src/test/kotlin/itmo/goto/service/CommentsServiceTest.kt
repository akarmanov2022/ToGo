package itmo.goto.service

import itmo.goto.generated.model.CreateCommentRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.random.Random

class CommentsServiceTest {
    @Test
    fun `comment listing answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                CommentsService().getEventComments(UUID.randomUUID(), Random.nextInt(1, 100), Random.nextInt(0, 1000))
            }?.statusCode,
        )
            .withFailMessage("Comment listing doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `commenting answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                CommentsService().createEventComment(
                    UUID.randomUUID(),
                    CreateCommentRequest("Комментарий ${Random.nextInt()}"),
                )
            }?.statusCode,
        )
            .withFailMessage("Commenting doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
}
