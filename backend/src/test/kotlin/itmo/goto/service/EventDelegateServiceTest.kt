package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.UpdateEventRequest
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, EventDelegateService::class, NotificationQueue::class)
class EventDelegateServiceTest(
    @Autowired private val delegateService: EventDelegateService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `organizer cancellation notifies guests only once`() {
        holder()
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        val guest = Rows(jdbc).user()
        Rows(jdbc).participant(event, guest)
        delegateService.cancel(event)
        Failure { delegateService.cancel(event) }.code()
        entities.flush()
        assertThat(jdbc.queryForList<UUID>("SELECT user_id FROM notifications WHERE event_id = ? AND kind = 'cancelled'", event))
            .withFailMessage("Organizer cancellation notified the wrong recipients")
            .containsExactly(guest)
    }

    @Test
    fun `organizer cancels a recruiting event`() {
        holder()
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        assertThat(delegateService.cancel(event).state)
            .withFailMessage("Organizer doesnt cancel a recruiting event")
            .isEqualTo(EventState.cancelled_by_organizer)
    }

    @Test
    fun `organizer cancels an event with closed registration before its start`() {
        holder()
        val event = event(holder, "registration_closed", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        assertThat(delegateService.cancel(event).state)
            .withFailMessage("Organizer doesnt cancel an event with closed registration before its start")
            .isEqualTo(EventState.cancelled_by_organizer)
    }

    @Test
    fun `cancellation stores the cancelled state`() {
        holder()
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        delegateService.cancel(event)
        entities.flush()
        assertThat(jdbc.queryForObject("SELECT state FROM events WHERE id = ?", String::class.java, event))
            .withFailMessage("Cancellation doesnt store the cancelled state")
            .isEqualTo("cancelled_by_organizer")
    }

    @Test
    fun `cancellation keeps the participants`() {
        holder()
        val rows = Rows(jdbc)
        val event = event(holder, "registration_closed", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        val guests = List(Random.nextInt(1, 5)) { rows.user().also { rows.participant(event, it) } }
        delegateService.cancel(event)
        entities.flush()
        assertThat(jdbc.queryForList("SELECT user_id FROM event_users WHERE event_id = ?", UUID::class.java, event))
            .withFailMessage("Cancellation doesnt keep the participants")
            .containsExactlyInAnyOrderElementsOf(guests + holder)
    }

    @Test
    fun `cancellation by another user answers forbidden`() {
        holder()
        val event = event(Rows(jdbc).user(), "recruiting", now.plus(minutes(1, 600)))
        assertThat(Failure { delegateService.cancel(event) }.code())
            .withFailMessage("Cancellation by another user doesnt answer FORBIDDEN")
            .isEqualTo(ApiError.Code.FORBIDDEN)
    }

    @Test
    fun `cancellation after the start answers invalid event state`() {
        holder()
        val starts = now.minus(minutes(1, 600))
        val event = event(holder, "registration_closed", starts.minus(minutes(1, 600)), starts)
        assertThat(Failure { delegateService.cancel(event) }.code())
            .withFailMessage("Cancellation after the start doesnt answer INVALID_EVENT_STATE")
            .isEqualTo(ApiError.Code.INVALID_EVENT_STATE)
    }

    @Test
    fun `cancellation of an event cancelled by the system answers invalid event state`() {
        holder()
        val event = event(holder, "cancelled_by_system", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        assertThat(Failure { delegateService.cancel(event) }.code())
            .withFailMessage("Cancellation of an event cancelled by the system doesnt answer INVALID_EVENT_STATE")
            .isEqualTo(ApiError.Code.INVALID_EVENT_STATE)
    }

    @Test
    fun `repeated cancellation after the start answers the cancelled event`() {
        holder()
        val starts = now.minus(minutes(1, 600))
        val event = event(holder, "cancelled_by_organizer", starts.minus(minutes(1, 600)), starts)
        assertThat(delegateService.cancel(event).state)
            .withFailMessage("Repeated cancellation after the start doesnt answer the cancelled event")
            .isEqualTo(EventState.cancelled_by_organizer)
    }

    @Test
    fun `refused cancellation still stores the state brought up to date`() {
        holder()
        val event = event(Rows(jdbc).user(), "recruiting", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        jdbc.update("UPDATE events SET min_users = 2 WHERE id = ?", event)
        Failure { delegateService.cancel(event) }.code()
        entities.flush()
        assertThat(jdbc.queryForObject("SELECT state FROM events WHERE id = ?", String::class.java, event))
            .withFailMessage("Refused cancellation doesnt store the state brought up to date")
            .isEqualTo("cancelled_by_system")
    }

    @Test
    fun `cancellation of unknown event answers event not found`() {
        holder()
        assertThat(Failure { delegateService.cancel(UUID.randomUUID()) }.code())
            .withFailMessage("Cancellation of an unknown event doesnt answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    @Test
    fun `organizer moves the deadline`() {
        holder()
        val closes = now.plus(minutes(1, 600))
        val event = event(holder, "recruiting", closes, closes.plus(Duration.ofHours(3)))
        val moved = closes.plus(minutes(1, 179))
        assertThat(
            delegateService.change(event, UpdateEventRequest(registrationClosesAt = moved.atOffset(ZoneOffset.UTC)))
                .registrationClosesAt.toInstant(),
        )
            .withFailMessage("Organizer doesnt move the deadline")
            .isEqualTo(moved)
    }

    @Test
    fun `moving the deadline keeps the start`() {
        holder()
        val closes = now.plus(minutes(1, 600))
        val starts = closes.plus(Duration.ofHours(3))
        val event = event(holder, "recruiting", closes, starts)
        delegateService.change(event, UpdateEventRequest(registrationClosesAt = starts.atOffset(ZoneOffset.UTC)))
        entities.flush()
        assertThat(
            jdbc.queryForObject("SELECT starts_at FROM events WHERE id = ?", OffsetDateTime::class.java, event)
                ?.toInstant(),
        )
            .withFailMessage("Moving the deadline doesnt keep the start")
            .isEqualTo(starts)
    }

    @Test
    fun `moving the deadline keeps the end`() {
        holder()
        val closes = now.plus(minutes(1, 600))
        val starts = closes.plus(Duration.ofHours(3))
        val event = event(holder, "recruiting", closes, starts)
        val ends = jdbc.queryForObject("SELECT ends_at FROM events WHERE id = ?", OffsetDateTime::class.java, event)
        assertThat(
            delegateService.change(event, UpdateEventRequest(registrationClosesAt = starts.atOffset(ZoneOffset.UTC)))
                .endsAt,
        )
            .withFailMessage("Moving the deadline doesnt keep the end")
            .isEqualTo(ends)
    }

    @Test
    fun `organizer renames the event`() {
        holder()
        val title = "Событие ${Random.nextInt()}"
        assertThat(
            delegateService.change(event(holder, "recruiting", now.plus(minutes(1, 600))), UpdateEventRequest(title))
                .title,
        )
            .withFailMessage("Organizer doesnt rename the event")
            .isEqualTo(title)
    }

    @Test
    fun `renaming keeps the location`() {
        holder()
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        val location = jdbc.queryForObject("SELECT location FROM events WHERE id = ?", String::class.java, event)
        assertThat(delegateService.change(event, UpdateEventRequest("Событие ${Random.nextInt()}")).location)
            .withFailMessage("Renaming doesnt keep the location")
            .isEqualTo(location)
    }

    @Test
    fun `organizer changes the description`() {
        holder()
        val description = "Описание ${Random.nextInt()}"
        assertThat(
            delegateService.change(
                event(holder, "recruiting", now.plus(minutes(1, 600))),
                UpdateEventRequest(description = description),
            ).description,
        )
            .withFailMessage("Organizer doesnt change the description")
            .isEqualTo(description)
    }

    @Test
    fun `organizer changes the icon`() {
        holder()
        val icon = listOf("🎲", "🎬", "🏃", "☕").random()
        assertThat(
            delegateService.change(
                event(holder, "recruiting", now.plus(minutes(1, 600))),
                UpdateEventRequest(icon = icon),
            )
                .icon,
        )
            .withFailMessage("Organizer doesnt change the icon")
            .isEqualTo(icon)
    }

    @Test
    fun `empty icon removes the icon`() {
        holder()
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        jdbc.update("UPDATE events SET icon = ? WHERE id = ?", "🎲", event)
        assertThat(delegateService.change(event, UpdateEventRequest(icon = "")).icon)
            .withFailMessage("Empty icon doesnt remove the icon")
            .isNull()
    }

    @Test
    fun `change without icon keeps the icon`() {
        holder()
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        val icon = listOf("🎲", "🎬", "🏃", "☕").random()
        jdbc.update("UPDATE events SET icon = ? WHERE id = ?", icon, event)
        assertThat(delegateService.change(event, UpdateEventRequest("Событие ${Random.nextInt()}")).icon)
            .withFailMessage("Change without an icon doesnt keep the icon")
            .isEqualTo(icon)
    }

    @Test
    fun `organizer changes the limits`() {
        holder()
        val minimum = Random.nextInt(1, 50)
        assertThat(
            delegateService.change(
                event(holder, "recruiting", now.plus(minutes(1, 600))),
                UpdateEventRequest(minUsers = minimum, maxUsers = 50),
            ).minUsers,
        )
            .withFailMessage("Organizer doesnt change the limits")
            .isEqualTo(minimum)
    }

    @Test
    fun `organizer changes the category`() {
        holder()
        val category = EventCategory.entries.random()
        assertThat(
            delegateService.change(
                event(holder, "recruiting", now.plus(minutes(1, 600))),
                UpdateEventRequest(category = category),
            ).category,
        )
            .withFailMessage("Organizer doesnt change the category")
            .isEqualTo(category)
    }

    @Test
    fun `change after the deadline answers registration closed`() {
        holder()
        val event = event(holder, "recruiting", now.minus(minutes(1, 600)), now.plus(minutes(60, 600)))
        assertThat(
            Failure {
                delegateService.change(
                    event,
                    UpdateEventRequest(registrationClosesAt = now.plus(minutes(1, 10)).atOffset(ZoneOffset.UTC)),
                )
            }.code(),
        )
            .withFailMessage("Change after the deadline doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `renaming after the deadline answers registration closed`() {
        holder()
        val event = event(holder, "recruiting", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        assertThat(Failure { delegateService.change(event, UpdateEventRequest("Событие ${Random.nextInt()}")) }.code())
            .withFailMessage("Renaming after the deadline doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `refused change keeps the registration closed`() {
        holder()
        val event = event(holder, "recruiting", now.minus(minutes(1, 600)), now.plus(minutes(1, 600)))
        Failure { delegateService.change(event, UpdateEventRequest("Событие ${Random.nextInt()}")) }.code()
        entities.flush()
        assertThat(jdbc.queryForObject("SELECT state FROM events WHERE id = ?", String::class.java, event))
            .withFailMessage("Refused change doesnt keep the registration closed")
            .isEqualTo("registration_closed")
    }

    @Test
    fun `change of a cancelled event answers invalid event state`() {
        holder()
        val event = event(holder, "cancelled_by_organizer", now.plus(minutes(1, 600)))
        assertThat(Failure { delegateService.change(event, UpdateEventRequest("Событие ${Random.nextInt()}")) }.code())
            .withFailMessage("Change of a cancelled event doesnt answer INVALID_EVENT_STATE")
            .isEqualTo(ApiError.Code.INVALID_EVENT_STATE)
    }

    @Test
    fun `maximum below the participants answers capacity below participants`() {
        holder()
        val count = Random.nextInt(2, 51)
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        jdbc.update("UPDATE events SET participants_count = ? WHERE id = ?", count, event)
        assertThat(Failure { delegateService.change(event, UpdateEventRequest(maxUsers = count - 1)) }.code())
            .withFailMessage("Maximum below the participants doesnt answer CAPACITY_BELOW_PARTICIPANTS")
            .isEqualTo(ApiError.Code.CAPACITY_BELOW_PARTICIPANTS)
    }

    @Test
    fun `minimum above the kept maximum names the minimum`() {
        holder()
        val event = event(holder, "recruiting", now.plus(minutes(1, 600)))
        val maximum = jdbc.queryForObject("SELECT max_users FROM events WHERE id = ?", Int::class.java, event)!!
        assertThat(Failure { delegateService.change(event, UpdateEventRequest(minUsers = maximum + 1)) }.fields())
            .withFailMessage("Minimum above the kept maximum doesnt name minUsers")
            .containsExactly("minUsers")
    }

    @Test
    fun `deadline moved after the start names the deadline`() {
        holder()
        val closes = now.plus(minutes(1, 600))
        val event = event(holder, "recruiting", closes, closes.plus(Duration.ofHours(2)))
        assertThat(
            Failure {
                delegateService.change(
                    event,
                    UpdateEventRequest(
                        registrationClosesAt = closes.plus(Duration.ofHours(3)).atOffset(ZoneOffset.UTC),
                    ),
                )
            }.fields(),
        )
            .withFailMessage("Deadline moved after the start doesnt name registrationClosesAt")
            .containsExactly("registrationClosesAt")
    }

    @Test
    fun `empty change gives validation error`() {
        holder()
        assertThat(
            Failure {
                delegateService.change(event(holder, "recruiting", now.plus(minutes(1, 600))), UpdateEventRequest())
            }.code(),
        )
            .withFailMessage("Empty change doesnt give VALIDATION_ERROR")
            .isEqualTo(ApiError.Code.VALIDATION_ERROR)
    }

    @Test
    fun `change by another user answers forbidden`() {
        holder()
        val event = event(Rows(jdbc).user(), "recruiting", now.plus(minutes(1, 600)))
        assertThat(Failure { delegateService.change(event, UpdateEventRequest("Событие ${Random.nextInt()}")) }.code())
            .withFailMessage("Change by another user doesnt answer FORBIDDEN")
            .isEqualTo(ApiError.Code.FORBIDDEN)
    }

    @Test
    fun `change by a participant answers forbidden`() {
        holder()
        val event = event(Rows(jdbc).user(), "recruiting", now.plus(minutes(1, 600)))
        Rows(jdbc).participant(event, holder)
        assertThat(Failure { delegateService.change(event, UpdateEventRequest("Событие ${Random.nextInt()}")) }.code())
            .withFailMessage("Change by a participant doesnt answer FORBIDDEN")
            .isEqualTo(ApiError.Code.FORBIDDEN)
    }

    @Test
    fun `change of unknown event answers event not found`() {
        holder()
        assertThat(
            Failure {
                delegateService.change(UUID.randomUUID(), UpdateEventRequest("Событие ${Random.nextInt()}"))
            }.code(),
        )
            .withFailMessage("Change of an unknown event doesnt answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    private fun holder(): UUID = Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)

    private fun event(organizer: UUID, state: String, closes: Instant, starts: Instant = closes.plus(minutes(0, 600))) =
        Rows(jdbc).organized(organizer, state, closes, starts)

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
