package itmo.goto.domain

import java.time.Duration
import java.time.Instant

/**
 * Три момента встречи: срок регистрации от организатора, начало и окончание.
 */
class Schedule(private val closes: Instant, private val starts: Instant, private val ends: Instant) {

    fun violations(now: Instant): List<Violation> = listOfNotNull(
        Violation("registrationClosesAt", "Срок регистрации должен быть в будущем").takeIf { !closes.isAfter(now) },
        Violation("registrationClosesAt", "Срок регистрации не может быть позже начала").takeIf {
            closes.isAfter(starts)
        },
        Violation("endsAt", "Окончание должно быть позже начала").takeIf { !ends.isAfter(starts) },
    )

    /**
     * Сообщает, открыта ли ещё регистрация в момент [now]; она закрывается ровно в срок.
     */
    fun open(now: Instant): Boolean = now.isBefore(closes)

    /**
     * Сообщает, началась ли встреча к моменту [now], включая сам момент начала.
     */
    fun started(now: Instant): Boolean = !now.isBefore(starts)

    /**
     * Сообщает, закончилась ли встреча к моменту [now], включая сам момент окончания.
     */
    fun ended(now: Instant): Boolean = !now.isBefore(ends)

    /**
     * Сообщает, начинается ли встреча [other] меньше чем через три часа от этой, раньше или позже неё.
     */
    fun near(other: Schedule): Boolean = Duration.between(starts, other.starts).abs() < gap

    /**
     * То же начало и окончание с новым сроком регистрации [deadline].
     */
    fun moved(deadline: Instant): Schedule = Schedule(deadline, starts, ends)

    private companion object {
        val gap: Duration = Duration.ofHours(3)
    }
}
