package itmo.goto.auth

import itmo.goto.generated.model.ApiError
import itmo.goto.web.ApiFailure
import org.springframework.security.core.context.SecurityContext
import java.util.UUID

/**
 * Identity carried by the access token that authenticated the current request.
 */
class TokenIdentity(private val context: () -> SecurityContext) : Identity {
    override fun user(): UUID =
        context().authentication?.name
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: throw ApiFailure(ApiError.Code.UNAUTHORIZED, "Требуется вход через Telegram")
}
