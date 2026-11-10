package itmo.goto.repository

import itmo.goto.generated.model.EventState
import itmo.goto.model.Event
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification

/**
 * Events in one stored state, which matches the current one once due events are brought up to date.
 */
class Staged(private val state: EventState) : Specification<Event> {
    override fun toPredicate(root: Root<Event>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate =
        builder.equal(root.get<String>("state"), state.value)
}
