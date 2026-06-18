package com.eduflow.student;

import com.eduflow.student.dto.RegisterStudentRequest;
import com.eduflow.student.dto.StudentResponse;
import com.eduflow.student.dto.StudentSearchCriteria;
import com.eduflow.student.dto.UpdateStudentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * REST controller for the Student module.
 *
 * <p>Base path: {@code /api/v1/students}</p>
 *
 * <p>All endpoints are tenant-scoped; the resolved tenant ID is always taken from the
 * authenticated principal in the service layer — never from the URL or request body.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    // ── Search / List ────────────────────────────────────────────────────────

    /**
     * Returns a paginated, filtered list of students for the authenticated user's tenant.
     *
     * <p>Query parameters: {@code name}, {@code email}, {@code status}, {@code assignedCounselorId},
     * plus Spring's standard {@code page}, {@code size}, and {@code sort}.</p>
     *
     * @param name                partial match on first or last name
     * @param email               partial match on email address
     * @param status              exact status filter
     * @param assignedCounselorId filter by counselor UUID
     * @param pageable            pagination and sort (default: page=0, size=20, sort=lastName asc)
     * @return 200 OK with a page of {@link StudentResponse}
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<Page<StudentResponse>> searchStudents(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) StudentStatus status,
            @RequestParam(required = false) UUID assignedCounselorId,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable) {

        StudentSearchCriteria criteria = StudentSearchCriteria.builder()
                .name(name)
                .email(email)
                .status(status)
                .assignedCounselorId(assignedCounselorId)
                .build();

        return ResponseEntity.ok(studentService.searchStudents(criteria, pageable));
    }

    // ── Get one ──────────────────────────────────────────────────────────────

    /**
     * Retrieves a single student by ID.
     *
     * @param id the student UUID
     * @return 200 OK with the {@link StudentResponse}, or 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','COUNSELOR','DOC_OFFICER','VISA_OFFICER','SUPER_ADMIN')")
    public ResponseEntity<StudentResponse> getStudent(@PathVariable UUID id) {
        return ResponseEntity.ok(studentService.getStudent(id));
    }

    // ── Create ───────────────────────────────────────────────────────────────

    /**
     * Registers a new student.
     *
     * @param request validated registration payload
     * @return 201 Created with the {@link StudentResponse} and a {@code Location} header
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<StudentResponse> registerStudent(
            @Valid @RequestBody RegisterStudentRequest request) {

        StudentResponse response = studentService.registerStudent(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    // ── Update profile ───────────────────────────────────────────────────────

    /**
     * Updates a student's profile. Only non-null fields in the request body are applied.
     *
     * @param id      the student UUID
     * @param request the update payload
     * @return 200 OK with the updated {@link StudentResponse}
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<StudentResponse> updateStudent(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStudentRequest request) {

        return ResponseEntity.ok(studentService.updateStudent(id, request));
    }

    // ── Status transition ────────────────────────────────────────────────────

    /**
     * Transitions a student to a new lifecycle status.
     *
     * <p>Allowed transitions:
     * {@code LEAD → QUALIFIED → ACTIVE → ENROLLED}, and any → {@code INACTIVE}.</p>
     *
     * @param id        the student UUID
     * @param newStatus the target status
     * @return 200 OK with the updated {@link StudentResponse}
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('COUNSELOR','TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<StudentResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam StudentStatus newStatus) {

        return ResponseEntity.ok(studentService.updateStatus(id, newStatus));
    }

    // ── Delete (soft) ────────────────────────────────────────────────────────

    /**
     * Soft-deletes a student by setting their status to {@code INACTIVE}.
     * The student record is preserved for audit and reporting purposes.
     *
     * @param id the student UUID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> deleteStudent(@PathVariable UUID id) {
        studentService.deleteStudent(id);
        return ResponseEntity.noContent().build();
    }
}

