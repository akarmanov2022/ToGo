package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@DataJpaTest
@Import(PostgresConfiguration::class)
class JoinedTest(@Autowired private val events: EventRepository, @Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `event with the participation of the user is found`() {
        val user = Rows(jdbc).user()
        val event = Rows(jdbc).event()
        Rows(jdbc).participant(event, user)
        assertThat(events.findAll(Joined(user)).map { it.id })
            .withFailMessage("Event with the participation of the user isnt found")
            .contains(event)
    }

    @Test
    fun `event with the participation of another user isnt found`() {
        val event = Rows(jdbc).event()
        Rows(jdbc).participant(event, Rows(jdbc).user())
        assertThat(events.findAll(Joined(Rows(jdbc).user())).map { it.id })
            .withFailMessage("Event with the participation of another user is found")
            .doesNotContain(event)
    }

    @Test
    fun `event with several participants is found once`() {
        val user = Rows(jdbc).user()
        val event = Rows(jdbc).event()
        Rows(jdbc).participant(event, user)
        repeat((1..5).random()) { Rows(jdbc).participant(event, Rows(jdbc).user()) }
        assertThat(events.findAll(Joined(user)).map { it.id })
            .withFailMessage("Event with several participants isnt found exactly once")
            .containsOnlyOnce(event)
    }
}
