package com.eduflow.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Tenant} entities.
 *
 * <p>Tenant queries are platform-level (super-admin), so unlike business
 * repositories they are not scoped to a single tenant. {@link JpaSpecificationExecutor}
 * powers the filtered list endpoint via {@link TenantSpecification}.</p>
 */
@Repository
public interface TenantRepository
        extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
