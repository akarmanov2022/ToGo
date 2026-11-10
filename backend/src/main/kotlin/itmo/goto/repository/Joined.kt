package itmo.goto.repository

import itmo.goto.model.Event
import itmo.goto.model.Participation
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

/**
 * Events the user takes part in, the ones the user organizes included.
 */
class Joined(private val user: UUID) : Specification<Event> {
    override fun toPredicate(root: Root<Event>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate =
        builder.exists(
            query.subquery(UUID::class.java).let { subquery ->
                subquery.from(Participation::class.java).let { participation ->
                    subquery.select(participation.get<Event>("event").get("id")).where(
                        builder.equal(participation.get<Event>("event"), root),
                        builder.equal(participation.get<Any>("user").get<UUID>("id"), user),
                    )
                }
            },
        )
}
