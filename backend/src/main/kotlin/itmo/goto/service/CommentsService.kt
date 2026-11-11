package itmo.goto.service

import itmo.goto.auth.Identity
import itmo.goto.generated.api.CommentsApiService
import itmo.goto.generated.model.ApiError
import itmo.goto.generated.model.CommentPage
import itmo.goto.generated.model.CreateCommentRequest
import itmo.goto.model.Comment
import itmo.goto.repository.CommentRepository
import itmo.goto.repository.EventRepository
import itmo.goto.repository.ParticipationRepository
import itmo.goto.repository.UserRepository
import itmo.goto.web.ApiFailure
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID
import itmo.goto.generated.model.Comment as Note

/** * Lets participants of an event, the organizer included, talk under it and shows that talk to every signed-in user.
 */
@Service
class CommentsService(
    private val comments: CommentRepository,
    private val events: EventRepository,
    private val participations: ParticipationRepository,
    private val users: UserRepository,
    private val identity: Identity,
    private val clock: Clock,
    private val notifications: NotificationQueue,
) : CommentsApiService {
    @Transactional(readOnly = true)
    override fun getEventComments(eventId: UUID, limit: Int, offset: Int): CommentPage {
        if (!events.existsById(eventId)) {
            throw ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")
        }
        return CommentPage(
            comments.page(eventId, limit, offset).map { it.note() },
            comments.countByEventId(eventId),
            limit,
            offset,
        )
    }

    @Transactional
    override fun createEventComment(eventId: UUID, createCommentRequest: CreateCommentRequest): Note {
        val event = events.findById(eventId).orElseThrow {
            ApiFailure(ApiError.Code.EVENT_NOT_FOUND, "Событие не найдено")
        }
        if (!participations.existsByEventIdAndUserId(eventId, identity.user())) {
            throw ApiFailure(ApiError.Code.FORBIDDEN, "Комментировать могут только участники события")
        }
        val author = users.findById(identity.user()).orElseThrow {
            ApiFailure(ApiError.Code.UNAUTHORIZED, "Учётная запись не найдена, войдите заново")
        }
        val now = clock.instant()
        val comment = comments.saveAndFlush(Comment(event, author, createCommentRequest.text, now))
        notifications.comment(event, identity.user(), checkNotNull(comment.id), now)
        return comment.note()
    }
}
