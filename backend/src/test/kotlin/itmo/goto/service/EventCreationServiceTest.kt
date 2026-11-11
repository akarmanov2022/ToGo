package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventState
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
@Import(PostgresConfiguration::class, EventCreationService::class, NotificationQueue::class)
class EventCreationServiceTest(
    @Autowired private val creation: EventCreationService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `creation schedules only future reminders`() {
        holder()
        val event = creation.event(request(now.plus(Duration.ofHours(1)), now.plus(Duration.ofHours(5)))).id
        entities.flush()
        assertThat(jdbc.queryForList<String>("SELECT kind FROM notifications WHERE event_id = ?", event))
            .withFailMessage("Creation did not schedule only the future hour reminder")
            .containsExactly("reminder_hour")
    }

    @Test
    fun `created event is organized by the creator`() {
        holder()
        assertThat(creation.event(request()).organizer.id)
            .withFailMessage("Created event isnt organized by the creator")
            .isEqualTo(holder)
    }

    @Test
    fun `created event counts the organizer as its only participant`() {
        holder()
        assertThat(creation.event(request()).participantsCount)
            .withFailMessage("Created event doesnt count the organizer as its only participant")
            .isEqualTo(1)
    }

    @Test
    fun `created event stores the organizer participation`() {
        holder()
        val event = creation.event(request()).id
        entities.flush()
        assertThat(
            jdbc.queryForList("SELECT user_id FROM event_users WHERE event_id = ?", UUID::class.java, event),
        )
            .withFailMessage("Created event doesnt store the organizer participation")
            .containsExactly(holder)
    }

    @Test
    fun `created event is recruiting`() {
        holder()
        assertThat(creation.event(request()).state)
            .withFailMessage("Created event isnt recruiting")
            .isEqualTo(EventState.recruiting)
    }

    @Test
    fun `created event leaves all places but one free`() {
        holder()
        val maximum = Random.nextInt(1, 51)
        assertThat(creation.event(request().copy(maxUsers = maximum)).availableSlots)
            .withFailMessage("Created event doesnt leave all places but the organizer one free")
            .isEqualTo(maximum - 1)
    }

    @Test
    fun `created event marks its creator as organizer`() {
        holder()
        assertThat(creation.event(request()).isOrganizer)
            .withFailMessage("Created event doesnt mark its creator as organizer")
            .isTrue()
    }

    @Test
    fun `created event marks its creator as participant`() {
        holder()
        assertThat(creation.event(request()).isParticipant)
            .withFailMessage("Created event doesnt mark its creator as participant")
            .isTrue()
    }

    @Test
    fun `created event keeps the deadline set by the organizer`() {
        holder()
        val closes = now.plus(minutes(60, 600))
        assertThat(creation.event(request(closes, closes.plus(minutes(60, 600)))).registrationClosesAt.toInstant())
            .withFailMessage("Created event doesnt keep the deadline set by the organizer")
            .isEqualTo(closes)
    }

    @Test
    fun `created event accepts a deadline at the start`() {
        holder()
        val closes = now.plus(minutes(1, 600))
        assertThat(creation.event(request(closes, closes)).registrationClosesAt.toInstant())
            .withFailMessage("Created event doesnt accept a deadline at the start")
            .isEqualTo(closes)
    }

    @Test
    fun `created event answers its dates in utc`() {
        holder()
        val closes = now.plus(minutes(1, 600)).atOffset(ZoneOffset.ofHours(Random.nextInt(1, 12)))
        assertThat(
            creation.event(
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
            ).startsAt.offset,
        )
            .withFailMessage("Created event doesnt answer its dates in UTC")
            .isEqualTo(ZoneOffset.UTC)
    }

    @Test
    fun `created event without point has no latitude`() {
        holder()
        assertThat(creation.event(request()).latitude)
            .withFailMessage("Created event without a point has a latitude")
            .isNull()
    }

    @Test
    fun `created event keeps its point`() {
        holder()
        val latitude = Random.nextDouble(-90.0, 90.0)
        assertThat(
            creation.event(request().copy(latitude = latitude, longitude = Random.nextDouble(-180.0, 180.0))).latitude,
        )
            .withFailMessage("Created event doesnt keep its point")
            .isEqualTo(latitude)
    }

    @Test
    fun `created event with empty icon has no icon`() {
        holder()
        assertThat(creation.event(request().copy(icon = "")).icon)
            .withFailMessage("Created event with an empty icon has an icon")
            .isNull()
    }

    @Test
    fun `created event keeps its icon`() {
        holder()
        val icon = listOf("🎲", "🎬", "🏃", "☕").random()
        assertThat(creation.event(request().copy(icon = icon)).icon)
            .withFailMessage("Created event doesnt keep its icon")
            .isEqualTo(icon)
    }

    @Test
    fun `creation with deadline at the current moment names the deadline`() {
        holder()
        assertThat(Failure { creation.event(request(now, now.plus(minutes(1, 600)))) }.fields())
            .withFailMessage("Creation with a deadline at the current moment doesnt name registrationClosesAt")
            .containsExactly("registrationClosesAt")
    }

    @Test
    fun `creation with deadline after the start names the deadline`() {
        holder()
        val starts = now.plus(minutes(1, 600))
        assertThat(Failure { creation.event(request(starts.plus(minutes(1, 600)), starts)) }.fields())
            .withFailMessage("Creation with a deadline after the start doesnt name registrationClosesAt")
            .containsExactly("registrationClosesAt")
    }

    @Test
    fun `creation with start beyond a month names the start`() {
        holder()
        val starts = now.atOffset(ZoneOffset.UTC).plusMonths(1).plusDays(1).toInstant()
        assertThat(Failure { creation.event(request(now.plus(minutes(1, 600)), starts)) }.fields())
            .withFailMessage("Creation with a start beyond a month doesnt name startsAt")
            .containsExactly("startsAt")
    }

    @Test
    fun `creation with end at the start names the end`() {
        holder()
        val starts = now.plus(minutes(60, 600))
        assertThat(
            Failure {
                creation.event(request(now.plus(minutes(1, 59)), starts).copy(endsAt = starts.atOffset(ZoneOffset.UTC)))
            }.fields(),
        )
            .withFailMessage("Creation with an end at the start doesnt name endsAt")
            .containsExactly("endsAt")
    }

    @Test
    fun `creation with minimum above maximum names the minimum`() {
        holder()
        val maximum = Random.nextInt(1, 50)
        assertThat(Failure { creation.event(request().copy(minUsers = maximum + 1, maxUsers = maximum)) }.fields())
            .withFailMessage("Creation with a minimum above the maximum doesnt name minUsers")
            .containsExactly("minUsers")
    }

    @Test
    fun `creation with latitude alone names the longitude`() {
        holder()
        assertThat(Failure { creation.event(request().copy(latitude = Random.nextDouble(-90.0, 90.0))) }.fields())
            .withFailMessage("Creation with a latitude alone doesnt name longitude")
            .containsExactly("longitude")
    }

    @Test
    fun `creation with longitude alone names the latitude`() {
        holder()
        assertThat(Failure { creation.event(request().copy(longitude = Random.nextDouble(-180.0, 180.0))) }.fields())
            .withFailMessage("Creation with a longitude alone doesnt name latitude")
            .containsExactly("latitude")
    }

    @Test
    fun `creation near another event of the organizer answers schedule conflict`() {
        holder()
        val starts = now.plus(Duration.ofDays(Random.nextLong(1, 20)))
        val other = Rows(jdbc).organized(Rows(jdbc).user(), "recruiting", starts.minus(Duration.ofHours(1)), starts)
        Rows(jdbc).participant(other, holder)
        assertThat(
            Failure { creation.event(request(now.plus(minutes(1, 60)), starts.plus(minutes(-179, 179)))) }.code(),
        )
            .withFailMessage("Creation near another event of the organizer doesnt answer SCHEDULE_CONFLICT")
            .isEqualTo(ApiError.Code.SCHEDULE_CONFLICT)
    }

    @Test
    fun `creation near a cancelled event of the organizer succeeds`() {
        holder()
        val starts = now.plus(Duration.ofDays(Random.nextLong(1, 20)))
        val other = Rows(jdbc).organized(
            Rows(jdbc).user(),
            listOf("cancelled_by_organizer", "cancelled_by_system").random(),
            starts.minus(Duration.ofHours(1)),
            starts,
        )
        Rows(jdbc).participant(other, holder)
        assertThat(creation.event(request(now.plus(minutes(1, 60)), starts)).state)
            .withFailMessage("Creation near a cancelled event of the organizer doesnt succeed")
            .isEqualTo(EventState.recruiting)
    }

    @Test
    fun `creation three hours from another event succeeds`() {
        holder()
        val starts = now.plus(Duration.ofDays(Random.nextLong(1, 20)))
        Rows(
            jdbc,
        ).participant(
            Rows(jdbc).organized(Rows(jdbc).user(), "recruiting", starts.minus(Duration.ofHours(1)), starts),
            holder,
        )
        assertThat(creation.event(request(now.plus(minutes(1, 60)), starts.plus(Duration.ofHours(3)))).state)
            .withFailMessage("Creation three hours from another event doesnt succeed")
            .isEqualTo(EventState.recruiting)
    }

    @Test
    fun `creation by unknown token holder answers unauthorized`() {
        assertThat(Failure { creation.event(request()) }.code())
            .withFailMessage("Creation by an unknown token holder doesnt answer UNAUTHORIZED")
            .isEqualTo(ApiError.Code.UNAUTHORIZED)
    }

    private fun holder(): UUID = Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)

    private fun request(
        closes: Instant = now.plus(minutes(1, 600)),
        starts: Instant = closes.plus(minutes(0, 600)),
    ): CreateEventRequest = CreateEventRequest(
        "Событие ${Random.nextInt()}",
        "Описание ${Random.nextInt()}",
        "Место ${Random.nextInt()}",
        starts.atOffset(ZoneOffset.UTC),
        starts.plus(minutes(1, 600)).atOffset(ZoneOffset.UTC),
        closes.atOffset(ZoneOffset.UTC),
        1,
        Random.nextInt(1, 51),
        EventCategory.entries.random(),
    )

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
