package com.eduflow.document.event;

import com.eduflow.audit.AuditAction;
import com.eduflow.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Reference wiring for document domain events.
 *
 * <p>Audit listeners run synchronously with {@link EventListener} so the audit row commits
 * atomically with the change. Notification listeners run with
 * {@link TransactionalEventListener} {@code AFTER_COMMIT} so a student is never notified
 * about a change that rolled back. Swap the log lines for the real
 * {@code NotificationService} once that module lands.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentEventListener {

    private final AuditService auditService;

    // ── Audit (synchronous, within the transaction) ──────────────────────────────

    @EventListener
    public void onUploaded(DocumentEvents.DocumentUploaded e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.DOCUMENT_UPLOADED, "DOCUMENT", e.documentId());
    }

    @EventListener
    public void onVerified(DocumentEvents.DocumentVerified e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.DOCUMENT_VERIFIED, "DOCUMENT", e.documentId(),
                e.previousStatus().name(), e.newStatus().name());
    }

    @EventListener
    public void onResubmitted(DocumentEvents.DocumentResubmitted e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.DOCUMENT_RESUBMITTED, "DOCUMENT", e.documentId());
    }

    @EventListener
    public void onDeleted(DocumentEvents.DocumentDeleted e) {
        auditService.publish(e.tenantId(), e.actorUserId(),
                AuditAction.DOCUMENT_DELETED, "DOCUMENT", e.documentId());
    }

    // ── Notifications (after commit only) ─────────────────────────────────────────

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyOnVerified(DocumentEvents.DocumentVerified e) {
        // TODO: replace with NotificationService once available.
        log.info("[notify] document {} verified → {} (student {})",
                e.documentId(), e.newStatus(), e.studentId());
    }
}
