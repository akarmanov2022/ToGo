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
    override fun rateEvent(eventId: UUID, rateEventRequest: RateEventRequest): Card =
        rate(eventId, identity.user(), rateEventRequest.score)

    /**
     * Saves the only [score] that [user] gives the event, refusing a second one.
     */
    @Transactional(noRollbackFor = [ApiFailure::class])
    fun rate(eventId: UUID, user: UUID, score: Int): Card {
        val event = events.locked(eventId) ?: throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")
        val now = clock.instant()
        val decision = event.meeting().rate(now)
        notifications.settle(event, decision.fold({ it }, { meeting, _ -> meeting }), now)
        val failure = when (event.role(user, participations.existsByEventIdAndUserId(eventId, user))) {
            Role.ORGANIZER -> ApiFailure(ApiError.Code.FORBIDDEN, "Организатор не может оценить своё событие")
            Role.OUTSIDER -> ApiFailure(ApiError.Code.FORBIDDEN, "Оценить событие могут только его участники")
            Role.PARTICIPANT -> decision.fold({ null }, { _, refusal -> ApiFailure(refusal) })
                ?: ApiFailure(ApiError.Code.ALREADY_RATED, "Вы уже оценили это событие")
                    .takeIf { participations.score(eventId, user) != null }
        }
        if (failure != null) throw failure
        ratings.rate(eventId, user, score, now)
        return events.findById(eventId).orElseThrow().card(user, true, score)
    }
}
