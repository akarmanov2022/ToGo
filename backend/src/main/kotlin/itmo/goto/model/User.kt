package itmo.goto.model

import itmo.goto.generated.model.CurrentUser
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
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

    @ManyToMany
    @JoinTable(
        name = "user_categories",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "category_id")],
    )
    var categories: MutableSet<Category> = mutableSetOf()

    fun current(): CurrentUser = CurrentUser(
        checkNotNull(id) { "User has no id before it is saved" },
        tgId,
        tgLogin,
        name,
        photoUrl,
        city,
        categories.sortedWith(compareBy({ it.name }, { it.id })).map { it.view() }.toSet(),
    )
}
