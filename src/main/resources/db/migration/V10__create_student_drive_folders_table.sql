-- ─────────────────────────────────────────────────────────────────────────────
-- V10: Per-student storage folder-id cache.
--      Used by the Google Drive storage adapter to avoid re-provisioning the
--      folder tree on every upload. The local filesystem adapter does not need it.
--      category = 'ROOT' or a DocumentCategory name.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE student_drive_folders (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       UUID         NOT NULL,
    student_id      UUID         NOT NULL,
    category        VARCHAR(50)  NOT NULL,
    drive_folder_id VARCHAR(255) NOT NULL,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),

    CONSTRAINT pk_student_drive_folders            PRIMARY KEY (id),
    CONSTRAINT uq_student_drive_folders_stu_cat    UNIQUE (student_id, category),
    CONSTRAINT fk_student_drive_folders_tenant     FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_drive_folders_student    FOREIGN KEY (student_id)
        REFERENCES students (id) ON DELETE CASCADE
);

CREATE INDEX idx_student_drive_folders_tenant_id ON student_drive_folders (tenant_id);
CREATE INDEX idx_student_drive_folders_student   ON student_drive_folders (student_id);

COMMENT ON TABLE  student_drive_folders          IS 'Cache of provisioned storage folder ids per student/category (Google Drive adapter)';
COMMENT ON COLUMN student_drive_folders.category IS 'ROOT or a DocumentCategory name';
