package itmo.goto.service

import itmo.goto.domain.Meeting
import itmo.goto.domain.State
import itmo.goto.model.Event
import itmo.goto.model.Notification
import itmo.goto.model.User
import itmo.goto.repository.NotificationRepository
import itmo.goto.repository.ParticipationRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/** Records delivery work in the same transaction as the event change. */
@Service
class NotificationQueue(
    private val notifications: NotificationRepository,
    private val participations: ParticipationRepository,
) {
    fun joined(event: Event, user: User, now: Instant) {
        add(event, user, "joined", now)
        reminders(event, user, now)
    }

    fun reminders(event: Event, user: User, now: Instant) {
        listOf("reminder_day" to Duration.ofDays(1), "reminder_hour" to Duration.ofHours(1))
            .forEach { (kind, before) ->
                val due = event.startsAt.minus(before)
                if (due.isAfter(now)) add(event, user, kind, due)
            }
    }

    fun left(event: Event, user: User) {
        notifications.deleteByUserIdAndEventIdAndKindInAndSentAtIsNull(
            checkNotNull(user.id), checkNotNull(event.id), listOf("reminder_day", "reminder_hour"),
        )
    }

    fun settle(event: Event, meeting: Meeting, now: Instant) {
        val before = event.meeting().state()
        val after = meeting.state()
        event.settle(meeting)
        if (before == State.RECRUITING && after != before) {
            when (after) {
                State.CANCELLED_BY_SYSTEM -> notifyMembers(event, "cancelled", now, true)
                State.REGISTRATION_CLOSED, State.FINISHED -> notifyMembers(event, "confirmed", now, true)
                else -> Unit
            }
        }
        if (before != State.CANCELLED_BY_ORGANIZER && after == State.CANCELLED_BY_ORGANIZER) {
            notifyMembers(event, "cancelled", now, false)
        }
    }

    private fun notifyMembers(event: Event, kind: String, now: Instant, includeOrganizer: Boolean) {
        participations.allMembers(checkNotNull(event.id)).forEach { user ->
            if (includeOrganizer || user.id != event.organizer.id) add(event, user, kind, now)
        }
    }

    private fun add(event: Event, user: User, kind: String, due: Instant) {
        if (!notifications.existsByUserIdAndEventIdAndKind(checkNotNull(user.id), checkNotNull(event.id), kind)) {
            notifications.save(Notification(user, event, kind, due))
        }
    }
}
