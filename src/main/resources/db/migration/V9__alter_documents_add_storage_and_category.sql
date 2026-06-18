-- ─────────────────────────────────────────────────────────────────────────────
-- V9: Extend the documents table for real file storage + categorisation.
--     Adds storage metadata (provider-agnostic), the denormalised category,
--     a revision counter, and a free-text description.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE documents
    ADD COLUMN document_category VARCHAR(50),
    ADD COLUMN mime_type         VARCHAR(150),
    ADD COLUMN file_size_bytes   BIGINT,
    ADD COLUMN storage_key       VARCHAR(512),
    ADD COLUMN description       VARCHAR(500),
    ADD COLUMN revision_number   INT NOT NULL DEFAULT 1;

-- Backfill the category for any existing rows from the document type.
UPDATE documents SET document_category = CASE document_type
    WHEN 'SEE_CERTIFICATE'       THEN 'ACADEMIC'
    WHEN 'PLUS_TWO_TRANSCRIPT'   THEN 'ACADEMIC'
    WHEN 'BACHELOR_TRANSCRIPT'   THEN 'ACADEMIC'
    WHEN 'DEGREE_CERTIFICATE'    THEN 'ACADEMIC'
    WHEN 'RECOMMENDATION_LETTER' THEN 'ACADEMIC'
    WHEN 'BANK_STATEMENT'        THEN 'FINANCIAL'
    WHEN 'SPONSORSHIP_LETTER'    THEN 'FINANCIAL'
    WHEN 'TAX_CLEARANCE'         THEN 'FINANCIAL'
    WHEN 'PASSPORT'              THEN 'IDENTITY'
    WHEN 'CITIZENSHIP'           THEN 'IDENTITY'
    WHEN 'PHOTOGRAPH'            THEN 'IDENTITY'
    WHEN 'IELTS'                 THEN 'ENGLISH_PROFICIENCY'
    WHEN 'PTE'                   THEN 'ENGLISH_PROFICIENCY'
    WHEN 'TOEFL'                 THEN 'ENGLISH_PROFICIENCY'
    WHEN 'OFFER_LETTER'          THEN 'OFFER_LETTERS'
    WHEN 'VISA_DOCS'             THEN 'VISA'
END;

ALTER TABLE documents
    ALTER COLUMN document_category SET NOT NULL,
    ADD CONSTRAINT chk_documents_category CHECK (document_category IN (
        'ACADEMIC', 'FINANCIAL', 'IDENTITY', 'ENGLISH_PROFICIENCY', 'OFFER_LETTERS', 'VISA'
    ));

CREATE INDEX idx_documents_category ON documents (document_category);

COMMENT ON COLUMN documents.document_category IS 'ACADEMIC | FINANCIAL | IDENTITY | ENGLISH_PROFICIENCY | OFFER_LETTERS | VISA (denormalised from document_type)';
COMMENT ON COLUMN documents.mime_type        IS 'MIME type captured at upload time';
COMMENT ON COLUMN documents.file_size_bytes  IS 'Stored file size in bytes';
COMMENT ON COLUMN documents.storage_key      IS 'Opaque reference from the active DocumentStoragePort (local path or Drive file id)';
COMMENT ON COLUMN documents.revision_number  IS 'Incremented on each in-place resubmission; starts at 1';
COMMENT ON COLUMN documents.description       IS 'Optional free-text description supplied at upload';
