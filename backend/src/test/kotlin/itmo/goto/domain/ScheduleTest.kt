package itmo.goto.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class ScheduleTest {
    @Test
    fun `deadline at the current moment breaks the deadline`() {
        val now = moment()
        val starts = now.plus(hours())
        assertThat(Schedule(now, starts, starts.plus(hours())).violations(now).map { it.field() })
            .withFailMessage("Deadline at the current moment doesnt break the registrationClosesAt field")
            .containsExactly("registrationClosesAt")
    }

    @Test
    fun `deadline after the start breaks the deadline`() {
        val now = moment()
        val starts = now.plus(hours())
        assertThat(Schedule(starts.plus(hours()), starts, starts.plus(hours())).violations(now).map { it.field() })
            .withFailMessage("Deadline after the start doesnt break the registrationClosesAt field")
            .containsExactly("registrationClosesAt")
    }

    @Test
    fun `deadline at the start breaks nothing`() {
        val now = moment()
        val starts = now.plus(hours())
        assertThat(Schedule(starts, starts, starts.plus(hours())).violations(now))
            .withFailMessage("Deadline at the start breaks a field")
            .isEmpty()
    }

    @Test
    fun `end at the start breaks the end`() {
        val now = moment()
        val starts = now.plus(hours()).plus(hours())
        assertThat(Schedule(now.plus(hours()), starts, starts).violations(now).map { it.field() })
            .withFailMessage("End at the start doesnt break the endsAt field")
            .containsExactly("endsAt")
    }

    private fun moment(): Instant = Instant.ofEpochSecond(Random.nextLong(1_700_000_000, 1_900_000_000))

    private fun hours(): Duration = Duration.ofMinutes(Random.nextLong(1, 600))
}
