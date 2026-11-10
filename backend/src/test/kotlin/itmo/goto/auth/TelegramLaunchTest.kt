package itmo.goto.auth

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random

class TelegramLaunchTest {
    @Test
    fun `signed launch data reveals telegram id`() {
        val token = token()
        val now = moment()
        val id = Random.nextLong(1, 10_000_000_000)
        assertThat(TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now, user(id)).text()).id())
            .withFailMessage("Signed launch data doesnt reveal the Telegram id")
            .isEqualTo(id)
    }

    @Test
    fun `name joins telegram first and last name`() {
        val token = token()
        val now = moment()
        val first = "Анна${Random.nextInt(1000)}"
        val last = "Смирнова${Random.nextInt(1000)}"
        assertThat(TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now, user(first = first, last = last)).text()).name())
            .withFailMessage("Name doesnt join the Telegram first and last name")
            .isEqualTo("$first $last")
    }

    @Test
    fun `missing username gives no login`() {
        val token = token()
        val now = moment()
        assertThat(TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now, user(login = null)).text()).login())
            .withFailMessage("Missing username doesnt give an empty login")
            .isNull()
    }

    @Test
    fun `launch data signed with another token is rejected`() {
        val now = moment()
        assertThatThrownBy { TelegramLaunch(token(), Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token(), now, user()).text()) }
            .withFailMessage("Launch data signed with another bot token isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `telegram id changed after signing is rejected`() {
        val token = token()
        val now = moment()
        val id = Random.nextLong(1, 1_000_000_000)
        assertThatThrownBy {
            TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC))
                .user(SignedLaunch(token, now, user(id)).text().replace("%22id%22%3A$id", "%22id%22%3A${id + Random.nextLong(1, 1000)}"))
        }
            .withFailMessage("Launch data with a Telegram id changed after signing isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `launch data without hash is rejected`() {
        val token = token()
        val now = moment()
        assertThatThrownBy {
            TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now, user()).text().replace(Regex("&hash=[0-9a-f]+"), ""))
        }
            .withFailMessage("Launch data without a hash isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `signed launch data without user is rejected`() {
        val token = token()
        val now = moment()
        assertThatThrownBy {
            TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC))
                .user(SignedLaunch(token, mapOf("auth_date" to now.epochSecond.toString(), "query_id" to "AAH${Random.nextInt()}")).text())
        }
            .withFailMessage("Signed launch data without a user isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `signed user without numeric id is rejected`() {
        val token = token()
        val now = moment()
        assertThatThrownBy {
            TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC))
                .user(SignedLaunch(token, now, """{"id":"${Random.nextInt()}","first_name":"Анна"}""").text())
        }
            .withFailMessage("Signed user without a numeric id isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `repeated key is rejected`() {
        val token = token()
        val now = moment()
        assertThatThrownBy {
            TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now, user()).text() + "&auth_date=${now.epochSecond}")
        }
            .withFailMessage("Launch data with a repeated key isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `encoded values are decoded exactly once`() {
        val token = token()
        val now = moment()
        val first = "Анна%41${Random.nextInt(1000)}"
        assertThat(TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now, user(first = first, last = null)).text()).name())
            .withFailMessage("Encoded values arent decoded exactly once")
            .isEqualTo(first)
    }

    @Test
    fun `data aged exactly five minutes is accepted`() {
        val token = token()
        val now = moment()
        val id = Random.nextLong(1, 10_000_000_000)
        assertThat(
            TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now.minusSeconds(300), user(id)).text()).id(),
        )
            .withFailMessage("Launch data aged exactly five minutes isnt accepted")
            .isEqualTo(id)
    }

    @Test
    fun `data aged five minutes and one second is rejected`() {
        val token = token()
        val now = moment()
        assertThatThrownBy { TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now.minusSeconds(301), user()).text()) }
            .withFailMessage("Launch data aged five minutes and one second isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `data issued thirty seconds ahead is accepted`() {
        val token = token()
        val now = moment()
        val id = Random.nextLong(1, 10_000_000_000)
        assertThat(
            TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now.plusSeconds(30), user(id)).text()).id(),
        )
            .withFailMessage("Launch data issued thirty seconds ahead isnt accepted")
            .isEqualTo(id)
    }

    @Test
    fun `data issued thirty one seconds ahead is rejected`() {
        val token = token()
        val now = moment()
        assertThatThrownBy { TelegramLaunch(token, Clock.fixed(now, ZoneOffset.UTC)).user(SignedLaunch(token, now.plusSeconds(31), user()).text()) }
            .withFailMessage("Launch data issued thirty one seconds ahead isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    @Test
    fun `malformed launch data is rejected`() {
        assertThatThrownBy { TelegramLaunch(token(), Clock.fixed(moment(), ZoneOffset.UTC)).user("user=%E0%A4%A&hash=${Random.nextInt()}") }
            .withFailMessage("Malformed launch data isnt rejected")
            .isInstanceOf(InvalidLaunch::class.java)
    }

    private fun token(): String = "${Random.nextLong(1, 10_000_000_000)}:AA${Random.nextBytes(24).joinToString("") { "%02x".format(it) }}"

    private fun moment(): Instant = Instant.ofEpochSecond(Random.nextLong(1_600_000_000, 2_000_000_000))

    private fun user(
        id: Long = Random.nextLong(1, 10_000_000_000),
        first: String = "Имя${Random.nextInt(1000)}",
        last: String? = "Фамилия${Random.nextInt(1000)}",
        login: String? = "user${Random.nextInt(1000)}",
    ): String =
        listOfNotNull(
            "\"id\":$id",
            "\"first_name\":\"$first\"",
            last?.let { "\"last_name\":\"$it\"" },
            login?.let { "\"username\":\"$it\"" },
            "\"language_code\":\"ru\"",
            "\"photo_url\":\"https://t.me/i/userpic/320/${Random.nextInt()}.jpg\"",
        ).joinToString(",", "{", "}")
}
