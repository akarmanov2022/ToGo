package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.jdbc.core.queryForObject
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@DataJpaTest(showSql = false)
@Import(PostgresConfiguration::class, EventParticipationService::class, EventDeadlineService::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EventParticipationRaceTest(
    @Autowired private val participation: EventParticipationService,
    @Autowired private val deadlines: EventDeadlineService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val transactions: PlatformTransactionManager,
) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `two users racing for the last place fill the event exactly`() {
        val event = event(Instant.now().plus(Duration.ofHours(1)))
        last(event)
        racing({ joined(event, Rows(jdbc).user()) }, { joined(event, Rows(jdbc).user()) })
        assertThat(jdbc.queryForObject<Int>("SELECT count(*) FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Two users racing for the last place dont fill the event exactly")
            .isEqualTo(jdbc.queryForObject<Int>("SELECT max_users FROM events WHERE id = ?", event))
    }

    @Test
    fun `user losing the race for the last place gets event full`() {
        val event = event(Instant.now().plus(Duration.ofHours(1)))
        last(event)
        assertThat(racing({ joined(event, Rows(jdbc).user()) }, { joined(event, Rows(jdbc).user()) }))
            .withFailMessage("User losing the race for the last place doesnt get EVENT_FULL")
            .containsExactlyInAnyOrder(null, ApiError.Code.EVENT_FULL)
    }

    @Test
    fun `joining that waited for the lock past the deadline doesnt add the participant`() {
        val closes = Instant.now().plus(Duration.ofSeconds(2))
        val event = event(closes)
        val user = Rows(jdbc).user()
        held(event, closes) { joined(event, user) }
        assertThat(jdbc.queryForList<UUID>("SELECT user_id FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Joining that waited for the lock past the deadline adds the participant")
            .doesNotContain(user)
    }

    @Test
    fun `joining that waited for the lock past the deadline answers registration closed`() {
        val closes = Instant.now().plus(Duration.ofSeconds(2))
        val event = event(closes)
        assertThat(held(event, closes) { joined(event, Rows(jdbc).user()) })
            .withFailMessage("Joining that waited for the lock past the deadline doesnt answer REGISTRATION_CLOSED")
            .isEqualTo(ApiError.Code.REGISTRATION_CLOSED)
    }

    @Test
    fun `refused joining past the deadline commits the system cancellation`() {
        val closes = Instant.now().plus(Duration.ofSeconds(2))
        val event = event(closes)
        jdbc.update("UPDATE events SET min_users = ? WHERE id = ?", Random.nextInt(2, 51), event)
        held(event, closes) { joined(event, Rows(jdbc).user()) }
        assertThat(jdbc.queryForObject<String>("SELECT state FROM events WHERE id = ?", event))
            .withFailMessage("Refused joining past the deadline doesnt commit the system cancellation")
            .isEqualTo("cancelled_by_system")
    }

    @Test
    fun `leaving that waited for the lock past the deadline keeps the participant`() {
        val closes = Instant.now().plus(Duration.ofSeconds(2))
        val event = event(closes)
        val user = Rows(jdbc).user()
        Rows(jdbc).participant(event, user)
        jdbc.update("UPDATE events SET participants_count = 2 WHERE id = ?", event)
        held(event, closes) { left(event, user) }
        assertThat(jdbc.queryForList<UUID>("SELECT user_id FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Leaving that waited for the lock past the deadline doesnt keep the participant")
            .contains(user)
    }

    @Test
    fun `joining racing with the closing of the registration keeps the participants`() {
        val closes = Instant.now().plus(Duration.ofSeconds(2))
        val event = event(closes)
        held(event, closes) {
            racing(
                { joined(event, Rows(jdbc).user()) },
                {
                    while (!Instant.now().isAfter(closes)) {
                        Thread.sleep(10)
                    }
                    deadlines.settle()
                },
            )
        }
        assertThat(jdbc.queryForObject<Int>("SELECT count(*) FROM event_users WHERE event_id = ?", event))
            .withFailMessage("Joining racing with the closing of the registration changes the participants")
            .isEqualTo(1)
    }

    @Test
    fun `user joining two near events at once joins exactly one`() {
        val user = Rows(jdbc).user()
        val starts = Instant.now().plus(Duration.ofDays(Random.nextLong(1, 20)))
        val first = event(Instant.now().plus(Duration.ofHours(1)), starts)
        val second = event(Instant.now().plus(Duration.ofHours(1)), starts.plus(Duration.ofMinutes(60)))
        racing({ joined(first, user) }, { joined(second, user) })
        assertThat(jdbc.queryForObject<Int>("SELECT count(*) FROM event_users WHERE user_id = ?", user))
            .withFailMessage("User joining two near events at once doesnt join exactly one")
            .isEqualTo(1)
    }

    @Test
    fun `user joining two near events at once gets schedule conflict once`() {
        val user = Rows(jdbc).user()
        val starts = Instant.now().plus(Duration.ofDays(Random.nextLong(1, 20)))
        val first = event(Instant.now().plus(Duration.ofHours(1)), starts)
        val second = event(Instant.now().plus(Duration.ofHours(1)), starts.plus(Duration.ofMinutes(60)))
        assertThat(racing({ joined(first, user) }, { joined(second, user) }))
            .withFailMessage("User joining two near events at once doesnt get SCHEDULE_CONFLICT once")
            .containsExactlyInAnyOrder(null, ApiError.Code.SCHEDULE_CONFLICT)
    }

    private fun event(closes: Instant, starts: Instant = closes.plus(Duration.ofHours(1))): UUID =
        Rows(jdbc).organized(Rows(jdbc).user(), "recruiting", closes, starts)

    private fun last(event: UUID) {
        val maximum = Random.nextInt(2, 51)
        jdbc.update(
            "UPDATE events SET max_users = ?, participants_count = ? WHERE id = ?",
            maximum,
            maximum - 1,
            event,
        )
        repeat(maximum - 2) { Rows(jdbc).participant(event, Rows(jdbc).user()) }
    }

    private fun joined(event: UUID, user: UUID): ApiError.Code? {
        caller.set(user)
        return Failure { participation.join(event) }.code()
    }

    private fun left(event: UUID, user: UUID): ApiError.Code? {
        caller.set(user)
        return Failure { participation.leave(event) }.code()
    }

    private fun racing(vararg calls: () -> Any?): List<Any?> = Executors.newFixedThreadPool(calls.size).use { pool ->
        val start = CyclicBarrier(calls.size)
        calls.map { call ->
            pool.submit<Any?> {
                start.await(5, TimeUnit.SECONDS)
                call()
            }
        }.map { it.get(10, TimeUnit.SECONDS) }
    }

    private fun <T> held(event: UUID, until: Instant, call: () -> T): T = Executors.newFixedThreadPool(2).use { pool ->
        val locked = CountDownLatch(1)
        val lock = pool.submit {
            TransactionTemplate(transactions).executeWithoutResult {
                jdbc.queryForList("SELECT id FROM events WHERE id = ? FOR UPDATE", event)
                locked.countDown()
                while (Instant.now().isBefore(until.plusMillis(200))) {
                    Thread.sleep(10)
                }
            }
        }
        check(locked.await(5, TimeUnit.SECONDS)) { "Event lock was not taken in time" }
        val waiting = pool.submit<T> { call() }
        waited()
        lock.get(10, TimeUnit.SECONDS)
        waiting.get(10, TimeUnit.SECONDS)
    }

    private fun waited() {
        val deadline = Instant.now().plusSeconds(5)
        while (
            jdbc.queryForObject<Int>(
                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'",
            ) == 0 && Instant.now().isBefore(deadline)
        ) {
            Thread.sleep(10)
        }
    }

    companion object {
        private val caller = ThreadLocal<UUID>()

        @JvmStatic
        fun identity(): Identity = Identity { caller.get() }

        @JvmStatic
        fun clock(): Clock = Clock.systemUTC()
    }
}
