-- ─────────────────────────────────────────────────────────────────────────────
-- V7: Create documents table for student document management
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE documents (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL,
    student_id          UUID         NOT NULL,

    -- Document metadata
    document_type       VARCHAR(100) NOT NULL,
    original_filename   VARCHAR(255),
    drive_file_id       VARCHAR(255),
    drive_folder_id     VARCHAR(255),

    -- Review state
    status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    notes               VARCHAR(1000),
    rejection_reason    VARCHAR(500),
    verified_by_id      UUID,
    verified_at         TIMESTAMPTZ,

    -- Audit columns
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    CONSTRAINT pk_documents                 PRIMARY KEY (id),
    CONSTRAINT fk_documents_tenant          FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_documents_student         FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE,
    CONSTRAINT fk_documents_verified_by     FOREIGN KEY (verified_by_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_documents_status         CHECK (status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'NEEDS_REVISION'
    )),
    CONSTRAINT chk_documents_type           CHECK (document_type IN (
        'SEE_CERTIFICATE', 'PLUS_TWO_TRANSCRIPT', 'BACHELOR_TRANSCRIPT',
        'DEGREE_CERTIFICATE', 'RECOMMENDATION_LETTER', 'BANK_STATEMENT',
        'SPONSORSHIP_LETTER', 'TAX_CLEARANCE', 'PASSPORT', 'CITIZENSHIP',
        'PHOTOGRAPH', 'IELTS', 'PTE', 'TOEFL', 'OFFER_LETTER', 'VISA_DOCS'
    ))
);

CREATE INDEX idx_documents_tenant_id    ON documents (tenant_id);
CREATE INDEX idx_documents_student_id   ON documents (student_id);
CREATE INDEX idx_documents_status       ON documents (status);
CREATE INDEX idx_documents_type         ON documents (document_type);
CREATE INDEX idx_documents_verified_by  ON documents (verified_by_id);

-- Comments
COMMENT ON TABLE  documents                     IS 'Student document submissions; Drive metadata only — no file bytes stored here';
COMMENT ON COLUMN documents.tenant_id           IS 'The consultancy this document belongs to';
COMMENT ON COLUMN documents.student_id          IS 'The student who owns this document';
COMMENT ON COLUMN documents.document_type       IS 'Category of document per DocumentType enum';
COMMENT ON COLUMN documents.drive_file_id       IS 'Google Drive file ID; populated after upload to Drive';
COMMENT ON COLUMN documents.drive_folder_id     IS 'Google Drive folder ID for the student document folder';
COMMENT ON COLUMN documents.status              IS 'PENDING | APPROVED | REJECTED | NEEDS_REVISION';
COMMENT ON COLUMN documents.rejection_reason    IS 'Required when status = REJECTED or NEEDS_REVISION';
COMMENT ON COLUMN documents.verified_by_id      IS 'The doc officer who last reviewed this document';
COMMENT ON COLUMN documents.verified_at         IS 'Timestamp of the last verification action';

