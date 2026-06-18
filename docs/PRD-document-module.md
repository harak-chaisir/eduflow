# PRD — EduFlow CRM Document Module

**Status:** Ready for integration
**Owner:** Engineering
**Applies to:** `com.eduflow.document` (+ supporting `config`, `security` glue)
**Stack:** Java 25 · Spring Boot 4.1 · Spring Data JPA · Flyway · PostgreSQL 17 · Spring Security 6 · Thymeleaf · Google Drive API v3

---

## 1. Summary

The Document module lets a consultancy collect, store, and verify the documents a
student needs across their journey to university enrollment. Files live in **Google
Drive** (organised into a predictable per-student folder tree); EduFlow stores the
Drive references plus the verification state and exposes both a REST API and a
server-rendered staff UI.

The module's single job, and the lens every decision is measured against, is:

> **"Is this student's document file complete and verified?"**

That question drives the signature UI element (a *dossier-readiness meter*) and the
data model (status lifecycle + a default required-document set).

---

## 2. Business context & value

Consultancies today scatter documents across WhatsApp, email, and ad-hoc Drive links.
That creates four concrete costs this module removes:

| Problem today | Cost | How the module addresses it |
|---|---|---|
| Documents spread across channels | Staff waste time hunting; files get lost before a university deadline | One structured Drive tree per student, mirrored in the CRM |
| No clear "is this file ready?" signal | Applications submitted with missing/expired documents → rejections, re-work | Dossier-readiness meter over a defined required set |
| Verification is informal | No record of *who* approved *what*; disputes and compliance gaps | Every decision stamps the officer, timestamp, and remarks |
| Revisions handled by re-sending files | Version confusion; staff act on stale copies | In-place revision flow with revision counter and a single source of truth |

### Representative business cases

1. **Counselor onboarding a new student** uploads transcripts, passport, and bank
   statement; the readiness meter immediately shows what is still outstanding, so the
   counselor chases the *right* documents rather than guessing.
2. **Documentation officer clearing a queue** opens a student, sees everything
   `PENDING`, and approves / rejects / requests revision with a mandatory reason on
   anything not approved — producing an audit trail automatically.
3. **Student self-service** (future portal role) uploads their own documents and sees
   live status, cutting the back-and-forth with staff.
4. **Compliance review / audit** reconstructs exactly who verified each document and
   when, from the append-only audit events the module publishes.

---

## 3. Goals & non-goals

**Goals**
- Upload documents against a typed catalogue (`DocumentType`) and store them in Drive.
- Track verification through a guarded lifecycle.
- Show a per-student dossier with readiness and per-category breakdown.
- Decouple audit and notifications via domain events.
- Enforce tenant isolation on every read and write.

**Non-goals (this iteration)**
- Tenant-configurable required-document sets (uses a sensible default; see §12).
- Client-side direct-to-Drive upload (backend proxies the upload; see §11).
- OCR / document data extraction (V2 roadmap).
- Bulk upload / zip import.

---

## 4. Personas & permissions

| Role | List/View | Upload | Verify | Resubmit |
|---|:--:|:--:|:--:|:--:|
| `TENANT_ADMIN` | ✓ | ✓ | ✓ | ✓ |
| `COUNSELOR` | ✓ | ✓ | — | ✓ |
| `DOC_OFFICER` | ✓ | ✓ | ✓ | — |
| `VISA_OFFICER` | ✓ | — | — | — |
| `STUDENT` | ✓ | ✓ | — | ✓ |
| `SUPER_ADMIN` | cross-tenant (via security context) | — | — | — |

Enforced with method-level `@PreAuthorize` on both controllers.

---

## 5. Domain model

```
StudentDocument (extends BaseEntity)
  ├─ tenantId            UUID         (owning consultancy; from security context)
  ├─ student             Student      (@ManyToOne LAZY)
  ├─ documentType        DocumentType (enum)
  ├─ documentCategory    DocumentCategory (denormalised from type)
  ├─ status              DocumentStatus (PENDING → …)
  ├─ fileName / mimeType / fileSizeBytes
  ├─ driveFileId / driveFolderId / driveViewLink
  ├─ description
  ├─ revisionNumber
  └─ verifiedBy / verifiedAt / verificationRemarks

StudentDriveFolder (extends BaseEntity)   ← Drive folder-id cache
  ├─ tenantId / studentId
  ├─ category   ("ROOT" | DocumentCategory name)
  └─ driveFolderId
```

**Enums**
- `DocumentType` — the 16 catalogue values; each maps to a `DocumentCategory`, a UI
  label, and a `coreRequirement` flag.
- `DocumentCategory` — `ACADEMIC`, `FINANCIAL`, `IDENTITY`, `ENGLISH_PROFICIENCY`,
  `OFFER_LETTERS`, `VISA`; each carries the exact Drive folder name.
- `DocumentStatus` — `PENDING`, `APPROVED`, `REJECTED`, `NEEDS_REVISION`, with the
  transition rules encoded on the enum itself.

---

## 6. Status lifecycle

```
        ┌──────────► APPROVED   (terminal)
        │
PENDING ├──────────► REJECTED   (terminal)
        │
        └──────────► NEEDS_REVISION ──(resubmit)──► PENDING
```

- The rule lives on `DocumentStatus.canTransitionTo(...)`; the service validates every
  change and throws `InvalidDocumentTransitionException` on a violation.
- Verification requires **remarks** for `REJECT` and `REQUEST_REVISION` (enforced in
  the service so both REST and UI obey it).
- Resubmission is only valid from `NEEDS_REVISION`; it replaces the Drive file content
  in place (same `driveFileId`), bumps `revisionNumber`, and clears the prior stamp.

---

## 7. Dossier readiness

- A **required set** = all `DocumentType` with `coreRequirement = true`
  (default: `PLUS_TWO_TRANSCRIPT`, `BANK_STATEMENT`, `PASSPORT`, `PHOTOGRAPH`).
- **Percent complete** = `requiredApproved / requiredTotal` (a required type counts
  only when it has an `APPROVED` upload).
- The dossier view also returns per-status tallies and documents grouped by category,
  including placeholder rows for required types not yet uploaded.

---

## 8. Google Drive integration

### Folder tree
```
{appRoot}/ {tenantId}/ Students/ {Student Full Name (studentId)}/
    ├── Academic/
    ├── Financial/
    ├── Identity/
    ├── English Proficiency/
    ├── Offer Letters/
    └── Visa/
```

### Provisioning & caching
- `DriveFolderProvisioningService` ensures the tree exists and returns the target
  category folder id.
- Per-student folder ids (ROOT + each category) are cached in `student_drive_folders`
  so the tree is built once. Tenant-level container folders are cached in memory and
  cheaply re-resolved after a restart.

### Authentication
- A Google **service account** key (scope `drive.file` — least privilege).
- Recommended deployment: the service account is a member of a **Shared Drive**, so
  files are owned by the organisation (not a person) and survive staff turnover. Set
  `eduflow.google-drive.shared-drive=true` and point `root-folder-id` at a folder on
  that Shared Drive. All API calls set `supportsAllDrives=true`.
- Optional domain-wide delegation (`impersonated-user`) is supported for setups that
  must act as a specific workspace user.

### Adapter behaviour
- `upload` streams the file to Drive via `InputStreamContent` (never buffered whole in
  memory) and captures `id`, `webViewLink`, `size`, `mimeType`.
- `replace` updates an existing file's content in place (revision flow).
- `download` opens an authenticated stream for the proxied download endpoint.

---

## 9. Events, audit & notifications

The service publishes Spring application events instead of calling audit/notification
services directly:

| Event | When | Intended consumers |
|---|---|---|
| `DocumentUploaded` | new upload saved | audit |
| `DocumentVerified` | approve/reject/revision | audit (sync) + notifications (after commit) |
| `DocumentResubmitted` | revision re-uploaded | audit |

`DocumentEventListener` is a reference wiring: audit listeners run with
`@EventListener` (transactional) so the audit row commits atomically with the change;
notification listeners run with `@TransactionalEventListener(AFTER_COMMIT)` so a
student is never emailed about a change that rolled back. Replace the log lines with
the real `AuditService` / `NotificationService` once those modules land.

---

## 10. Security & multi-tenancy

- Tenant id is always resolved from the authenticated principal through
  `CurrentUser` — never from a path or body parameter.
- Every repository query is filtered by `tenantId`; a document fetched by id is fetched
  with `findByIdAndTenantId`, so one tenant can never act on another's document.
- The authenticated principal must implement `TenantPrincipal` (single integration
  seam — see the integration guide).
- Drive view links are safe to surface to authorised staff; no credentials or tokens
  ever appear in a response DTO.

---

## 11. API contract

| Method | Path | Role | Body |
|---|---|---|---|
| GET | `/api/v1/students/{studentId}/documents` | any tenant staff + student | — |
| POST | `/api/v1/students/{studentId}/documents` | admin/counselor/officer/student | multipart: `type`, `description?`, `file` |
| GET | `/api/v1/documents/{documentId}` | any tenant staff + student | — |
| PATCH | `/api/v1/documents/{documentId}/verify` | doc officer / admin | JSON `{ decision, remarks? }` |
| POST | `/api/v1/documents/{documentId}/resubmit` | admin/counselor/student | multipart: `file` |
| GET | `/api/v1/documents/{documentId}/content` | any tenant staff + student | — (streams bytes) |

Staff UI (Thymeleaf, Post/Redirect/Get) lives at `GET /students/{studentId}/documents`
with sibling `POST` actions for upload/verify/resubmit.

Errors use the project's standard envelope via `GlobalExceptionHandler` (see
integration guide for the handlers to add).

---

## 12. Data model & migrations

- `V4__create_student_documents_table.sql` — documents + Drive metadata + verification
  fields, status/category `CHECK` constraints, FK to `tenants` and `students`, indexes
  on `tenant_id`, `student_id`, `status`.
- `V5__create_student_drive_folders_table.sql` — folder-id cache, unique on
  `(student_id, category)`.

Both follow the project Flyway conventions (UUID PK `gen_random_uuid()`, `TIMESTAMPTZ`,
named constraints, `COMMENT ON` everywhere).

---

## 13. Testing strategy

- **`@DataJpaTest`** — repository tenant-isolation: a document created for tenant A is
  never returned for tenant B; folder cache uniqueness.
- **Service unit tests** (Mockito) — transition guard, remarks-required rule,
  resubmit-only-from-NEEDS_REVISION, event publication, tenant resolution.
- **`@WebMvcTest`** with `@MockitoBean` service — `@PreAuthorize` matrix per role,
  multipart binding, verify JSON validation.
- **Drive adapter** — integration-tested against a test Shared Drive folder (tag and
  exclude from the default CI profile; keep a contract test for the port).

Naming follows `methodName_stateUnderTest_expectedBehaviour`.

---

## 14. Risks & trade-offs

| Decision | Trade-off | Mitigation / future |
|---|---|---|
| Backend proxies the upload to Drive | Bytes transit the service, deviating from the "bytes never pass through" ideal in the project docs | Streamed (not buffered); kept behind `DocumentStoragePort` so a client-side resumable-upload adapter can replace it without touching the service |
| Default required set hard-coded on the enum | Not yet tenant-specific | Move to tenant workflow config (§ workflow engine) |
| Tenant container folder cached in memory | Re-resolved after restart (extra Drive calls) | Persist a per-tenant `driveRootFolderId` on the tenant entity in production |
| Single application root folder | Tenant isolation relies on per-tenant sub-folders, not separate Drives | Per-tenant Shared Drives for stricter isolation |
| Service-account-owned files | Quota/ownership concerns on My Drive | Use a Shared Drive (recommended) |

---

## 15. Future work

- Tenant-configurable required documents and per-workflow-stage gating.
- Client-side resumable upload (browser → Drive) with the backend only minting
  metadata, fully honouring "bytes never pass through".
- Document expiry/validity (e.g. IELTS validity windows) feeding readiness.
- OCR extraction (passport, transcripts) — V2 roadmap.
- Notification templates per trigger (`DOCUMENT_APPROVED`, revision requested, missing).
