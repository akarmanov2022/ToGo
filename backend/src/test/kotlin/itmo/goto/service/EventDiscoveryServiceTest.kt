package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.EventState
import itmo.goto.repository.Staged
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
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
@Import(PostgresConfiguration::class, EventDiscoveryService::class, EventDeadlineService::class)
class EventDiscoveryServiceTest(
    @Autowired private val discovery: EventDiscoveryService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `listing without filters returns events of every state`() {
        val events = listOf(
            event("recruiting", now.plus(minutes(1, 600))),
            event("finished", now.minus(Duration.ofDays(2))),
            event(listOf("cancelled_by_organizer", "cancelled_by_system").random(), now.plus(minutes(-600, 600))),
        )
        assertThat(discovery.events(emptyList(), 100, 0).items.map { it.id })
            .withFailMessage("Listing without filters doesnt return events of every state")
            .containsExactlyInAnyOrderElementsOf(events)
    }

    @Test
    fun `listing orders events by start`() {
        val later = event("recruiting", now.plus(Duration.ofDays(3)))
        val sooner = event("recruiting", now.plus(Duration.ofDays(1)))
        assertThat(discovery.events(emptyList(), 100, 0).items.map { it.id })
            .withFailMessage("Listing doesnt order events by start")
            .containsExactly(sooner, later)
    }

    @Test
    fun `listing orders events with the same start by id`() {
        val closes = now.plus(minutes(1, 600))
        val events = List(Random.nextInt(2, 6)) { event("recruiting", closes, closes.plus(Duration.ofHours(1))) }
        assertThat(discovery.events(emptyList(), 100, 0).items.map { it.id })
            .withFailMessage("Listing doesnt order events with the same start by id")
            .containsExactlyElementsOf(events.sortedBy { it.toString() })
    }

    @Test
    fun `listing page holds the events after the offset`() {
        val events = List(25) { event("recruiting", hour(it), hour(it)) }
        assertThat(discovery.events(emptyList(), 10, 20).items.map { it.id })
            .withFailMessage("Listing page doesnt hold the events after the offset")
            .containsExactlyElementsOf(events.drop(20))
    }

    @Test
    fun `listing page starts at an offset that isnt a multiple of the limit`() {
        val events = List(Random.nextInt(5, 12)) { event("recruiting", hour(it), hour(it)) }
        val offset = Random.nextInt(1, 4)
        assertThat(discovery.events(emptyList(), 2, offset).items.map { it.id })
            .withFailMessage("Listing page doesnt start at an offset that isnt a multiple of the limit")
            .containsExactlyElementsOf(events.subList(offset, offset + 2))
    }

    @Test
    fun `listing total counts events before paging`() {
        val count = Random.nextInt(3, 30)
        repeat(count) { event("recruiting", hour(it), hour(it)) }
        assertThat(discovery.events(emptyList(), Random.nextInt(1, 3), Random.nextInt(0, 3)).total)
            .withFailMessage("Listing total doesnt count events before paging")
            .isEqualTo(count.toLong())
    }

    @Test
    fun `listing total counts filtered events`() {
        val count = Random.nextInt(1, 10)
        repeat(count) { event("recruiting", hour(it), hour(it)) }
        repeat(Random.nextInt(1, 10)) { event("cancelled_by_organizer", hour(it), hour(it)) }
        assertThat(discovery.events(listOf(Staged(EventState.recruiting)), 100, 0).total)
            .withFailMessage("Listing total doesnt count filtered events")
            .isEqualTo(count.toLong())
    }

    @Test
    fun `listing without matching events returns no items`() {
        event("cancelled_by_organizer", now.plus(minutes(1, 600)))
        assertThat(discovery.events(listOf(Staged(EventState.finished)), 100, 0).items)
            .withFailMessage("Listing without matching events returns items")
            .isEmpty()
    }

    @Test
    fun `listing shows closed registration when background processing is late`() {
        event("recruiting", now.minus(minutes(0, 600)), now.plus(minutes(1, 600)))
        assertThat(discovery.events(emptyList(), 100, 0).items.map { it.state })
            .withFailMessage("Listing doesnt show closed registration when background processing is late")
            .containsExactly(EventState.registration_closed)
    }

    @Test
    fun `state filter applies to the current state`() {
        val event = event("recruiting", now.minus(minutes(0, 600)), now.plus(minutes(1, 600)))
        assertThat(discovery.events(listOf(Staged(EventState.registration_closed)), 100, 0).items.map { it.id })
            .withFailMessage("State filter doesnt apply to the current state")
            .containsExactly(event)
    }

    @Test
    fun `listing marks events the caller takes part in`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        Rows(jdbc).participant(event, holder)
        assertThat(discovery.events(emptyList(), 100, 0).items.single().isParticipant)
            .withFailMessage("Listing doesnt mark events the caller takes part in")
            .isTrue()
    }

    @Test
    fun `listing doesnt mark events the caller stays out of`() {
        holder()
        event("recruiting", now.plus(minutes(1, 600)))
        assertThat(discovery.events(emptyList(), 100, 0).items.single().isParticipant)
            .withFailMessage("Listing marks events the caller stays out of")
            .isFalse()
    }

    @Test
    fun `details hold the organizer`() {
        val organizer = Rows(jdbc).user()
        val closes = now.plus(minutes(1, 600))
        assertThat(discovery.event(Rows(jdbc).organized(organizer, "recruiting", closes, closes)).organizer.id)
            .withFailMessage("Details dont hold the organizer")
            .isEqualTo(organizer)
    }

    @Test
    fun `details hold the point on the map`() {
        val latitude = Random.nextDouble(-90.0, 90.0)
        val event = Rows(jdbc).event(point = latitude to Random.nextDouble(-180.0, 180.0))
        assertThat(discovery.event(event).latitude)
            .withFailMessage("Details dont hold the point on the map")
            .isEqualTo(latitude)
    }

    @Test
    fun `details count the free places`() {
        val event = event("recruiting", now.plus(minutes(1, 600)))
        val count = Random.nextInt(1, 50)
        jdbc.update("UPDATE events SET participants_count = ? WHERE id = ?", count, event)
        assertThat(discovery.event(event).availableSlots)
            .withFailMessage("Details dont count the free places")
            .isEqualTo(50 - count)
    }

    @Test
    fun `details mark the organizer as the caller`() {
        holder()
        val closes = now.plus(minutes(1, 600))
        assertThat(discovery.event(Rows(jdbc).organized(holder, "recruiting", closes, closes)).isOrganizer)
            .withFailMessage("Details dont mark the organizer as the caller")
            .isTrue()
    }

    @Test
    fun `details mark the participation of the caller`() {
        holder()
        val event = event("recruiting", now.plus(minutes(1, 600)))
        Rows(jdbc).participant(event, holder)
        assertThat(discovery.event(event).isParticipant)
            .withFailMessage("Details dont mark the participation of the caller")
            .isTrue()
    }

    @Test
    fun `details of an unknown event answer event not found`() {
        assertThat(Failure { discovery.event(UUID.randomUUID()) }.code())
            .withFailMessage("Details of an unknown event dont answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    @Test
    fun `details show closed registration before the start when background processing is late`() {
        val event = event("recruiting", now.minus(minutes(0, 600)), now.plus(minutes(1, 600)))
        assertThat(discovery.event(event).state)
            .withFailMessage(
                "Details dont show closed registration before the start when background processing is late",
            )
            .isEqualTo(EventState.registration_closed)
    }

    @Test
    fun `details store the current state`() {
        val event = event("recruiting", now.minus(minutes(0, 600)), now.plus(minutes(1, 600)))
        jdbc.update("UPDATE events SET min_users = ? WHERE id = ?", Random.nextInt(2, 51), event)
        discovery.event(event)
        entities.flush()
        assertThat(jdbc.queryForObject<String>("SELECT state FROM events WHERE id = ?", event))
            .withFailMessage("Details dont store the current state")
            .isEqualTo("cancelled_by_system")
    }

    private fun holder(): UUID = Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)

    private fun event(state: String, closes: Instant, starts: Instant = closes.plus(minutes(0, 600))): UUID =
        Rows(jdbc).organized(Rows(jdbc).user(), state, closes, starts)

    private fun minutes(from: Long, until: Long): Duration = Duration.ofMinutes(Random.nextLong(from, until))

    private fun hour(index: Int): Instant = now.plus(Duration.ofHours(index + 1L))

    companion object {
        private val holder = UUID.randomUUID()

        private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        @JvmStatic
        fun identity(): Identity = Identity { holder }

        @JvmStatic
        fun clock(): Clock = Clock.fixed(now, ZoneOffset.UTC)
    }
}
