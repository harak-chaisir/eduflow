package com.eduflow.application;

import com.eduflow.application.dto.ApplicationResponse;
import com.eduflow.application.dto.ApplicationSearchCriteria;
import com.eduflow.application.dto.CreateApplicationRequest;
import com.eduflow.application.dto.UpdateApplicationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Application module.
 *
 * <p>Applications are created/listed under a student
 * ({@code /api/v1/students/{studentId}/applications}) and managed individually under
 * {@code /api/v1/applications}. All endpoints are tenant-scoped via the principal.</p>
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    // ── Student-scoped ─────────────────────────────────────────────────────────

    @GetMapping("/students/{studentId}/applications")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<List<ApplicationResponse>> listForStudent(@PathVariable UUID studentId) {
        return ResponseEntity.ok(applicationService.listForStudent(studentId));
    }

    @PostMapping("/students/{studentId}/applications")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApplicationResponse> create(
            @PathVariable UUID studentId, @Valid @RequestBody CreateApplicationRequest request) {
        ApplicationResponse response = applicationService.createApplication(studentId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/applications/{id}").buildAndExpand(response.getId()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    // ── Application-scoped ──────────────────────────────────────────────────────

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<Page<ApplicationResponse>> search(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) UUID universityId,
            @RequestParam(required = false) UUID studentId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        ApplicationSearchCriteria criteria = ApplicationSearchCriteria.builder()
                .status(status).courseId(courseId).universityId(universityId).studentId(studentId).build();
        return ResponseEntity.ok(applicationService.search(criteria, pageable));
    }

    @GetMapping("/applications/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<ApplicationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(applicationService.get(id));
    }

    @PatchMapping("/applications/{id}")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApplicationResponse> updateNotes(
            @PathVariable UUID id, @Valid @RequestBody UpdateApplicationRequest request) {
        return ResponseEntity.ok(applicationService.updateNotes(id, request));
    }

    @PatchMapping("/applications/{id}/status")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable UUID id, @RequestParam ApplicationStatus newStatus) {
        return ResponseEntity.ok(applicationService.updateStatus(id, newStatus));
    }
}
