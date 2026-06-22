package com.eduflow.exception;

import com.eduflow.application.ApplicationNotFoundException;
import com.eduflow.application.DuplicateApplicationException;
import com.eduflow.application.InvalidApplicationStatusTransitionException;
import com.eduflow.document.DocumentNotFoundException;
import com.eduflow.document.InvalidDocumentStatusTransitionException;
import com.eduflow.document.storage.DocumentStorageException;
import com.eduflow.university.CourseNotFoundException;
import com.eduflow.university.UniversityNotFoundException;
import com.eduflow.student.DuplicateStudentException;
import com.eduflow.student.InvalidStudentStatusTransitionException;
import com.eduflow.student.StudentNotFoundException;
import com.eduflow.task.InvalidTaskStatusTransitionException;
import com.eduflow.task.TaskNotFoundException;
import com.eduflow.tenant.DuplicateSlugException;
import com.eduflow.tenant.InvalidTenantStatusTransitionException;
import com.eduflow.tenant.TenantLimitExceededException;
import com.eduflow.tenant.TenantNotFoundException;
import com.eduflow.user.DuplicateStaffException;
import com.eduflow.user.LastTenantAdminException;
import com.eduflow.user.StaffNotFoundException;
import com.eduflow.workflow.DuplicateWorkflowNameException;
import com.eduflow.workflow.InvalidWorkflowGraphException;
import com.eduflow.workflow.InvalidWorkflowTransitionException;
import com.eduflow.workflow.RequiredDocumentsMissingException;
import com.eduflow.workflow.StudentWorkflowNotFoundException;
import com.eduflow.workflow.WorkflowArchivedException;
import com.eduflow.workflow.WorkflowTemplateNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralised exception handler that maps domain and framework exceptions to a
 * consistent {@link ErrorResponse} envelope.
 *
 * <p>All unhandled exceptions fall through to the catch-all {@link #handleGeneric} handler
 * which returns a 500 without leaking internal details.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String NOT_FOUND             = "NOT_FOUND";
    private static final String CONFLICT              = "CONFLICT";
    private static final String UNPROCESSABLE_ENTITY  = "UNPROCESSABLE_ENTITY";
    private static final String VALIDATION_ERROR      = "VALIDATION_ERROR";
    private static final String BAD_REQUEST           = "BAD_REQUEST";
    private static final String FORBIDDEN             = "FORBIDDEN";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    // ── 404 Not Found ────────────────────────────────────────────────────────

    @ExceptionHandler(StudentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStudentNotFound(
            StudentNotFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDocumentNotFound(
            DocumentNotFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(
            TenantNotFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(StaffNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStaffNotFound(
            StaffNotFoundException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({WorkflowTemplateNotFoundException.class, StudentWorkflowNotFoundException.class,
            TaskNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleWorkflowNotFound(
            RuntimeException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({UniversityNotFoundException.class, CourseNotFoundException.class,
            ApplicationNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleCatalogNotFound(
            RuntimeException ex, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, NOT_FOUND, ex.getMessage(), request);
    }

    // ── 409 Conflict ─────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateStudentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateStudent(
            DuplicateStudentException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateStaffException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateStaff(
            DuplicateStaffException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateSlugException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSlug(
            DuplicateSlugException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(TenantLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTenantLimitExceeded(
            TenantLimitExceededException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateWorkflowNameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateWorkflowName(
            DuplicateWorkflowNameException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateApplicationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateApplication(
            DuplicateApplicationException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidApplicationStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidApplicationStatusTransition(
            InvalidApplicationStatusTransitionException ex, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, CONFLICT, ex.getMessage(), request);
    }

    // ── 422 Unprocessable Entity ─────────────────────────────────────────────

    @ExceptionHandler(InvalidStudentStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(
            InvalidStudentStatusTransitionException ex, HttpServletRequest request) {

        return build(HttpStatus.UNPROCESSABLE_CONTENT, UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidDocumentStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDocumentStatusTransition(
            InvalidDocumentStatusTransitionException ex, HttpServletRequest request) {

        return build(HttpStatus.UNPROCESSABLE_CONTENT, UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTenantStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTenantStatusTransition(
            InvalidTenantStatusTransitionException ex, HttpServletRequest request) {

        return build(HttpStatus.UNPROCESSABLE_CONTENT, UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler(LastTenantAdminException.class)
    public ResponseEntity<ErrorResponse> handleLastTenantAdmin(
            LastTenantAdminException ex, HttpServletRequest request) {

        return build(HttpStatus.UNPROCESSABLE_CONTENT, UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    @ExceptionHandler({InvalidWorkflowGraphException.class, WorkflowArchivedException.class,
            InvalidWorkflowTransitionException.class, RequiredDocumentsMissingException.class,
            InvalidTaskStatusTransitionException.class})
    public ResponseEntity<ErrorResponse> handleInvalidWorkflow(
            RuntimeException ex, HttpServletRequest request) {

        return build(HttpStatus.UNPROCESSABLE_CONTENT, UNPROCESSABLE_ENTITY, ex.getMessage(), request);
    }

    // ── 400 Bad Request (bean validation) ────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return build(HttpStatus.BAD_REQUEST, VALIDATION_ERROR, message, request);
    }

    // ── 400 Bad Request (illegal arguments) ──────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST, BAD_REQUEST, ex.getMessage(), request);
    }

    // ── 403 Forbidden ────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    @SuppressWarnings("unused")     // ex intentionally not used — don't leak exception details
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        return build(HttpStatus.FORBIDDEN, FORBIDDEN, "Access denied", request);
    }

    // ── 413 Payload Too Large ────────────────────────────────────────────────

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @SuppressWarnings("unused")
    public ResponseEntity<ErrorResponse> handleTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {

        return build(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                "File exceeds the upload limit", request);
    }

    // ── 502 Bad Gateway (document storage backend) ───────────────────────────

    @ExceptionHandler(DocumentStorageException.class)
    public ResponseEntity<ErrorResponse> handleStorage(
            DocumentStorageException ex, HttpServletRequest request) {

        log.error("Document storage error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.BAD_GATEWAY, "STORAGE_ERROR", "Document storage error", request);
    }

    // ── 500 Internal Server Error (catch-all) ────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(
            Exception ex, HttpServletRequest request) {

        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", request);
    }

    // ── Builder helper ───────────────────────────────────────────────────────

    private static ResponseEntity<ErrorResponse> build(
            HttpStatus status, String error, String message, HttpServletRequest request) {

        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity.status(status).body(body);
    }
}

