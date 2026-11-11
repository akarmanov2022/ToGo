package itmo.goto.service

import itmo.goto.PostgresConfiguration
import itmo.goto.Rows
import itmo.goto.auth.Identity
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateCommentRequest
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
@Import(PostgresConfiguration::class, CommentsService::class, NotificationQueue::class)
class CommentsServiceTest(
    @Autowired private val comments: CommentsService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val entities: TestEntityManager,
) {
    @TestBean
    private lateinit var identity: Identity

    @TestBean
    private lateinit var clock: Clock

    @Test
    fun `participant publishes a comment as its author`() {
        holder()
        val event = event()
        Rows(jdbc).participant(event, holder)
        assertThat(comments.createEventComment(event, request()).author.id)
            .withFailMessage("Participant doesnt publish a comment as its author")
            .isEqualTo(holder)
    }

    @Test
    fun `published comment keeps its text`() {
        holder()
        val event = event()
        Rows(jdbc).participant(event, holder)
        val request = request()
        assertThat(comments.createEventComment(event, request).text)
            .withFailMessage("Published comment doesnt keep its text")
            .isEqualTo(request.text)
    }

    @Test
    fun `published comment is dated by the clock`() {
        holder()
        val event = event()
        Rows(jdbc).participant(event, holder)
        assertThat(comments.createEventComment(event, request()).createdAt.toInstant())
            .withFailMessage("Published comment isnt dated by the clock")
            .isEqualTo(now)
    }

    @Test
    fun `published comment is stored`() {
        holder()
        val event = event()
        Rows(jdbc).participant(event, holder)
        val id = comments.createEventComment(event, request()).id
        entities.flush()
        assertThat(jdbc.queryForList<UUID>("SELECT id FROM comments WHERE event_id = ?", event))
            .withFailMessage("Published comment isnt stored")
            .containsExactly(id)
    }

    @Test
    fun `each comment notifies other current participants including organizer`() {
        holder()
        val rows = Rows(jdbc)
        val event = event()
        val organizer = jdbc.queryForObject("SELECT organizer_id FROM events WHERE id = ?", UUID::class.java, event)!!
        val participant = rows.user()
        val former = rows.user()
        rows.participant(event, holder)
        rows.participant(event, participant)
        rows.participant(event, former)
        jdbc.update("DELETE FROM event_users WHERE event_id = ? AND user_id = ?", event, former)
        val first = comments.createEventComment(event, request()).id
        val second = comments.createEventComment(event, request()).id
        entities.flush()
        val deliveries = jdbc.query(
            "SELECT user_id, comment_id FROM notifications WHERE event_id = ? AND kind = 'comment'",
            { result, _ ->
                result.getObject("user_id", UUID::class.java) to result.getObject("comment_id", UUID::class.java)
            },
            event,
        ).toSet()
        assertThat(deliveries)
            .withFailMessage("Comment notifications did not target current members once per comment")
            .containsExactlyInAnyOrder(
                organizer to first, participant to first,
                organizer to second, participant to second,
            )
    }

    @Test
    fun `organizer publishes a comment`() {
        holder()
        val event = Rows(jdbc).organized(holder, "recruiting", now.plus(minutes(1, 600)), now.plus(minutes(600, 900)))
        assertThat(comments.createEventComment(event, request()).author.id)
            .withFailMessage("Organizer doesnt publish a comment")
            .isEqualTo(holder)
    }

    @Test
    fun `participant comments an event in any state`() {
        holder()
        val event = event(
            listOf(
                "registration_closed",
                "finished",
                "cancelled_by_organizer",
                "cancelled_by_system",
            ).random(),
        )
        Rows(jdbc).participant(event, holder)
        assertThat(comments.createEventComment(event, request()).author.id)
            .withFailMessage("Participant doesnt comment an event in any state")
            .isEqualTo(holder)
    }

    @Test
    fun `outsider commenting answers forbidden`() {
        holder()
        val event = event()
        assertThat(Failure { comments.createEventComment(event, request()) }.code())
            .withFailMessage("Outsider commenting doesnt answer FORBIDDEN")
            .isEqualTo(ApiError.Code.FORBIDDEN)
    }

    @Test
    fun `commenting an unknown event answers event not found`() {
        holder()
        assertThat(Failure { comments.createEventComment(UUID.randomUUID(), request()) }.code())
            .withFailMessage("Commenting an unknown event doesnt answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    @Test
    fun `comments go from old to new`() {
        holder()
        val event = event()
        val rows = Rows(jdbc)
        val earlier = rows.comment(event, rows.user(), now.minus(minutes(10, 600)))
        val later = rows.comment(event, rows.user(), now.minus(minutes(1, 10)))
        assertThat(comments.getEventComments(event, 100, 0).items.map { it.id })
            .withFailMessage("Comments dont go from old to new")
            .containsExactly(earlier, later)
    }

    @Test
    fun `comments published at one moment go by id`() {
        holder()
        val event = event()
        val rows = Rows(jdbc)
        val moment = now.minus(minutes(1, 600))
        val ids = List(Random.nextInt(2, 6)) { rows.comment(event, rows.user(), moment) }
        assertThat(comments.getEventComments(event, 100, 0).items.map { it.id })
            .withFailMessage("Comments published at one moment dont go by id")
            .containsExactlyElementsOf(ids.sorted())
    }

    @Test
    fun `comments name their authors`() {
        holder()
        val event = event()
        val rows = Rows(jdbc)
        val author = rows.user()
        rows.comment(event, author, now.minus(minutes(1, 600)))
        assertThat(comments.getEventComments(event, 100, 0).items.map { it.author.id })
            .withFailMessage("Comments dont name their authors")
            .containsExactly(author)
    }

    @Test
    fun `comment page counts every comment`() {
        holder()
        val event = event()
        val rows = Rows(jdbc)
        val count = Random.nextInt(2, 8)
        repeat(count) { rows.comment(event, rows.user(), now.minus(minutes(1, 600))) }
        assertThat(comments.getEventComments(event, 1, Random.nextInt(0, 10)).total)
            .withFailMessage("Comment page doesnt count every comment")
            .isEqualTo(count.toLong())
    }

    @Test
    fun `comment page skips the offset and keeps the limit`() {
        holder()
        val event = event()
        val rows = Rows(jdbc)
        repeat(Random.nextInt(4, 8)) { rows.comment(event, rows.user(), now.minus(minutes(1, 600))) }
        val all = comments.getEventComments(event, 100, 0).items.map { it.id }
        val offset = Random.nextInt(0, 3)
        val limit = Random.nextInt(1, 3)
        assertThat(comments.getEventComments(event, limit, offset).items.map { it.id })
            .withFailMessage("Comment page doesnt skip the offset and keep the limit")
            .containsExactlyElementsOf(all.drop(offset).take(limit))
    }

    @Test
    fun `comment page skips comments of other events`() {
        holder()
        val event = event()
        val rows = Rows(jdbc)
        rows.comment(event(), rows.user(), now.minus(minutes(1, 600)))
        assertThat(comments.getEventComments(event, 100, 0).items)
            .withFailMessage("Comment page doesnt skip comments of other events")
            .isEmpty()
    }

    @Test
    fun `comments of an unknown event answer event not found`() {
        holder()
        assertThat(Failure { comments.getEventComments(UUID.randomUUID(), 100, 0) }.code())
            .withFailMessage("Comments of an unknown event dont answer EVENT_NOT_FOUND")
            .isEqualTo(ApiError.Code.EVENT_NOT_FOUND)
    }

    private fun holder(): UUID = Rows(jdbc).user(holder, Random.nextLong(1, 10_000_000_000), null)

    private fun event(state: String = "recruiting"): UUID =
        Rows(jdbc).organized(Rows(jdbc).user(), state, now.plus(minutes(1, 600)), now.plus(minutes(600, 900)))

    private fun request(): CreateCommentRequest = CreateCommentRequest("Встречаемся у входа ${Random.nextInt()}")

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
