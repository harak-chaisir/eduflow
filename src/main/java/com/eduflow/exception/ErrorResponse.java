package com.eduflow.exception;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * Standardised error envelope returned by {@link GlobalExceptionHandler}.
 *
 * <pre>{@code
 * {
 *   "status":    404,
 *   "error":     "NOT_FOUND",
 *   "message":   "Student not found with id: <uuid>",
 *   "timestamp": "2026-06-14T10:00:00Z",
 *   "path":      "/api/v1/students/<uuid>"
 * }
 * }</pre>
 */
@Value
@Builder
public class ErrorResponse {

    int status;
    String error;
    String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant timestamp;

    String path;
}

