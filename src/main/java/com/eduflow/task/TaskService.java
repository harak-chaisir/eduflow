package com.eduflow.task;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentRepository;
import com.eduflow.task.dto.TaskResponse;
import com.eduflow.workflow.WorkflowStage;
import com.eduflow.workflow.WorkflowStageRepository;
import com.eduflow.workflow.event.WorkflowEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for the Task domain (PRD §7.7). Generates owner-role tasks when a workflow
 * stage is entered, and tracks them through their lifecycle. Tenant is resolved from the
 * authenticated principal for user-facing operations; generation uses the event payload.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

    private static final Set<TaskStatus> OPEN = Set.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS);

    private final TaskRepository taskRepository;
    private final StudentRepository studentRepository;
    private final WorkflowStageRepository stageRepository;
    private final AuditService auditService;

    // ── Generation (PRD §14 "Generate Tasks") ────────────────────────────────

    /**
     * Creates a task for a stage's owner role when a student enters that stage. No-op if
     * the stage has no owner role. The due date is derived from the stage SLA, if set.
     */
    public void generateForStage(WorkflowEvents.StageEntered event) {
        WorkflowStage stage = stageRepository.findById(event.stageId()).orElse(null);
        if (stage == null || stage.getOwnerRole() == null || stage.getOwnerRole().isBlank()) {
            return;     // nothing to assign
        }
        Student student = studentRepository.findById(event.studentId()).orElse(null);
        if (student == null) {
            return;
        }
        Instant due = stage.getSlaDays() != null
                ? Instant.now().plus(stage.getSlaDays(), ChronoUnit.DAYS) : null;

        Task task = Task.builder()
                .tenant(student.getTenant())
                .student(student)
                .studentWorkflowId(event.instanceId())
                .stageId(stage.getId())
                .title(stage.getName())
                .description("Complete the '" + stage.getName() + "' stage for " + student.getFullName())
                .assignedRole(stage.getOwnerRole())
                .status(TaskStatus.PENDING)
                .priority(TaskPriority.MEDIUM)
                .dueAt(due)
                .build();
        Task saved = taskRepository.save(task);
        auditService.publish(event.tenantId(), event.actorUserId(),
                AuditAction.TASK_CREATED, "TASK", saved.getId());
        log.debug("Generated task '{}' for role {} (student {})",
                saved.getTitle(), saved.getAssignedRole(), student.getId());
    }

    // ── Read ───────────────────────────────────────────────────────────────────

    /** Open tasks visible to the calling user (assigned to them or to one of their roles). */
    @Transactional(readOnly = true)
    public List<TaskResponse> listMyTasks() {
        EduFlowUserDetails p = principal();
        Set<String> roles = p.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return taskRepository.findMyOpenTasks(p.getTenantId(), p.getUserId(), roles, OPEN).stream()
                .map(TaskResponse::from)
                .toList();
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    public TaskResponse start(UUID taskId) {
        Task task = requireTask(taskId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new InvalidTaskStatusTransitionException(task.getStatus(), TaskStatus.IN_PROGRESS);
        }
        task.setStatus(TaskStatus.IN_PROGRESS);
        Task saved = taskRepository.save(task);
        auditService.publish(resolvedTenantId(), resolvedUserId(),
                AuditAction.TASK_STATUS_CHANGED, "TASK", taskId, "PENDING", "IN_PROGRESS");
        return TaskResponse.from(saved);
    }

    public TaskResponse complete(UUID taskId) {
        Task task = requireTask(taskId);
        if (!OPEN.contains(task.getStatus())) {
            throw new InvalidTaskStatusTransitionException(task.getStatus(), TaskStatus.COMPLETED);
        }
        TaskStatus old = task.getStatus();
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());
        task.setCompletedBy(currentUsername());
        Task saved = taskRepository.save(task);
        auditService.publish(resolvedTenantId(), resolvedUserId(),
                AuditAction.TASK_COMPLETED, "TASK", taskId, old.name(), "COMPLETED");
        return TaskResponse.from(saved);
    }

    public TaskResponse cancel(UUID taskId) {
        Task task = requireTask(taskId);
        if (!OPEN.contains(task.getStatus())) {
            throw new InvalidTaskStatusTransitionException(task.getStatus(), TaskStatus.CANCELLED);
        }
        TaskStatus old = task.getStatus();
        task.setStatus(TaskStatus.CANCELLED);
        Task saved = taskRepository.save(task);
        auditService.publish(resolvedTenantId(), resolvedUserId(),
                AuditAction.TASK_STATUS_CHANGED, "TASK", taskId, old.name(), "CANCELLED");
        return TaskResponse.from(saved);
    }

    public TaskResponse reassign(UUID taskId, UUID userId) {
        Task task = requireTask(taskId);
        task.setAssignedUserId(userId);
        Task saved = taskRepository.save(task);
        auditService.publish(resolvedTenantId(), resolvedUserId(),
                AuditAction.TASK_ASSIGNED, "TASK", taskId);
        return TaskResponse.from(saved);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Task requireTask(UUID taskId) {
        return taskRepository.findByIdAndTenantId(taskId, resolvedTenantId())
                .orElseThrow(() -> new TaskNotFoundException(taskId));
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
