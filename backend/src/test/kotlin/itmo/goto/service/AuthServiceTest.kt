package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.auth.SignedLaunch
import itmo.goto.auth.TelegramConfiguration
import itmo.goto.auth.TokenConfiguration
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.TelegramAuthRequest
import itmo.goto.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.convention.TestBean
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

@DataJpaTest(showSql = false, properties = ["telegram.bot-token=7000000002:AAauth-service-test-token"])
@Import(PostgresConfiguration::class, TelegramConfiguration::class, TokenConfiguration::class, AuthService::class)
class AuthServiceTest(
    @Autowired private val service: AuthService,
    @Autowired private val users: UserRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val decoder: JwtDecoder,
    @Value("\${telegram.bot-token}") private val token: String,
) {
    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `first login names account after telegram first and last name`() {
        val first = "Анна${Random.nextInt(1000)}"
        val last = "Смирнова${Random.nextInt(1000)}"
        assertThat(service.authenticateTelegram(login(user(first = first, last = last))).user.name)
            .withFailMessage("First login doesnt name the account after the Telegram first and last name")
            .isEqualTo("$first $last")
    }

    @Test
    fun `first login takes telegram photo`() {
        val photo = "https://t.me/i/userpic/320/${Random.nextInt()}.jpg"
        assertThat(service.authenticateTelegram(login(user(photo = photo))).user.photoUrl)
            .withFailMessage("First login doesnt take the Telegram photo")
            .isEqualTo(photo)
    }

    @Test
    fun `first login reports telegram id`() {
        val id = Random.nextLong(1, 10_000_000_000)
        assertThat(service.authenticateTelegram(login(user(id = id))).user.tgId)
            .withFailMessage("First login doesnt report the Telegram id")
            .isEqualTo(id)
    }

    @Test
    fun `repeated login creates no duplicate account`() {
        val id = Random.nextLong(1, 10_000_000_000)
        service.authenticateTelegram(login(user(id = id)))
        service.authenticateTelegram(login(user(id = id)))
        users.flush()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE tg_id = ?", Long::class.java, id))
            .withFailMessage("Repeated login creates a duplicate account")
            .isEqualTo(1)
    }

    @Test
    fun `repeated login returns same account`() {
        val id = Random.nextLong(1, 10_000_000_000)
        val first = service.authenticateTelegram(login(user(id = id))).user.id
        assertThat(service.authenticateTelegram(login(user(id = id))).user.id)
            .withFailMessage("Repeated login doesnt return the same account")
            .isEqualTo(first)
    }

    @Test
    fun `repeated login keeps changed profile name`() {
        val id = Random.nextLong(1, 10_000_000_000)
        val changed = "Аня ${Random.nextInt(1000)}"
        service.authenticateTelegram(login(user(id = id)))
        users.findByTgId(id)!!.name = changed
        assertThat(service.authenticateTelegram(login(user(id = id))).user.name)
            .withFailMessage("Repeated login doesnt keep the changed profile name")
            .isEqualTo(changed)
    }

    @Test
    fun `repeated login refreshes username`() {
        val id = Random.nextLong(1, 10_000_000_000)
        val fresh = "new_${Random.nextInt(1000)}"
        service.authenticateTelegram(login(user(id = id, username = "old_${Random.nextInt(1000)}")))
        assertThat(service.authenticateTelegram(login(user(id = id, username = fresh))).user.tgLogin)
            .withFailMessage("Repeated login doesnt refresh the username")
            .isEqualTo(fresh)
    }

    @Test
    fun `login without username gives no login`() {
        assertThat(service.authenticateTelegram(login(user(username = null))).user.tgLogin)
            .withFailMessage("Login without a username doesnt give an empty login")
            .isNull()
    }

    @Test
    fun `issued token belongs to account`() {
        val response = service.authenticateTelegram(login(user()))
        assertThat(decoder.decode(response.accessToken).subject)
            .withFailMessage("Issued token doesnt belong to the account")
            .isEqualTo(response.user.id.toString())
    }

    @Test
    fun `issued token lives one hour`() {
        val jwt = decoder.decode(service.authenticateTelegram(login(user())).accessToken)
        assertThat(Duration.between(jwt.issuedAt, jwt.expiresAt))
            .withFailMessage("Issued token doesnt live one hour")
            .isEqualTo(Duration.ofHours(1))
    }

    @Test
    fun `rejected launch data gives invalid init data code`() {
        assertThatThrownBy { service.authenticateTelegram(TelegramAuthRequest(SignedLaunch("${Random.nextLong()}:AAforged", moment, user()).text())) }
            .extracting("code")
            .withFailMessage("Rejected launch data doesnt give INVALID_TELEGRAM_INIT_DATA")
            .isEqualTo(ApiError.Code.INVALID_TELEGRAM_INIT_DATA)
    }

    @Test
    fun `rejected launch data creates no account`() {
        val id = Random.nextLong(1, 10_000_000_000)
        runCatching { service.authenticateTelegram(TelegramAuthRequest(SignedLaunch(token, moment.minusSeconds(301), user(id = id)).text())) }
        users.flush()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE tg_id = ?", Long::class.java, id))
            .withFailMessage("Rejected launch data creates an account")
            .isZero()
    }

    private fun login(user: String): TelegramAuthRequest = TelegramAuthRequest(SignedLaunch(token, moment, user).text())

    private fun user(
        id: Long = Random.nextLong(1, 10_000_000_000),
        first: String = "Имя${Random.nextInt(1000)}",
        last: String = "Фамилия${Random.nextInt(1000)}",
        username: String? = "user${Random.nextInt(1000)}",
        photo: String = "https://t.me/i/userpic/320/${Random.nextInt()}.jpg",
    ): String =
        listOfNotNull(
            "\"id\":$id",
            "\"first_name\":\"$first\"",
            "\"last_name\":\"$last\"",
            username?.let { "\"username\":\"$it\"" },
            "\"photo_url\":\"$photo\"",
        ).joinToString(",", "{", "}")

    companion object {
        private val moment = Instant.ofEpochSecond(Random.nextLong(1_600_000_000, 2_000_000_000))

        @JvmStatic
        fun clock(): Clock = Clock.fixed(moment, ZoneOffset.UTC)
    }
}
