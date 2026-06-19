package com.eduflow.workflow;

import com.eduflow.workflow.dto.StageDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link StudentWorkflow}. All finders are tenant-scoped.
 */
public interface StudentWorkflowRepository extends JpaRepository<StudentWorkflow, UUID> {

    Optional<StudentWorkflow> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<StudentWorkflow> findByStudentIdAndStatus(UUID studentId, InstanceStatus status);

    List<StudentWorkflow> findByStudentIdAndTenantId(UUID studentId, UUID tenantId);

    List<StudentWorkflow> findByTenantIdAndStatus(UUID tenantId, InstanceStatus status);

    /** All instances in a given status across tenants — used by the system-wide SLA job. */
    List<StudentWorkflow> findByStatus(InstanceStatus status);

    long countByTenantIdAndStatus(UUID tenantId, InstanceStatus status);

    long countByTenantIdAndCurrentStageId(UUID tenantId, UUID currentStageId);

    long countByTenantIdAndStatusAndSlaBreachedTrue(UUID tenantId, InstanceStatus status);

    /** Count of ACTIVE instances grouped by current stage — the "students per stage" report. */
    @Query("""
            select new com.eduflow.workflow.dto.StageDistribution(
                       s.id, s.name, s.color, count(sw))
            from StudentWorkflow sw join sw.currentStage s
            where sw.tenant.id = :tenantId and sw.status = com.eduflow.workflow.InstanceStatus.ACTIVE
            group by s.id, s.name, s.color, s.displayOrder
            order by s.displayOrder asc
            """)
    List<StageDistribution> countActivePerStage(@Param("tenantId") UUID tenantId);
}
