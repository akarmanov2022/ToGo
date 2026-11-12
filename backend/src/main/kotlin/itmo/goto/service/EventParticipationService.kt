package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.domain.Agenda
import itmo.goto.domain.Role
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.ParticipantPage
import itmo.goto.model.Event
import itmo.goto.model.Participation
import itmo.goto.repository.EventRepository
import itmo.goto.repository.ParticipationRepository
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import itmo.goto.generated.model.Event as Card

/**
 * Lets the caller join and leave events and shows who takes part in them.
 *
 * Joining and leaving lock the event row first and the caller row second, the order every operation keeps,
 * so the participant limit and the schedule conflict rule hold under parallel requests.
 */
@Service
class EventParticipationService(
    private val events: EventRepository,
    private val participations: ParticipationRepository,
    private val users: UserRepository,
    private val identity: Identity,
    private val clock: Clock,
    private val notifications: NotificationQueue,
) {
    @Transactional(noRollbackFor = [ApiFailure::class])
    fun join(id: UUID): Card {
        val event = locked(id)
        val user = users.locked(identity.user())
            ?: throw ApiFailure(ApiError.Code.UNAUTHORIZED, "Учётная запись не найдена, войдите заново")
        val now = clock.instant()
        val role = event.role(identity.user(), participations.existsByEventIdAndUserId(id, identity.user()))
        return event.meeting()
            .join(role, Agenda(participations.active(identity.user()).map { it.meeting() }), now)
            .fold(
                {
                    if (role == Role.OUTSIDER) {
                        participations.save(Participation(event, user))
                        notifications.joined(event, user, now)
                    }
                    notifications.settle(event, it, now)
                    event.card(identity.user(), true, participations.score(id, identity.user()))
                },
                { meeting, refusal ->
                    notifications.settle(event, meeting, now)
                    throw ApiFailure(refusal)
                },
            )
    }

    @Transactional(noRollbackFor = [ApiFailure::class])
    fun leave(id: UUID) {
        val event = locked(id)
        val now = clock.instant()
        val role = event.role(identity.user(), participations.existsByEventIdAndUserId(id, identity.user()))
        event.meeting().leave(role, now).fold(
            {
                if (role == Role.PARTICIPANT) {
                    notifications.left(event, users.getReferenceById(identity.user()))
                    participations.deleteByEventIdAndUserId(id, identity.user())
                }
                notifications.settle(event, it, now)
            },
            { meeting, refusal ->
                notifications.settle(event, meeting, now)
                throw ApiFailure(refusal)
            },
        )
    }

    @Transactional(readOnly = true)
    fun participants(id: UUID, limit: Int, offset: Int): ParticipantPage {
        if (!events.existsById(id)) {
            throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")
        }
        return ParticipantPage(
            participations.members(id, limit, offset).map { it.summary() },
            participations.countByEventId(id),
            limit,
            offset,
        )
    }

    private fun locked(id: UUID): Event =
        events.locked(id) ?: throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")
}
