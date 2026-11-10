package itmo.goto.domain

import java.time.Duration
import java.time.Instant

class Schedule(private val closes: Instant, private val starts: Instant, private val ends: Instant) {
    fun violations(now: Instant): List<Violation> = listOfNotNull(
        Violation("registrationClosesAt", "Срок регистрации должен быть в будущем").takeIf { !closes.isAfter(now) },
        Violation("registrationClosesAt", "Срок регистрации не может быть позже начала").takeIf {
            closes.isAfter(starts)
        },
        Violation("endsAt", "Окончание должно быть позже начала").takeIf { !ends.isAfter(starts) },
    )

    fun open(now: Instant): Boolean = now.isBefore(closes)

    fun started(now: Instant): Boolean = !now.isBefore(starts)

    fun ended(now: Instant): Boolean = !now.isBefore(ends)

    fun near(other: Schedule): Boolean = Duration.between(starts, other.starts).abs() < gap

    fun moved(deadline: Instant): Schedule = Schedule(deadline, starts, ends)

    private companion object {
        val gap: Duration = Duration.ofHours(3)
    }
}
