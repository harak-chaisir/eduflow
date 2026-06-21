package com.eduflow.user;

import com.eduflow.user.dto.StaffSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA {@link Specification} factory for dynamic staff ({@link User}) queries.
 *
 * <p>Every specification scopes results to the provided {@code tenantId} so no query
 * can leak users across tenant boundaries.</p>
 */
public final class StaffSpecification {

    private StaffSpecification() {
        // utility class — no instantiation
    }

    /**
     * Builds a compound {@link Specification} from the supplied search criteria.
     * The {@code tenantId} predicate is always applied first.
     *
     * @param criteria search parameters (nullable fields are ignored)
     * @param tenantId the tenant UUID — required; never sourced from the request
     */
    public static Specification<User> from(StaffSearchCriteria criteria, UUID tenantId) {
        return (root, query, cb) -> {
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
                predicates.add(cb.like(cb.lower(root.get("email")), likePattern(criteria.getEmail())));
            }

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            // Role filter joins the user_roles many-to-many; DISTINCT avoids duplicate rows.
            if (criteria.getRole() != null && !criteria.getRole().isBlank()) {
                predicates.add(cb.equal(root.join("roles").get("name"), criteria.getRole()));
                if (query != null) {
                    query.distinct(true);
                }
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Wraps a search term with SQL wildcards and lowercases it. LIKE metacharacters
     * ({@code %}, {@code _}, {@code \}) are escaped so user input cannot match unintended rows.
     */
    private static String likePattern(String term) {
        String escaped = term.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
