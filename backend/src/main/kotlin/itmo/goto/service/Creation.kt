package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.domain.Agenda
import itmo.goto.domain.Limits
import itmo.goto.domain.Point
import itmo.goto.domain.Refusal
import itmo.goto.domain.Schedule
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CreateEventRequest
import itmo.goto.model.Event
import itmo.goto.model.Participation
import itmo.goto.repository.EventRepository
import itmo.goto.repository.ParticipationRepository
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import itmo.goto.generated.model.Event as Card

/**
 * Creates events whose organizer is the caller and their first participant.
 *
 * The organizer row stays locked until the event is saved, so two parallel creations cannot both slip past
 * the schedule conflict rule.
 */
@Service
class Creation(
    private val events: EventRepository,
    private val participations: ParticipationRepository,
    private val users: UserRepository,
    private val identity: Identity,
    private val clock: Clock,
) {
    @Transactional
    fun event(request: CreateEventRequest): Card = request.run {
        val organizer = users.locked(identity.user())
            ?: throw ApiFailure(ApiError.Code.UNAUTHORIZED, "Учётная запись не найдена, войдите заново")
        val now = clock.instant()
        val schedule = Schedule(registrationClosesAt.toInstant(), startsAt.toInstant(), endsAt.toInstant())
        val violations =
            schedule.violations(now) + Limits(minUsers, maxUsers).violations() + Point(latitude, longitude).violations()
        if (violations.isNotEmpty()) {
            throw ApiFailure(violations)
        }
        if (Agenda(participations.active(identity.user()).map { it.meeting() }).conflicts(schedule, now)) {
            throw ApiFailure(Refusal.SCHEDULE_CONFLICT)
        }
        val event = events.save(
            Event(
                title,
                description,
                location,
                icon?.takeIf { it.isNotEmpty() },
                startsAt.toInstant(),
                endsAt.toInstant(),
                registrationClosesAt.toInstant(),
                minUsers,
                maxUsers,
                category,
                latitude,
                longitude,
                organizer,
            ),
        )
        participations.save(Participation(event, organizer))
        event.card(identity.user(), true)
    }
}
