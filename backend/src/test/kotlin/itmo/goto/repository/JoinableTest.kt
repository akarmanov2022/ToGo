package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@DataJpaTest
@Import(PostgresConfiguration::class)
class JoinableTest(@Autowired private val events: EventRepository, @Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `recruiting event with a free place before the deadline is found`() {
        val now = moment()
        val event = Rows(jdbc).event(closes = now.plusSeconds(Random.nextLong(1, 100_000)).atOffset(ZoneOffset.UTC))
        assertThat(events.findAll(Joinable(now)).map { it.id })
            .withFailMessage("Recruiting event with a free place before the deadline isnt found")
            .contains(event)
    }

    @Test
    fun `full event isnt found`() {
        val now = moment()
        val maximum = Random.nextInt(1, 51)
        val event = Rows(jdbc).event(closes = now.plusSeconds(Random.nextLong(1, 100_000)).atOffset(ZoneOffset.UTC))
        jdbc.update("UPDATE events SET max_users = ?, participants_count = ? WHERE id = ?", maximum, maximum, event)
        assertThat(events.findAll(Joinable(now)).map { it.id })
            .withFailMessage("Full event is found")
            .doesNotContain(event)
    }

    @Test
    fun `event with a free place after the deadline isnt found`() {
        val now = moment()
        val event = Rows(jdbc).event(
            closes = now.minusSeconds(Random.nextLong(0, 100_000)).atOffset(ZoneOffset.UTC),
            starts = now.plusSeconds(Random.nextLong(1, 100_000)).atOffset(ZoneOffset.UTC),
        )
        assertThat(events.findAll(Joinable(now)).map { it.id })
            .withFailMessage("Event with a free place after the deadline is found")
            .doesNotContain(event)
    }

    @Test
    fun `event with a free place out of recruiting isnt found`() {
        val now = moment()
        val event = Rows(jdbc).event(closes = now.plusSeconds(Random.nextLong(1, 100_000)).atOffset(ZoneOffset.UTC))
        jdbc.update(
            "UPDATE events SET state = ? WHERE id = ?",
            listOf("registration_closed", "cancelled_by_organizer", "cancelled_by_system", "finished").random(),
            event,
        )
        assertThat(events.findAll(Joinable(now)).map { it.id })
            .withFailMessage("Event with a free place out of recruiting is found")
            .doesNotContain(event)
    }

    private fun moment(): Instant =
        Instant.now().plusSeconds(Random.nextLong(-100_000, 100_000)).truncatedTo(ChronoUnit.SECONDS)
}
