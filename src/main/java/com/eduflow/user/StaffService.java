package com.eduflow.user;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditEvent;
import com.eduflow.audit.AuditEventRepository;
import com.eduflow.audit.AuditService;
import com.eduflow.role.Role;
import com.eduflow.role.RoleRepository;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentRepository;
import com.eduflow.student.StudentStatus;
import com.eduflow.student.dto.StudentResponse;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantLimitService;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.user.dto.ActivityItem;
import com.eduflow.user.dto.CaseloadView;
import com.eduflow.user.dto.InviteStaffRequest;
import com.eduflow.user.dto.PipelineStage;
import com.eduflow.user.dto.RoleOption;
import com.eduflow.user.dto.RolePermissions;
import com.eduflow.user.dto.StaffInviteResult;
import com.eduflow.user.dto.StaffResponse;
import com.eduflow.user.dto.StaffRosterRow;
import com.eduflow.user.dto.StaffSearchCriteria;
import com.eduflow.user.dto.StaffStats;
import com.eduflow.user.dto.UpdateStaffRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core service for tenant staff (user) management.
 *
 * <p>All methods resolve {@code tenantId} from the authenticated principal via
 * {@link SecurityContextHolder} — never from a request parameter. Every query is
 * tenant-scoped; a query without the tenant filter would be a security bug.</p>
 *
 * <p>Invites reuse the proven mechanics from the tenant-admin invite flow: a
 * {@code PENDING_VERIFICATION} user is created with an unusable placeholder hash,
 * and a single-use set-password token is issued so the invitee can activate.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StaffService {

    private static final String TENANT_ADMIN_ROLE = "ROLE_TENANT_ADMIN";
    private static final String COUNSELOR_ROLE    = "ROLE_COUNSELOR";

    /**
     * Roles a tenant admin may assign from the Staff UI, in display order.
     * {@code ROLE_SUPER_ADMIN} and legacy roles are deliberately excluded.
     */
    private static final List<String> ASSIGNABLE_ROLE_NAMES = List.of(
            "ROLE_TENANT_ADMIN", "ROLE_COUNSELOR", "ROLE_DOC_OFFICER", "ROLE_VISA_OFFICER");

    /** Active student statuses shown in a counselor's caseload pipeline, in lifecycle order. */
    private static final List<StudentStatus> PIPELINE_STATUSES = List.of(
            StudentStatus.LEAD, StudentStatus.QUALIFIED, StudentStatus.ACTIVE, StudentStatus.ENROLLED);

    /** Per-role capabilities for the "Role & access" card. */
    private static final Map<String, List<String>> ROLE_ALLOWED = Map.of(
            "ROLE_COUNSELOR",    List.of("Register & edit students", "Advance pipeline status",
                                        "Assign & move workflows", "Upload student documents"),
            "ROLE_DOC_OFFICER",  List.of("Review & verify documents", "Request document revisions",
                                        "View every student dossier"),
            "ROLE_VISA_OFFICER", List.of("Manage the visa stage", "Record visa outcomes",
                                        "View assigned applications"),
            "ROLE_TENANT_ADMIN", List.of("Full student & staff management", "Invite & deactivate staff",
                                        "Edit workspace settings", "Configure workflows"));

    private static final Map<String, List<String>> ROLE_RESTRICTED = Map.of(
            "ROLE_COUNSELOR",    List.of("Manage staff accounts", "Edit workspace settings", "Change plan or billing"),
            "ROLE_DOC_OFFICER",  List.of("Register or edit students", "Advance pipeline status", "Manage staff accounts"),
            "ROLE_VISA_OFFICER", List.of("Register or edit students", "Manage staff accounts", "Edit workspace settings"),
            "ROLE_TENANT_ADMIN", List.of("Platform-level tenant administration"));

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final TenantLimitService tenantLimitService;
    private final PasswordResetService passwordResetService;
    private final AuditService auditService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StudentRepository studentRepository;
    private final AuditEventRepository auditEventRepository;

    // ── Read ───────────────────────────────────────────────────────────────────

    /** Searches staff for the calling user's tenant using dynamic, paginated criteria. */
    @Transactional(readOnly = true)
    public Page<StaffResponse> searchStaff(StaffSearchCriteria criteria, Pageable pageable) {
        UUID tenantId = resolvedTenantId();
        log.debug("Searching staff for tenant {} with criteria: {}", tenantId, criteria);
        return userRepository.findAll(StaffSpecification.from(criteria, tenantId), pageable)
                .map(StaffResponse::from);
    }

    /** Retrieves a single staff member by ID, scoped to the calling user's tenant. */
    @Transactional(readOnly = true)
    public StaffResponse getStaff(UUID userId) {
        return StaffResponse.from(findStaffOrThrow(userId, resolvedTenantId()));
    }

    /** Lists the roles a tenant admin may assign, for the staff form's checkboxes. */
    @Transactional(readOnly = true)
    public List<RoleOption> listAssignableRoles() {
        return ASSIGNABLE_ROLE_NAMES.stream()
                .map(roleRepository::findByName)
                .flatMap(java.util.Optional::stream)
                .map(r -> new RoleOption(r.getName(), labelFor(r.getName()), r.getDescription()))
                .toList();
    }

    /**
     * Searches staff and decorates each row with the counselor's active caseload count
     * (resolved in a single grouped query to avoid N+1). Powers the roster table.
     */
    @Transactional(readOnly = true)
    public Page<StaffRosterRow> searchStaffRoster(StaffSearchCriteria criteria, Pageable pageable) {
        UUID tenantId = resolvedTenantId();
        Map<UUID, Long> caseloads = caseloadByCounselor(tenantId);
        return userRepository.findAll(StaffSpecification.from(criteria, tenantId), pageable)
                .map(u -> toRosterRow(u, caseloads));
    }

    /** Aggregate metrics for the roster stat strip. */
    @Transactional(readOnly = true)
    public StaffStats getStaffStats() {
        UUID tenantId = resolvedTenantId();
        long total = userRepository.countByTenantId(tenantId);
        List<User> counselors = userRepository.findActiveByTenantIdAndRoleName(tenantId, COUNSELOR_ROLE);
        Map<UUID, Long> caseloads = caseloadByCounselor(tenantId);

        long avg = 0;
        if (!counselors.isEmpty()) {
            long sum = counselors.stream()
                    .mapToLong(c -> caseloads.getOrDefault(c.getId(), 0L))
                    .sum();
            avg = Math.round((double) sum / counselors.size());
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        Integer max = tenant.getMaxStaffUsers();
        Integer available = max == null ? null : Math.max(0, (int) (max - total));
        int pct = (max == null || max == 0) ? 0 : (int) Math.min(100, Math.round(total * 100.0 / max));
        String plan = tenant.getPlan() != null ? labelFor(tenant.getPlan().name()) : "—";

        return new StaffStats(total, counselors.size(), avg, total, max, available, pct, plan);
    }

    /** A counselor's active caseload (count, pipeline, students). Empty for non-counselors. */
    @Transactional(readOnly = true)
    public CaseloadView getCaseload(UUID userId) {
        UUID tenantId = resolvedTenantId();
        User user = findStaffOrThrow(userId, tenantId);
        if (!hasRole(user, COUNSELOR_ROLE)) {
            return CaseloadView.empty();
        }

        List<Student> students = studentRepository
                .findByAssignedCounselorIdAndTenantIdAndStatusNotOrderByFirstNameAsc(
                        userId, tenantId, StudentStatus.INACTIVE);
        if (students.isEmpty()) {
            return CaseloadView.empty();
        }

        List<PipelineStage> pipeline = PIPELINE_STATUSES.stream()
                .map(s -> new PipelineStage(s, students.stream().filter(st -> st.getStatus() == s).count()))
                .toList();

        return new CaseloadView(true, students.size(), pipeline,
                students.stream().map(StudentResponse::from).toList());
    }

    /** The staff member's most recent audited actions, newest first. */
    @Transactional(readOnly = true)
    public List<ActivityItem> getRecentActivity(UUID userId) {
        UUID tenantId = resolvedTenantId();
        findStaffOrThrow(userId, tenantId);   // tenant-scope guard
        return auditEventRepository
                .findTop8ByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId).stream()
                .map(this::toActivityItem)
                .toList();
    }

    /** The combined capabilities a staff member's roles grant and deny. */
    @Transactional(readOnly = true)
    public RolePermissions getPermissions(UUID userId) {
        User user = findStaffOrThrow(userId, resolvedTenantId());
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet());

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        LinkedHashSet<String> restricted = new LinkedHashSet<>();
        for (String r : roleNames) {
            allowed.addAll(ROLE_ALLOWED.getOrDefault(r, List.of()));
            restricted.addAll(ROLE_RESTRICTED.getOrDefault(r, List.of()));
        }
        restricted.removeAll(allowed);   // a capability allowed by one role is not "restricted"
        return new RolePermissions(new ArrayList<>(allowed), new ArrayList<>(restricted));
    }

    // ── Invite (create) ─────────────────────────────────────────────────────────

    /**
     * Invites a new staff member into the calling user's tenant.
     *
     * <p>Creates a {@code PENDING_VERIFICATION} user with an unusable placeholder hash
     * and issues a single-use set-password token. The returned token lets the web layer
     * build an activation link.</p>
     *
     * @throws DuplicateStaffException if the email already exists in this tenant
     */
    public StaffInviteResult inviteStaff(InviteStaffRequest request) {
        UUID tenantId = resolvedTenantId();
        String email = request.getEmail().trim();
        log.info("Inviting staff '{}' for tenant {}", email, tenantId);

        if (userRepository.existsByEmailIgnoreCaseAndTenantId(email, tenantId)) {
            throw new DuplicateStaffException(email);
        }

        // Enforce the tenant's plan staff cap before creating (PRD §9).
        tenantLimitService.assertCanAddStaff(tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        Set<Role> roles = resolveRoles(request.getRoleNames());

        // Unusable random hash until the invitee sets their own password via the token.
        String placeholderHash = passwordEncoder.encode(UUID.randomUUID().toString());

        User user = User.builder()
                .email(email)
                .passwordHash(placeholderHash)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .tenant(tenant)
                .status(UserStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .roles(new HashSet<>(roles))
                .build();

        User saved = userRepository.save(user);
        String token = passwordResetService.createToken(saved);

        log.info("Staff {} invited for tenant {} — set-password token issued", saved.getId(), tenantId);
        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STAFF_INVITED, "STAFF", saved.getId());

        return new StaffInviteResult(StaffResponse.from(saved), token);
    }

    // ── Update ───────────────────────────────────────────────────────────────────

    /**
     * Updates a staff member's profile name and role assignment.
     *
     * @throws StaffNotFoundException   if the staff member is not in this tenant
     * @throws LastTenantAdminException if this would strip admin from the last active admin
     */
    public StaffResponse updateStaff(UUID userId, UpdateStaffRequest request) {
        UUID tenantId = resolvedTenantId();
        User user = findStaffOrThrow(userId, tenantId);

        Set<Role> newRoles = resolveRoles(request.getRoleNames());
        boolean willBeAdmin = newRoles.stream().anyMatch(r -> TENANT_ADMIN_ROLE.equals(r.getName()));

        // Guard: don't remove the admin role from the tenant's last active admin.
        if (isCurrentlyAdmin(user) && !willBeAdmin && user.getStatus() == UserStatus.ACTIVE) {
            assertNotLastActiveAdmin(tenantId, userId);
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.getRoles().clear();
        user.getRoles().addAll(newRoles);

        User saved = userRepository.save(user);
        log.info("Staff {} updated by '{}'", userId, currentUsername());
        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STAFF_UPDATED, "STAFF", userId);

        return StaffResponse.from(saved);
    }

    /**
     * Activates or deactivates a staff member.
     *
     * @param newStatus must be {@link UserStatus#ACTIVE} or {@link UserStatus#INACTIVE}
     * @throws IllegalArgumentException if a user tries to deactivate themselves, or
     *                                  {@code newStatus} is not ACTIVE/INACTIVE
     * @throws LastTenantAdminException if deactivating the tenant's last active admin
     */
    public StaffResponse setStatus(UUID userId, UserStatus newStatus) {
        if (newStatus != UserStatus.ACTIVE && newStatus != UserStatus.INACTIVE) {
            throw new IllegalArgumentException("Status can only be set to ACTIVE or INACTIVE");
        }
        UUID tenantId = resolvedTenantId();
        User user = findStaffOrThrow(userId, tenantId);

        if (newStatus == UserStatus.INACTIVE) {
            if (userId.equals(resolvedUserId())) {
                throw new IllegalArgumentException("You cannot deactivate your own account");
            }
            if (isCurrentlyAdmin(user)) {
                assertNotLastActiveAdmin(tenantId, userId);
            }
        }

        UserStatus current = user.getStatus();
        if (current == newStatus) {
            return StaffResponse.from(user);
        }

        user.setStatus(newStatus);
        User saved = userRepository.save(user);
        log.info("Staff {} status changed {} → {} by '{}'", userId, current, newStatus, currentUsername());
        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STAFF_STATUS_CHANGED, "STAFF", userId, current.name(), newStatus.name());

        return StaffResponse.from(saved);
    }

    /**
     * Issues a fresh single-use set-password token for an existing staff member so an
     * admin can help them (re)set their password. The current password is unchanged
     * until the token is consumed.
     *
     * @return the opaque token to embed in the activation link
     */
    public String issuePasswordReset(UUID userId) {
        UUID tenantId = resolvedTenantId();
        User user = findStaffOrThrow(userId, tenantId);

        String token = passwordResetService.createToken(user);
        log.info("Password-reset token issued for staff {} by '{}'", userId, currentUsername());
        auditService.publish(tenantId, resolvedUserId(),
                AuditAction.STAFF_PASSWORD_RESET, "STAFF", userId);

        return token;
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

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

    private User findStaffOrThrow(UUID userId, UUID tenantId) {
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new StaffNotFoundException(userId));
    }

    /**
     * Resolves the requested authority names to {@link Role} entities, rejecting any
     * name outside the assignable set (so {@code ROLE_SUPER_ADMIN} can never be granted).
     */
    private Set<Role> resolveRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            throw new IllegalArgumentException("At least one role must be selected");
        }
        Set<Role> roles = new LinkedHashSet<>();
        for (String name : roleNames) {
            if (!ASSIGNABLE_ROLE_NAMES.contains(name)) {
                throw new IllegalArgumentException("Role is not assignable: " + name);
            }
            roles.add(roleRepository.findByName(name)
                    .orElseThrow(() -> new IllegalStateException("Missing system role " + name)));
        }
        return roles;
    }

    private boolean isCurrentlyAdmin(User user) {
        return hasRole(user, TENANT_ADMIN_ROLE);
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream().anyMatch(r -> roleName.equals(r.getName()));
    }

    /** Active caseload count per counselor id for a tenant, from one grouped query. */
    private Map<UUID, Long> caseloadByCounselor(UUID tenantId) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : studentRepository.countActiveCaseloadByCounselor(tenantId)) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }

    private StaffRosterRow toRosterRow(User u, Map<UUID, Long> caseloads) {
        boolean counselor = hasRole(u, COUNSELOR_ROLE);
        List<String> roleNames = u.getRoles().stream().map(Role::getName).sorted().toList();
        String fullName = u.getFullName().trim();
        return new StaffRosterRow(
                u.getId(),
                fullName.isEmpty() ? u.getEmail() : fullName,
                u.getFirstName(),
                u.getEmail(),
                roleNames,
                u.getStatus(),
                counselor,
                counselor ? caseloads.getOrDefault(u.getId(), 0L) : null,
                roleNote(u));
    }

    /** Short caption shown in the caseload cell for staff who don't own a caseload. */
    private String roleNote(User u) {
        if (hasRole(u, "ROLE_DOC_OFFICER"))  return "Reviews all documents";
        if (hasRole(u, "ROLE_VISA_OFFICER")) return "Handles visa queue";
        if (hasRole(u, TENANT_ADMIN_ROLE))   return "Manages the team";
        return "—";
    }

    private ActivityItem toActivityItem(AuditEvent e) {
        return new ActivityItem(activityLabel(e.getAction()), activityIcon(e.getAction()), e.getCreatedAt());
    }

    /** Maps an audit action constant to a human-readable activity label. */
    private static String activityLabel(String action) {
        return switch (action) {
            case AuditAction.STUDENT_CREATED        -> "Registered a student";
            case AuditAction.STUDENT_UPDATED        -> "Updated a student";
            case AuditAction.STUDENT_STATUS_CHANGED -> "Advanced a student's status";
            case AuditAction.STUDENT_DELETED        -> "Deactivated a student";
            case AuditAction.DOCUMENT_UPLOADED      -> "Uploaded a document";
            case AuditAction.DOCUMENT_VERIFIED      -> "Verified a document";
            case AuditAction.DOCUMENT_STATUS_CHANGED, AuditAction.DOCUMENT_RESUBMITTED
                                                    -> "Worked on documents";
            case AuditAction.WORKFLOW_ASSIGNED      -> "Assigned a workflow";
            case AuditAction.WORKFLOW_STAGE_CHANGED -> "Advanced a workflow stage";
            case AuditAction.TASK_CREATED, AuditAction.TASK_ASSIGNED,
                 AuditAction.TASK_STATUS_CHANGED, AuditAction.TASK_COMPLETED
                                                    -> "Worked on a task";
            case AuditAction.STAFF_INVITED          -> "Invited a staff member";
            case AuditAction.STAFF_UPDATED, AuditAction.STAFF_STATUS_CHANGED,
                 AuditAction.STAFF_PASSWORD_RESET   -> "Managed a staff account";
            default -> labelFor(action);
        };
    }

    /** Maps an audit action constant to a lucide icon name used in the activity feed. */
    private static String activityIcon(String action) {
        return switch (action) {
            case AuditAction.STUDENT_CREATED        -> "user-plus";
            case AuditAction.STUDENT_UPDATED        -> "pencil";
            case AuditAction.STUDENT_STATUS_CHANGED -> "arrow-right";
            case AuditAction.STUDENT_DELETED        -> "user-x";
            case AuditAction.DOCUMENT_UPLOADED, AuditAction.DOCUMENT_VERIFIED,
                 AuditAction.DOCUMENT_STATUS_CHANGED, AuditAction.DOCUMENT_RESUBMITTED
                                                    -> "file-text";
            case AuditAction.WORKFLOW_ASSIGNED, AuditAction.WORKFLOW_STAGE_CHANGED
                                                    -> "workflow";
            case AuditAction.TASK_CREATED, AuditAction.TASK_ASSIGNED,
                 AuditAction.TASK_STATUS_CHANGED, AuditAction.TASK_COMPLETED
                                                    -> "check-square";
            case AuditAction.STAFF_INVITED          -> "user-plus";
            case AuditAction.STAFF_UPDATED, AuditAction.STAFF_STATUS_CHANGED,
                 AuditAction.STAFF_PASSWORD_RESET   -> "user-cog";
            default -> "activity";
        };
    }

    /**
     * Throws {@link LastTenantAdminException} if {@code userId} is the only active
     * {@code ROLE_TENANT_ADMIN} left in the tenant.
     */
    private void assertNotLastActiveAdmin(UUID tenantId, UUID userId) {
        List<User> activeAdmins = userRepository.findActiveByTenantIdAndRoleName(tenantId, TENANT_ADMIN_ROLE);
        boolean anotherActiveAdmin = activeAdmins.stream()
                .anyMatch(a -> !a.getId().equals(userId));
        if (!anotherActiveAdmin) {
            throw new LastTenantAdminException();
        }
    }

    /** Turns {@code ROLE_DOC_OFFICER} into {@code Doc Officer} for display. */
    private static String labelFor(String roleName) {
        String base = roleName.startsWith("ROLE_") ? roleName.substring(5) : roleName;
        String[] parts = base.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
