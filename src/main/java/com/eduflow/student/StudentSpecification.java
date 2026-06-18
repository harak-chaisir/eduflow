package com.eduflow.student;

import com.eduflow.student.dto.StudentSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA {@link Specification} factory for dynamic {@link Student} queries.
 *
 * <p>All specifications automatically scope results to the provided {@code tenantId}
 * so no query can leak data across tenant boundaries.</p>
 */
public final class StudentSpecification {

    private StudentSpecification() {
        // utility class — no instantiation
    }

    /**
     * Builds a compound {@link Specification} from the supplied search criteria.
     * Every predicate is ANDed together. The {@code tenantId} predicate is always applied.
     *
     * @param criteria  search parameters (nullable fields are ignored)
     * @param tenantId  the tenant UUID — required; never sourced from the request
     * @return a Specification ready to pass to {@link StudentRepository#findAll(Specification, org.springframework.data.domain.Pageable)}
     */
    public static Specification<Student> from(StudentSearchCriteria criteria, UUID tenantId) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // ── Mandatory tenant scope ───────────────────────────────────────
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));

            // ── Optional filters ─────────────────────────────────────────────
            if (criteria.getName() != null && !criteria.getName().isBlank()) {
                String pattern = likePattern(criteria.getName());
                Predicate firstName = cb.like(cb.lower(root.get("firstName")), pattern);
                Predicate lastName  = cb.like(cb.lower(root.get("lastName")),  pattern);
                predicates.add(cb.or(firstName, lastName));
            }

            if (criteria.getEmail() != null && !criteria.getEmail().isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("email")),
                        likePattern(criteria.getEmail())
                ));
            }

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            if (criteria.getAssignedCounselorId() != null) {
                predicates.add(cb.equal(
                        root.get("assignedCounselor").get("id"),
                        criteria.getAssignedCounselorId()
                ));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Wraps a search term with SQL wildcard characters and lowercases it.
     * Special LIKE characters ({@code %}, {@code _}, {@code \}) in the term are
     * escaped so a user-supplied value cannot accidentally match unintended rows.
     */
    private static String likePattern(String term) {
        String escaped = term.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}

