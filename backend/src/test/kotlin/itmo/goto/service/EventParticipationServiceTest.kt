package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
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
@Import(PostgresConfiguration::class, EventParticipationService::class, NotificationQueue::class)
class EventParticipationServiceTest(
    @Autowired private val participation: EventParticipationService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `joining schedules one message and future reminders`() {
        holder()
        val event = event("recruiting", now.plus(Duration.ofHours(3)), now.plus(Duration.ofDays(2)))
        participation.join(event)
        participation.join(event)
        entities.flush()
        assertThat(jdbc.queryForList<String>("SELECT kind FROM notifications WHERE event_id = ? AND user_id = ?", event, holder))
            .withFailMessage("Repeated joining duplicated a notification")
            .containsExactlyInAnyOrder("joined", "reminder_day", "reminder_hour")
    }

    @Test
    fun `leaving removes pending reminders`() {
        holder()
        val event = event("recruiting", now.plus(Duration.ofHours(3)), now.plus(Duration.ofDays(2)))
        participation.join(event)
        participation.leave(event)
        entities.flush()
        assertThat(jdbc.queryForList<String>("SELECT kind FROM notifications WHERE event_id = ? AND user_id = ?", event, holder))
            .withFailMessage("Leaving kept pending reminders")
            .containsExactly("joined")
    }

    @Test
    fun `outsider joins a recruiting event`() {
        holder()
        assertThat(participation.join(event("recruiting", now.plus(minutes(1, 600)))).isParticipant)
            .withFailMessage("Outsider doesnt join a recruiting event")
            .isTrue()
    }

    @Test
    fun `joining adds one participant`() {
        holder()
        val count = Random.nextInt(1, 50)
        val event = event("recruiting", now.plus(minutes(1, 600)))
        jdbc.update("UPDATE events SET participants_count = ? WHERE id = ?", count, event)
        assertThat(participation.join(event).participantsCount)
            .withFailMessage("Joining doesnt add one participant")
            .isEqualTo(count + 1)
    }

    @Test
    fun `joining stores the participation`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        participation.join(event)
        entities.flush()
        assertThat(jdbc.queryForList<UUID>("SELECT user_id FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Joining doesnt store the participation")
            .contains(holder)
    }

    @Test
    fun `joining a full event answers event full`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        val maximum = Random.nextInt(1, 51)
        jdbc.update("UPDATE events SET max_users = ?, participants_count = ? WHERE id = ?", maximum, maximum, event)
        assertThat(Failure { participation.join(event) }.code())
            .withFailMessage("Joining a full event doesnt answer EVENT_FULL")
            .isEqualTo(ApiError.Code.EVENT_FULL)
    }

    @Test
    fun `joining a cancelled event answers invalid event state`() {
        holder()
        val event = event(
            listOf("cancelled_by_organizer", "cancelled_by_system").random(),
            now.plus(minutes(-600, 600)),
        )
        assertThat(Failure { participation.join(event) }.code())
            .withFailMessage("Joining a cancelled event doesnt answer INVALID_EVENT_STATE")
            .isEqualTo(ApiError.Code.INVALID_EVENT_STATE)
    }

    @Test
    fun `joining after the deadline before the start answers registration closed`() {
        holder()
        val event = event("registration_closed", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        assertThat(Failure { participation.join(event) }.code())
            .withFailMessage("Joining after the deadline before the start doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `joining exactly at the deadline answers registration closed`() {
        holder()
        val event = event("recruiting", now, now.plus(minutes(0, 600)))
        assertThat(Failure { participation.join(event) }.code())
            .withFailMessage("Joining exactly at the deadline doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `joining a stale recruiting event past the deadline answers registration closed`() {
        holder()
        val event = event("recruiting", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        assertThat(Failure { participation.join(event) }.code())
            .withFailMessage("Joining a stale recruiting event past the deadline doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `refused joining of a stale event doesnt add the participant`() {
        holder()
        val event = event("recruiting", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        Failure { participation.join(event) }.code()
        entities.flush()
        assertThat(jdbc.queryForList<UUID>("SELECT user_id FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Refused joining of a stale event adds the participant")
            .doesNotContain(holder)
    }

    @Test
    fun `refused joining of a stale event below the minimum stores the system cancellation`() {
        holder()
        val event = event("recruiting", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        jdbc.update("UPDATE events SET min_users = ? WHERE id = ?", Random.nextInt(2, 51), event)
        Failure { participation.join(event) }.code()
        entities.flush()
        assertThat(jdbc.queryForObject<String>("SELECT state FROM events WHERE id = ?", event))
            .withFailMessage("Refused joining of a stale event below the minimum doesnt store the system cancellation")
            .isEqualTo("cancelled_by_system")
    }

    @Test
    fun `joining a started event answers registration closed`() {
        holder()
        val starts = now.minus(minutes(1, 179))
        val event = event("registration_closed", starts.minus(minutes(0, 600)), starts)
        assertThat(Failure { participation.join(event) }.code())
            .withFailMessage("Joining a started event doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `participant joining again keeps the participants`() {
        holder()
        val event = event("registration_closed", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        joined(event, holder)
        assertThat(participation.join(event).participantsCount)
            .withFailMessage("Participant joining again changes the participants")
            .isEqualTo(2)
    }

    @Test
    fun `participant joining a full event again succeeds`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        joined(event, holder)
        jdbc.update("UPDATE events SET max_users = 2 WHERE id = ?", event)
        assertThat(participation.join(event).isParticipant)
            .withFailMessage("Participant joining a full event again doesnt succeed")
            .isTrue()
    }

    @Test
    fun `joining near another event of the user answers schedule conflict`() {
        holder()
        val starts = now.plus(Duration.ofDays(Random.nextLong(1, 20)))
        joined(event("recruiting", starts.minus(Duration.ofHours(1)), starts), holder)
        val near = starts.plus(minutes(-179, 179))
        assertThat(Failure { participation.join(event("recruiting", now.plus(minutes(1, 60)), near)) }.code())
            .withFailMessage("Joining near another event of the user doesnt answer SCHEDULE_CONFLICT")
            .isEqualTo(ApiError.Code.SCHEDULE_CONFLICT)
    }

    @Test
    fun `joining exactly three hours after another event of the user succeeds`() {
        holder()
        val starts = now.plus(Duration.ofDays(Random.nextLong(1, 20)))
        joined(event("recruiting", starts.minus(Duration.ofHours(1)), starts), holder)
        val later = starts.plus(Duration.ofHours(3))
        assertThat(participation.join(event("recruiting", now.plus(minutes(1, 60)), later)).isParticipant)
            .withFailMessage("Joining exactly three hours after another event of the user doesnt succeed")
            .isTrue()
    }

    @Test
    fun `joining near a cancelled event of the user succeeds`() {
        holder()
        val starts = now.plus(Duration.ofDays(Random.nextLong(1, 20)))
        joined(
            event(
                listOf("cancelled_by_organizer", "cancelled_by_system").random(),
                starts.minus(Duration.ofHours(1)),
                starts,
            ),
            holder,
        )
        val near = starts.plus(minutes(-179, 179))
        assertThat(participation.join(event("recruiting", now.plus(minutes(1, 60)), near)).isParticipant)
            .withFailMessage("Joining near a cancelled event of the user doesnt succeed")
            .isTrue()
    }

    @Test
    fun `joining a full event near another event answers event full`() {
        holder()
        val starts = now.plus(Duration.ofDays(Random.nextLong(1, 20)))
        joined(event("recruiting", starts.minus(Duration.ofHours(1)), starts), holder)
        val event = event("recruiting", now.plus(minutes(1, 60)), starts)
        jdbc.update("UPDATE events SET max_users = 1 WHERE id = ?", event)
        assertThat(Failure { participation.join(event) }.code())
            .withFailMessage("Joining a full event near another event doesnt answer EVENT_FULL")
            .isEqualTo(ApiError.Code.EVENT_FULL)
    }

    @Test
    fun `joining an unknown event answers event not found`() {
        holder()
        assertThat(Failure { participation.join(UUID.randomUUID()) }.code())
            .withFailMessage("Joining an unknown event doesnt answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    @Test
    fun `participant leaves a recruiting event`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        joined(event, holder)
        participation.leave(event)
        entities.flush()
        assertThat(jdbc.queryForList<UUID>("SELECT user_id FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Participant doesnt leave a recruiting event")
            .doesNotContain(holder)
    }

    @Test
    fun `leaving frees one place`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        joined(event, holder)
        participation.leave(event)
        entities.flush()
        assertThat(jdbc.queryForObject<Int>("SELECT participants_count FROM events WHERE id = ?", event))
            .withFailMessage("Leaving doesnt free one place")
            .isEqualTo(1)
    }

    @Test
    fun `leaving below the minimum keeps recruiting`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        joined(event, holder)
        jdbc.update("UPDATE events SET min_users = 2 WHERE id = ?", event)
        participation.leave(event)
        entities.flush()
        assertThat(jdbc.queryForObject<String>("SELECT state FROM events WHERE id = ?", event))
            .withFailMessage("Leaving below the minimum doesnt keep recruiting")
            .isEqualTo("recruiting")
    }

    @Test
    fun `outsider leaving succeeds without changes`() {
        holder()
        val event = event(
            listOf("recruiting", "registration_closed", "cancelled_by_system", "finished").random(),
            now.plus(minutes(-600, 600)),
        )
        assertThat(Failure { participation.leave(event) }.code())
            .withFailMessage("Outsider leaving doesnt succeed")
            .isNull()
    }

    @Test
    fun `organizer leaving answers organizer cannot leave`() {
        holder()
        val event = Rows(jdbc).organized(holder, "recruiting", now.plus(minutes(1, 600)), now.plus(minutes(600, 900)))
        assertThat(Failure { participation.leave(event) }.code())
            .withFailMessage("Organizer leaving doesnt answer ORGANIZER_CANNOT_LEAVE")
            .isEqualTo(ApiError.Code.ORGANIZER_CANNOT_LEAVE)
    }

    @Test
    fun `leaving exactly at the deadline answers registration closed`() {
        holder()
        val event = event("recruiting", now, now.plus(minutes(0, 600)))
        joined(event, holder)
        assertThat(Failure { participation.leave(event) }.code())
            .withFailMessage("Leaving exactly at the deadline doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `leaving between the deadline and the start answers registration closed`() {
        holder()
        val event = event("registration_closed", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        joined(event, holder)
        assertThat(Failure { participation.leave(event) }.code())
            .withFailMessage("Leaving between the deadline and the start doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `refused leaving keeps the participant`() {
        holder()
        val event = event("registration_closed", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        joined(event, holder)
        Failure { participation.leave(event) }.code()
        entities.flush()
        assertThat(jdbc.queryForList<UUID>("SELECT user_id FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Refused leaving doesnt keep the participant")
            .contains(holder)
    }

    @Test
    fun `leaving a cancelled event answers invalid event state`() {
        holder()
        val event = event(
            listOf("cancelled_by_organizer", "cancelled_by_system").random(),
            now.plus(minutes(-600, 600)),
        )
        joined(event, holder)
        assertThat(Failure { participation.leave(event) }.code())
            .withFailMessage("Leaving a cancelled event doesnt answer INVALID_EVENT_STATE")
            .isEqualTo(ApiError.Code.INVALID_EVENT_STATE)
    }

    @Test
    fun `leaving an unknown event answers event not found`() {
        holder()
        assertThat(Failure { participation.leave(UUID.randomUUID()) }.code())
            .withFailMessage("Leaving an unknown event doesnt answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    @Test
    fun `participants include the organizer sorted by id`() {
        holder()
        val rows = Rows(jdbc)
        val organizer = rows.user()
        val event = rows.organized(organizer, "recruiting", now.plus(minutes(1, 600)), now.plus(minutes(600, 900)))
        val guests = List(Random.nextInt(1, 6)) { rows.user().also { joined(event, it) } }
        assertThat(participation.participants(event, 100, 0).items.map { it.id })
            .withFailMessage("Participants dont include the organizer sorted by id")
            .containsExactlyElementsOf((guests + organizer).sortedBy { it.toString() })
    }

    @Test
    fun `participants page counts every participant`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        val count = Random.nextInt(2, 8)
        repeat(count - 1) { joined(event, Rows(jdbc).user()) }
        assertThat(participation.participants(event, 1, Random.nextInt(0, 10)).total)
            .withFailMessage("Participants page doesnt count every participant")
            .isEqualTo(count.toLong())
    }

    @Test
    fun `participants page skips the offset and keeps the limit`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        repeat(Random.nextInt(4, 8)) { joined(event, Rows(jdbc).user()) }
        val all = participation.participants(event, 100, 0).items.map { it.id }
        val offset = Random.nextInt(0, 3)
        val limit = Random.nextInt(1, 3)
        assertThat(participation.participants(event, limit, offset).items.map { it.id })
            .withFailMessage("Participants page doesnt skip the offset and keep the limit")
            .containsExactlyElementsOf(all.drop(offset).take(limit))
    }

    @Test
    fun `participants of an unknown event answer event not found`() {
        holder()
        assertThat(Failure { participation.participants(UUID.randomUUID(), 100, 0) }.code())
            .withFailMessage("Participants of an unknown event dont answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    private fun holder(): UUID = Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)

    private fun event(state: String, closes: Instant, starts: Instant = closes.plus(minutes(0, 600))): UUID =
        Rows(jdbc).organized(Rows(jdbc).user(), state, closes, starts)

    private fun joined(event: UUID, user: UUID) {
        Rows(jdbc).participant(event, user)
        jdbc.update("UPDATE events SET participants_count = participants_count + 1 WHERE id = ?", event)
    }

    private fun minutes(from: Long, until: Long): Duration = Duration.ofMinutes(Random.nextLong(from, until))

    companion object {
        private val holder = UUID.randomUUID()

        private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        @JvmStatic
        fun identity(): Identity = Identity { holder }

        @JvmStatic
        fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
