package com.eduflow.task;

import java.util.UUID;

/** Thrown when a {@link Task} cannot be found for the calling tenant. */
public class TaskNotFoundException extends RuntimeException {
    public TaskNotFoundException(UUID id) {
        super("Task not found with id: " + id);
    }
}
