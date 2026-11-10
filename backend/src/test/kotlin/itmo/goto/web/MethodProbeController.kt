package itmo.goto.web

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only endpoint without class-level validation, so Spring MVC validates its parameters itself.
 */
@RestController
class MethodProbeController {
    @GetMapping("/probe/method/page")
    fun page(@Min(1) @Max(100) @RequestParam limit: Int): Int = limit
}
