package itmo.goto.auth

import itmo.goto.generated.model.AuthResponse
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Application access tokens that the API accepts as Bearer credentials for the lifetime the contract promises.
 */
class AccessTokens(private val encoder: JwtEncoder, private val clock: Clock) {
    fun issue(user: UUID): String {
        val now = clock.instant()
        return encoder.encode(
            JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                    .subject(user.toString())
                    .issuedAt(now)
                    .expiresAt(now.plus(Duration.ofSeconds(AuthResponse.ExpiresIn._3600.value.toLong())))
                    .build(),
            ),
        ).tokenValue
    }
}
