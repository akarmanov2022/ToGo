package itmo.goto.web

import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateEventRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Test-only endpoints that raise each kind of failure the API error handler must translate.
 */
@RestController
@Validated
class ProbeController {
    @PostMapping("/probe/events")
    fun create(@Valid @RequestBody request: CreateEventRequest): String = request.title

    @GetMapping("/probe/events/{eventId}")
    fun event(@PathVariable eventId: UUID): String = eventId.toString()

    @GetMapping("/probe/page")
    fun page(@Min(1) @Max(100) @RequestParam limit: Int): Int = limit

    @GetMapping("/probe/failures/{code}")
    fun failure(@PathVariable code: String): String =
        throw ApiFailure(ApiError.Code.valueOf(code), "Отказ $code")

    @GetMapping("/probe/crash")
    fun crash(@RequestParam secret: String): String = throw IllegalStateException(secret)
}
