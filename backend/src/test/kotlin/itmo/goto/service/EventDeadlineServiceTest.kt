package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.test.context.bean.override.convention.TestBean
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, EventDeadlineService::class, NotificationQueue::class)
class EventDeadlineServiceTest(
    @Autowired private val deadlines: EventDeadlineService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `deadline confirms every participant once`() {
        val event = event("recruiting", now, now.plus(Duration.ofHours(2)))
        val guest = Rows(jdbc).user()
        Rows(jdbc).participant(event, guest)
        jdbc.update("UPDATE events SET participants_count = 2 WHERE id = ?", event)
        deadlines.settle()
        deadlines.settle()
        entities.flush()
        val recipients = jdbc.queryForList<UUID>(
            "SELECT user_id FROM notifications WHERE event_id = ? AND kind = 'confirmed'",
            event,
        )
        val organizer = jdbc.queryForObject("SELECT organizer_id FROM events WHERE id = ?", UUID::class.java, event)
        assertThat(recipients)
            .withFailMessage("Deadline did not confirm each participant exactly once")
            .containsExactlyInAnyOrder(guest, organizer)
    }

    @Test
    fun `system cancellation notifies organizer and guest`() {
        val event = event("recruiting", now, now.plus(Duration.ofHours(2)))
        val guest = Rows(jdbc).user()
        Rows(jdbc).participant(event, guest)
        jdbc.update("UPDATE events SET participants_count = 2, min_users = 3 WHERE id = ?", event)
        deadlines.settle()
        entities.flush()
        val recipients = jdbc.queryForList<UUID>(
            "SELECT user_id FROM notifications WHERE event_id = ? AND kind = 'cancelled'",
            event,
        )
        val organizer = jdbc.queryForObject("SELECT organizer_id FROM events WHERE id = ?", UUID::class.java, event)
        assertThat(recipients)
            .withFailMessage("System cancellation did not notify organizer and guest")
            .containsExactlyInAnyOrder(guest, organizer)
    }

    @Test
    fun `end asks every participant except the organizer to rate once`() {
        val starts = now.minus(Duration.ofHours(3))
        val event = event("registration_closed", starts.minus(minutes(0, 600)), starts)
        val guests = List(Random.nextInt(1, 4)) { Rows(jdbc).user() }
        guests.forEach { Rows(jdbc).participant(event, it) }
        deadlines.settle()
        deadlines.settle()
        entities.flush()
        assertThat(
            jdbc.queryForList<UUID>("SELECT user_id FROM notifications WHERE event_id = ? AND kind = 'rating'", event),
        )
            .withFailMessage("End didnt ask every participant except the organizer to rate exactly once")
            .containsExactlyInAnyOrderElementsOf(guests)
    }

    @Test
    fun `system cancellation asks nobody to rate`() {
        val starts = now.minus(Duration.ofHours(3)).minus(minutes(0, 600))
        val event = event("recruiting", starts.minus(minutes(0, 600)), starts)
        Rows(jdbc).participant(event, Rows(jdbc).user())
        jdbc.update("UPDATE events SET min_users = 3 WHERE id = ?", event)
        deadlines.settle()
        entities.flush()
        assertThat(
            jdbc.queryForList<UUID>("SELECT user_id FROM notifications WHERE event_id = ? AND kind = 'rating'", event),
        )
            .withFailMessage("System cancellation asked participants to rate")
            .isEmpty()
    }

    @Test
    fun `deadline closes the registration of an event with the minimum`() {
        val event = event("recruiting", now, now.plus(minutes(0, 600)))
        assertThat(settled(event))
            .withFailMessage("Deadline doesnt close the registration of an event with the minimum")
            .isEqualTo("registration_closed")
    }

    @Test
    fun `deadline cancels an event below the minimum`() {
        val event = event("recruiting", now, now.plus(minutes(0, 600)))
        jdbc.update("UPDATE events SET min_users = ? WHERE id = ?", Random.nextInt(2, 51), event)
        assertThat(settled(event))
            .withFailMessage("Deadline doesnt cancel an event below the minimum")
            .isEqualTo("cancelled_by_system")
    }

    @Test
    fun `recruiting before the deadline stays recruiting`() {
        val event = event("recruiting", now.plus(minutes(1, 600)), now.plus(minutes(600, 900)))
        assertThat(settled(event))
            .withFailMessage("Recruiting before the deadline doesnt stay recruiting")
            .isEqualTo("recruiting")
    }

    @Test
    fun `end finishes an event with closed registration`() {
        val starts = now.minus(Duration.ofHours(3))
        val event = event("registration_closed", starts.minus(minutes(0, 600)), starts)
        assertThat(settled(event))
            .withFailMessage("End doesnt finish an event with closed registration")
            .isEqualTo("finished")
    }

    @Test
    fun `event with closed registration before the end keeps going`() {
        val starts = now.minus(minutes(0, 179))
        val event = event("registration_closed", starts.minus(minutes(0, 600)), starts)
        assertThat(settled(event))
            .withFailMessage("Event with closed registration before the end doesnt keep going")
            .isEqualTo("registration_closed")
    }

    @Test
    fun `end keeps a cancelled event cancelled`() {
        val state = listOf("cancelled_by_organizer", "cancelled_by_system").random()
        val starts = now.minus(Duration.ofHours(3)).minus(minutes(0, 600))
        val event = event(state, starts.minus(minutes(0, 600)), starts)
        assertThat(settled(event))
            .withFailMessage("End doesnt keep a cancelled event cancelled")
            .isEqualTo(state)
    }

    @Test
    fun `missed deadline and end finish an event with the minimum`() {
        val starts = now.minus(Duration.ofHours(3)).minus(minutes(0, 600))
        val event = event("recruiting", starts.minus(minutes(0, 600)), starts)
        assertThat(settled(event))
            .withFailMessage("Missed deadline and end dont finish an event with the minimum")
            .isEqualTo("finished")
    }

    @Test
    fun `missed deadline and end cancel an event below the minimum`() {
        val starts = now.minus(Duration.ofHours(3)).minus(minutes(0, 600))
        val event = event("recruiting", starts.minus(minutes(0, 600)), starts)
        jdbc.update("UPDATE events SET min_users = ? WHERE id = ?", Random.nextInt(2, 51), event)
        assertThat(settled(event))
            .withFailMessage("Missed deadline and end dont cancel an event below the minimum")
            .isEqualTo("cancelled_by_system")
    }

    @Test
    fun `repeated settling keeps the finished event`() {
        val starts = now.minus(Duration.ofHours(3)).minus(minutes(0, 600))
        val event = event("recruiting", starts.minus(minutes(0, 600)), starts)
        deadlines.settle()
        assertThat(settled(event))
            .withFailMessage("Repeated settling doesnt keep the finished event")
            .isEqualTo("finished")
    }

    private fun settled(event: UUID): String? {
        deadlines.settle()
        entities.flush()
        return jdbc.queryForObject("SELECT state FROM events WHERE id = ?", String::class.java, event)
    }

    private fun event(state: String, closes: Instant, starts: Instant): UUID =
        Rows(jdbc).organized(Rows(jdbc).user(), state, closes, starts)

    private fun minutes(from: Long, until: Long): Duration = Duration.ofMinutes(Random.nextLong(from, until))

    companion object {
        private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        @JvmStatic
        fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
