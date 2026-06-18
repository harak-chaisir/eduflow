package com.eduflow.document;

import com.eduflow.document.dto.DocumentResponse;
import com.eduflow.document.dto.DossierView;
import com.eduflow.document.dto.VerificationDecision;
import com.eduflow.document.event.DocumentEvents;
import com.eduflow.document.storage.DocumentStoragePort;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentNotFoundException;
import com.eduflow.student.StudentRepository;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.user.User;
import com.eduflow.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for the Document domain.
 *
 * <p>Stores file bytes through {@link DocumentStoragePort} (local filesystem by default,
 * Google Drive when enabled) and persists the metadata + verification state. All
 * operations are tenant-scoped — {@code tenantId} is always resolved from the
 * authenticated principal, never from a request parameter — and every state change is
 * published as a domain event for audit/notification.</p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final StudentRepository  studentRepository;
    private final TenantRepository   tenantRepository;
    private final UserRepository     userRepository;
    private final DocumentStoragePort storage;
    private final ApplicationEventPublisher events;

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Uploads a new document for a student. Stores the bytes via the active storage
     * backend and creates the record in {@link DocumentStatus#PENDING}.
     */
    public DocumentResponse uploadDocument(UUID studentId, DocumentType type,
                                           String description, MultipartFile file) {
        requireFile(file);
        UUID tenantId = resolvedTenantId();
        Student student = requireStudent(studentId, tenantId);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        DocumentStoragePort.StoredFile stored = storage.store(storageContext(student, type), file);

        Document document = Document.builder()
                .tenant(tenant)
                .student(student)
                .documentType(type)
                .documentCategory(type.category())
                .originalFilename(file.getOriginalFilename())
                .mimeType(stored.mimeType())
                .fileSizeBytes(stored.sizeBytes())
                .storageKey(stored.storageKey())
                .description(description)
                .revisionNumber(1)
                .status(DocumentStatus.PENDING)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Document {} ({}) uploaded for student {} in tenant {}",
                saved.getId(), type, studentId, tenantId);

        events.publishEvent(new DocumentEvents.DocumentUploaded(
                tenantId, resolvedUserId(), studentId, saved.getId(), type));

        return DocumentResponse.from(saved);
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    /**
     * Records a verification decision (approve / reject / request revision).
     * Remarks are mandatory for REJECT and REQUEST_REVISION.
     */
    public DocumentResponse verifyDocument(UUID documentId, VerificationDecision decision, String remarks) {
        UUID tenantId = resolvedTenantId();
        Document document = requireDocument(documentId, tenantId);

        DocumentStatus current = document.getStatus();
        DocumentStatus target = decision.targetStatus();
        if (!current.canTransitionTo(target)) {
            throw new InvalidDocumentStatusTransitionException(current, target);
        }
        if (decision.isRemarksRequired() && (remarks == null || remarks.isBlank())) {
            throw new IllegalArgumentException("Remarks are required to " + decision.name().toLowerCase().replace('_', ' '));
        }

        document.setStatus(target);
        document.setRejectionReason(decision.isRemarksRequired() ? remarks : null);
        document.setVerifiedBy(userRepository.findByIdAndTenantId(resolvedUserId(), tenantId).orElse(null));
        document.setVerifiedAt(Instant.now());

        Document saved = documentRepository.save(document);
        log.info("Document {} verified {} → {} by user {} in tenant {}",
                documentId, current, target, resolvedUserId(), tenantId);

        events.publishEvent(new DocumentEvents.DocumentVerified(
                tenantId, resolvedUserId(), document.getStudent().getId(), documentId,
                document.getDocumentType(), current, target, remarks));

        return DocumentResponse.from(saved);
    }

    // ── Resubmit ──────────────────────────────────────────────────────────────

    /**
     * Resubmits a document's file in place. Valid only from {@link DocumentStatus#NEEDS_REVISION};
     * replaces the stored bytes, bumps the revision counter, clears the prior verification
     * stamp, and returns the document to {@link DocumentStatus#PENDING}.
     */
    public DocumentResponse resubmitDocument(UUID documentId, MultipartFile file) {
        requireFile(file);
        UUID tenantId = resolvedTenantId();
        Document document = requireDocument(documentId, tenantId);

        if (document.getStatus() != DocumentStatus.NEEDS_REVISION) {
            throw new InvalidDocumentStatusTransitionException(document.getStatus(), DocumentStatus.PENDING);
        }

        DocumentStoragePort.StoredFile stored = storage.replace(
                document.getStorageKey(), storageContext(document.getStudent(), document.getDocumentType()), file);

        document.setOriginalFilename(file.getOriginalFilename());
        document.setMimeType(stored.mimeType());
        document.setFileSizeBytes(stored.sizeBytes());
        document.setStorageKey(stored.storageKey());
        document.setRevisionNumber(document.getRevisionNumber() + 1);
        document.setStatus(DocumentStatus.PENDING);
        document.setRejectionReason(null);
        document.setVerifiedBy(null);
        document.setVerifiedAt(null);

        Document saved = documentRepository.save(document);
        log.info("Document {} resubmitted (revision {}) in tenant {}",
                documentId, saved.getRevisionNumber(), tenantId);

        events.publishEvent(new DocumentEvents.DocumentResubmitted(
                tenantId, resolvedUserId(), document.getStudent().getId(), documentId,
                document.getDocumentType(), saved.getRevisionNumber()));

        return DocumentResponse.from(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID studentId) {
        UUID tenantId = resolvedTenantId();
        requireStudent(studentId, tenantId);
        return documentRepository.findAllByStudentIdAndTenantId(studentId, tenantId)
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(UUID documentId) {
        return DocumentResponse.from(requireDocument(documentId, resolvedTenantId()));
    }

    /** Opens a document's stored content for the authenticated download proxy. */
    @Transactional(readOnly = true)
    public DocumentContent downloadDocument(UUID documentId) {
        Document document = requireDocument(documentId, resolvedTenantId());
        if (document.getStorageKey() == null) {
            throw new DocumentNotFoundException(documentId);
        }
        Resource resource = storage.load(document.getStorageKey());
        String filename = document.getOriginalFilename() != null
                ? document.getOriginalFilename() : "document";
        return new DocumentContent(resource, filename, document.getMimeType(), document.getFileSizeBytes());
    }

    // ── Dossier ───────────────────────────────────────────────────────────────

    /**
     * Builds the dossier view: readiness over the required set, per-status tallies, and
     * documents grouped by category with placeholder rows for required types not uploaded.
     */
    @Transactional(readOnly = true)
    public DossierView getDossier(UUID studentId) {
        UUID tenantId = resolvedTenantId();
        Student student = requireStudent(studentId, tenantId);
        List<Document> docs = documentRepository.findAllByStudentIdAndTenantId(studentId, tenantId);

        // status tallies
        Map<DocumentStatus, Long> tally = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus s : DocumentStatus.values()) tally.put(s, 0L);
        for (Document d : docs) tally.merge(d.getStatus(), 1L, Long::sum);

        // readiness over the core-required set
        List<DocumentType> required = DocumentType.coreRequirements();
        long approvedRequired = required.stream()
                .filter(t -> docs.stream().anyMatch(d ->
                        d.getDocumentType() == t && d.getStatus() == DocumentStatus.APPROVED))
                .count();
        int total = required.size();
        int percent = total == 0 ? 100 : (int) Math.round(approvedRequired * 100.0 / total);
        DossierView.Readiness readiness =
                new DossierView.Readiness((int) approvedRequired, total, percent);

        // group by category (preserve enum order), with placeholders for missing required types
        Map<DocumentCategory, List<DossierView.Row>> grouped = new LinkedHashMap<>();
        for (DocumentCategory c : DocumentCategory.values()) grouped.put(c, new ArrayList<>());

        for (Document d : docs) {
            grouped.get(d.getDocumentCategory()).add(new DossierView.Row(
                    d.getDocumentType(), d.getDocumentType().label(),
                    d.getDocumentType().isCoreRequirement(), true, DocumentResponse.from(d)));
        }
        for (DocumentType t : required) {
            boolean uploaded = docs.stream().anyMatch(d -> d.getDocumentType() == t);
            if (!uploaded) {
                grouped.get(t.category()).add(new DossierView.Row(
                        t, t.label(), true, false, null));
            }
        }

        List<DossierView.CategoryGroup> categories = new ArrayList<>();
        grouped.forEach((category, rows) -> {
            if (!rows.isEmpty()) {
                categories.add(new DossierView.CategoryGroup(category, category.folderName(), rows));
            }
        });

        return new DossierView(studentId, student.getFullName().trim(), readiness, tally, categories);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Deletes a document record and its stored bytes. Approved documents are retained
     * for audit integrity and cannot be deleted.
     */
    public void deleteDocument(UUID documentId) {
        UUID tenantId = resolvedTenantId();
        Document document = requireDocument(documentId, tenantId);

        if (document.getStatus() == DocumentStatus.APPROVED) {
            throw new IllegalStateException(
                    "Cannot delete an approved document. Contact a tenant admin to override.");
        }

        if (document.getStorageKey() != null) {
            storage.delete(document.getStorageKey());
        }
        UUID studentId = document.getStudent().getId();
        DocumentType type = document.getDocumentType();
        documentRepository.delete(document);
        log.info("Document {} deleted in tenant {}", documentId, tenantId);

        events.publishEvent(new DocumentEvents.DocumentDeleted(
                tenantId, resolvedUserId(), studentId, documentId, type));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private DocumentStoragePort.StorageContext storageContext(Student student, DocumentType type) {
        String folder = student.getFullName().trim() + " (" + student.getId() + ")";
        return new DocumentStoragePort.StorageContext(
                resolvedTenantId(), student.getId(), folder, type.category());
    }

    private void requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required");
        }
    }

    private Student requireStudent(UUID studentId, UUID tenantId) {
        return studentRepository.findByIdAndTenantId(studentId, tenantId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));
    }

    private Document requireDocument(UUID documentId, UUID tenantId) {
        return documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
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
