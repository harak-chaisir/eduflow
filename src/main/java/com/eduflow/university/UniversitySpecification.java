package com.eduflow.university;

import com.eduflow.university.dto.UniversitySearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA {@link Specification} factory for dynamic {@link University} queries.
 *
 * <p>The {@code tenantId} predicate is always ANDed in first so no query can leak
 * data across tenant boundaries.</p>
 */
public final class UniversitySpecification {

    private UniversitySpecification() {
        // utility class — no instantiation
    }

    public static Specification<University> from(UniversitySearchCriteria criteria, UUID tenantId) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Mandatory tenant scope
            predicates.add(cb.equal(root.get("tenant").get("id"), tenantId));

            if (criteria.getQ() != null && !criteria.getQ().isBlank()) {
                String pattern = likePattern(criteria.getQ());
                Predicate name = cb.like(cb.lower(root.get("name")), pattern);
                Predicate code = cb.like(cb.lower(root.get("code")), pattern);
                predicates.add(cb.or(name, code));
            }

            if (criteria.getCountry() != null && !criteria.getCountry().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("country")), criteria.getCountry().toLowerCase()));
            }

            if (criteria.getActive() != null) {
                predicates.add(cb.equal(root.get("active"), criteria.getActive()));
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
