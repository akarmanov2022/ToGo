package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.RateEventRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.bean.override.convention.TestBean
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, RatingsService::class, NotificationQueue::class)
class RatingsServiceTest(
    @Autowired private val ratings: RatingsService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `participant rating answers with the own score`() {
        holder()
        val event = finished()
        Rows(jdbc).participant(event, holder)
        val score = Random.nextInt(1, 6)
        assertThat(ratings.rateEvent(event, RateEventRequest(score)).myRating)
            .withFailMessage("Participant rating doesnt answer with the own score")
            .isEqualTo(score)
    }

    @Test
    fun `participant rating answers with the counted rating`() {
        holder()
        val event = finished()
        Rows(jdbc).participant(event, holder)
        Rows(jdbc).rating(event, Rows(jdbc).user(), Random.nextInt(1, 6))
        assertThat(ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))).rating?.count)
            .withFailMessage("Participant rating doesnt answer with the rating that counts it")
            .isEqualTo(2)
    }

    @Test
    fun `participant rating is stored`() {
        holder()
        val event = finished()
        Rows(jdbc).participant(event, holder)
        val score = Random.nextInt(1, 6)
        ratings.rateEvent(event, RateEventRequest(score))
        entities.flush()
        assertThat(jdbc.queryForList<Int>("SELECT score FROM event_ratings WHERE user_id = ?", holder))
            .withFailMessage("Participant rating isnt stored")
            .containsExactly(score)
    }

    @Test
    fun `repeated rating replaces the earlier score`() {
        holder()
        val event = finished()
        Rows(jdbc).participant(event, holder)
        val score = Random.nextInt(1, 6)
        ratings.rateEvent(event, RateEventRequest((1..5).minus(score).random()))
        assertThat(ratings.rateEvent(event, RateEventRequest(score)).rating?.average)
            .withFailMessage("Repeated rating doesnt replace the earlier score")
            .isEqualTo(score.toDouble())
    }

    @Test
    fun `organizer rating answers forbidden`() {
        holder()
        val event = finished(holder)
        assertThat(Failure { ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))) }.code())
            .withFailMessage("Organizer rating the own event doesnt answer FORBIDDEN")
            .isEqualTo(ApiError.Code.FORBIDDEN)
    }

    @Test
    fun `outsider rating answers forbidden`() {
        holder()
        val event = finished()
        assertThat(Failure { ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))) }.code())
            .withFailMessage("Outsider rating doesnt answer FORBIDDEN")
            .isEqualTo(ApiError.Code.FORBIDDEN)
    }

    @Test
    fun `former participant rating answers forbidden`() {
        holder()
        val event = finished()
        Rows(jdbc).participant(event, holder)
        jdbc.update("DELETE FROM event_users WHERE event_id = ? AND user_id = ?", event, holder)
        assertThat(Failure { ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))) }.code())
            .withFailMessage("Former participant rating doesnt answer FORBIDDEN")
            .isEqualTo(ApiError.Code.FORBIDDEN)
    }

    @Test
    fun `rating before the end answers invalid event state`() {
        holder()
        val starts = now.minus(Duration.ofMinutes(Random.nextLong(1, 120)))
        val event = Rows(jdbc).organized(Rows(jdbc).user(), "registration_closed", starts, starts)
        Rows(jdbc).participant(event, holder)
        assertThat(Failure { ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))) }.code())
            .withFailMessage("Rating before the end doesnt answer INVALID_EVENT_STATE")
            .isEqualTo(ApiError.Code.INVALID_EVENT_STATE)
    }

    @Test
    fun `rating a cancelled event answers invalid event state`() {
        holder()
        val starts = ago()
        val event = Rows(jdbc).organized(
            Rows(jdbc).user(),
            listOf("cancelled_by_organizer", "cancelled_by_system").random(),
            starts,
            starts,
        )
        Rows(jdbc).participant(event, holder)
        assertThat(Failure { ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))) }.code())
            .withFailMessage("Rating a cancelled event doesnt answer INVALID_EVENT_STATE")
            .isEqualTo(ApiError.Code.INVALID_EVENT_STATE)
    }

    @Test
    fun `rating an unknown event answers event not found`() {
        holder()
        assertThat(Failure { ratings.rateEvent(UUID.randomUUID(), RateEventRequest(Random.nextInt(1, 6))) }.code())
            .withFailMessage("Rating an unknown event doesnt answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    @Test
    fun `rating past the end finishes the event when background processing is late`() {
        holder()
        val event = late()
        Rows(jdbc).participant(event, holder)
        assertThat(ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))).state)
            .withFailMessage("Rating past the end doesnt finish the event when background processing is late")
            .isEqualTo(EventState.finished)
    }

    @Test
    fun `refused rating stores the finished state`() {
        holder()
        val event = late()
        Failure { ratings.rateEvent(event, RateEventRequest(Random.nextInt(1, 6))) }.code()
        entities.flush()
        assertThat(jdbc.queryForObject<String>("SELECT state FROM events WHERE id = ?", event))
            .withFailMessage("Refused rating doesnt store the finished state")
            .isEqualTo("finished")
    }

    private fun holder(): UUID = Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)

    private fun finished(organizer: UUID = Rows(jdbc).user()): UUID {
        val starts = ago()
        return Rows(jdbc).organized(organizer, "finished", starts, starts)
    }

    private fun late(): UUID {
        val starts = now.minus(Duration.ofHours(Random.nextLong(4, 48)))
        return Rows(jdbc).organized(Rows(jdbc).user(), "registration_closed", starts, starts)
    }

    private fun ago(): Instant = now.minus(Duration.ofDays(Random.nextLong(2, 30)))

    companion object {
        private val holder = UUID.randomUUID()

        private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        @JvmStatic
        fun identity(): Identity = Identity { holder }

        @JvmStatic
        fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
