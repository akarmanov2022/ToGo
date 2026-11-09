package itmo.goto.web

import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.FieldError
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.core.JacksonException

/**
 * Translates every failure that reaches the web layer into the contract's ApiError body,
 * so clients always receive a stable code and never see internal details.
 */
@RestControllerAdvice
class ApiErrorHandler {
    private val log = LoggerFactory.getLogger(ApiErrorHandler::class.java)

    @ExceptionHandler(ApiFailure::class)
    fun failure(failure: ApiFailure): ResponseEntity<ApiError> = ResponseEntity.status(failure.status()).body(failure.error())

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun body(exception: MethodArgumentNotValidException): ResponseEntity<ApiError> =
        invalid(
            exception.bindingResult.fieldErrors.map { FieldError(it.field, it.defaultMessage.orEmpty()) } +
                exception.bindingResult.globalErrors.map { FieldError(it.objectName, it.defaultMessage.orEmpty()) },
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(exception: HttpMessageNotReadableException): ResponseEntity<ApiError> =
        invalid(
            generateSequence(exception.cause) { it.cause }
                .filterIsInstance<JacksonException>()
                .firstOrNull()
                ?.path
                ?.takeIf { it.isNotEmpty() }
                ?.let { listOf(FieldError(field(it), "Некорректное или отсутствующее значение")) }
                .orEmpty(),
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun mismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> =
        invalid(listOf(FieldError(exception.name, "Некорректное значение")))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missing(exception: MissingServletRequestParameterException): ResponseEntity<ApiError> =
        invalid(listOf(FieldError(exception.parameterName, "Обязательный параметр")))

    @ExceptionHandler(ConstraintViolationException::class)
    fun constraint(exception: ConstraintViolationException): ResponseEntity<ApiError> =
        invalid(exception.constraintViolations.map { FieldError(it.propertyPath.last().name, it.message) })

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun parameters(exception: HandlerMethodValidationException): ResponseEntity<ApiError> =
        invalid(
            exception.parameterValidationResults.flatMap { result ->
                result.resolvableErrors.map {
                    FieldError(result.methodParameter.parameterName.orEmpty(), it.defaultMessage.orEmpty())
                }
            },
        )

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<ApiError> =
        if (exception is ErrorResponse && exception.statusCode.is4xxClientError) {
            ResponseEntity.status(exception.statusCode)
                .body(ApiError(ApiError.Code.VALIDATION_ERROR, "Некорректный запрос", emptyList()))
        } else {
            log.error("Request failed with an unexpected exception", exception)
            ResponseEntity.status(if (exception is ErrorResponse) exception.statusCode else HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError(ApiError.Code.INTERNAL_ERROR, "Непредвиденная ошибка сервера", emptyList()))
        }

    private fun invalid(details: List<FieldError>): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(ApiError(ApiError.Code.VALIDATION_ERROR, "Некорректные данные запроса", details))

    private fun field(path: List<JacksonException.Reference>): String =
        path.joinToString("") { if (it.index >= 0) "[${it.index}]" else ".${it.propertyName}" }.removePrefix(".")
}
