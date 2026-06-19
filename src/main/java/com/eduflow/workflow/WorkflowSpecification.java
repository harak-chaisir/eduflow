package com.eduflow.workflow;

import com.eduflow.workflow.dto.WorkflowSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA {@link Specification} factory for dynamic {@link WorkflowTemplate} queries.
 *
 * <p>The {@code tenantId} predicate is always applied so no query can leak data across
 * tenant boundaries.</p>
 */
public final class WorkflowSpecification {

    private WorkflowSpecification() {
        // utility class — no instantiation
    }

    public static Specification<WorkflowTemplate> from(WorkflowSearchCriteria criteria, UUID tenantId) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── Mandatory tenant scope ───────────────────────────────────────
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));

            // ── Optional filters ─────────────────────────────────────────────
            if (criteria.getName() != null && !criteria.getName().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), likePattern(criteria.getName())));
            }
            if (criteria.getCountry() != null && !criteria.getCountry().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("country")), criteria.getCountry().toLowerCase()));
            }
            if (criteria.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), criteria.getActive()));
            }
            if (criteria.getArchived() != null) {
                predicates.add(cb.equal(root.get("archived"), criteria.getArchived()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String likePattern(String term) {
        String escaped = term.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
