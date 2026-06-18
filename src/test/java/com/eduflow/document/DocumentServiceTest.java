package com.eduflow.document;

import com.eduflow.document.dto.DocumentResponse;
import com.eduflow.document.dto.DossierView;
import com.eduflow.document.dto.VerificationDecision;
import com.eduflow.document.event.DocumentEvents;
import com.eduflow.document.storage.DocumentStoragePort;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.student.Student;
import com.eduflow.student.StudentRepository;
import com.eduflow.tenant.Tenant;
import com.eduflow.tenant.TenantRepository;
import com.eduflow.user.User;
import com.eduflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentService}: transition guard, remarks-required rule,
 * resubmit-only-from-NEEDS_REVISION, dossier readiness, and event publication.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final UUID TENANT_ID  = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID DOC_ID     = UUID.randomUUID();

    @Mock DocumentRepository documentRepository;
    @Mock StudentRepository  studentRepository;
    @Mock TenantRepository   tenantRepository;
    @Mock UserRepository     userRepository;
    @Mock DocumentStoragePort storage;
    @Mock ApplicationEventPublisher events;

    @InjectMocks DocumentService documentService;

    private Tenant tenant;
    private Student student;

    @BeforeEach
    void setUp() {
        tenant = Tenant.builder().build();
        setId(tenant, TENANT_ID);

        User user = User.builder().tenant(tenant).build();
        setId(user, USER_ID);

        student = Student.builder().tenant(tenant).firstName("Jane").lastName("Doe").build();
        setId(student, STUDENT_ID);

        EduFlowUserDetails principal = new EduFlowUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    // ── upload ──────────────────────────────────────────────────────────────────

    @Test
    void uploadDocument_whenValid_storesBytesAndCreatesPending() {
        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID)).thenReturn(Optional.of(student));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(storage.store(any(), any())).thenReturn(
                new DocumentStoragePort.StoredFile("key/abc.pdf", 1234L, "application/pdf", null));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            setId(d, DOC_ID);
            return d;
        });

        MultipartFile file = new MockMultipartFile("file", "abc.pdf", "application/pdf", new byte[]{1, 2, 3});
        DocumentResponse res = documentService.uploadDocument(STUDENT_ID, DocumentType.PASSPORT, "scan", file);

        assertThat(res.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(res.getDocumentCategory()).isEqualTo(DocumentCategory.IDENTITY);
        assertThat(res.getRevisionNumber()).isEqualTo(1);
        verify(events).publishEvent(any(DocumentEvents.DocumentUploaded.class));
    }

    @Test
    void uploadDocument_whenFileEmpty_throws() {
        MultipartFile empty = new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> documentService.uploadDocument(STUDENT_ID, DocumentType.PASSPORT, null, empty))
                .isInstanceOf(IllegalArgumentException.class);
        verify(documentRepository, never()).save(any());
    }

    // ── verify ──────────────────────────────────────────────────────────────────

    @Test
    void verifyDocument_approve_movesToApprovedAndPublishes() {
        Document doc = pendingDoc();
        when(documentRepository.findByIdAndTenantId(DOC_ID, TENANT_ID)).thenReturn(Optional.of(doc));
        when(userRepository.findByIdAndTenantId(USER_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(documentRepository.save(doc)).thenReturn(doc);

        DocumentResponse res = documentService.verifyDocument(DOC_ID, VerificationDecision.APPROVE, null);

        assertThat(res.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        verify(events).publishEvent(any(DocumentEvents.DocumentVerified.class));
    }

    @Test
    void verifyDocument_rejectWithoutRemarks_throws() {
        Document doc = pendingDoc();
        when(documentRepository.findByIdAndTenantId(DOC_ID, TENANT_ID)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.verifyDocument(DOC_ID, VerificationDecision.REJECT, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(documentRepository, never()).save(any());
    }

    @Test
    void verifyDocument_onApproved_isInvalidTransition() {
        Document doc = pendingDoc();
        doc.setStatus(DocumentStatus.APPROVED);
        when(documentRepository.findByIdAndTenantId(DOC_ID, TENANT_ID)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> documentService.verifyDocument(DOC_ID, VerificationDecision.REJECT, "no"))
                .isInstanceOf(InvalidDocumentStatusTransitionException.class);
    }

    // ── resubmit ──────────────────────────────────────────────────────────────────

    @Test
    void resubmit_fromNeedsRevision_bumpsRevisionAndReturnsToPending() {
        Document doc = pendingDoc();
        doc.setStatus(DocumentStatus.NEEDS_REVISION);
        doc.setRevisionNumber(1);
        doc.setStorageKey("key/old.pdf");
        when(documentRepository.findByIdAndTenantId(DOC_ID, TENANT_ID)).thenReturn(Optional.of(doc));
        when(storage.replace(eq("key/old.pdf"), any(), any())).thenReturn(
                new DocumentStoragePort.StoredFile("key/old.pdf", 999L, "application/pdf", null));
        when(documentRepository.save(doc)).thenReturn(doc);

        MultipartFile file = new MockMultipartFile("file", "new.pdf", "application/pdf", new byte[]{9});
        DocumentResponse res = documentService.resubmitDocument(DOC_ID, file);

        assertThat(res.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(res.getRevisionNumber()).isEqualTo(2);
        verify(events).publishEvent(any(DocumentEvents.DocumentResubmitted.class));
    }

    @Test
    void resubmit_whenNotNeedsRevision_isInvalidTransition() {
        Document doc = pendingDoc(); // PENDING
        when(documentRepository.findByIdAndTenantId(DOC_ID, TENANT_ID)).thenReturn(Optional.of(doc));

        MultipartFile file = new MockMultipartFile("file", "new.pdf", "application/pdf", new byte[]{9});
        assertThatThrownBy(() -> documentService.resubmitDocument(DOC_ID, file))
                .isInstanceOf(InvalidDocumentStatusTransitionException.class);
        verify(storage, never()).replace(any(), any(), any());
    }

    // ── dossier ──────────────────────────────────────────────────────────────────

    @Test
    void getDossier_computesReadinessOverRequiredSet() {
        // 4 core-required types: PLUS_TWO_TRANSCRIPT, BANK_STATEMENT, PASSPORT, PHOTOGRAPH.
        // Approve PASSPORT only → 1/4 = 25%.
        Document passport = doc(DocumentType.PASSPORT, DocumentStatus.APPROVED);
        Document bank = doc(DocumentType.BANK_STATEMENT, DocumentStatus.PENDING);
        when(studentRepository.findByIdAndTenantId(STUDENT_ID, TENANT_ID)).thenReturn(Optional.of(student));
        when(documentRepository.findAllByStudentIdAndTenantId(STUDENT_ID, TENANT_ID))
                .thenReturn(List.of(passport, bank));

        DossierView dossier = documentService.getDossier(STUDENT_ID);

        assertThat(dossier.readiness().requiredTotal()).isEqualTo(4);
        assertThat(dossier.readiness().requiredApproved()).isEqualTo(1);
        assertThat(dossier.readiness().percent()).isEqualTo(25);
        assertThat(dossier.statusTally().get(DocumentStatus.APPROVED)).isEqualTo(1L);
        // categories include uploaded docs + placeholders for missing required types
        assertThat(dossier.categories()).isNotEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private Document pendingDoc() {
        return doc(DocumentType.PASSPORT, DocumentStatus.PENDING);
    }

    private Document doc(DocumentType type, DocumentStatus status) {
        Document d = Document.builder()
                .tenant(tenant)
                .student(student)
                .documentType(type)
                .documentCategory(type.category())
                .status(status)
                .revisionNumber(1)
                .build();
        setId(d, DOC_ID);
        return d;
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = com.eduflow.common.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
