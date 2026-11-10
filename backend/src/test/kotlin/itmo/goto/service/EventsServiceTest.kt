package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.EventState
import itmo.goto.generated.model.UpdateEventRequest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowableOfType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.web.server.ResponseStatusException
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
    fun `event listing answers not implemented`() {
        val from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(Random.nextLong(1, 30))
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                service.getEvents(
                    "запрос ${Random.nextInt()}",
                    EventCategory.entries.random(),
                    from,
                    from.plusDays(Random.nextLong(1, 30)),
                    EventState.entries.random(),
                    Random.nextBoolean(),
                    Random.nextBoolean(),
                    Random.nextInt(1, 100),
                    Random.nextInt(0, 1000),
                )
            }?.statusCode,
        )
            .withFailMessage("Event listing doesnt answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
    }

    @Test
    fun `event details answer not implemented`() {
        assertThat(
            catchThrowableOfType(ResponseStatusException::class.java) {
                service.getEvent(UUID.randomUUID())
            }?.statusCode,
        )
            .withFailMessage("Event details dont answer 501")
            .isEqualTo(HttpStatus.NOT_IMPLEMENTED)
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

    companion object {
        private val holder = UUID.randomUUID()

        private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        @JvmStatic
        fun identity(): Identity = Identity { holder }

        @JvmStatic
        fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
