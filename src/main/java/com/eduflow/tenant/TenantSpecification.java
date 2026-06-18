package com.eduflow.tenant;

import com.eduflow.tenant.dto.TenantSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA {@link Specification} factory for dynamic {@link Tenant} queries.
 *
 * <p>Tenant listing is a platform-level (super-admin) operation, so — unlike
 * business-entity specifications — there is no tenant predicate to AND in.</p>
 */
public final class TenantSpecification {

    private TenantSpecification() {
        // utility class — no instantiation
    }

    /**
     * Builds a compound {@link Specification} from the supplied search criteria.
     * Every predicate is ANDed together; null/blank criteria are ignored.
     */
    public static Specification<Tenant> from(TenantSearchCriteria criteria) {
        return (root, _, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.getQ() != null && !criteria.getQ().isBlank()) {
                String pattern = likePattern(criteria.getQ());
                Predicate name = cb.like(cb.lower(root.get("name")), pattern);
                Predicate slug = cb.like(cb.lower(root.get("slug")), pattern);
                predicates.add(cb.or(name, slug));
            }

            if (criteria.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.getStatus()));
            }

            if (criteria.getPlan() != null) {
                predicates.add(cb.equal(root.get("plan"), criteria.getPlan()));
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
