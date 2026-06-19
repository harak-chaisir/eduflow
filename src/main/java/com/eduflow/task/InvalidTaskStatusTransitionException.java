package com.eduflow.task;

/** Thrown when a task status change is not permitted by the task lifecycle. */
public class InvalidTaskStatusTransitionException extends RuntimeException {
    public InvalidTaskStatusTransitionException(TaskStatus from, TaskStatus to) {
        super("Illegal task status transition: " + from + " → " + to);
    }
}
