package itmo.goto.service

import itmo.goto.auth.AccessTokens
import itmo.goto.auth.InvalidLaunch
import itmo.goto.auth.TelegramLaunch
import itmo.goto.generated.api.AuthApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.AuthResponse
import itmo.goto.generated.model.TelegramAuthRequest
import itmo.goto.model.User
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Signs Telegram users in: verified launch data finds or creates their account.
 */
@Service
class AuthService(
    private val launch: TelegramLaunch,
    private val users: UserRepository,
    private val tokens: AccessTokens,
) : AuthApiService {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    @Transactional
    override fun authenticateTelegram(telegramAuthRequest: TelegramAuthRequest): AuthResponse {
        val telegram =
            try {
                launch.user(telegramAuthRequest.initData)
            } catch (exception: InvalidLaunch) {
                log.info("Telegram login refused because {}", exception.message)
                throw ApiFailure(ApiError.Code.INVALID_TELEGRAM_INIT_DATA, "Данные запуска Telegram не прошли проверку")
            }
        val user =
            users.save(
                (
                    users.findByTgId(telegram.id()) ?: User(
                        telegram.id(),
                        telegram.login(),
                        telegram.name(),
                        telegram.photo(),
                    )
                    )
                    .apply { tgLogin = telegram.login() },
            )
        return AuthResponse(
            tokens.issue(checkNotNull(user.id) { "Saved user has no id" }),
            AuthResponse.TokenType.Bearer,
            AuthResponse.ExpiresIn._3600,
            user.current(),
        )
    }
}
