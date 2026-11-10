package itmo.goto.repository

import itmo.goto.model.Event
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

/**
 * Events that start at the moment or later.
 */
class StartingFrom(private val moment: Instant) : Specification<Event> {
    override fun toPredicate(root: Root<Event>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate =
        builder.greaterThanOrEqualTo(root.get("startsAt"), moment)
}
