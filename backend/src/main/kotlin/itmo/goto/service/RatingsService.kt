package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.domain.Role
import itmo.goto.generated.api.RatingsApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.RateEventRequest
import itmo.goto.repository.EventRepository
import itmo.goto.repository.ParticipationRepository
import itmo.goto.repository.RatingRepository
import itmo.goto.web.ApiFailure
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import itmo.goto.generated.model.Event as Card

@Service
class RatingsService(
    private val events: EventRepository,
    private val participations: ParticipationRepository,
    private val ratings: RatingRepository,
    private val identity: Identity,
    private val clock: Clock,
    private val notifications: NotificationQueue,
) : RatingsApiService {
    @Transactional(noRollbackFor = [ApiFailure::class])
    override fun rateEvent(eventId: UUID, rateEventRequest: RateEventRequest): Card {
        val event = events.locked(eventId) ?: throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")
        val now = clock.instant()
        val decision = event.meeting().rate(now)
        notifications.settle(event, decision.fold({ it }, { meeting, _ -> meeting }), now)
        when (event.role(identity.user(), participations.existsByEventIdAndUserId(eventId, identity.user()))) {
            Role.ORGANIZER -> throw ApiFailure(ApiError.Code.FORBIDDEN, "Организатор не может оценить своё событие")
            Role.OUTSIDER -> throw ApiFailure(ApiError.Code.FORBIDDEN, "Оценить событие могут только его участники")
            Role.PARTICIPANT -> decision.fold({}, { _, refusal -> throw ApiFailure(refusal) })
        }
        ratings.rate(eventId, identity.user(), rateEventRequest.score, now)
        return events.findById(eventId).orElseThrow().card(identity.user(), true, rateEventRequest.score)
    }
}
