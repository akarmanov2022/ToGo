package itmo.goto.model

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Interest from the shared directory that migrations fill and users and events refer to.
 */
@Entity
@Table(name = "categories")
class Category(
    @Id
    val id: UUID,
    val name: String,
) {
    fun view(): itmo.goto.generated.model.Category = itmo.goto.generated.model.Category(id, name)
}
