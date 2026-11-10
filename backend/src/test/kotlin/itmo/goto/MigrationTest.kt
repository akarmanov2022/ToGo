package itmo.goto

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.random.Random

@DataJpaTest
@Import(PostgresConfiguration::class)
class MigrationTest(@Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `valid event is accepted`() {
        assertThat(Rows(jdbc).event())
            .withFailMessage("Database doesnt accept a valid event")
            .isNotNull()
    }

    @Test
    fun `registration deadline after start is rejected`() {
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(Random.nextLong(1, 30))
        assertThatThrownBy { Rows(jdbc).event(closes = start.plusSeconds(Random.nextLong(1, 3600)), starts = start) }
            .withFailMessage("Database doesnt reject a registration deadline after the start")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `end not after start is rejected`() {
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(Random.nextLong(1, 30))
        assertThatThrownBy {
            Rows(jdbc).event(closes = start, starts = start, ends = start.minusSeconds(Random.nextLong(0, 3600)))
        }
            .withFailMessage("Database doesnt reject an end that is not after the start")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `maximum above fifty is rejected`() {
        assertThatThrownBy { Rows(jdbc).event(maximum = Random.nextInt(51, 1000)) }
            .withFailMessage("Database doesnt reject a participant maximum above fifty")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `single coordinate is rejected`() {
        assertThatThrownBy { Rows(jdbc).event(point = Random.nextDouble(-90.0, 90.0) to null) }
            .withFailMessage("Database doesnt reject a point with only a latitude")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `unknown state is rejected`() {
        val event = Rows(jdbc).event()
        assertThatThrownBy { jdbc.update("UPDATE events SET state = 'started' WHERE id = ?", event) }
            .withFailMessage("Database doesnt reject an unknown event state")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `event category outside the list is rejected`() {
        val event = Rows(jdbc).event()
        assertThatThrownBy {
            jdbc.update("UPDATE events SET category = ? WHERE id = ?", "SPORT_${Random.nextInt()}", event)
        }
            .withFailMessage("Database doesnt reject an event category outside the list")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `user category outside the list is rejected`() {
        val rows = Rows(jdbc)
        assertThatThrownBy { rows.interest(rows.user(), "SPORT_${Random.nextInt()}") }
            .withFailMessage("Database doesnt reject a user category outside the list")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `empty description is rejected`() {
        val event = Rows(jdbc).event()
        assertThatThrownBy { jdbc.update("UPDATE events SET description = '' WHERE id = ?", event) }
            .withFailMessage("Database doesnt reject an empty description")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `description of five hundred and one characters is rejected`() {
        val event = Rows(jdbc).event()
        assertThatThrownBy {
            jdbc.update(
                "UPDATE events SET description = ? WHERE id = ?",
                (1..501).map { ('а'..'я').random() }.joinToString(""),
                event,
            )
        }
            .withFailMessage("Database doesnt reject a description of 501 characters")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `repeated notification of one kind is rejected`() {
        val rows = Rows(jdbc)
        val user = rows.user()
        val event = rows.event()
        val kind = listOf("joined", "confirmed", "cancelled", "reminder_day", "reminder_hour").random()
        rows.notification(user, event, kind)
        assertThatThrownBy { rows.notification(user, event, kind) }
            .withFailMessage("Database doesnt reject a repeated notification of one kind")
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
