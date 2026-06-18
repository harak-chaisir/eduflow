package com.eduflow.student;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.dto.CounselorOption;
import com.eduflow.student.dto.RegisterStudentRequest;
import com.eduflow.student.dto.StudentResponse;
import com.eduflow.student.dto.StudentSearchCriteria;
import com.eduflow.student.dto.UpdateStudentRequest;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantLimitService;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.user.User;
import com.eduflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core service for the Student domain.
 *
 * <p>All methods resolve {@code tenantId} from the authenticated principal via
 * {@link SecurityContextHolder} — never from a request parameter.</p>
 *
 * <p>Status transitions are validated against a strict allowed-transitions map.
 * An {@link InvalidStudentStatusTransitionException} is thrown for illegal moves.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StudentService {

    // ── Allowed status transitions ───────────────────────────────────────────

    /**
     * Maps each status to the set of statuses it is permitted to move to.
     * {@code INACTIVE} is reachable from any status except itself.
     */
    private static final Map<StudentStatus, Set<StudentStatus>> ALLOWED_TRANSITIONS = Map.of(
            StudentStatus.LEAD,      EnumSet.of(StudentStatus.QUALIFIED, StudentStatus.INACTIVE),
            StudentStatus.QUALIFIED, EnumSet.of(StudentStatus.ACTIVE,    StudentStatus.INACTIVE),
            StudentStatus.ACTIVE,    EnumSet.of(StudentStatus.ENROLLED,  StudentStatus.INACTIVE),
            StudentStatus.ENROLLED,  EnumSet.of(StudentStatus.INACTIVE),
            StudentStatus.INACTIVE,  EnumSet.noneOf(StudentStatus.class)
    );

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final StudentRepository studentRepository;
    private final TenantRepository  tenantRepository;
    private final UserRepository    userRepository;
    private final AuditService      auditService;
    private final TenantLimitService tenantLimitService;

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Registers a new student for the calling user's tenant.
     *
     * @param request validated registration payload
     * @return the saved student as a response DTO
     * @throws DuplicateStudentException if the email is already registered in this tenant
     */
    public StudentResponse registerStudent(RegisterStudentRequest request) {
        UUID tenantId = resolvedTenantId();
        log.info("Registering student with email '{}' for tenant {}", request.getEmail(), tenantId);

        if (studentRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new DuplicateStudentException(request.getEmail());
        }

        // Enforce the tenant's plan student cap before creating (PRD §9).
        tenantLimitService.assertCanAddStudent(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        User counselor = resolveCounselor(request.getAssignedCounselorId(), tenantId);

        Student student = Student.builder()
                .tenant(tenant)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .nationality(request.getNationality())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .stateProvince(request.getStateProvince())
                .country(request.getCountry())
                .postalCode(request.getPostalCode())
                .interestedCountries(
                        request.getInterestedCountries() != null ? request.getInterestedCountries() : new java.util.ArrayList<>())
                .interestedCourses(
                        request.getInterestedCourses() != null ? request.getInterestedCourses() : new java.util.ArrayList<>())
                .assignedCounselor(counselor)
                .status(StudentStatus.LEAD)
                .build();

        Student saved = studentRepository.save(student);
        log.info("Student registered with id {} for tenant {}", saved.getId(), tenantId);

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STUDENT_CREATED, "STUDENT", saved.getId());

        return StudentResponse.from(saved);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single student by ID, scoped to the calling user's tenant.
     *
     * @param studentId the student UUID
     * @return the student response DTO
     * @throws StudentNotFoundException if the student does not exist in this tenant
     */
    @Transactional(readOnly = true)
    public StudentResponse getStudent(UUID studentId) {
        UUID tenantId = resolvedTenantId();
        Student student = findStudentOrThrow(studentId, tenantId);
        return StudentResponse.from(student);
    }

    /**
     * Searches students for the calling user's tenant using dynamic criteria.
     * Results are paginated and sorted via the supplied {@link Pageable}.
     *
     * @param criteria optional search filters
     * @param pageable pagination and sort specification
     * @return a page of matching student response DTOs
     */
    @Transactional(readOnly = true)
    public Page<StudentResponse> searchStudents(StudentSearchCriteria criteria, Pageable pageable) {
        UUID tenantId = resolvedTenantId();
        log.debug("Searching students for tenant {} with criteria: {}", tenantId, criteria);
        return studentRepository
                .findAll(StudentSpecification.from(criteria, tenantId), pageable)
                .map(StudentResponse::from);
    }

    /**
     * Lists the active counselors in the calling user's tenant for the student form's
     * counselor picker. Tenant-scoped via the authenticated principal.
     *
     * @return counselor options (id + display name), ordered by name
     */
    @Transactional(readOnly = true)
    public List<CounselorOption> listCounselors() {
        UUID tenantId = resolvedTenantId();
        return userRepository.findActiveByTenantIdAndRoleName(tenantId, "ROLE_COUNSELOR").stream()
                .map(u -> new CounselorOption(u.getId(), u.getFullName().trim()))
                .toList();
    }

    /**
     * Counts students per {@link StudentStatus} for the calling user's tenant.
     * Used to drive the dashboard funnel / KPI cards.
     *
     * @return a map keyed by every status (zero-filled), preserving enum order
     */
    @Transactional(readOnly = true)
    public Map<StudentStatus, Long> countByStatus() {
        UUID tenantId = resolvedTenantId();
        Map<StudentStatus, Long> counts = new EnumMap<>(StudentStatus.class);
        for (StudentStatus status : StudentStatus.values()) {
            counts.put(status, studentRepository.countByTenantIdAndStatus(tenantId, status));
        }
        return counts;
    }

    /** Total number of students in the calling user's tenant. */
    @Transactional(readOnly = true)
    public long countAll() {
        return studentRepository.countByTenantId(resolvedTenantId());
    }

    // ── Update ───────────────────────────────────────────────────────────────

    /**
     * Updates a student's profile fields. Only non-null fields in the request are applied.
     *
     * @param studentId the student UUID
     * @param request   the update payload
     * @return the updated student response DTO
     * @throws StudentNotFoundException  if the student does not exist in this tenant
     * @throws DuplicateStudentException if the new email is already taken by another student
     */
    public StudentResponse updateStudent(UUID studentId, UpdateStudentRequest request) {
        UUID tenantId = resolvedTenantId();
        Student student = findStudentOrThrow(studentId, tenantId);

        // Guard email uniqueness if the email is being changed
        if (request.getEmail() != null
                && !request.getEmail().equalsIgnoreCase(student.getEmail())
                && studentRepository.existsByEmailAndTenantId(request.getEmail(), tenantId)) {
            throw new DuplicateStudentException(request.getEmail());
        }

        applyUpdates(student, request, tenantId);

        Student saved = studentRepository.save(student);
        log.info("Student {} updated by '{}'", studentId, currentUsername());

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STUDENT_UPDATED, "STUDENT", studentId);

        return StudentResponse.from(saved);
    }

    /**
     * Transitions a student to a new {@link StudentStatus}.
     *
     * @param studentId the student UUID
     * @param newStatus the target status
     * @return the updated student response DTO
     * @throws StudentNotFoundException                if the student does not exist in this tenant
     * @throws InvalidStudentStatusTransitionException if the transition is not permitted
     */
    public StudentResponse updateStatus(UUID studentId, StudentStatus newStatus) {
        UUID tenantId = resolvedTenantId();
        Student student = findStudentOrThrow(studentId, tenantId);
        StudentStatus current = student.getStatus();
        if (!ALLOWED_TRANSITIONS.get(current).contains(newStatus)) {
            throw new InvalidStudentStatusTransitionException(current, newStatus);
        }

        student.setStatus(newStatus);
        Student saved = studentRepository.save(student);
        log.info("Student {} status changed {} → {} by '{}'", studentId, current, newStatus, currentUsername());

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STUDENT_STATUS_CHANGED, "STUDENT", studentId,
                current.name(), newStatus.name());

        return StudentResponse.from(saved);
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    /**
     * Soft-deletes a student by setting their status to {@code INACTIVE}.
     * Hard-delete is intentionally not supported to preserve the audit trail.
     *
     * @param studentId the student UUID
     * @throws StudentNotFoundException if the student does not exist in this tenant
     */
    public void deleteStudent(UUID studentId) {
        UUID tenantId = resolvedTenantId();
        Student student = findStudentOrThrow(studentId, tenantId);

        if (student.getStatus() == StudentStatus.INACTIVE) {
            log.debug("Student {} is already INACTIVE, no-op delete", studentId);
            return;
        }

        student.setStatus(StudentStatus.INACTIVE);
        studentRepository.save(student);
        log.info("Student {} soft-deleted (INACTIVE) by '{}'", studentId, currentUsername());

        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STUDENT_DELETED, "STUDENT", studentId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves the tenant UUID from the currently authenticated principal.
     * Super admins are still scoped to their own tenant when using this method;
     * cross-tenant admin operations require dedicated super-admin endpoints.
     */
    private UUID resolvedTenantId() {
        return principal().getTenantId();
    }

    /** Returns the UUID of the currently authenticated user. */
    private UUID resolvedUserId() {
        return principal().getUserId();
    }

    /** Returns the username string of the currently authenticated user (for logging). */
    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** Returns the authenticated {@link EduFlowUserDetails} principal. */
    private EduFlowUserDetails principal() {
        return (EduFlowUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /** Finds a student by ID + tenantId or throws {@link StudentNotFoundException}. */
    private Student findStudentOrThrow(UUID studentId, UUID tenantId) {
        return studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    /**
     * Looks up the counselor by ID, ensuring they belong to the same tenant.
     * Returns {@code null} if {@code counselorId} is null.
     *
     * @throws IllegalArgumentException if the user is not found or belongs to a different tenant
     */
    private User resolveCounselor(UUID counselorId, UUID tenantId) {
        if (counselorId == null) {
            return null;
        }
        return userRepository.findById(counselorId)
                .filter(u -> u.getTenant().getId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Counselor not found or does not belong to this tenant: " + counselorId));
    }

    /** Applies non-null fields from the update request to the entity. */
    private void applyUpdates(Student student, UpdateStudentRequest req, UUID tenantId) {
        if (req.getFirstName()       != null) student.setFirstName(req.getFirstName());
        if (req.getLastName()        != null) student.setLastName(req.getLastName());
        if (req.getEmail()           != null) student.setEmail(req.getEmail());
        if (req.getPhone()           != null) student.setPhone(req.getPhone());
        if (req.getDateOfBirth()     != null) student.setDateOfBirth(req.getDateOfBirth());
        if (req.getGender()          != null) student.setGender(req.getGender());
        if (req.getNationality()     != null) student.setNationality(req.getNationality());
        if (req.getAddressLine1()    != null) student.setAddressLine1(req.getAddressLine1());
        if (req.getAddressLine2()    != null) student.setAddressLine2(req.getAddressLine2());
        if (req.getCity()            != null) student.setCity(req.getCity());
        if (req.getStateProvince()   != null) student.setStateProvince(req.getStateProvince());
        if (req.getCountry()         != null) student.setCountry(req.getCountry());
        if (req.getPostalCode()      != null) student.setPostalCode(req.getPostalCode());
        if (req.getInterestedCountries() != null) student.setInterestedCountries(req.getInterestedCountries());
        if (req.getInterestedCourses()   != null) student.setInterestedCourses(req.getInterestedCourses());
        if (req.getAssignedCounselorId() != null) {
            student.setAssignedCounselor(resolveCounselor(req.getAssignedCounselorId(), tenantId));
        }
    }
}

