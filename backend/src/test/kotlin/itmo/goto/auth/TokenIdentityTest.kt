package itmo.goto.auth

import itmo.goto.generated.model.ApiError
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.util.UUID
import kotlin.random.Random

class TokenIdentityTest {
    @Test
    fun `token subject identifies user`() {
        val user = UUID.randomUUID()
        assertThat(
            TokenIdentity(
                context(
                    JwtAuthenticationToken(
                        Jwt.withTokenValue(
                            "token${Random.nextInt()}",
                        ).header("alg", "HS256").subject(user.toString()).build(),
                    ),
                ),
            ).user(),
        )
            .withFailMessage("Token subject doesnt identify the user")
            .isEqualTo(user)
    }

    @Test
    fun `anonymous request answers unauthorized`() {
        assertThatThrownBy {
            TokenIdentity(
                context(
                    AnonymousAuthenticationToken(
                        "key${Random.nextInt()}",
                        "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"),
                    ),
                ),
            ).user()
        }
            .extracting("code")
            .withFailMessage("Anonymous request doesnt answer UNAUTHORIZED")
            .isEqualTo(ApiError.Code.UNAUTHORIZED)
    }

    @Test
    fun `subject that is not a uuid answers unauthorized`() {
        assertThatThrownBy {
            TokenIdentity(
                context(
                    JwtAuthenticationToken(
                        Jwt.withTokenValue(
                            "token${Random.nextInt()}",
                        ).header("alg", "HS256").subject("user${Random.nextInt()}").build(),
                    ),
                ),
            ).user()
        }
            .extracting("code")
            .withFailMessage("Subject that is not a UUID doesnt answer UNAUTHORIZED")
            .isEqualTo(ApiError.Code.UNAUTHORIZED)
    }

    @Test
    fun `request without authentication answers unauthorized`() {
        assertThatThrownBy { TokenIdentity { SecurityContextImpl() }.user() }
            .extracting("code")
            .withFailMessage("Request without authentication doesnt answer UNAUTHORIZED")
            .isEqualTo(ApiError.Code.UNAUTHORIZED)
    }

    private fun context(authentication: Authentication): () -> SecurityContext = { SecurityContextImpl(authentication) }
}
