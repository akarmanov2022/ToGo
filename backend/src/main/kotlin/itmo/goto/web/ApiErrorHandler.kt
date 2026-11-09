package itmo.goto.web

import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.FieldError
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.core.JacksonException

/**
 * Translates every failure of an API request into the contract error body with a stable code.
 *
 * Extends the Spring handler, breaking the composition rule, so that standard web failures keep their statuses.
 */
@RestControllerAdvice
class ApiErrorHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(ApiErrorHandler::class.java)

    @ExceptionHandler
    fun failure(failure: ApiFailure): ResponseEntity<Any> =
        ResponseEntity.status(status(failure.code)).body(ApiError(failure.code, failure.message, failure.details))

    @ExceptionHandler
    fun violation(violation: ConstraintViolationException): ResponseEntity<Any> =
        invalid(violation.constraintViolations.map { FieldError(it.propertyPath.last().name, it.message) })

    @ExceptionHandler
    fun unexpected(exception: Exception): ResponseEntity<Any> {
        log.error("Unexpected failure while handling an API request", exception)
        return ResponseEntity.internalServerError()
            .body(ApiError(ApiError.Code.INTERNAL_ERROR, "Непредвиденная ошибка сервера", emptyList()))
    }

    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        invalid(ex.bindingResult.fieldErrors.map { FieldError(it.field, it.defaultMessage ?: "Недопустимое значение") })

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        invalid(
            ex.parameterValidationResults.flatMap { result ->
                result.resolvableErrors.map {
                    FieldError(result.methodParameter.parameterName ?: "", it.defaultMessage ?: "Недопустимое значение")
                }
            },
        )

    override fun handleTypeMismatch(
        ex: TypeMismatchException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> = invalid(listOf(FieldError(ex.propertyName ?: "", "Недопустимый формат значения")))

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any> =
        invalid(
            generateSequence<Throwable>(ex) { it.cause }
                .filterIsInstance<JacksonException>()
                .firstOrNull()
                ?.path
                ?.takeIf { it.isNotEmpty() }
                ?.let { path -> listOf(FieldError(field(path), "Недопустимое значение поля")) }
                ?: emptyList(),
        )

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? =
        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            invalid(emptyList())
        } else {
            super.handleExceptionInternal(ex, body, headers, statusCode, request)
        }

    private fun invalid(details: List<FieldError>): ResponseEntity<Any> =
        ResponseEntity.badRequest().body(ApiError(ApiError.Code.VALIDATION_ERROR, "Некорректный запрос", details))

    private fun field(path: List<JacksonException.Reference>): String =
        path.foldIndexed("") { index, name, reference ->
            when {
                reference.propertyName == null -> "$name[${reference.index}]"
                index == 0 -> reference.propertyName
                else -> "$name.${reference.propertyName}"
            }
        }

    private fun status(code: ApiError.Code): HttpStatus =
        when (code) {
            ApiError.Code.VALIDATION_ERROR, ApiError.Code.CATEGORY_NOT_FOUND -> HttpStatus.BAD_REQUEST
            ApiError.Code.INVALID_TELEGRAM_INIT_DATA, ApiError.Code.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
            ApiError.Code.FORBIDDEN -> HttpStatus.FORBIDDEN
            ApiError.Code.EVENT_NOT_FOUND -> HttpStatus.NOT_FOUND
            ApiError.Code.REGISTRATION_CLOSED,
            ApiError.Code.INVALID_EVENT_STATE,
            ApiError.Code.EVENT_FULL,
            ApiError.Code.CAPACITY_BELOW_PARTICIPANTS,
            ApiError.Code.ORGANIZER_CANNOT_LEAVE,
            -> HttpStatus.CONFLICT
            ApiError.Code.INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR
        }
}
