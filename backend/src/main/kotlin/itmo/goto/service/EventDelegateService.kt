package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.domain.Decision
import itmo.goto.domain.Limits
import itmo.goto.domain.Meeting
import itmo.goto.domain.Role
import itmo.goto.domain.Schedule
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.UpdateEventRequest
import itmo.goto.model.Event
import itmo.goto.repository.EventRepository
import itmo.goto.repository.ParticipationRepository
import itmo.goto.web.ApiFailure
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
import itmo.goto.generated.model.Event as Card

@Service
class EventDelegateService(
    private val events: EventRepository,
    private val participations: ParticipationRepository,
    private val identity: Identity,
    private val clock: Clock,
    private val notifications: NotificationQueue,
) {
    @Transactional(noRollbackFor = [ApiFailure::class])
    fun change(id: UUID, request: UpdateEventRequest): Card = request.run {
        if (this == UpdateEventRequest()) {
            throw ApiFailure(ApiError.Code.VALIDATION_ERROR, "Изменение должно содержать хотя бы одно поле")
        }
        val event = locked(id)
        val now = clock.instant()
        val limits = Limits(minUsers ?: event.minUsers, maxUsers ?: event.maxUsers)
        val violations = limits.violations() +
            registrationClosesAt?.let { Schedule(it.toInstant(), event.startsAt, event.endsAt).violations(now) }
                .orEmpty()
        if (violations.isNotEmpty()) {
            throw ApiFailure(violations)
        }
        val deadline = registrationClosesAt?.toInstant() ?: event.registrationClosesAt
        decide(event, now, { meeting, role -> meeting.change(role, limits, deadline, now) }) {
            event.registrationClosesAt = deadline
            event.minUsers = minUsers ?: event.minUsers
            event.maxUsers = maxUsers ?: event.maxUsers
            event.title = title ?: event.title
            event.description = description ?: event.description
            event.icon = if (icon == null) event.icon else icon.takeIf { it.isNotEmpty() }
            event.category = category ?: event.category
        }
    }

    @Transactional(noRollbackFor = [ApiFailure::class])
    fun cancel(id: UUID): Card = locked(id).let { event ->
        val now = clock.instant()
        decide(event, now, { meeting, role -> meeting.cancel(role, now) }) {}
    }

    private fun locked(id: UUID): Event =
        events.locked(id) ?: throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")

    private fun decide(event: Event, now: Instant, rule: (Meeting, Role) -> Decision, change: () -> Unit): Card {
        val user = identity.user()
        val participant = participations.existsByEventIdAndUserId(checkNotNull(event.id), user)
        return rule(event.meeting(), event.role(user, participant)).fold(
            {
                notifications.settle(event, it, now)
                change()
                event.card(user, participant)
            },
            { meeting, refusal ->
                notifications.settle(event, meeting, now)
                throw ApiFailure(refusal)
            },
        )
    }
}
