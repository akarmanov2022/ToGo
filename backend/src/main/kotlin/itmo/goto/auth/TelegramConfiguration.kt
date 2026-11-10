package itmo.goto.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Wires the check of Mini App launch data with the bot token, which never leaves the server.
 */
@Configuration(proxyBeanMethods = false)
class TelegramConfiguration {
    @Bean
    fun telegramLaunch(@Value("\${telegram.bot-token}") token: String, clock: Clock): TelegramLaunch =
        TelegramLaunch(token, clock)
}
