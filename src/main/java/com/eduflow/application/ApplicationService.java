package com.eduflow.application;

import com.eduflow.application.dto.ApplicationResponse;
import com.eduflow.application.dto.ApplicationSearchCriteria;
import com.eduflow.application.dto.CreateApplicationRequest;
import com.eduflow.application.dto.UpdateApplicationRequest;
import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentNotFoundException;
import com.eduflow.student.StudentRepository;
import com.eduflow.university.Course;
import com.eduflow.university.CourseNotFoundException;
import com.eduflow.university.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core service for the Application domain.
 *
 * <p>All methods resolve {@code tenantId} from the authenticated principal via
 * {@link SecurityContextHolder} — never from a request parameter.</p>
 *
 * <p>Status transitions are validated against {@link #ALLOWED_TRANSITIONS}. An
 * {@link InvalidApplicationStatusTransitionException} is thrown for illegal moves.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ApplicationService {

    // ── Allowed status transitions ───────────────────────────────────────────

    static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS;
    static {
        Map<ApplicationStatus, Set<ApplicationStatus>> m = new EnumMap<>(ApplicationStatus.class);
        m.put(ApplicationStatus.DRAFT,        EnumSet.of(ApplicationStatus.SUBMITTED));
        m.put(ApplicationStatus.SUBMITTED,    EnumSet.of(ApplicationStatus.UNDER_REVIEW, ApplicationStatus.REJECTED));
        m.put(ApplicationStatus.UNDER_REVIEW, EnumSet.of(ApplicationStatus.CONDITIONAL_OFFER,
                ApplicationStatus.UNCONDITIONAL_OFFER, ApplicationStatus.REJECTED));
        m.put(ApplicationStatus.CONDITIONAL_OFFER, EnumSet.of(ApplicationStatus.UNCONDITIONAL_OFFER,
                ApplicationStatus.REJECTED));
        m.put(ApplicationStatus.UNCONDITIONAL_OFFER, EnumSet.noneOf(ApplicationStatus.class));
        m.put(ApplicationStatus.REJECTED,          EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED_TRANSITIONS = Map.copyOf(m);
    }

    /** Statuses that record a decision (populate {@code decisionDate}). */
    private static final Set<ApplicationStatus> DECISION_STATUSES = EnumSet.of(
            ApplicationStatus.CONDITIONAL_OFFER,
            ApplicationStatus.UNCONDITIONAL_OFFER,
            ApplicationStatus.REJECTED);

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final ApplicationRepository applicationRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final AuditService auditService;

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Creates a {@code DRAFT} application for the given student against a course.
     *
     * @throws StudentNotFoundException     if the student is not in this tenant
     * @throws CourseNotFoundException      if the course is not in this tenant
     * @throws DuplicateApplicationException if the student already applied to this course
     */
    public ApplicationResponse createApplication(UUID studentId, CreateApplicationRequest request) {
        UUID tenantId = resolvedTenantId();

        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        Course course = courseRepository.findByIdAndTenantId(request.getCourseId(), tenantId)
                .orElseThrow(() -> new CourseNotFoundException(request.getCourseId()));

        if (applicationRepository.existsByStudentIdAndCourseIdAndTenantId(studentId, course.getId(), tenantId)) {
            throw new DuplicateApplicationException(studentId, course.getId());
        }

        Application application = Application.builder()
                .tenant(student.getTenant())
                .student(student)
                .course(course)
                .status(ApplicationStatus.DRAFT)
                .notes(request.getNotes())
                .build();

        Application saved = applicationRepository.save(application);
        log.info("Application {} created for student {} → course {} (tenant {})",
                saved.getId(), studentId, course.getId(), tenantId);

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.APPLICATION_CREATED, "APPLICATION", saved.getId());

        return ApplicationResponse.from(saved);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID id) {
        return ApplicationResponse.from(findOrThrow(id, resolvedTenantId()));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> listForStudent(UUID studentId) {
        UUID tenantId = resolvedTenantId();
        return applicationRepository.findByStudentIdAndTenantIdOrderByCreatedAtDesc(studentId, tenantId)
                .stream().map(ApplicationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> search(ApplicationSearchCriteria criteria, Pageable pageable) {
        UUID tenantId = resolvedTenantId();
        return applicationRepository
                .findAll(ApplicationSpecification.from(criteria, tenantId), pageable)
                .map(ApplicationResponse::from);
    }

    // ── Update notes ──────────────────────────────────────────────────────────

    public ApplicationResponse updateNotes(UUID id, UpdateApplicationRequest request) {
        UUID tenantId = resolvedTenantId();
        Application application = findOrThrow(id, tenantId);
        application.setNotes(request.getNotes());
        Application saved = applicationRepository.save(application);
        return ApplicationResponse.from(saved);
    }

    // ── Status transition ──────────────────────────────────────────────────────

    /**
     * Transitions an application to {@code newStatus}, enforcing the state machine.
     * Sets {@code appliedDate} when entering {@code SUBMITTED} and {@code decisionDate}
     * on an offer or rejection.
     */
    public ApplicationResponse updateStatus(UUID id, ApplicationStatus newStatus) {
        UUID tenantId = resolvedTenantId();
        Application application = findOrThrow(id, tenantId);
        ApplicationStatus current = application.getStatus();

        if (!ALLOWED_TRANSITIONS.get(current).contains(newStatus)) {
            throw new InvalidApplicationStatusTransitionException(current, newStatus);
        }

        application.setStatus(newStatus);
        if (newStatus == ApplicationStatus.SUBMITTED && application.getAppliedDate() == null) {
            application.setAppliedDate(Instant.now());
        }
        if (DECISION_STATUSES.contains(newStatus)) {
            application.setDecisionDate(Instant.now());
        }

        Application saved = applicationRepository.save(application);
        log.info("Application {} status {} → {} by '{}'", id, current, newStatus, currentUsername());

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.APPLICATION_STATUS_CHANGED, "APPLICATION", id,
                current.name(), newStatus.name());

        return ApplicationResponse.from(saved);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Application findOrThrow(UUID id, UUID tenantId) {
        return applicationRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
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
