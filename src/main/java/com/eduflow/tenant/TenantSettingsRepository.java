package com.eduflow.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link TenantSettings}. Keyed 1:1 by the owning tenant id.
 */
@Repository
public interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {

    Optional<TenantSettings> findByTenantId(UUID tenantId);
}
