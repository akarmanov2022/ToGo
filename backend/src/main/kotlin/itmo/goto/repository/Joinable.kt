package itmo.goto.repository

import itmo.goto.model.Event
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification
import java.time.Instant

/**
 * Events that still take new participants: recruiting, before the registration deadline, with a free place.
 */
class Joinable(private val now: Instant) : Specification<Event> {
    override fun toPredicate(root: Root<Event>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate =
        builder.and(
            builder.equal(root.get<String>("state"), "recruiting"),
            builder.greaterThan(root.get("registrationClosesAt"), now),
            builder.lessThan(root.get("participantsCount"), root.get<Int>("maxUsers")),
        )
}
