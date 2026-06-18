package com.eduflow.tenant;

import com.eduflow.student.StudentRepository;
import com.eduflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Centralises plan-limit enforcement (PRD §9). Other modules call this before
 * creating a tenant-scoped resource so the rule lives in one place. A {@code null}
 * limit means unlimited (ENTERPRISE). Exceeding a limit throws
 * {@link TenantLimitExceededException} → {@code 409 CONFLICT}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantLimitService {

    private final TenantRepository tenantRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    /** Throws if adding a student would exceed the tenant's student cap. */
    @Transactional(readOnly = true)
    public void assertCanAddStudent(UUID tenantId) {
        Integer max = tenant(tenantId).getMaxStudents();
        if (max != null && studentRepository.countByTenantId(tenantId) >= max) {
            throw new TenantLimitExceededException("students", max);
        }
    }

    /** Throws if adding a staff user would exceed the tenant's staff cap. */
    @Transactional(readOnly = true)
    public void assertCanAddStaff(UUID tenantId) {
        Integer max = tenant(tenantId).getMaxStaffUsers();
        if (max != null && userRepository.countByTenantId(tenantId) >= max) {
            throw new TenantLimitExceededException("staff users", max);
        }
    }

    private Tenant tenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }
}
