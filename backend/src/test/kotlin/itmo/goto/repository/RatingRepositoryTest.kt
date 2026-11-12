package itmo.goto.repository

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@DataJpaTest
@Import(PostgresConfiguration::class)
class RatingRepositoryTest(
    @Autowired private val ratings: RatingRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `rating stores the score`() {
        val rows = Rows(jdbc)
        val event = rows.event()
        val score = Random.nextInt(1, 6)
        ratings.rate(event, rows.user(), score, moment())
        assertThat(jdbc.queryForList<Int>("SELECT score FROM event_ratings WHERE event_id = ?", event))
            .withFailMessage("Rating doesnt store the score")
            .containsExactly(score)
    }

    private fun moment(): Instant = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        .minusSeconds(Random.nextLong(0, 86_400))
}
