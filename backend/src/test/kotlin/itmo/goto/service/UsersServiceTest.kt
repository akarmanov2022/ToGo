package itmo.goto.service

import itmo.goto.generated.model.UpdateProfileRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.random.Random

class UsersServiceTest {
    @Test
    fun `current user answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                UsersService().getCurrentUser()
            }?.statusCode,
        )
            .withFailMessage("Current user doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `profile update answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                UsersService().updateCurrentUser(
                    UpdateProfileRequest("Имя ${Random.nextInt()}", setOf(UUID.randomUUID())),
                )
            }?.statusCode,
        )
            .withFailMessage("Profile update doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `other profile answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                UsersService().getUser(UUID.randomUUID())
            }?.statusCode,
        )
            .withFailMessage("Other profile doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
}
