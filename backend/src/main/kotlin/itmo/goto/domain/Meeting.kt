package itmo.goto.domain

import java.time.Instant

class Meeting(
    private val schedule: Schedule,
    private val limits: Limits,
    private val participants: Int,
    private val state: State,
) {
    constructor(schedule: Schedule, limits: Limits) : this(schedule, limits, 1, State.RECRUITING)

    fun state(): State = state

    fun participants(): Int = participants

    fun free(): Int = limits.free(participants)

    fun current(now: Instant): Meeting = when (state) {
        State.RECRUITING if !schedule.open(now) ->
            Meeting(
                schedule,
                limits,
                participants,
                if (limits.reached(participants)) State.REGISTRATION_CLOSED else State.CANCELLED_BY_SYSTEM,
            ).current(now)
        State.REGISTRATION_CLOSED if schedule.ended(now) ->
            Meeting(schedule, limits, participants, State.FINISHED)
        else -> this
    }

    fun join(role: Role, agenda: Agenda, now: Instant): Decision = current(now).admission(role, agenda, now)

    fun leave(role: Role, now: Instant): Decision = current(now).departure(role)

    fun change(role: Role, limits: Limits, deadline: Instant, now: Instant): Decision {
        require(limits.violations().isEmpty()) { "Meeting limits must pass their own checks before a change" }
        require(schedule.moved(deadline).violations(now).isEmpty()) {
            "Meeting deadline must pass its own checks before a change"
        }
        return current(now).revision(role, limits, deadline)
    }

    fun cancel(role: Role, now: Instant): Decision = current(now).cancellation(role, now)

    fun clashes(other: Schedule, now: Instant): Boolean =
        current(now).state in setOf(State.RECRUITING, State.REGISTRATION_CLOSED) && schedule.near(other)

    private fun admission(role: Role, agenda: Agenda, now: Instant): Decision = when {
        role != Role.OUTSIDER -> Accepted(this)
        state != State.RECRUITING -> Refused(this, blocked())
        limits.full(participants) -> Refused(this, Refusal.EVENT_FULL)
        agenda.conflicts(schedule, now) -> Refused(this, Refusal.SCHEDULE_CONFLICT)
        else -> Accepted(Meeting(schedule, limits, participants + 1, state))
    }

    private fun departure(role: Role): Decision = when {
        role == Role.OUTSIDER -> Accepted(this)
        role == Role.ORGANIZER -> Refused(this, Refusal.ORGANIZER_CANNOT_LEAVE)
        state != State.RECRUITING -> Refused(this, blocked())
        else -> Accepted(Meeting(schedule, limits, participants - 1, state))
    }

    private fun revision(role: Role, limits: Limits, deadline: Instant): Decision = when {
        role != Role.ORGANIZER -> Refused(this, Refusal.FORBIDDEN)
        state != State.RECRUITING -> Refused(this, blocked())
        !limits.holds(participants) -> Refused(this, Refusal.CAPACITY_BELOW_PARTICIPANTS)
        else -> Accepted(Meeting(schedule.moved(deadline), limits, participants, state))
    }

    private fun cancellation(role: Role, now: Instant): Decision = when {
        role != Role.ORGANIZER -> Refused(this, Refusal.FORBIDDEN)
        state == State.CANCELLED_BY_ORGANIZER -> Accepted(this)
        state in setOf(State.RECRUITING, State.REGISTRATION_CLOSED) && !schedule.started(now) ->
            Accepted(Meeting(schedule, limits, participants, State.CANCELLED_BY_ORGANIZER))
        else -> Refused(this, Refusal.INVALID_EVENT_STATE)
    }

    private fun blocked(): Refusal =
        if (state == State.REGISTRATION_CLOSED) Refusal.REGISTRATION_CLOSED else Refusal.INVALID_EVENT_STATE
}
