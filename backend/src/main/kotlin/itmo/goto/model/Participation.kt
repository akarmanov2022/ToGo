package itmo.goto.model

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

/**
 * Membership of one user in one event, the organizer included.
 */
@Entity
@Table(name = "event_users")
class Participation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", updatable = false)
    val event: Event,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    val user: User,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null
}
