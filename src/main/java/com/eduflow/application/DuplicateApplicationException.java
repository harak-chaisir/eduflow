package com.eduflow.application;

import java.util.UUID;

/**
 * Thrown when a student already has an application to the same course.
 */
public class DuplicateApplicationException extends RuntimeException {

    public DuplicateApplicationException(UUID studentId, UUID courseId) {
        super("Student " + studentId + " already has an application to course " + courseId);
    }
}
