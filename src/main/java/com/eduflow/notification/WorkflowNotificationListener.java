package com.eduflow.notification;

import com.eduflow.student.Student;
import com.eduflow.student.StudentRepository;
import com.eduflow.user.User;
import com.eduflow.workflow.WorkflowStage;
import com.eduflow.workflow.WorkflowStageRepository;
import com.eduflow.workflow.event.WorkflowEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Translates workflow domain events into notifications (PRD §13). Runs synchronously
 * within the publishing transaction so in-app notifications are visible immediately.
 */
@Component
@RequiredArgsConstructor
public class WorkflowNotificationListener {

    private final NotificationService notificationService;
    private final WorkflowStageRepository stageRepository;
    private final StudentRepository studentRepository;

    /** Stage entered → notify the stage's owner role (they have the new task). */
    @EventListener
    public void onStageEntered(WorkflowEvents.StageEntered event) {
        WorkflowStage stage = stageRepository.findById(event.stageId()).orElse(null);
        if (stage == null || stage.getOwnerRole() == null || stage.getOwnerRole().isBlank()) {
            return;
        }
        Student student = studentRepository.findById(event.studentId()).orElse(null);
        String name = student != null ? student.getFullName() : "a student";
        notificationService.notifyRole(event.tenantId(), stage.getOwnerRole(),
                NotificationType.STAGE_ENTERED,
                "New stage: " + stage.getName(),
                name + " entered the '" + stage.getName() + "' stage.",
                studentLink(event.studentId()));
    }

    /** Workflow completed → notify the student's assigned counselor. */
    @EventListener
    public void onWorkflowCompleted(WorkflowEvents.WorkflowCompleted event) {
        Student student = studentRepository.findById(event.studentId()).orElse(null);
        if (student == null || student.getAssignedCounselor() == null) {
            return;
        }
        User counselor = student.getAssignedCounselor();
        notificationService.notifyUser(event.tenantId(), counselor.getId(),
                NotificationType.STAGE_COMPLETED,
                "Workflow completed",
                student.getFullName() + " has completed their workflow.",
                studentLink(event.studentId()));
    }

    /** SLA breach → notify the breached stage's owner role. */
    @EventListener
    public void onSlaBreached(WorkflowEvents.SlaBreached event) {
        WorkflowStage stage = stageRepository.findById(event.stageId()).orElse(null);
        Student student = studentRepository.findById(event.studentId()).orElse(null);
        String name = student != null ? student.getFullName() : "A student";
        String stageName = stage != null ? stage.getName() : "a stage";
        if (stage != null && stage.getOwnerRole() != null && !stage.getOwnerRole().isBlank()) {
            notificationService.notifyRole(event.tenantId(), stage.getOwnerRole(),
                    NotificationType.SLA_BREACHED,
                    "SLA breached: " + stageName,
                    name + " has exceeded the SLA for the '" + stageName + "' stage.",
                    studentLink(event.studentId()));
        }
    }

    private String studentLink(UUID studentId) {
        return "/students/" + studentId;
    }
}
