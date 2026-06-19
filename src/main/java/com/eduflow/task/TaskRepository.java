package com.eduflow.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Task}. All finders are tenant-scoped.
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * "My tasks": open tasks in the tenant that are either assigned directly to the user
     * or owned by one of the user's roles. Ordered by due date (nulls last) then created.
     */
    @Query("""
            select t from Task t
            where t.tenant.id = :tenantId
              and t.status in :statuses
              and (t.assignedUserId = :userId or t.assignedRole in :roles)
            order by t.dueAt asc nulls last, t.createdAt asc
            """)
    List<Task> findMyOpenTasks(@Param("tenantId") UUID tenantId,
                               @Param("userId") UUID userId,
                               @Param("roles") Collection<String> roles,
                               @Param("statuses") Collection<TaskStatus> statuses);

    List<Task> findByTenantIdAndStudentId(UUID tenantId, UUID studentId);

    long countByTenantIdAndStatus(UUID tenantId, TaskStatus status);

    List<Task> findByTenantIdAndStatusAndDueAtBefore(UUID tenantId, TaskStatus status, java.time.Instant before);
}
