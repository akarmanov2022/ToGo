package itmo.goto.repository

import itmo.goto.model.Event
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

/**
 * Events that start strictly before the moment.
 */
class StartingBefore(private val moment: Instant) : Specification<Event> {
    override fun toPredicate(root: Root<Event>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate =
        builder.lessThan(root.get("startsAt"), moment)
}
