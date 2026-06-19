package com.eduflow.workflow;

import com.eduflow.student.event.StudentEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Auto-assigns the tenant's default workflow to a newly created student (PRD §14).
 *
 * <p>Runs {@code AFTER_COMMIT} so a rolled-back student registration never creates a
 * workflow instance, and in a {@code REQUIRES_NEW} transaction since the original one
 * has already committed — mirroring {@code TenantAdminInviteListener}. The security
 * context is still on the thread (synchronous listener), so the service resolves the
 * tenant from the principal as usual.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowAssignmentListener {

    private final StudentWorkflowService studentWorkflowService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStudentCreated(StudentEvents.StudentCreated event) {
        try {
            studentWorkflowService.assignDefault(event.studentId());
        } catch (RuntimeException ex) {
            // Never let auto-assignment failure surface to the user; the student is saved.
            log.warn("Default workflow auto-assignment failed for student {}: {}",
                    event.studentId(), ex.getMessage());
        }
    }
}
