package itmo.goto.service

import itmo.goto.generated.api.CommentsApiService
import itmo.goto.generated.model.Comment
import itmo.goto.generated.model.CommentPage
import itmo.goto.generated.model.CreateCommentRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** Contract placeholder for event comments, which are second-priority work. */
@Service
class CommentsService : CommentsApiService {
    override fun getEventComments(eventId: UUID, limit: Int, offset: Int): CommentPage =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Comment listing is not implemented yet")

    override fun createEventComment(eventId: UUID, createCommentRequest: CreateCommentRequest): Comment =
        throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Commenting is not implemented yet")
}
