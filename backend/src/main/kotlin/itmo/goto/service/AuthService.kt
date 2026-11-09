package itmo.goto.service

import itmo.goto.generated.api.AuthApiService
import itmo.goto.generated.model.AuthResponse
import itmo.goto.generated.model.TelegramAuthRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/** Contract placeholder; Telegram verification and token issuance are not implemented yet. */
@Service
class AuthService : AuthApiService {
    override fun authenticateTelegram(telegramAuthRequest: TelegramAuthRequest): AuthResponse =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Telegram authentication is not implemented yet")
}
