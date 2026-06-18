package com.eduflow.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link Document}.
 *
 * <p>All finder methods that return tenant-scoped data require a {@code tenantId} argument.
 * Never call the inherited {@code findAll()} or {@code findById()} directly — always use
 * the tenant-scoped variants below.</p>
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /**
     * Lists all documents for a specific student, scoped to the tenant.
     *
     * @param studentId the student UUID
     * @param tenantId  the tenant UUID
     * @return list of documents (may be empty, never null)
     */
    List<Document> findAllByStudentIdAndTenantId(UUID studentId, UUID tenantId);

    /**
     * Finds a single document by its ID, scoped to both student and tenant.
     *
     * @param id        the document UUID
     * @param studentId the student UUID
     * @param tenantId  the tenant UUID
     * @return optional document
     */
    Optional<Document> findByIdAndStudentIdAndTenantId(UUID id, UUID studentId, UUID tenantId);

    /**
     * Finds a single document by its ID, scoped to the tenant. Used by the top-level
     * {@code /api/v1/documents/{id}} endpoints where the student is not in the path.
     *
     * @param id       the document UUID
     * @param tenantId the tenant UUID
     * @return optional document
     */
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Checks whether a document of the given type already exists for the student.
     * Used to prevent duplicate uploads of the same document type.
     *
     * @param studentId    the student UUID
     * @param documentType the document type to check
     * @param tenantId     the tenant UUID
     * @return {@code true} if a document of that type already exists
     */
    boolean existsByStudentIdAndDocumentTypeAndTenantId(
            UUID studentId, DocumentType documentType, UUID tenantId);

    /**
     * Lists all documents of a given status for a tenant (e.g. all PENDING docs for review).
     *
     * @param status   the document status to filter by
     * @param tenantId the tenant UUID
     * @return list of matching documents
     */
    List<Document> findAllByStatusAndTenantId(DocumentStatus status, UUID tenantId);
}

