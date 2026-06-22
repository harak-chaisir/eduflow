package com.eduflow.university;

import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for {@link UniversityRepository}, asserting tenant isolation.
 * Runs against the configured PostgreSQL; changes roll back via {@link Transactional}.
 */
@SpringBootTest
@Transactional
class UniversityRepositoryTest {

    @Autowired UniversityRepository universityRepository;
    @Autowired TenantRepository tenantRepository;

    private UUID tenantAId;
    private UUID tenantBId;

    @BeforeEach
    void setUp() {
        // Avoid a leaked SecurityContext from prior unit tests interfering with JPA auditing.
        SecurityContextHolder.clearContext();
        tenantAId = tenantRepository.save(
                Tenant.builder().name("Tenant A").slug("tenant-a-" + suffix()).build()).getId();
        tenantBId = tenantRepository.save(
                Tenant.builder().name("Tenant B").slug("tenant-b-" + suffix()).build()).getId();
    }

    @Test
    void findByIdAndTenantId_whenForeignTenant_returnsEmpty() {
        Tenant a = tenantRepository.findById(tenantAId).orElseThrow();
        University uni = universityRepository.save(University.builder()
                .tenant(a).name("Oxford").country("UK").active(true).build());

        Optional<University> sameTenant = universityRepository.findByIdAndTenantId(uni.getId(), tenantAId);
        Optional<University> foreignTenant = universityRepository.findByIdAndTenantId(uni.getId(), tenantBId);

        assertThat(sameTenant).isPresent();
        assertThat(foreignTenant).isEmpty();
    }

    @Test
    void findByTenantId_returnsOnlyOwnTenantRows() {
        Tenant a = tenantRepository.findById(tenantAId).orElseThrow();
        Tenant b = tenantRepository.findById(tenantBId).orElseThrow();
        universityRepository.save(University.builder().tenant(a).name("A-Uni").country("UK").active(true).build());
        universityRepository.save(University.builder().tenant(b).name("B-Uni").country("US").active(true).build());

        assertThat(universityRepository.findByTenantId(tenantAId))
                .extracting(University::getName).containsExactly("A-Uni");
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
