package itmo.goto.web

import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.FieldError

/**
 * Refusal of an API operation, carrying the contract error code and the message shown to the user.
 */
class ApiFailure(
    val code: ApiError.Code,
    override val message: String,
    val details: List<FieldError>,
) : RuntimeException(message) {
    constructor(code: ApiError.Code, message: String) : this(code, message, emptyList())
}
