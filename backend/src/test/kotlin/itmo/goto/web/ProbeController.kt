package itmo.goto.web

import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.UpdateProfileRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Test-only endpoints that raise each kind of failure the API error handler must translate.
 */
@RestController
@Validated
class ProbeController {
    @PostMapping("/probe/events")
    fun create(@Valid @RequestBody request: CreateEventRequest): String = request.title

    @PostMapping("/probe/profile")
    fun profile(@Valid @RequestBody request: UpdateProfileRequest): String = request.name

    @GetMapping("/probe/events/{eventId}")
    fun event(@PathVariable eventId: UUID): String = eventId.toString()

    @GetMapping("/probe/page")
    fun page(@Min(1) @Max(100) @RequestParam limit: Int): Int = limit

    @GetMapping("/probe/failures/{code}")
    fun failure(@PathVariable code: String): String = throw ApiFailure(ApiError.Code.valueOf(code), "Отказ $code")

    @GetMapping("/probe/crash")
    fun crash(@RequestParam secret: String): String = throw IllegalStateException(secret)

    @GetMapping("/probe/unavailable")
    fun unavailable(): String = throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Не реализовано")
}
