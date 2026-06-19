package com.eduflow.workflow;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.document.DocumentService;
import com.eduflow.document.DocumentStatus;
import com.eduflow.document.DocumentType;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentNotFoundException;
import com.eduflow.student.StudentRepository;
import com.eduflow.workflow.dto.AvailableTransitionResponse;
import com.eduflow.workflow.dto.StudentWorkflowResponse;
import com.eduflow.workflow.event.WorkflowEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Execution-side service for the Workflow Management module (PRD §14, FR-6, FR-7).
 *
 * <p>Assigns workflow templates to students, advances them through stages with
 * transition + required-document gating, and records per-stage history. The instance's
 * current stage is the source of truth for process state; SLA breach is computed on read
 * (see {@link StudentWorkflowResponse}).</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class StudentWorkflowService {

    private final StudentWorkflowRepository instanceRepository;
    private final WorkflowStageHistoryRepository historyRepository;
    private final WorkflowTemplateRepository templateRepository;
    private final WorkflowTransitionRepository transitionRepository;
    private final StudentRepository studentRepository;
    private final DocumentService documentService;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    // ── Assignment (FR-6) ────────────────────────────────────────────────────

    /** Assigns a specific (non-archived) template to a student, creating an ACTIVE instance. */
    public StudentWorkflow assignWorkflow(UUID studentId, UUID templateId) {
        UUID tenantId = resolvedTenantId();
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        WorkflowTemplate template = templateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new WorkflowTemplateNotFoundException(templateId));
        return createInstance(student, template, tenantId);
    }

    /**
     * Assigns the tenant's default template to a student if one is configured and the
     * student has no active workflow. No-op otherwise. Invoked on student creation.
     */
    public Optional<StudentWorkflow> assignDefault(UUID studentId) {
        UUID tenantId = resolvedTenantId();
        if (instanceRepository.findByStudentIdAndStatus(studentId, InstanceStatus.ACTIVE).isPresent()) {
            return Optional.empty();
        }
        Optional<WorkflowTemplate> defaultTemplate = templateRepository.findByTenantId(tenantId).stream()
                .filter(WorkflowTemplate::isDefaultTemplate)
                .filter(t -> !t.isArchived())
                .findFirst();
        if (defaultTemplate.isEmpty()) {
            log.debug("No default workflow configured for tenant {}; skipping auto-assign", tenantId);
            return Optional.empty();
        }
        Student student = studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
        return Optional.of(createInstance(student, defaultTemplate.get(), tenantId));
    }

    private StudentWorkflow createInstance(Student student, WorkflowTemplate template, UUID tenantId) {
        if (template.isArchived()) {
            throw new WorkflowArchivedException(template.getId());
        }
        WorkflowStage entry = template.getStages().stream()
                .min(Comparator.comparingInt(WorkflowStage::getDisplayOrder))
                .orElseThrow(() -> new InvalidWorkflowGraphException(
                        "Workflow '" + template.getName() + "' has no stages to start from"));

        Instant now = Instant.now();
        StudentWorkflow instance = StudentWorkflow.builder()
                .tenant(template.getTenant())
                .student(student)
                .template(template)
                .currentStage(entry)
                .status(InstanceStatus.ACTIVE)
                .startedAt(now)
                .currentStageEnteredAt(now)
                .build();
        StudentWorkflow saved = instanceRepository.save(instance);

        historyRepository.save(WorkflowStageHistory.builder()
                .tenant(template.getTenant())
                .studentWorkflow(saved)
                .stage(entry)
                .enteredAt(now)
                .movedByUserId(resolvedUserId())
                .build());

        log.info("Assigned workflow {} to student {} at entry stage {}",
                template.getId(), student.getId(), entry.getCode());
        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_ASSIGNED,
                "STUDENT_WORKFLOW", saved.getId());
        events.publishEvent(new WorkflowEvents.StageEntered(
                tenantId, resolvedUserId(), saved.getId(), student.getId(), entry.getId(), true));
        return saved;
    }

    // ── Stage movement (FR-7) ────────────────────────────────────────────────

    /** Advances an instance to {@code toStageId} if a transition allows it and gating passes. */
    public StudentWorkflow moveStage(UUID instanceId, UUID toStageId, String notes) {
        UUID tenantId = resolvedTenantId();
        StudentWorkflow instance = instanceRepository.findByIdAndTenantId(instanceId, tenantId)
                .orElseThrow(() -> new StudentWorkflowNotFoundException("Workflow instance not found: " + instanceId));
        if (instance.getStatus() != InstanceStatus.ACTIVE) {
            throw new InvalidWorkflowTransitionException("Workflow is not active and cannot be advanced");
        }
        WorkflowStage from = instance.getCurrentStage();
        if (from == null) {
            throw new InvalidWorkflowTransitionException("Workflow has no current stage");
        }

        WorkflowTransition transition = transitionRepository
                .findByTemplateIdAndFromStageIdAndToStageId(instance.getTemplate().getId(), from.getId(), toStageId)
                .orElseThrow(() -> new InvalidWorkflowTransitionException(
                        "No transition exists from '" + from.getName() + "' to the requested stage"));
        WorkflowStage to = transition.getToStage();

        // Approval gating: MANUAL_APPROVAL or requiresApproval transitions need the owner role.
        if ((transition.getTransitionType() == TransitionType.MANUAL_APPROVAL || transition.isRequiresApproval())
                && !callerHasOwnerAuthority(from.getOwnerRole())) {
            throw new InvalidWorkflowTransitionException(
                    "This transition requires the " + from.getOwnerRole() + " role to approve");
        }

        // Required-document gating (FR-5).
        List<DocumentType> missing = missingRequiredDocuments(instance.getStudent().getId(), from);
        if (!missing.isEmpty()) {
            throw new RequiredDocumentsMissingException(missing);
        }

        Instant now = Instant.now();
        // Close the open history row, open a new one.
        historyRepository.findFirstByStudentWorkflowIdAndExitedAtIsNull(instanceId).ifPresent(open -> {
            open.setExitedAt(now);
            open.setTransitionId(transition.getId());
            open.setMovedByUserId(resolvedUserId());
            open.setNotes(notes);
            historyRepository.save(open);
        });
        historyRepository.save(WorkflowStageHistory.builder()
                .tenant(instance.getTenant())
                .studentWorkflow(instance)
                .stage(to)
                .enteredAt(now)
                .movedByUserId(resolvedUserId())
                .build());

        instance.setCurrentStage(to);
        instance.setCurrentStageEnteredAt(now);
        instance.setSlaBreached(false);

        String oldCode = from.getCode();
        boolean completed = to.getStageType() == StageType.FINAL_STAGE;
        if (completed) {
            instance.setStatus(InstanceStatus.COMPLETED);
            instance.setCompletedAt(now);
        }
        StudentWorkflow saved = instanceRepository.save(instance);

        auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_STAGE_CHANGED,
                "STUDENT_WORKFLOW", instanceId, oldCode, to.getCode());
        events.publishEvent(new WorkflowEvents.StageEntered(
                tenantId, resolvedUserId(), instanceId, instance.getStudent().getId(), to.getId(), false));
        if (completed) {
            auditService.publish(tenantId, resolvedUserId(), AuditAction.WORKFLOW_COMPLETED,
                    "STUDENT_WORKFLOW", instanceId);
            events.publishEvent(new WorkflowEvents.WorkflowCompleted(
                    tenantId, resolvedUserId(), instanceId, instance.getStudent().getId()));
        }
        log.info("Student workflow {} moved {} → {}", instanceId, oldCode, to.getCode());
        return saved;
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /** The student's active (or most recent) workflow as a response, or empty if none. */
    @Transactional(readOnly = true)
    public Optional<StudentWorkflowResponse> getForStudent(UUID studentId) {
        UUID tenantId = resolvedTenantId();
        Optional<StudentWorkflow> active = instanceRepository.findByStudentIdAndStatus(studentId, InstanceStatus.ACTIVE);
        StudentWorkflow instance = active.orElseGet(() ->
                instanceRepository.findByStudentIdAndTenantId(studentId, tenantId).stream()
                        .max(Comparator.comparing(StudentWorkflow::getStartedAt))
                        .orElse(null));
        if (instance == null) {
            return Optional.empty();
        }
        return Optional.of(StudentWorkflowResponse.from(instance, allowedTransitions(instance)));
    }

    private List<AvailableTransitionResponse> allowedTransitions(StudentWorkflow instance) {
        if (instance.getStatus() != InstanceStatus.ACTIVE || instance.getCurrentStage() == null) {
            return List.of();
        }
        return transitionRepository.findByFromStageId(instance.getCurrentStage().getId()).stream()
                .map(AvailableTransitionResponse::from)
                .toList();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Returns the required documents for a stage that are not yet APPROVED for the student. */
    private List<DocumentType> missingRequiredDocuments(UUID studentId, WorkflowStage stage) {
        if (stage.getRequiredDocuments() == null || stage.getRequiredDocuments().isEmpty()) {
            return List.of();
        }
        Set<DocumentType> approved = documentService.listDocuments(studentId).stream()
                .filter(d -> d.getStatus() == DocumentStatus.APPROVED)
                .map(d -> d.getDocumentType())
                .collect(Collectors.toSet());
        return stage.getRequiredDocuments().stream()
                .filter(t -> !approved.contains(t))
                .toList();
    }

    private boolean callerHasOwnerAuthority(String ownerRole) {
        Set<String> authorities = principal().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (authorities.contains("ROLE_TENANT_ADMIN") || authorities.contains("ROLE_SUPER_ADMIN")) {
            return true;
        }
        return ownerRole != null && authorities.contains(ownerRole);
    }

    private UUID resolvedTenantId() {
        return principal().getTenantId();
    }

    private UUID resolvedUserId() {
        return principal().getUserId();
    }

    private EduFlowUserDetails principal() {
        return (EduFlowUserDetails)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
