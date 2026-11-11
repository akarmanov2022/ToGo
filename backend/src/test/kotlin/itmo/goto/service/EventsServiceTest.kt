package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.Event
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.UpdateEventRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.bean.override.convention.TestBean
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

@DataJpaTest(showSql = false)
@Import(
    PostgresConfiguration::class,
    EventsService::class,
    EventCreationService::class,
    EventDelegateService::class,
    EventParticipationService::class,
    EventDiscoveryService::class,
    EventDeadlineService::class,
    NotificationQueue::class,
)
class EventsServiceTest(@Autowired private val service: EventsService, @Autowired private val jdbc: JdbcTemplate) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `event creation is handed over to creation`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600))).atOffset(ZoneOffset.UTC)
        assertThat(
            service.createEvent(
                CreateEventRequest(
                    "Событие ${Random.nextInt()}",
                    "Описание ${Random.nextInt()}",
                    "Место ${Random.nextInt()}",
                    closes.plusHours(1),
                    closes.plusHours(3),
                    closes,
                    1,
                    Random.nextInt(1, 51),
                    EventCategory.entries.random(),
                ),
            ).organizer.id,
        )
            .withFailMessage("Event creation isnt handed over to creation")
            .isEqualTo(holder)
    }

    @Test
    fun `event change is handed over to the delegate service`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        val title = "Событие ${Random.nextInt()}"
        assertThat(
            service.updateEvent(
                Rows(jdbc).organized(holder, "recruiting", closes, closes.plus(Duration.ofHours(1))),
                UpdateEventRequest(title),
            ).title,
        )
            .withFailMessage("Event change isnt handed over to the delegate service")
            .isEqualTo(title)
    }

    @Test
    fun `event cancellation is handed over to the delegate service`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        assertThat(
            service.cancelEvent(Rows(jdbc).organized(holder, "recruiting", closes, closes.plus(Duration.ofHours(1))))
                .state,
        )
            .withFailMessage("Event cancellation isnt handed over to the delegate service")
            .isEqualTo(EventState.cancelled_by_organizer)
    }

    @Test
    fun `event listing is handed over to discovery`() {
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        val event = Rows(jdbc).organized(Rows(jdbc).user(), "recruiting", closes, closes)
        assertThat(listed())
            .withFailMessage("Event listing isnt handed over to discovery")
            .containsExactly(event)
    }

    @Test
    fun `event details are handed over to discovery`() {
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        val event = Rows(jdbc).organized(Rows(jdbc).user(), "recruiting", closes, closes)
        assertThat(service.getEvent(event).id)
            .withFailMessage("Event details arent handed over to discovery")
            .isEqualTo(event)
    }

    @Test
    fun `listing with start bounds out of order names starts from`() {
        val from = now.plus(Duration.ofMinutes(Random.nextLong(-10_000, 10_000))).atOffset(ZoneOffset.UTC)
        assertThat(
            Failure {
                service.getEvents(
                    null,
                    null,
                    from,
                    from.minusMinutes(Random.nextLong(0, 10_000)),
                    null,
                    false,
                    false,
                    20,
                    0,
                )
            }.fields(),
        )
            .withFailMessage("Listing with start bounds out of order doesnt name startsFrom")
            .containsExactly("startsFrom")
    }

    @Test
    fun `listing leaves out events whose title lacks the query`() {
        val word = "настолок${Random.nextInt(1000)}"
        titled("Вечер ${word.uppercase()}")
        val other = titled("Пробежка ${Random.nextInt(1000)}")
        assertThat(listed(query = "  $word "))
            .withFailMessage("Listing doesnt leave out events whose title lacks the query")
            .doesNotContain(other)
    }

    @Test
    fun `listing with blank query keeps every event`() {
        val event = titled("Событие ${Random.nextInt(1000)}")
        assertThat(listed(query = " ".repeat(Random.nextInt(0, 5))))
            .withFailMessage("Listing with a blank query doesnt keep every event")
            .containsExactly(event)
    }

    @Test
    fun `listing leaves out events of another category`() {
        val category = EventCategory.entries.random()
        val other = upcoming()
        jdbc.update(
            "UPDATE events SET category = ? WHERE id = ?",
            (EventCategory.entries - category).random().name,
            other,
        )
        assertThat(listed(category = category))
            .withFailMessage("Listing doesnt leave out events of another category")
            .doesNotContain(other)
    }

    @Test
    fun `listing leaves out events starting before starts from`() {
        val event = upcoming()
        val starts = jdbc.queryForObject<OffsetDateTime>("SELECT starts_at FROM events WHERE id = ?", event)
        assertThat(listed(startsFrom = starts?.plusSeconds(Random.nextLong(1, 10_000))))
            .withFailMessage("Listing doesnt leave out events starting before startsFrom")
            .doesNotContain(event)
    }

    @Test
    fun `listing leaves out events starting at starts to`() {
        val event = upcoming()
        val starts = jdbc.queryForObject<OffsetDateTime>("SELECT starts_at FROM events WHERE id = ?", event)
        assertThat(listed(startsTo = starts))
            .withFailMessage("Listing doesnt leave out events starting at startsTo")
            .doesNotContain(event)
    }

    @Test
    fun `listing leaves out events in another state`() {
        val event = upcoming()
        assertThat(listed(state = listOf(EventState.cancelled_by_organizer, EventState.finished).random()))
            .withFailMessage("Listing doesnt leave out events in another state")
            .doesNotContain(event)
    }

    @Test
    fun `listing with free places leaves out full events`() {
        val event = upcoming()
        jdbc.update("UPDATE events SET max_users = 1 WHERE id = ?", event)
        assertThat(service.getEvents(null, null, null, null, null, false, true, 100, 0).items.map { it.id })
            .withFailMessage("Listing with free places doesnt leave out full events")
            .doesNotContain(event)
    }

    @Test
    fun `listing combines category and state`() {
        val recruiting = upcoming()
        val cancelled = upcoming()
        jdbc.update("UPDATE events SET category = 'RUN'")
        jdbc.update("UPDATE events SET state = 'cancelled_by_system' WHERE id = ?", cancelled)
        assertThat(listed(category = EventCategory.RUN, state = EventState.recruiting))
            .withFailMessage("Listing doesnt combine category and state")
            .containsExactly(recruiting)
    }

    @Test
    fun `my events hold organized, joined and past events in order of start`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val tomorrow = now.plus(Duration.ofDays(1))
        val organized = Rows(jdbc).organized(holder, "recruiting", tomorrow, tomorrow)
        val joined = joined("recruiting", tomorrow.plus(Duration.ofDays(1)))
        val past = joined("finished", now.minus(Duration.ofDays(1)))
        upcoming()
        assertThat(mine().map { it.id })
            .withFailMessage("My events dont hold organized, joined and past events in order of start")
            .containsExactly(past, organized, joined)
    }

    @Test
    fun `my events mark the organized event`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        Rows(jdbc).organized(holder, "recruiting", closes, closes)
        assertThat(mine().single().isOrganizer)
            .withFailMessage("My events dont mark the organized event")
            .isTrue()
    }

    @Test
    fun `my events dont mark a joined event as organized`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        joined("recruiting", now.plus(Duration.ofMinutes(Random.nextLong(1, 600))))
        assertThat(mine().single().isOrganizer)
            .withFailMessage("My events mark a joined event as organized")
            .isFalse()
    }

    @Test
    fun `my events leave out the event the caller left`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val event = upcoming()
        service.joinEvent(event)
        service.leaveEvent(event)
        assertThat(mine().map { it.id })
            .withFailMessage("My events dont leave out the event the caller left")
            .doesNotContain(event)
    }

    @Test
    fun `participant listing is handed over to participation`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        assertThat(
            service.getEventParticipants(
                Rows(jdbc).organized(holder, "recruiting", closes, closes.plus(Duration.ofHours(1))),
                Random.nextInt(1, 100),
                0,
            ).items.map { it.id },
        )
            .withFailMessage("Participant listing isnt handed over to participation")
            .containsExactly(holder)
    }

    @Test
    fun `joining is handed over to participation`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        assertThat(
            service.joinEvent(
                Rows(jdbc).organized(Rows(jdbc).user(), "recruiting", closes, closes.plus(Duration.ofHours(1))),
            ).isParticipant,
        )
            .withFailMessage("Joining isnt handed over to participation")
            .isTrue()
    }

    @Test
    fun `leaving is handed over to participation`() {
        Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        assertThat(
            Failure {
                service.leaveEvent(Rows(jdbc).organized(holder, "recruiting", closes, closes.plus(Duration.ofHours(1))))
            }.code(),
        )
            .withFailMessage("Leaving isnt handed over to participation")
            .isEqualTo(ApiError.Code.ORGANIZER_CANNOT_LEAVE)
    }

    private fun listed(
        query: String? = null,
        category: EventCategory? = null,
        startsFrom: OffsetDateTime? = null,
        startsTo: OffsetDateTime? = null,
        state: EventState? = null,
    ): List<UUID> = service.getEvents(query, category, startsFrom, startsTo, state, false, false, 100, 0).items.map {
        it.id
    }

    private fun mine(): List<Event> = service.getEvents(null, null, null, null, null, true, false, 100, 0).items

    private fun upcoming(): UUID {
        val closes = now.plus(Duration.ofMinutes(Random.nextLong(1, 600)))
        return Rows(jdbc).organized(Rows(jdbc).user(), "recruiting", closes, closes.plus(Duration.ofHours(1)))
    }

    private fun titled(title: String): UUID = upcoming().also {
        jdbc.update("UPDATE events SET title = ? WHERE id = ?", title, it)
    }

    private fun joined(state: String, starts: Instant): UUID =
        Rows(jdbc).organized(Rows(jdbc).user(), state, starts, starts).also { Rows(jdbc).participant(it, holder) }

    companion object {
        private val holder = UUID.randomUUID()

        private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        @JvmStatic
        fun identity(): Identity = Identity { holder }

        @JvmStatic
        fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
