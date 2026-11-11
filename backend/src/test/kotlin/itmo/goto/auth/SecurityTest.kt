package itmo.goto.auth

import itmo.goto.ClockConfiguration
import itmo.goto.generated.api.AuthApiController
import itmo.goto.generated.api.AuthApiService
import itmo.goto.generated.api.CategoriesApiController
import itmo.goto.generated.api.CategoriesApiService
import itmo.goto.generated.api.CommentsApiController
import itmo.goto.generated.api.CommentsApiService
import itmo.goto.generated.api.EventsApiController
import itmo.goto.generated.api.EventsApiService
import itmo.goto.generated.api.UsersApiController
import itmo.goto.generated.api.UsersApiService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

@WebMvcTest(
    controllers = [
        AuthApiController::class,
        CategoriesApiController::class,
        CommentsApiController::class,
        EventsApiController::class,
        UsersApiController::class,
    ],
    properties = ["telegram.bot-token=7000000001:AAsecurity-test-token"],
)
@Import(SecurityConfiguration::class, TokenConfiguration::class, ClockConfiguration::class)
class SecurityTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val tokens: AccessTokens,
    @Autowired private val encoder: JwtEncoder,
) {
    @MockitoBean
    private lateinit var auth: AuthApiService

    @MockitoBean
    private lateinit var categories: CategoriesApiService

    @MockitoBean
    private lateinit var comments: CommentsApiService

    @MockitoBean
    private lateinit var events: EventsApiService

    @MockitoBean
    private lateinit var users: UsersApiService

    @Test
    fun `telegram login is open without token`() {
        assertThat(
            mvc.post().uri("/api/v1/auth/telegram").contentType(MediaType.APPLICATION_JSON)
                .content("""{"initData":"query_id=${Random.nextInt()}"}"""),
        )
            .withFailMessage("Telegram login isnt open without a token")
            .hasStatus(HttpStatus.OK)
    }

    @Test
    fun `event feed without token answers unauthorized`() {
        assertThat(mvc.get().uri("/api/v1/events").param("limit", Random.nextInt(1, 100).toString()))
            .withFailMessage("Event feed without a token doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `event card without token answers unauthorized`() {
        assertThat(mvc.get().uri("/api/v1/events/{eventId}", UUID.randomUUID()))
            .withFailMessage("Event card without a token doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `event comments without token answer unauthorized`() {
        assertThat(mvc.get().uri("/api/v1/events/{eventId}/comments", UUID.randomUUID()))
            .withFailMessage("Event comments without a token dont answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `category directory without token answers unauthorized`() {
        assertThat(mvc.get().uri("/api/v1/categories"))
            .withFailMessage("Category directory without a token doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `event creation without token answers unauthorized`() {
        assertThat(
            mvc.post().uri(
                "/api/v1/events",
            ).contentType(MediaType.APPLICATION_JSON).content("""{"title":"Событие ${Random.nextInt()}"}"""),
        )
            .withFailMessage("Event creation without a token doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `current user without token answers unauthorized`() {
        assertThat(mvc.get().uri("/api/v1/users/me"))
            .withFailMessage("Current user without a token doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `api description is open without token`() {
        assertThat(mvc.get().uri("/v3/api-docs/{group}", "group${Random.nextInt()}").exchange().response.status)
            .withFailMessage("API description without a token answers 401")
            .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `swagger page is open without token`() {
        assertThat(mvc.get().uri("/swagger-ui/{page}", "page${Random.nextInt()}.html").exchange().response.status)
            .withFailMessage("Swagger page without a token answers 401")
            .isNotEqualTo(HttpStatus.UNAUTHORIZED.value())
    }

    @Test
    fun `missing token gives unauthorized code`() {
        assertThat(mvc.get().uri("/api/v1/categories"))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Missing token doesnt give UNAUTHORIZED")
            .isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `missing token gets bearer challenge`() {
        assertThat(mvc.get().uri("/api/v1/categories").exchange().response.getHeader(HttpHeaders.WWW_AUTHENTICATE))
            .withFailMessage("Missing token doesnt get a Bearer challenge")
            .matches("Bearer( .*)?")
    }

    @Test
    fun `issued token opens category directory`() {
        assertThat(
            mvc.get().uri(
                "/api/v1/categories",
            ).header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.issue(UUID.randomUUID())}"),
        )
            .withFailMessage("Issued token doesnt open the category directory")
            .hasStatus(HttpStatus.OK)
    }

    @Test
    fun `token signed with another key answers unauthorized`() {
        val forged =
            AccessTokens(
                NimbusJwtEncoder.withSecretKey(SecretKeySpec(Random.nextBytes(32), "HmacSHA256")).build(),
                Clock.systemUTC(),
            )
                .issue(UUID.randomUUID())
        assertThat(mvc.get().uri("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer $forged"))
            .withFailMessage("Token signed with another key doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `token with altered subject answers unauthorized`() {
        val parts = tokens.issue(UUID.randomUUID()).split(".")
        val altered =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                String(
                    Base64.getUrlDecoder().decode(parts[1]),
                ).replace(Regex("\"sub\":\"[^\"]+\""), "\"sub\":\"${UUID.randomUUID()}\"").toByteArray(),
            )
        assertThat(
            mvc.get().uri(
                "/api/v1/categories",
            ).header(HttpHeaders.AUTHORIZATION, "Bearer ${parts[0]}.$altered.${parts[2]}"),
        )
            .withFailMessage("Token with an altered subject doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `expired token answers unauthorized`() {
        val expired =
            AccessTokens(
                encoder,
                Clock.offset(Clock.systemUTC(), Duration.ofSeconds(-Random.nextLong(3601, 1_000_000))),
            )
                .issue(UUID.randomUUID())
        assertThat(mvc.get().uri("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer $expired"))
            .withFailMessage("Expired token doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `token without expiry answers unauthorized`() {
        val endless =
            encoder.encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(),
                    JwtClaimsSet.builder().subject(UUID.randomUUID().toString()).issuedAt(Instant.now()).build(),
                ),
            ).tokenValue
        assertThat(mvc.get().uri("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Bearer $endless"))
            .withFailMessage("Token without expiry doesnt answer 401")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `rejected token gives unauthorized code`() {
        assertThat(
            mvc.get().uri(
                "/api/v1/categories",
            ).header(HttpHeaders.AUTHORIZATION, "Bearer ${Random.nextLong()}.${Random.nextLong()}"),
        )
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Rejected token doesnt give UNAUTHORIZED")
            .isEqualTo("UNAUTHORIZED")
    }

    @Test
    fun `http basic credentials are not accepted`() {
        val credentials = Base64.getEncoder().encodeToString(
            "user${Random.nextInt()}:password${Random.nextInt()}".toByteArray(),
        )
        assertThat(mvc.get().uri("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, "Basic $credentials"))
            .withFailMessage("HTTP Basic credentials are accepted")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `form login is not served`() {
        assertThat(
            mvc.post().uri(
                "/login",
            ).param("username", "user${Random.nextInt()}").param("password", "password${Random.nextInt()}"),
        )
            .withFailMessage("Form login is served")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `authorized request creates no session`() {
        assertThat(
            mvc.get().uri(
                "/api/v1/categories",
            ).header(HttpHeaders.AUTHORIZATION, "Bearer ${tokens.issue(UUID.randomUUID())}")
                .exchange().request.getSession(false),
        )
            .withFailMessage("Authorized request creates a session")
            .isNull()
    }
}
