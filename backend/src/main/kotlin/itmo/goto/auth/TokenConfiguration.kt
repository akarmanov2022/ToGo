package itmo.goto.auth

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Wires signing and checking of application access tokens.
 *
 * The signing key is derived from the bot token, so the server needs no secret beyond the ones
 * it already holds, and anyone able to forge tokens could forge Telegram launch data anyway.
 * Expiry is checked against the server clock without tolerance.
 */
@Configuration(proxyBeanMethods = false)
class TokenConfiguration {
    @Bean
    fun accessKey(@Value($$"${telegram.bot-token}") token: String): SecretKey =
        SecretKeySpec(Hmac("ToGo access token".toByteArray()).sign(token.toByteArray()), "HmacSHA256")

    @Bean
    fun jwtEncoder(accessKey: SecretKey): JwtEncoder = NimbusJwtEncoder.withSecretKey(accessKey).build()

    @Bean
    fun jwtDecoder(accessKey: SecretKey, clock: Clock): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(accessKey).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(
                DelegatingOAuth2TokenValidator(
                    JwtTimestampValidator(Duration.ZERO).apply { setClock(clock) },
                    {
                        if (it.expiresAt == null) {
                            OAuth2TokenValidatorResult.failure(
                                OAuth2Error(
                                    "invalid_token",
                                    "Token carries no expiry",
                                    null,
                                ),
                            )
                        } else {
                            OAuth2TokenValidatorResult.success()
                        }
                    },
                ),
            )
        }

    @Bean
    fun accessTokens(jwtEncoder: JwtEncoder, clock: Clock): AccessTokens = AccessTokens(jwtEncoder, clock)
}
