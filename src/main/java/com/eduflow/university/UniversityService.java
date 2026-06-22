package com.eduflow.university;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.university.dto.UniversityRequest;
import com.eduflow.university.dto.UniversityResponse;
import com.eduflow.university.dto.UniversitySearchCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Core service for the University master-data domain.
 *
 * <p>All methods resolve {@code tenantId} from the authenticated principal via
 * {@link SecurityContextHolder} — never from a request parameter.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UniversityService {

    private final UniversityRepository universityRepository;
    private final TenantRepository tenantRepository;
    private final AuditService auditService;

    // ── Create ───────────────────────────────────────────────────────────────

    public UniversityResponse create(UniversityRequest request) {
        UUID tenantId = resolvedTenantId();
        log.info("Creating university '{}' ({}) for tenant {}", request.getName(), request.getCountry(), tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        University university = University.builder()
                .tenant(tenant)
                .name(request.getName())
                .country(request.getCountry())
                .city(request.getCity())
                .website(request.getWebsite())
                .code(request.getCode())
                .active(request.getActive() == null || request.getActive())
                .build();

        University saved = universityRepository.save(university);

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.UNIVERSITY_CREATED, "UNIVERSITY", saved.getId());

        return UniversityResponse.from(saved);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public UniversityResponse get(UUID id) {
        return UniversityResponse.from(findOrThrow(id, resolvedTenantId()));
    }

    @Transactional(readOnly = true)
    public Page<UniversityResponse> search(UniversitySearchCriteria criteria, Pageable pageable) {
        UUID tenantId = resolvedTenantId();
        return universityRepository
                .findAll(UniversitySpecification.from(criteria, tenantId), pageable)
                .map(UniversityResponse::from);
    }

    /** Active universities for the calling tenant, ordered by name — drives form pickers. */
    @Transactional(readOnly = true)
    public List<UniversityResponse> listActive() {
        return universityRepository.findByTenantIdAndActiveTrueOrderByNameAsc(resolvedTenantId())
                .stream().map(UniversityResponse::from).toList();
    }

    // ── Update ───────────────────────────────────────────────────────────────

    public UniversityResponse update(UUID id, UniversityRequest request) {
        UUID tenantId = resolvedTenantId();
        University university = findOrThrow(id, tenantId);

        if (request.getName() != null)    university.setName(request.getName());
        if (request.getCountry() != null) university.setCountry(request.getCountry());
        if (request.getCity() != null)    university.setCity(request.getCity());
        if (request.getWebsite() != null) university.setWebsite(request.getWebsite());
        if (request.getCode() != null)    university.setCode(request.getCode());
        if (request.getActive() != null)  university.setActive(request.getActive());

        University saved = universityRepository.save(university);
        log.info("University {} updated by '{}'", id, currentUsername());

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.UNIVERSITY_UPDATED, "UNIVERSITY", id);

        return UniversityResponse.from(saved);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    University findOrThrow(UUID id, UUID tenantId) {
        return universityRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new UniversityNotFoundException(id));
    }

    private UUID resolvedTenantId() {
        return principal().getTenantId();
    }

    private UUID resolvedUserId() {
        return principal().getUserId();
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private EduFlowUserDetails principal() {
        return (EduFlowUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
