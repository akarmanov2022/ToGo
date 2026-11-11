package itmo.goto.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import itmo.goto.generated.model.Comment as Note

/**
 * Remark that one participant leaves under an event, which nobody can change or remove afterwards.
 */
@Entity
@Table(name = "comments")
class Comment(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", updatable = false)
    val event: Event,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", updatable = false)
    val author: User,
    @Column(updatable = false)
    val text: String,
    @Column(updatable = false)
    val createdAt: Instant,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    fun note(): Note = Note(
        checkNotNull(id) { "Comment has no id before it is saved" },
        author.summary(),
        text,
        createdAt.atOffset(ZoneOffset.UTC),
    )
}
