package com.eduflow.university;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA {@link Specification} factory for dynamic {@link Course} queries.
 *
 * <p>The {@code tenantId} predicate is always ANDed in first.</p>
 */
public final class CourseSpecification {

    private CourseSpecification() {
        // utility class — no instantiation
    }

    /**
     * Builds a course specification scoped to {@code tenantId}, optionally filtered
     * by university and a free-text term matched against the course name.
     */
    public static Specification<Course> of(UUID tenantId, UUID universityId, String q) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Mandatory tenant scope
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));

            if (universityId != null) {
                predicates.add(cb.equal(root.get("university").get("id"), universityId));
            }

            if (q != null && !q.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), likePattern(q)));
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
