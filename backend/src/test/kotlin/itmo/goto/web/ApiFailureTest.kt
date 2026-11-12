package itmo.goto.web

import itmo.goto.domain.Refusal
import itmo.goto.domain.Violation
import itmo.goto.generated.model.ApiError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpStatus
import kotlin.random.Random

class ApiFailureTest {
    @ParameterizedTest
    @CsvSource(
        "VALIDATION_ERROR, BAD_REQUEST",
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
        "SCHEDULE_CONFLICT, CONFLICT",
        "ALREADY_RATED, CONFLICT",
        "INTERNAL_ERROR, INTERNAL_SERVER_ERROR",
    )
    fun `failure code decides http status`(code: ApiError.Code, status: HttpStatus) {
        assertThat(ApiFailure(code, "Отказ ${Random.nextInt()}").status())
            .withFailMessage("Failure code %s doesnt answer %s", code, status)
            .isEqualTo(status)
    }

    @Test
    fun `violations give validation error`() {
        assertThat(ApiFailure(listOf(Violation("minUsers", "Нарушение ${Random.nextInt()}"))).error().code)
            .withFailMessage("Violations dont give VALIDATION_ERROR")
            .isEqualTo(ApiError.Code.VALIDATION_ERROR)
    }

    @Test
    fun `violations are named in details`() {
        val fields = listOf("registrationClosesAt", "startsAt", "endsAt", "minUsers", "maxUsers").shuffled()
        assertThat(
            ApiFailure(
                fields.map {
                    Violation(it, "Нарушение ${Random.nextInt()}")
                },
            ).error().details.map { it.field },
        )
            .withFailMessage("Violations arent named in details")
            .containsExactlyElementsOf(fields)
    }

    @Test
    fun `refusal keeps its contract code`() {
        val refusal = Refusal.entries.random()
        assertThat(ApiFailure(refusal).error().code.name)
            .withFailMessage("Refusal %s doesnt keep its contract code", refusal)
            .isEqualTo(refusal.name)
    }

    @Test
    fun `refusal keeps its message`() {
        val refusal = Refusal.entries.random()
        assertThat(ApiFailure(refusal).error().message)
            .withFailMessage("Refusal %s doesnt keep its message", refusal)
            .isEqualTo(refusal.message())
    }
}
