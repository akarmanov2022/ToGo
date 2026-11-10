package itmo.goto.web

import itmo.goto.generated.model.ApiError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus
import kotlin.random.Random

class ApiFailureTest {
    @ParameterizedTest
    @CsvSource(
        "VALIDATION_ERROR, BAD_REQUEST",
        "CATEGORY_NOT_FOUND, BAD_REQUEST",
        "INVALID_TELEGRAM_INIT_DATA, UNAUTHORIZED",
        "UNAUTHORIZED, UNAUTHORIZED",
        "FORBIDDEN, FORBIDDEN",
        "EVENT_NOT_FOUND, NOT_FOUND",
        "USER_NOT_FOUND, NOT_FOUND",
        "REGISTRATION_CLOSED, CONFLICT",
        "INVALID_EVENT_STATE, CONFLICT",
        "EVENT_FULL, CONFLICT",
        "CAPACITY_BELOW_PARTICIPANTS, CONFLICT",
        "ORGANIZER_CANNOT_LEAVE, CONFLICT",
        "INTERNAL_ERROR, INTERNAL_SERVER_ERROR",
    )
    fun `failure code decides http status`(code: ApiError.Code, status: HttpStatus) {
        assertThat(ApiFailure(code, "Отказ ${Random.nextInt()}").status())
            .withFailMessage("Failure code %s doesnt answer %s", code, status)
            .isEqualTo(status)
    }
}
