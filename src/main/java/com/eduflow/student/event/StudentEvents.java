package com.eduflow.student.event;

import java.util.UUID;

/**
 * Domain events published by {@code StudentService} so cross-cutting concerns
 * (workflow auto-assignment, notifications) stay decoupled from student registration.
 */
public final class StudentEvents {

    private StudentEvents() {}

    /**
     * A student was registered (committed). Consumers: workflow module (auto-assign the
     * tenant's default workflow — PRD §14).
     */
    public record StudentCreated(UUID tenantId, UUID actorUserId, UUID studentId) {}
}
