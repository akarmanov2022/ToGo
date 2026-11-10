package itmo.goto.service

import itmo.goto.generated.model.ApiError
import itmo.goto.web.ApiFailure
import org.assertj.core.api.Assertions.catchThrowableOfType

/**
 * Contract error that a service call fails with, or nothing when the call succeeds.
 */
class Failure(private val call: () -> Unit) {
    fun code(): ApiError.Code? = error()?.code

    fun fields(): List<String>? = error()?.details?.map { it.field }

    private fun error(): ApiError? = catchThrowableOfType(ApiFailure::class.java) { call() }?.error()
}
