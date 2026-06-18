package com.eduflow.web;

import com.eduflow.document.DocumentContent;
import com.eduflow.document.DocumentService;
import com.eduflow.document.DocumentType;
import com.eduflow.document.dto.DocumentResponse;
import com.eduflow.document.dto.VerificationDecision;
import com.eduflow.document.storage.DocumentStorageException;
import com.eduflow.student.StudentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Thymeleaf staff UI for the Document module.
 *
 * <p>All mutation endpoints support both traditional POST/Redirect/Get (for
 * non-HTMX requests) and HTMX partial responses (detected via the
 * {@code HX-Request} header). HTMX responses swap {@code #dossierContent}
 * in place; non-HTMX requests redirect as before.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','STUDENT','SUPER_ADMIN')")
public class DocumentViewController {

    private final DocumentService documentService;

    // ── Dossier page ──────────────────────────────────────────────────────────

    @GetMapping("/students/{studentId}/documents")
    public String dossier(@PathVariable UUID studentId, Model model,
                          RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("dossier", documentService.getDossier(studentId));
            model.addAttribute("documentTypes", DocumentType.values());
            return "document/student-documents";
        } catch (StudentNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/students";
        }
    }

    // ── Verify form (HTMX lazy-load) ─────────────────────────────────────────

    /**
     * Returns the verify form fragment loaded into the verify modal via HTMX GET.
     * Role-guarded: only TENANT_ADMIN and DOC_OFFICER may access this endpoint.
     */
    @GetMapping("/documents/{documentId}/verify-form")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','DOC_OFFICER')")
    public String verifyForm(@PathVariable UUID documentId, Model model) {
        DocumentResponse doc = documentService.getDocument(documentId);
        model.addAttribute("document", doc);
        return "document/verify-form :: verifyForm";
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @PostMapping("/students/{studentId}/documents/upload")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','STUDENT','SUPER_ADMIN')")
    public String upload(@PathVariable UUID studentId,
                         @RequestParam("type") DocumentType type,
                         @RequestParam(value = "description", required = false) String description,
                         @RequestParam("file") MultipartFile file,
                         @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                         Model model,
                         RedirectAttributes ra) {
        String message;
        boolean success = true;
        try {
            DocumentResponse doc = documentService.uploadDocument(studentId, type, description, file);
            message = doc.getDocumentTypeLabel() + " uploaded — awaiting verification.";
        } catch (IllegalArgumentException | DocumentStorageException ex) {
            message = ex.getMessage();
            success = false;
        }

        if (htmxRequest != null) {
            return dossierFragment(studentId, success ? message : null,
                                   success ? null : message, model);
        }
        ra.addFlashAttribute(success ? "successMessage" : "errorMessage", message);
        return "redirect:/students/" + studentId + "/documents";
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    @PostMapping("/documents/{documentId}/verify")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','DOC_OFFICER')")
    public String verify(@PathVariable UUID documentId,
                         @RequestParam("decision") VerificationDecision decision,
                         @RequestParam(value = "remarks", required = false) String remarks,
                         @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                         Model model,
                         RedirectAttributes ra) {
        try {
            DocumentResponse doc = documentService.verifyDocument(documentId, decision, remarks);
            String message = doc.getDocumentTypeLabel() + " marked " + doc.getStatus().label() + ".";
            if (htmxRequest != null) {
                return dossierFragment(doc.getStudentId(), message, null, model);
            }
            ra.addFlashAttribute("successMessage", message);
            return "redirect:/students/" + doc.getStudentId() + "/documents";
        } catch (IllegalArgumentException ex) {
            DocumentResponse doc = documentService.getDocument(documentId);
            if (htmxRequest != null) {
                return dossierFragment(doc.getStudentId(), null, ex.getMessage(), model);
            }
            ra.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/students/" + doc.getStudentId() + "/documents";
        }
    }

    // ── Resubmit ──────────────────────────────────────────────────────────────

    @PostMapping("/documents/{documentId}/resubmit")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','STUDENT')")
    public String resubmit(@PathVariable UUID documentId,
                           @RequestParam("file") MultipartFile file,
                           @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                           Model model,
                           RedirectAttributes ra) {
        DocumentResponse doc;
        String message;
        boolean success = true;
        try {
            doc = documentService.resubmitDocument(documentId, file);
            message = doc.getDocumentTypeLabel() + " resubmitted (revision " + doc.getRevisionNumber() + ").";
        } catch (IllegalArgumentException | DocumentStorageException ex) {
            doc = documentService.getDocument(documentId);
            message = ex.getMessage();
            success = false;
        }

        if (htmxRequest != null) {
            return dossierFragment(doc.getStudentId(), success ? message : null,
                                   success ? null : message, model);
        }
        ra.addFlashAttribute(success ? "successMessage" : "errorMessage", message);
        return "redirect:/students/" + doc.getStudentId() + "/documents";
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @PostMapping("/documents/{documentId}/delete")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','DOC_OFFICER')")
    public String delete(@PathVariable UUID documentId,
                         @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                         Model model,
                         RedirectAttributes ra) {
        DocumentResponse doc = documentService.getDocument(documentId);
        UUID studentId = doc.getStudentId();
        try {
            documentService.deleteDocument(documentId);
            String message = doc.getDocumentTypeLabel() + " deleted.";
            if (htmxRequest != null) {
                return dossierFragment(studentId, message, null, model);
            }
            ra.addFlashAttribute("successMessage", message);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            if (htmxRequest != null) {
                return dossierFragment(studentId, null, ex.getMessage(), model);
            }
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/students/" + studentId + "/documents";
    }

    // ── Download (unchanged) ──────────────────────────────────────────────────

    @GetMapping("/documents/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID documentId) {
        DocumentContent c = documentService.downloadDocument(documentId);
        MediaType mediaType = c.mimeType() != null
                ? MediaType.parseMediaType(c.mimeType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(c.filename()).build().toString())
                .body(c.resource());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Populates the model and returns the {@code dossierContent} fragment view string
     * used by all HTMX mutation responses.
     */
    private String dossierFragment(UUID studentId, String successMessage, String errorMessage,
                                   Model model) {
        model.addAttribute("dossier", documentService.getDossier(studentId));
        model.addAttribute("documentTypes", DocumentType.values());
        if (successMessage != null) model.addAttribute("successMessage", successMessage);
        if (errorMessage   != null) model.addAttribute("errorMessage",   errorMessage);
        return "document/student-documents :: dossierContent";
    }
}
