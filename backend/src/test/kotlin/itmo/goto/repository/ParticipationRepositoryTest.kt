package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.random.Random

@DataJpaTest
@Import(PostgresConfiguration::class)
class ParticipationRepositoryTest(
    @Autowired private val participations: ParticipationRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `scores of the viewer are found by event`() {
        val rows = Rows(jdbc)
        val user = rows.user()
        val rated = rows.event()
        val score = Random.nextInt(1, 6)
        rows.rating(rated, user, score)
        rows.rating(rows.event(), rows.user(), Random.nextInt(1, 6))
        assertThat(participations.scores(user, listOf(rated, rows.event())).associate { it.event.id to it.score })
            .withFailMessage("Scores of the viewer arent found by event")
            .containsExactlyEntriesOf(mapOf(rated to score))
    }

    @Test
    fun `score of a viewer who didnt rate is absent`() {
        val rows = Rows(jdbc)
        val event = rows.event()
        rows.rating(event, rows.user(), Random.nextInt(1, 6))
        assertThat(participations.score(event, rows.user()))
            .withFailMessage("Score of a viewer who didnt rate is present")
            .isNull()
    }
}
