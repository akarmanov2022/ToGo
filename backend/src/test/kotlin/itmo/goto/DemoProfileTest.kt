package itmo.goto

import itmo.goto.auth.SignedLaunch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.assertj.MockMvcTester
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import kotlin.random.Random

/**
 * Runs the whole application in the demo profile, which may change data but never the way users sign in.
 */
@SpringBootTest(properties = ["telegram.bot-token=7000000003:AAdemo-profile-test-token"])
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(PostgresConfiguration::class)
class DemoProfileTest(
    @Autowired private val mvc: MockMvcTester,
    @Autowired private val jdbc: JdbcTemplate,
    @Value("\${telegram.bot-token}") private val token: String,
) {
    @Test
    fun `demo login is not served`() {
        assertThat(mvc.post().uri("/api/v1/auth/demo-login").contentType(MediaType.APPLICATION_JSON).content("""{"userId":"${Random.nextLong()}"}"""))
            .withFailMessage("Demo login is served in the demo profile")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `demo user listing is not served`() {
        assertThat(mvc.get().uri("/api/v1/auth/demo-users"))
            .withFailMessage("Demo user listing is served in the demo profile")
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `unverified launch data gets no token`() {
        assertThat(login(SignedLaunch("${Random.nextLong()}:AAforged", Instant.now(), user(Random.nextLong(1, 10_000_000_000))).text()))
            .bodyJson()
            .extractingPath("$.code")
            .withFailMessage("Unverified launch data gets a token in the demo profile")
            .isEqualTo("INVALID_TELEGRAM_INIT_DATA")
    }

    @Test
    fun `unverified launch data creates no account`() {
        val id = Random.nextLong(1, 10_000_000_000)
        login(SignedLaunch("${Random.nextLong()}:AAforged", Instant.now(), user(id)).text()).exchange()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE tg_id = ?", Long::class.java, id))
            .withFailMessage("Unverified launch data creates an account in the demo profile")
            .isZero()
    }

    @Test
    fun `verified login token opens own profile`() {
        val id = Random.nextLong(1, 10_000_000_000)
        val access = JsonMapper.builder().build().readTree(login(SignedLaunch(token, Instant.now(), user(id)).text()).exchange().response.contentAsString)
            .get("accessToken").stringValue()
        assertThat(mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer $access"))
            .bodyJson()
            .extractingPath("$.tgId")
            .convertTo(Long::class.javaObjectType)
            .withFailMessage("Verified login token doesnt open the own profile")
            .isEqualTo(id)
    }

    private fun login(data: String) =
        mvc.post().uri("/api/v1/auth/telegram").contentType(MediaType.APPLICATION_JSON)
            .content(JsonMapper.builder().build().writeValueAsString(mapOf("initData" to data)))

    private fun user(id: Long): String = """{"id":$id,"first_name":"Имя${Random.nextInt(1000)}","username":"user${Random.nextInt(1000)}"}"""
}
