package com.eduflow.university;

import java.util.UUID;

/**
 * Thrown when a requested {@link Course} cannot be found for the given tenant.
 */
public class CourseNotFoundException extends RuntimeException {

    public CourseNotFoundException(UUID courseId) {
        super("Course not found with id: " + courseId);
    }
}
