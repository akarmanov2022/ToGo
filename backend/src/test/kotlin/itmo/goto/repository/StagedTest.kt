package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.generated.model.EventState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@DataJpaTest
@Import(PostgresConfiguration::class)
class StagedTest(@Autowired private val events: EventRepository, @Autowired private val jdbc: JdbcTemplate) {
    @Test
    fun `event in the state is found`() {
        val state = EventState.entries.random()
        val event = Rows(jdbc).event(Rows(jdbc).user(), state.value)
        assertThat(events.findAll(Staged(state)).map { it.id })
            .withFailMessage("Event in the state isnt found")
            .contains(event)
    }

    @Test
    fun `event in another state isnt found`() {
        val state = EventState.entries.random()
        val event = Rows(jdbc).event(Rows(jdbc).user(), (EventState.entries - state).random().value)
        assertThat(events.findAll(Staged(state)).map { it.id })
            .withFailMessage("Event in another state is found")
            .doesNotContain(event)
    }
}
