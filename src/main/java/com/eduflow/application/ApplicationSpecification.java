package com.eduflow.application;

import com.eduflow.application.dto.ApplicationSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA {@link Specification} factory for dynamic {@link Application} queries.
 *
 * <p>The {@code tenantId} predicate is always ANDed in first so no query can leak
 * data across tenant boundaries.</p>
 */
public final class ApplicationSpecification {

    private ApplicationSpecification() {
        // utility class — no instantiation
    }

    public static Specification<Application> from(ApplicationSearchCriteria criteria, UUID tenantId) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Mandatory tenant scope
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));

            if (criteria.getStudentId() != null) {
                predicates.add(cb.equal(root.get("student").get("id"), criteria.getStudentId()));
            }

            if (criteria.getCourseId() != null) {
                predicates.add(cb.equal(root.get("course").get("id"), criteria.getCourseId()));
            }

            if (criteria.getUniversityId() != null) {
                predicates.add(cb.equal(
                        root.get("course").get("university").get("id"), criteria.getUniversityId()));
            }

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
