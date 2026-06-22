package com.eduflow.university;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.university.dto.CourseRequest;
import com.eduflow.university.dto.CourseResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Core service for {@link Course} master data.
 *
 * <p>Courses belong to a {@link University}; both are tenant-scoped. The tenant is
 * always resolved from the authenticated principal.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final UniversityService universityService;
    private final AuditService auditService;

    // ── Create ───────────────────────────────────────────────────────────────

    public CourseResponse create(UUID universityId, CourseRequest request) {
        UUID tenantId = resolvedTenantId();
        University university = universityService.findOrThrow(universityId, tenantId);
        log.info("Creating course '{}' for university {} (tenant {})", request.getName(), universityId, tenantId);

        Course course = Course.builder()
                .tenant(university.getTenant())
                .university(university)
                .name(request.getName())
                .level(request.getLevel())
                .intakeMonth(request.getIntakeMonth())
                .intakeYear(request.getIntakeYear())
                .tuitionFee(request.getTuitionFee())
                .entryRequirements(request.getEntryRequirements())
                .active(request.getActive() == null || request.getActive())
                .build();

        Course saved = courseRepository.save(course);

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.COURSE_CREATED, "COURSE", saved.getId());

        return CourseResponse.from(saved);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CourseResponse get(UUID id) {
        return CourseResponse.from(findOrThrow(id, resolvedTenantId()));
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> listForUniversity(UUID universityId) {
        UUID tenantId = resolvedTenantId();
        // Ensures the university exists within this tenant before listing.
        universityService.findOrThrow(universityId, tenantId);
        return courseRepository.findByUniversityIdAndTenantIdOrderByNameAsc(universityId, tenantId)
                .stream().map(CourseResponse::from).toList();
    }

    /** All active courses in the calling tenant — drives the "apply to course" picker. */
    @Transactional(readOnly = true)
    public List<CourseResponse> listActive() {
        return courseRepository.findByTenantIdAndActiveTrueOrderByNameAsc(resolvedTenantId())
                .stream().map(CourseResponse::from).toList();
    }

    // ── Update ───────────────────────────────────────────────────────────────

    public CourseResponse update(UUID id, CourseRequest request) {
        UUID tenantId = resolvedTenantId();
        Course course = findOrThrow(id, tenantId);

        if (request.getName() != null)              course.setName(request.getName());
        if (request.getLevel() != null)             course.setLevel(request.getLevel());
        if (request.getIntakeMonth() != null)       course.setIntakeMonth(request.getIntakeMonth());
        if (request.getIntakeYear() != null)        course.setIntakeYear(request.getIntakeYear());
        if (request.getTuitionFee() != null)        course.setTuitionFee(request.getTuitionFee());
        if (request.getEntryRequirements() != null) course.setEntryRequirements(request.getEntryRequirements());
        if (request.getActive() != null)            course.setActive(request.getActive());

        Course saved = courseRepository.save(course);
        log.info("Course {} updated by '{}'", id, currentUsername());

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.COURSE_UPDATED, "COURSE", id);

        return CourseResponse.from(saved);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Course findOrThrow(UUID id, UUID tenantId) {
        return courseRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new CourseNotFoundException(id));
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
