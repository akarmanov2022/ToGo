package itmo.goto.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class MeetingTest {
    @Test
    fun `new meeting recruits with its organizer inside`() {
        assertThat(Meeting(schedule(moment()), Limits(1, Random.nextInt(1, 51))).participants())
            .withFailMessage("New meeting doesnt count its organizer as the only participant")
            .isEqualTo(1)
    }

    @Test
    fun `meeting before its deadline keeps recruiting`() {
        val closes = moment()
        assertThat(
            Meeting(schedule(closes), Limits(5, 10), 5, State.RECRUITING)
                .current(closes.minusSeconds(Random.nextLong(1, 86_400)))
                .state(),
        )
            .withFailMessage("Meeting before its deadline stops recruiting")
            .isEqualTo(State.RECRUITING)
    }

    @Test
    fun `full meeting before its deadline keeps recruiting`() {
        val closes = moment()
        val max = Random.nextInt(1, 51)
        assertThat(
            Meeting(schedule(closes), Limits(1, max), max, State.RECRUITING).current(closes.minusSeconds(1)).state(),
        )
            .withFailMessage("Full meeting stops recruiting before its deadline")
            .isEqualTo(State.RECRUITING)
    }

    @Test
    fun `meeting with its minimum closes registration exactly at the deadline`() {
        val closes = moment()
        val min = Random.nextInt(1, 10)
        assertThat(Meeting(schedule(closes), Limits(min, 10), min, State.RECRUITING).current(closes).state())
            .withFailMessage("Meeting with its minimum doesnt close registration exactly at the deadline")
            .isEqualTo(State.REGISTRATION_CLOSED)
    }

    @Test
    fun `meeting short of its minimum is cancelled exactly at the deadline`() {
        val closes = moment()
        val min = Random.nextInt(2, 10)
        assertThat(Meeting(schedule(closes), Limits(min, 10), min - 1, State.RECRUITING).current(closes).state())
            .withFailMessage("Meeting short of its minimum isnt cancelled by the system at the deadline")
            .isEqualTo(State.CANCELLED_BY_SYSTEM)
    }

    @Test
    fun `meeting under way keeps its closed registration`() {
        val closes = moment()
        assertThat(
            Meeting(schedule(closes), Limits(1, 10), 3, State.REGISTRATION_CLOSED)
                .current(closes.plus(Duration.ofHours(3)))
                .state(),
        )
            .withFailMessage("Meeting between its start and end leaves the closed registration state")
            .isEqualTo(State.REGISTRATION_CLOSED)
    }

    @Test
    fun `meeting finishes exactly at its end`() {
        val closes = moment()
        assertThat(
            Meeting(schedule(closes), Limits(1, 10), 3, State.REGISTRATION_CLOSED)
                .current(closes.plus(Duration.ofHours(5)))
                .state(),
        )
            .withFailMessage("Meeting doesnt finish exactly at its end")
            .isEqualTo(State.FINISHED)
    }

    @Test
    fun `meeting with its minimum finishes after both missed deadlines`() {
        val closes = moment()
        assertThat(
            Meeting(schedule(closes), Limits(3, 10), 3, State.RECRUITING)
                .current(closes.plus(Duration.ofHours(Random.nextLong(5, 100))))
                .state(),
        )
            .withFailMessage("Meeting with its minimum doesnt finish after both deadlines were missed")
            .isEqualTo(State.FINISHED)
    }

    @Test
    fun `meeting short of its minimum stays cancelled after both missed deadlines`() {
        val closes = moment()
        assertThat(
            Meeting(schedule(closes), Limits(3, 10), 2, State.RECRUITING)
                .current(closes.plus(Duration.ofHours(Random.nextLong(5, 100))))
                .state(),
        )
            .withFailMessage(
                "Meeting short of its minimum isnt cancelled by the system after both deadlines were missed",
            )
            .isEqualTo(State.CANCELLED_BY_SYSTEM)
    }

    @Test
    fun `meeting cancelled by its organizer stays cancelled after its end`() {
        val closes = moment()
        assertThat(
            Meeting(schedule(closes), Limits(1, 10), 3, State.CANCELLED_BY_ORGANIZER)
                .current(closes.plus(Duration.ofHours(Random.nextLong(0, 100))))
                .state(),
        )
            .withFailMessage("Meeting cancelled by its organizer changes its state over time")
            .isEqualTo(State.CANCELLED_BY_ORGANIZER)
    }

    @Test
    fun `meeting cancelled by the system does not finish at its end`() {
        val closes = moment()
        assertThat(
            Meeting(schedule(closes), Limits(5, 10), 3, State.CANCELLED_BY_SYSTEM)
                .current(closes.plus(Duration.ofHours(5)))
                .state(),
        )
            .withFailMessage("Meeting cancelled by the system finishes at its end")
            .isEqualTo(State.CANCELLED_BY_SYSTEM)
    }

    @Test
    fun `outsider joins before the deadline`() {
        val closes = moment()
        val count = Random.nextInt(1, 10)
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), count, State.RECRUITING)
                    .join(Role.OUTSIDER, Agenda(emptyList()), closes.minusSeconds(1)),
            ).participants(),
        )
            .withFailMessage("Outsider joining before the deadline doesnt add one participant")
            .isEqualTo(count + 1)
    }

    @Test
    fun `outsider cannot join exactly at the deadline`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .join(Role.OUTSIDER, Agenda(emptyList()), closes),
            ),
        )
            .withFailMessage("Outsider isnt refused for closed registration exactly at the deadline")
            .isEqualTo(Refusal.REGISTRATION_CLOSED)
    }

    @Test
    fun `outsider cannot join after the deadline before the start`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .join(Role.OUTSIDER, Agenda(emptyList()), closes.plus(Duration.ofHours(1))),
            ),
        )
            .withFailMessage("Outsider isnt refused for closed registration between the deadline and the start")
            .isEqualTo(Refusal.REGISTRATION_CLOSED)
    }

    @Test
    fun `refused join keeps the closed registration`() {
        val closes = moment()
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .join(Role.OUTSIDER, Agenda(emptyList()), closes),
            ).state(),
        )
            .withFailMessage("Refused join doesnt keep the registration closed by the deadline")
            .isEqualTo(State.REGISTRATION_CLOSED)
    }

    @Test
    fun `outsider cannot join a full meeting`() {
        val closes = moment()
        val max = Random.nextInt(1, 51)
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, max), max, State.RECRUITING)
                    .join(Role.OUTSIDER, Agenda(emptyList()), closes.minusSeconds(1)),
            ),
        )
            .withFailMessage("Outsider isnt refused for a full meeting")
            .isEqualTo(Refusal.EVENT_FULL)
    }

    @Test
    fun `outsider cannot join a cancelled meeting`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.CANCELLED_BY_ORGANIZER)
                    .join(Role.OUTSIDER, Agenda(emptyList()), closes.minusSeconds(1)),
            ),
        )
            .withFailMessage("Outsider isnt refused for the state of a cancelled meeting")
            .isEqualTo(Refusal.INVALID_EVENT_STATE)
    }

    @Test
    fun `outsider cannot join a meeting near another one`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING).join(
                    Role.OUTSIDER,
                    Agenda(listOf(Meeting(schedule(closes.plus(Duration.ofHours(2))), Limits(1, 10)))),
                    closes.minusSeconds(1),
                ),
            ),
        )
            .withFailMessage("Outsider isnt refused for a meeting less than three hours from another one")
            .isEqualTo(Refusal.SCHEDULE_CONFLICT)
    }

    @Test
    fun `participant joining again leaves a closed meeting unchanged`() {
        val closes = moment()
        val count = Random.nextInt(1, 10)
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), count, State.REGISTRATION_CLOSED)
                    .join(Role.PARTICIPANT, Agenda(emptyList()), closes.plus(Duration.ofHours(1))),
            ).participants(),
        )
            .withFailMessage("Participant joining again changes the participants")
            .isEqualTo(count)
    }

    @Test
    fun `participant leaves before the deadline`() {
        val closes = moment()
        val count = Random.nextInt(2, 10)
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), count, State.RECRUITING)
                    .leave(Role.PARTICIPANT, closes.minusSeconds(1)),
            ).participants(),
        )
            .withFailMessage("Participant leaving before the deadline doesnt free one place")
            .isEqualTo(count - 1)
    }

    @Test
    fun `participant leaving below the minimum keeps recruiting`() {
        val closes = moment()
        val min = Random.nextInt(2, 10)
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(min, 10), min, State.RECRUITING)
                    .leave(Role.PARTICIPANT, closes.minusSeconds(1)),
            ).state(),
        )
            .withFailMessage("Participant leaving below the minimum cancels the meeting before the deadline")
            .isEqualTo(State.RECRUITING)
    }

    @Test
    fun `participant cannot leave exactly at the deadline`() {
        val closes = moment()
        assertThat(
            refusal(Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING).leave(Role.PARTICIPANT, closes)),
        )
            .withFailMessage("Participant isnt refused for closed registration exactly at the deadline")
            .isEqualTo(Refusal.REGISTRATION_CLOSED)
    }

    @Test
    fun `participant cannot leave a finished meeting`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.REGISTRATION_CLOSED)
                    .leave(Role.PARTICIPANT, closes.plus(Duration.ofHours(6))),
            ),
        )
            .withFailMessage("Participant isnt refused for the state of a finished meeting")
            .isEqualTo(Refusal.INVALID_EVENT_STATE)
    }

    @Test
    fun `organizer cannot leave`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .leave(Role.ORGANIZER, closes.minusSeconds(1)),
            ),
        )
            .withFailMessage("Organizer isnt refused when leaving the own meeting")
            .isEqualTo(Refusal.ORGANIZER_CANNOT_LEAVE)
    }

    @Test
    fun `outsider leaving leaves a closed meeting unchanged`() {
        val closes = moment()
        val count = Random.nextInt(1, 10)
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), count, State.REGISTRATION_CLOSED)
                    .leave(Role.OUTSIDER, closes.plus(Duration.ofHours(1))),
            ).participants(),
        )
            .withFailMessage("Outsider leaving changes the participants")
            .isEqualTo(count)
    }

    @Test
    fun `organizer cancels a recruiting meeting`() {
        val closes = moment()
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .cancel(Role.ORGANIZER, closes.minusSeconds(1)),
            ).state(),
        )
            .withFailMessage("Organizer cannot cancel a recruiting meeting")
            .isEqualTo(State.CANCELLED_BY_ORGANIZER)
    }

    @Test
    fun `organizer cancels after the deadline before the start`() {
        val closes = moment()
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .cancel(Role.ORGANIZER, closes.plus(Duration.ofHours(1))),
            ).state(),
        )
            .withFailMessage("Organizer cannot cancel a meeting between its deadline and its start")
            .isEqualTo(State.CANCELLED_BY_ORGANIZER)
    }

    @Test
    fun `organizer cannot cancel a started meeting`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.REGISTRATION_CLOSED)
                    .cancel(Role.ORGANIZER, closes.plus(Duration.ofHours(2))),
            ),
        )
            .withFailMessage("Organizer isnt refused when cancelling a started meeting")
            .isEqualTo(Refusal.INVALID_EVENT_STATE)
    }

    @Test
    fun `organizer cannot cancel a meeting cancelled by the system`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(5, 10), 3, State.RECRUITING)
                    .cancel(Role.ORGANIZER, closes.plus(Duration.ofHours(1))),
            ),
        )
            .withFailMessage("Organizer isnt refused when cancelling a meeting cancelled by the system")
            .isEqualTo(Refusal.INVALID_EVENT_STATE)
    }

    @Test
    fun `participant cannot cancel`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .cancel(Role.PARTICIPANT, closes.minusSeconds(1)),
            ),
        )
            .withFailMessage("Participant isnt forbidden to cancel a meeting")
            .isEqualTo(Refusal.FORBIDDEN)
    }

    @Test
    fun `organizer cancelling again after the start leaves the meeting cancelled`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.CANCELLED_BY_ORGANIZER)
                    .cancel(Role.ORGANIZER, closes.plus(Duration.ofHours(Random.nextLong(2, 100)))),
            ),
        )
            .withFailMessage("Organizer cancelling a cancelled meeting again is refused")
            .isNull()
    }

    @Test
    fun `organizer moves the deadline later`() {
        val closes = moment()
        assertThat(
            outcome(
                Meeting(schedule(closes), Limits(1, 10), 1, State.RECRUITING)
                    .change(Role.ORGANIZER, Limits(1, 10), closes.plus(Duration.ofHours(1)), closes.minusSeconds(1)),
            ).current(closes).state(),
        )
            .withFailMessage("Organizer moving the deadline later doesnt keep recruiting at the old deadline")
            .isEqualTo(State.RECRUITING)
    }

    @Test
    fun `organizer cannot change after the deadline`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .change(Role.ORGANIZER, Limits(1, 10), closes.plus(Duration.ofHours(2)), closes),
            ),
        )
            .withFailMessage("Organizer isnt refused for closed registration when changing at the deadline")
            .isEqualTo(Refusal.REGISTRATION_CLOSED)
    }

    @Test
    fun `organizer keeping a passed deadline is refused for closed registration`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .change(Role.ORGANIZER, Limits(1, 10), closes, closes.plusSeconds(Random.nextLong(0, 7200))),
            ),
        )
            .withFailMessage("Organizer keeping a passed deadline isnt refused for closed registration")
            .isEqualTo(Refusal.REGISTRATION_CLOSED)
    }

    @Test
    fun `organizer keeping a passed deadline of a cancelled meeting is refused for its state`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.CANCELLED_BY_ORGANIZER)
                    .change(Role.ORGANIZER, Limits(1, 10), closes, closes.plusSeconds(Random.nextLong(0, 7200))),
            ),
        )
            .withFailMessage("Organizer keeping a passed deadline of a cancelled meeting isnt refused for its state")
            .isEqualTo(Refusal.INVALID_EVENT_STATE)
    }

    @Test
    fun `organizer cannot change a cancelled meeting`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.CANCELLED_BY_ORGANIZER)
                    .change(Role.ORGANIZER, Limits(1, 10), closes, closes.minusSeconds(1)),
            ),
        )
            .withFailMessage("Organizer isnt refused for the state of a cancelled meeting when changing it")
            .isEqualTo(Refusal.INVALID_EVENT_STATE)
    }

    @Test
    fun `organizer cannot lower the maximum below the participants`() {
        val closes = moment()
        val count = Random.nextInt(2, 51)
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 50), count, State.RECRUITING)
                    .change(Role.ORGANIZER, Limits(1, count - 1), closes, closes.minusSeconds(1)),
            ),
        )
            .withFailMessage("Organizer isnt refused when lowering the maximum below the participants")
            .isEqualTo(Refusal.CAPACITY_BELOW_PARTICIPANTS)
    }

    @Test
    fun `participant cannot change`() {
        val closes = moment()
        assertThat(
            refusal(
                Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                    .change(Role.PARTICIPANT, Limits(1, 10), closes, closes.minusSeconds(1)),
            ),
        )
            .withFailMessage("Participant isnt forbidden to change a meeting")
            .isEqualTo(Refusal.FORBIDDEN)
    }

    @Test
    fun `change with broken limits fails`() {
        val closes = moment()
        assertThatThrownBy {
            Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                .change(Role.ORGANIZER, Limits(0, Random.nextInt(1, 51)), closes, closes.minusSeconds(1))
        }
            .withFailMessage("Change with limits that break the contract doesnt fail")
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `change with a past deadline fails`() {
        val closes = moment()
        assertThatThrownBy {
            Meeting(schedule(closes), Limits(1, 10), 3, State.RECRUITING)
                .change(Role.ORGANIZER, Limits(1, 10), closes.minus(Duration.ofHours(1)), closes.minusSeconds(1))
        }
            .withFailMessage("Change with a deadline in the past doesnt fail")
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun moment(): Instant = Instant.ofEpochSecond(Random.nextLong(1_700_000_000, 1_900_000_000))

    private fun schedule(closes: Instant): Schedule =
        Schedule(closes, closes.plus(Duration.ofHours(2)), closes.plus(Duration.ofHours(5)))

    private fun outcome(decision: Decision): Meeting = decision.fold({ it }, { meeting, _ -> meeting })

    private fun refusal(decision: Decision): Refusal? = decision.fold({ null }, { _, refusal -> refusal })
}
