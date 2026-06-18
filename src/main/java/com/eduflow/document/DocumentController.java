package com.eduflow.document;

import com.eduflow.document.dto.DocumentResponse;
import com.eduflow.document.dto.VerifyDocumentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST API for the Document module.
 *
 * <p>Listing and uploading are nested under a student
 * ({@code /api/v1/students/{studentId}/documents}); actions on an existing document use
 * the flat {@code /api/v1/documents/{documentId}} paths. Every endpoint is tenant-scoped
 * in the service via the authenticated principal.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private static final String STAFF_OR_STUDENT =
            "hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','STUDENT','SUPER_ADMIN')";

    private final DocumentService documentService;

    // ── List / upload (student-scoped) ──────────────────────────────────────────

    @GetMapping("/students/{studentId}/documents")
    @PreAuthorize(STAFF_OR_STUDENT)
    public ResponseEntity<List<DocumentResponse>> list(@PathVariable UUID studentId) {
        return ResponseEntity.ok(documentService.listDocuments(studentId));
    }

    @PostMapping(path = "/students/{studentId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','STUDENT')")
    public ResponseEntity<DocumentResponse> upload(
            @PathVariable UUID studentId,
            @RequestParam("type") DocumentType type,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file) {

        DocumentResponse response = documentService.uploadDocument(studentId, type, description, file);
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath().path("/api/v1/documents/{id}")
                .buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    // ── Single document actions ─────────────────────────────────────────────────

    @GetMapping("/documents/{documentId}")
    @PreAuthorize(STAFF_OR_STUDENT)
    public ResponseEntity<DocumentResponse> get(@PathVariable UUID documentId) {
        return ResponseEntity.ok(documentService.getDocument(documentId));
    }

    @PatchMapping("/documents/{documentId}/verify")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','DOC_OFFICER')")
    public ResponseEntity<DocumentResponse> verify(
            @PathVariable UUID documentId,
            @Valid @RequestBody VerifyDocumentRequest request) {

        return ResponseEntity.ok(
                documentService.verifyDocument(documentId, request.getDecision(), request.getRemarks()));
    }

    @PostMapping(path = "/documents/{documentId}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','STUDENT')")
    public ResponseEntity<DocumentResponse> resubmit(
            @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file) {

        return ResponseEntity.ok(documentService.resubmitDocument(documentId, file));
    }

    @GetMapping("/documents/{documentId}/content")
    @PreAuthorize(STAFF_OR_STUDENT)
    public ResponseEntity<Resource> content(@PathVariable UUID documentId) {
        DocumentContent c = documentService.downloadDocument(documentId);
        MediaType mediaType = c.mimeType() != null
                ? MediaType.parseMediaType(c.mimeType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(c.filename()).build().toString())
                .body(c.resource());
    }
}
