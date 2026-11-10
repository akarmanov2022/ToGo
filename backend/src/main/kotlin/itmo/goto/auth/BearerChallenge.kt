package itmo.goto.auth

import itmo.goto.generated.model.ApiError
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.json.JsonMapper

/**
 * Answers requests without a valid access token with the contract's UNAUTHORIZED error
 * and a Bearer challenge, so clients know to repeat the Telegram login.
 */
class BearerChallenge(private val mapper: JsonMapper) : AuthenticationEntryPoint {
    private val challenge = BearerTokenAuthenticationEntryPoint()

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        challenge.commence(request, response, exception)
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        mapper.writeValue(
            response.outputStream,
            ApiError(ApiError.Code.UNAUTHORIZED, "Требуется вход через Telegram", emptyList()),
        )
    }
}
