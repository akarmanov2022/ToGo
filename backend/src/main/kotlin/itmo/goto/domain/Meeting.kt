package itmo.goto.domain

import java.time.Instant

/**
 * Событие.
 */
class Meeting(
    private val schedule: Schedule,
    private val limits: Limits,
    private val participants: Int,
    private val state: State,
) {
    /**
     * Новая встреча в наборе, где организатор — единственный участник.
     */
    constructor(schedule: Schedule, limits: Limits) : this(schedule, limits, 1, State.RECRUITING)

    /**
     * Сохранённое состояние.
     */
    fun state(): State = state

    /**
     * Число участников вместе с организатором.
     */
    fun participants(): Int = participants

    /**
     * Число свободных мест, которое клиенты видят как доступные места.
     */
    fun free(): Int = limits.free(participants)

    /**
     * Встреча в своём состоянии на момент [now].
     */
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

    /**
     * Впускает постороннего, пока регистрация открыта, есть свободное место и в [agenda] нет встречи рядом
     * по времени.
     */
    fun join(role: Role, agenda: Agenda, now: Instant): Decision = current(now).run {
        when {
            role != Role.OUTSIDER -> Accepted(this)
            state != State.RECRUITING -> Refused(this, blocked())
            limits.full(participants) -> Refused(this, Refusal.EVENT_FULL)
            agenda.conflicts(schedule, now) -> Refused(this, Refusal.SCHEDULE_CONFLICT)
            else -> Accepted(Meeting(schedule, limits, participants + 1, state))
        }
    }

    /**
     * Выпускает участника, пока регистрация открыта, освобождая место без отмены встречи даже ниже минимума.
     */
    fun leave(role: Role, now: Instant): Decision = current(now).run {
        when {
            role == Role.OUTSIDER -> Accepted(this)
            role == Role.ORGANIZER -> Refused(this, Refusal.ORGANIZER_CANNOT_LEAVE)
            state != State.RECRUITING -> Refused(this, blocked())
            else -> Accepted(Meeting(schedule, limits, participants - 1, state))
        }
    }

    /**
     * Позволяет организатору задать новые [limits] и новый срок регистрации [deadline], пока регистрация открыта,
     * если новый максимум вмещает текущих участников.
     */
    fun change(role: Role, limits: Limits, deadline: Instant, now: Instant): Decision {
        require(limits.violations().isEmpty()) { "Meeting limits must pass their own checks before a change" }
        return current(now).run {
            when {
                role != Role.ORGANIZER -> Refused(this, Refusal.FORBIDDEN)
                state != State.RECRUITING -> Refused(this, blocked())
                else -> {
                    require(schedule.moved(deadline).violations(now).isEmpty()) {
                        "Meeting deadline must pass its own checks before a change"
                    }
                    if (limits.holds(participants)) {
                        Accepted(Meeting(schedule.moved(deadline), limits, participants, state))
                    } else {
                        Refused(this, Refusal.CAPACITY_BELOW_PARTICIPANTS)
                    }
                }
            }
        }
    }

    /**
     * Позволяет организатору отменить встречу в наборе или с закрытой регистрацией до её начала.
     */
    fun cancel(role: Role, now: Instant): Decision = current(now).run {
        when {
            role != Role.ORGANIZER -> Refused(this, Refusal.FORBIDDEN)
            state == State.CANCELLED_BY_ORGANIZER -> Accepted(this)
            state in setOf(State.RECRUITING, State.REGISTRATION_CLOSED) && !schedule.started(now) ->
                Accepted(Meeting(schedule, limits, participants, State.CANCELLED_BY_ORGANIZER))
            else -> Refused(this, Refusal.INVALID_EVENT_STATE)
        }
    }

    /**
     * Сообщает, действует ли встреча в момент [now] и начинается ли она меньше чем через три часа от расписания
     * [other].
     */
    fun clashes(other: Schedule, now: Instant): Boolean =
        current(now).state in setOf(State.RECRUITING, State.REGISTRATION_CLOSED) && schedule.near(other)

    private fun blocked(): Refusal =
        if (state == State.REGISTRATION_CLOSED) Refusal.REGISTRATION_CLOSED else Refusal.INVALID_EVENT_STATE
}
