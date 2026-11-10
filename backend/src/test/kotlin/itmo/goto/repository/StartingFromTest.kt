package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@DataJpaTest
@Import(PostgresConfiguration::class)
class StartingFromTest(@Autowired private val events: EventRepository, @Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `event starting exactly at the bound is found`() {
        val bound = moment()
        val event = Rows(jdbc).event(closes = bound.minusHours(1), starts = bound)
        assertThat(events.findAll(StartingFrom(bound.toInstant())).map { it.id })
            .withFailMessage("Event starting exactly at the bound isnt found")
            .contains(event)
    }

    @Test
    fun `event starting before the bound isnt found`() {
        val bound = moment()
        val starts = bound.minusSeconds(Random.nextLong(1, 100_000))
        val event = Rows(jdbc).event(closes = starts.minusHours(1), starts = starts)
        assertThat(events.findAll(StartingFrom(bound.toInstant())).map { it.id })
            .withFailMessage("Event starting before the bound is found")
            .doesNotContain(event)
    }

    private fun moment(): OffsetDateTime =
        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(Random.nextLong(-10_000, 10_000)).truncatedTo(ChronoUnit.SECONDS)
}
