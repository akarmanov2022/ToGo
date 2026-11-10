package itmo.goto.web

import itmo.goto.domain.Refusal
import itmo.goto.domain.Violation
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.FieldError
import org.springframework.http.HttpStatus

/**
 * A refusal that the API reports with one of the contract's error codes.
 *
 * The code alone decides the HTTP status, so a service cannot pair a code with the wrong status.
 */
class ApiFailure(
    private val code: ApiError.Code,
    message: String,
    private val details: List<FieldError> = emptyList(),
) : RuntimeException(message) {
    constructor(violations: List<Violation>) : this(
        ApiError.Code.VALIDATION_ERROR,
        "Некорректные данные запроса",
        violations.map { FieldError(it.field(), it.message()) },
    )

    constructor(refusal: Refusal) : this(ApiError.Code.valueOf(refusal.name), refusal.message())

    fun status(): HttpStatus = when (code) {
        ApiError.Code.VALIDATION_ERROR -> HttpStatus.BAD_REQUEST
        ApiError.Code.INVALID_TELEGRAM_INIT_DATA, ApiError.Code.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        ApiError.Code.FORBIDDEN -> HttpStatus.FORBIDDEN
        ApiError.Code.EVENT_NOT_FOUND, ApiError.Code.USER_NOT_FOUND -> HttpStatus.NOT_FOUND
        ApiError.Code.REGISTRATION_CLOSED,
        ApiError.Code.INVALID_EVENT_STATE,
        ApiError.Code.EVENT_FULL,
        ApiError.Code.CAPACITY_BELOW_PARTICIPANTS,
        ApiError.Code.ORGANIZER_CANNOT_LEAVE,
        ApiError.Code.SCHEDULE_CONFLICT,
        -> HttpStatus.CONFLICT
        ApiError.Code.INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
    }

    fun error(): ApiError = ApiError(code, message.orEmpty(), details)
}
