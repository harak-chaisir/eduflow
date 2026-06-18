# Document Module — Integration Guide

Everything in this archive mirrors the `eduflow/` repo layout, so in most cases you can
copy folders straight in. This guide lists the file map, the few things you must add to
files I don't have (your `pom.xml`, `application.properties`, `GlobalExceptionHandler`,
and security principal), the Google Drive setup, and the assumptions I made about your
existing code.

> ⚠️ I could not compile this in my environment (no Maven access). The code is written
> to the project conventions, but please run a build after wiring the four touch-points
> below.

---

## 1. File map

```
src/main/java/com/eduflow/
  document/
    DocumentCategory.java                 enum: category → Drive folder name
    DocumentType.java                     enum: 16 types → category, label, required flag
    DocumentStatus.java                   enum: lifecycle + transition rules
    StudentDocument.java                  entity (extends BaseEntity)
    StudentDocumentRepository.java        tenant-scoped queries
    DocumentService.java                  orchestration (upload/verify/resubmit/dossier)
    DocumentController.java               REST API (/api/v1)
    DocumentViewController.java           Thymeleaf staff UI (PRG)
    DocumentNotFoundException.java
    InvalidDocumentTransitionException.java
    dto/
      VerificationDecision.java
      VerifyDocumentRequest.java
      DocumentResponse.java
      DossierView.java                    readiness + checklist view models
    storage/
      DocumentStoragePort.java            storage abstraction (+ nested records)
      GoogleDriveStorageAdapter.java      Drive v3 implementation
      DriveFolderProvisioningService.java folder tree + caching
      StudentDriveFolder.java             folder-id cache entity
      StudentDriveFolderRepository.java
      DriveOperationException.java
    event/
      DocumentEvents.java                 domain events
      DocumentEventListener.java          reference audit + notification wiring
  config/
    GoogleDriveProperties.java            @ConfigurationProperties
    GoogleDriveConfig.java                builds the authenticated Drive bean
  security/
    TenantPrincipal.java                  interface your principal must implement
    CurrentUser.java                      resolves tenant/actor from SecurityContext

src/main/resources/
  db/migration/
    V4__create_student_documents_table.sql
    V5__create_student_drive_folders_table.sql
  templates/
    fragments/layout.html                 shared app shell (sidebar + topbar)
    document/student-documents.html        the dossier page
    dashboard.html                         reference page (optional)
  static/
    css/eduflow.css                        app-wide design system
    js/eduflow.js                          shell behaviour + modal
```

---

## 2. `pom.xml` — add the Google Drive client

These are the only new dependencies. **Verify the versions against Maven Central** —
the Google client libraries publish frequently and you should take the latest stable.

```xml
<dependency>
    <groupId>com.google.apis</groupId>
    <artifactId>google-api-services-drive</artifactId>
    <version>v3-rev20240914-2.0.0</version>
</dependency>
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.7.0</version>
</dependency>
<dependency>
    <groupId>com.google.auth</groupId>
    <artifactId>google-auth-library-oauth2-http</artifactId>
    <version>1.30.1</version>
</dependency>
<dependency>
    <groupId>com.google.http-client</groupId>
    <artifactId>google-http-client-gson</artifactId>
    <version>1.45.0</version>
</dependency>
```

`thymeleaf-extras-springsecurity6` is already in your stack (the layout uses `sec:`),
so nothing to add there.

---

## 3. `application.properties` — add configuration

```properties
# ---- Google Drive ----
eduflow.google-drive.enabled=true
eduflow.google-drive.application-name=EduFlow CRM
# Spring resource location of the service-account JSON key:
eduflow.google-drive.credentials-location=file:${EDUFLOW_DRIVE_KEY:/etc/eduflow/drive-sa.json}
# Folder id under which per-tenant trees are created (a Shared Drive folder is recommended):
eduflow.google-drive.root-folder-id=${EDUFLOW_DRIVE_ROOT:}
eduflow.google-drive.shared-drive=true
# Optional domain-wide delegation:
# eduflow.google-drive.impersonated-user=ops@yourconsultancy.com

# ---- Multipart upload limits ----
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=25MB
```

Keep the key path and root-folder id in environment variables / secrets — never commit
them.

---

## 4. Security — implement one interface

`CurrentUser` reads the tenant from the authenticated principal via `TenantPrincipal`.
Make the principal returned by your `EduFlowUserDetailsService` implement it:

```java
public class EduFlowUserPrincipal implements UserDetails, TenantPrincipal {
    @Override public UUID getTenantId() { return tenantId; }
    @Override public String getUsername() { return email; } // already from UserDetails
    // ...existing fields/methods...
}
```

Also confirm method security is enabled (needed for `@PreAuthorize`):

```java
@Configuration
@EnableMethodSecurity   // add if not already present
public class MethodSecurityConfig { }
```

Role names use the `ROLE_` prefix convention (`hasRole('DOC_OFFICER')` matches
authority `ROLE_DOC_OFFICER`).

---

## 5. `GlobalExceptionHandler` — add these handlers

Add to your existing `@RestControllerAdvice` so the module's exceptions map to your
standard error envelope:

```java
@ExceptionHandler(DocumentNotFoundException.class)
public ResponseEntity<ApiError> handleDocumentNotFound(DocumentNotFoundException ex, HttpServletRequest req) {
    return error(HttpStatus.NOT_FOUND, ex.getMessage(), req);
}

@ExceptionHandler(InvalidDocumentTransitionException.class)
public ResponseEntity<ApiError> handleInvalidTransition(InvalidDocumentTransitionException ex, HttpServletRequest req) {
    return error(HttpStatus.CONFLICT, ex.getMessage(), req);
}

@ExceptionHandler(DriveOperationException.class)
public ResponseEntity<ApiError> handleDrive(DriveOperationException ex, HttpServletRequest req) {
    return error(HttpStatus.BAD_GATEWAY, "Document storage error", req);
}

@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest req) {
    return error(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
}

@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the upload limit", req);
}
```

(`error(...)` / `ApiError` are placeholders for your existing envelope builder.)

---

## 6. Google Drive setup (one-time)

1. In Google Cloud Console, create/choose a project and **enable the Google Drive API**.
2. Create a **service account**; create a **JSON key** and store it where
   `credentials-location` points.
3. Create a **Shared Drive** (recommended) and a top folder (e.g. `EduFlow`).
4. Add the service account's email as a **member** of the Shared Drive with
   *Content manager* access.
5. Copy the top folder's id (from its URL) into `eduflow.google-drive.root-folder-id`.
6. (Optional) For domain-wide delegation, authorise the service-account client id for
   the `drive.file` scope in the Workspace admin console and set `impersonated-user`.

On the first upload for a student, the folder tree is created automatically and cached.

---

## 7. Assumptions about existing code

The module references these from your already-built `student` package and `common`
base. If any differ, adjust the noted call site:

| Assumption | Used in | If different |
|---|---|---|
| `com.eduflow.common.BaseEntity` provides `getId()`, `getCreatedAt()`, `getUpdatedAt()` | entities, `DocumentResponse` | align getter names |
| `com.eduflow.student.Student extends BaseEntity` and exposes `getFullName()` | `DocumentService`, `DriveFolderProvisioningService` | replace `getFullName()` with your name accessor (one spot in each) |
| `com.eduflow.student.StudentRepository.findByIdAndTenantId(UUID, UUID)` returns `Optional<Student>` | `DocumentService` | add the method or adjust the call |
| `com.eduflow.student.StudentNotFoundException(UUID)` exists | `DocumentService` | use your equivalent (the copilot conventions reference this type) |
| Authenticated principal implements `TenantPrincipal` | `CurrentUser` | see §4 |

---

## 8. Try it

1. Wire §2–§5, run `./mvnw spring-boot:run` (Flyway applies V4/V5 on startup).
2. Sign in as a counselor or documentation officer.
3. Visit `GET /students/{studentId}/documents` for an existing student.
4. Upload a file → it appears `PENDING`; check the Drive folder tree was created.
5. As a documentation officer, **Verify** it → approve/reject/request revision.
6. On a `NEEDS_REVISION` row, **Re-upload** → it returns to `PENDING`, revision bumps.
