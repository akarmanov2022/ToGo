package itmo.goto.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class AgendaTest {
    @Test
    fun `meeting starting two hours fifty nine minutes later conflicts`() {
        val now = moment()
        val starts = now.plus(Duration.ofDays(1))
        assertThat(
            Agenda(listOf(Meeting(schedule(starts), Limits(1, 10)))).conflicts(
                schedule(starts.plus(Duration.ofMinutes(179))),
                now,
            ),
        )
            .withFailMessage("Meeting starting 2 hours 59 minutes after another doesnt conflict with it")
            .isTrue()
    }

    @Test
    fun `meeting starting exactly three hours later does not conflict`() {
        val now = moment()
        val starts = now.plus(Duration.ofDays(1))
        assertThat(
            Agenda(listOf(Meeting(schedule(starts), Limits(1, 10)))).conflicts(
                schedule(starts.plus(Duration.ofHours(3))),
                now,
            ),
        )
            .withFailMessage("Meeting starting exactly 3 hours after another conflicts with it")
            .isFalse()
    }

    @Test
    fun `meeting starting two hours fifty nine minutes earlier conflicts`() {
        val now = moment()
        val starts = now.plus(Duration.ofDays(1))
        assertThat(
            Agenda(listOf(Meeting(schedule(starts), Limits(1, 10)))).conflicts(
                schedule(starts.minus(Duration.ofMinutes(179))),
                now,
            ),
        )
            .withFailMessage("Meeting starting 2 hours 59 minutes before another doesnt conflict with it")
            .isTrue()
    }

    @Test
    fun `cancelled meeting does not conflict`() {
        val now = moment()
        val starts = now.plus(Duration.ofDays(1))
        assertThat(
            Agenda(listOf(Meeting(schedule(starts), Limits(1, 10), 1, State.CANCELLED_BY_ORGANIZER))).conflicts(
                schedule(starts.plus(Duration.ofMinutes(Random.nextLong(0, 180)))),
                now,
            ),
        )
            .withFailMessage("Cancelled meeting conflicts with a meeting near it")
            .isFalse()
    }

    @Test
    fun `finished meeting does not conflict`() {
        val now = moment()
        val starts = now.minus(Duration.ofMinutes(Random.nextLong(1, 60)))
        assertThat(
            Agenda(listOf(Meeting(schedule(starts.minus(Duration.ofHours(2))), Limits(1, 10), 1, State.FINISHED)))
                .conflicts(schedule(starts), now),
        )
            .withFailMessage("Finished meeting conflicts with a meeting near it")
            .isFalse()
    }

    @Test
    fun `meeting short of its minimum at the deadline does not conflict`() {
        val now = moment()
        assertThat(
            Agenda(listOf(Meeting(schedule(now), Limits(Random.nextInt(2, 51), 50)))).conflicts(
                schedule(now.plus(Duration.ofMinutes(Random.nextLong(0, 180)))),
                now,
            ),
        )
            .withFailMessage("Meeting short of its minimum at the deadline still conflicts as if recruiting")
            .isFalse()
    }

    private fun moment(): Instant = Instant.ofEpochSecond(Random.nextLong(1_700_000_000, 1_900_000_000))

    private fun schedule(starts: Instant): Schedule = Schedule(starts, starts, starts.plus(Duration.ofHours(2)))
}
