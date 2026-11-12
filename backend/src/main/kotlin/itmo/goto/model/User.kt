package itmo.goto.model

import itmo.goto.generated.model.CurrentUser
import itmo.goto.generated.model.EventCategory
import itmo.goto.generated.model.Rating
import itmo.goto.generated.model.UserProfile
import itmo.goto.generated.model.UserSummary
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Formula
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

/**
 * ToGo account of one Telegram user, holding the profile other participants see.
 */
@Entity
@Table(name = "users")
class User(
    @Column(updatable = false)
    val tgId: Long,
    var tgLogin: String?,
    var name: String,
    var photoUrl: String?,
) {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    var id: UUID? = null

    var city: String? = null

    @ElementCollection
    @CollectionTable(name = "user_categories", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    var categories: MutableSet<EventCategory> = mutableSetOf()

    @Formula("(SELECT count(*) FROM events e WHERE e.organizer_id = id)")
    private var created: Int = 0

    @Formula(
        "(SELECT count(*) FROM event_users p JOIN events e ON e.id = p.event_id " +
            "WHERE p.user_id = id AND e.state = 'finished')",
    )
    private var visited: Int = 0

    @Formula(
        "(SELECT round(avg(r.score), 1) FROM event_ratings r JOIN events e ON e.id = r.event_id " +
            "WHERE e.organizer_id = id)",
    )
    private var average: Double? = null

    @Formula("(SELECT count(*) FROM event_ratings r JOIN events e ON e.id = r.event_id WHERE e.organizer_id = id)")
    private var ratings: Int = 0

    fun current(): CurrentUser = CurrentUser(
        checkNotNull(id) { "User has no id before it is saved" },
        tgId,
        tgLogin,
        name,
        photoUrl,
        city,
        categories.sorted().toSet(),
        created,
        visited,
        average?.let { Rating(it, ratings) },
    )

    fun profile(): UserProfile = UserProfile(
        checkNotNull(id) { "User has no id before it is saved" },
        tgLogin,
        name,
        photoUrl,
        city,
        categories.sorted().toSet(),
        created,
        visited,
        average?.let { Rating(it, ratings) },
    )

    fun summary(): UserSummary = UserSummary(checkNotNull(id) { "User has no id before it is saved" }, tgLogin)
}
