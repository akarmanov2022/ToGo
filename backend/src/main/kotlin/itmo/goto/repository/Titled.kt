package itmo.goto.repository

import itmo.goto.model.Event
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.springframework.data.jpa.domain.Specification

/**
 * Events whose title contains the text regardless of case and surrounding spaces, with LIKE wildcards in the text
 * matching only themselves.
 */
class Titled(private val text: String) : Specification<Event> {
    override fun toPredicate(root: Root<Event>, query: CriteriaQuery<*>, builder: CriteriaBuilder): Predicate =
        builder.like(
            builder.lower(root.get("title")),
            "%" + text.trim().lowercase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%",
            '\\',
        )
}
