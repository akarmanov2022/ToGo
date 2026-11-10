package itmo.goto.service

import itmo.goto.generated.model.TelegramAuthRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.random.Random

class AuthServiceTest {
    @Test
    fun `telegram authentication answers not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                AuthService().authenticateTelegram(
                    TelegramAuthRequest("user=${Random.nextLong()}&hash=${Random.nextLong()}"),
                )
            }?.statusCode,
        )
            .withFailMessage("Telegram authentication doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }
}
