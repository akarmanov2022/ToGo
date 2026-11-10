package itmo.goto.domain

import java.time.Instant

class Agenda(private val meetings: List<Meeting>) {
    fun conflicts(schedule: Schedule, now: Instant): Boolean = meetings.any { it.clashes(schedule, now) }
}
