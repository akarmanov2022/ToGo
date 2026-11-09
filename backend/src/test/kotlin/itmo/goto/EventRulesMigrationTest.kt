package itmo.goto

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresConfiguration::class)
class EventRulesMigrationTest(@Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `category directory is filled`() {
        assertThat(jdbc.queryForList("select name from categories", String::class.java))
            .withFailMessage("Category directory does not contain the agreed categories")
            .containsExactlyInAnyOrder("Кафе", "Спорт", "Выставки", "Кино", "Настолки", "Прогулки", "Музыка", "Обучение")
    }

    @Test
    fun `database accepts consistent event`() {
        val start = moment()
        assertThat(event(start.minusHours(2), start, start.plusHours(3), 1, 1, "recruiting"))
            .withFailMessage("Database rejects an event whose dates, limits and state are consistent")
            .isEqualTo(1)
    }

    @Test
    fun `database rejects deadline after start`() {
        val start = moment()
        assertThatThrownBy { event(start.plusSeconds(1), start, start.plusHours(3), 2, 5, "recruiting") }
            .withFailMessage("Database accepts a registration deadline after the event start")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database rejects end not after start`() {
        val start = moment()
        assertThatThrownBy { event(start.minusHours(1), start, start, 2, 5, "recruiting") }
            .withFailMessage("Database accepts an event that ends when it starts")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database rejects minimum above maximum`() {
        val start = moment()
        assertThatThrownBy { event(start.minusHours(1), start, start.plusHours(2), 6, 5, "recruiting") }
            .withFailMessage("Database accepts a minimum above the maximum")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database rejects zero minimum`() {
        val start = moment()
        assertThatThrownBy { event(start.minusHours(1), start, start.plusHours(2), 0, 5, "recruiting") }
            .withFailMessage("Database accepts a zero minimum")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database rejects unknown state`() {
        val start = moment()
        assertThatThrownBy { event(start.minusHours(1), start, start.plusHours(2), 2, 5, "open") }
            .withFailMessage("Database accepts a state outside the five API states")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun moment(): OffsetDateTime =
        OffsetDateTime.of(2027, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusMinutes(Random.nextLong(0, 500_000))

    private fun event(
        deadline: OffsetDateTime,
        start: OffsetDateTime,
        end: OffsetDateTime,
        minimum: Int,
        maximum: Int,
        state: String,
    ): Int =
        jdbc.update(
            """
            insert into events (starts_at, ends_at, registration_closes_at, title, description, location, state,
                min_users, max_users, organizer_id)
            values (?, ?, ?, ?, '', ?, ?, ?, ?, ?)
            """.trimIndent(),
            start,
            end,
            deadline,
            "Событие ${UUID.randomUUID()}",
            "Улица ${Random.nextInt(1, 300)}",
            state,
            minimum,
            maximum,
            jdbc.queryForObject("insert into users (tg_id) values (?) returning id", UUID::class.java, Random.nextLong(1, Long.MAX_VALUE)),
        )
}
