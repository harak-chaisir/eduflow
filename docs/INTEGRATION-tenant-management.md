# Tenant Management Module — Integration & Step Tracker

Staged build of the Tenant Management module specified in
[`PRD-tenant-management.md`](./PRD-tenant-management.md). Each step compiles and is
testable on its own. This doc is the working checklist; tick items as they land.

> Status legend: ✅ done · 🟡 partial · ⏭️ deferred

---

## Step checklist

| # | Step | Status |
|---|------|--------|
| 1 | Schema migration `V11` (extend `tenants`, add `tenant_settings`) | ✅ |
| 2 | Domain model — `TenantPlan`, `TenantStatus.canTransitionTo`, entity, `TenantSettings` | ✅ |
| 3 | Exceptions + audit actions + domain events | ✅ |
| 4 | `TenantService` + lifecycle + provisioning | ✅ |
| 5 | DTOs + `TenantController` (`/api/v1/tenants`) | ✅ |
| 6 | Auth status-gating + first-admin invite (stubbed) | ✅ |
| 7 | Plan-limit enforcement (`TenantLimitService`) | ✅ |
| 8 | Thymeleaf surfaces (super-admin Tenants, tenant-admin Workspace) | ✅ |
| 9 | Per-tenant Drive root + super-admin tenant switch | ⏭️ deferred |

---

## File map

```
src/main/java/com/eduflow/
  tenant/
    Tenant.java                            entity (extended: plan, limits, contact, locale, lifecycle stamps)
    TenantStatus.java                      enum + canTransitionTo()
    TenantPlan.java                        enum STARTER/PROFESSIONAL/ENTERPRISE (+ default limits)
    TenantSettings.java                    1:1 settings entity (shared PK via @MapsId)
    TenantRepository.java                  + JpaSpecificationExecutor
    TenantSettingsRepository.java
    TenantSpecification.java               q / status / plan filters (no tenant predicate — platform-level)
    TenantService.java                     provision/search/get/updateProfile/changeStatus/changePlan/settings/inviteAdmin
    TenantLimitService.java                assertCanAddStudent / assertCanAddStaff (PRD §9)
    TenantController.java                   REST API /api/v1/tenants
    DuplicateSlugException.java             → 409
    InvalidTenantStatusTransitionException  → 422
    TenantLimitExceededException.java       → 409
    dto/
      CreateTenantRequest, UpdateTenantProfileRequest, ChangeTenantStatusRequest,
      ChangeTenantPlanRequest, UpdateTenantSettingsRequest, InviteTenantAdminRequest,
      TenantSearchCriteria, TenantResponse, TenantSettingsResponse
    event/
      TenantEvents.java                    TenantCreated/StatusChanged/PlanChanged/AdminInvited
      TenantEventListener.java             @EventListener audit wiring
  user/
    TenantAdminInviteListener.java         AFTER_COMMIT: creates PENDING_VERIFICATION TENANT_ADMIN + logs invite
  web/
    TenantWebController.java               /tenants (super-admin) + /workspace (tenant-admin)

src/main/resources/
  db/migration/V11__extend_tenants_and_add_settings.sql
  templates/tenant/{list,form,detail,workspace}.html
```

### Touched existing files
- `exception/GlobalExceptionHandler.java` — handlers for the three new exceptions.
- `audit/AuditAction.java` — `TENANT_*` action constants.
- `security/EduFlowUserDetails.java` — `isEnabled()` now gates on tenant status (super-admin exempt).
- `user/UserRepository.java` — `countByTenantId`.
- `student/StudentService.java` — calls `TenantLimitService.assertCanAddStudent`.
- `templates/fragments/layout.html` — Tenants nav → `/tenants`; new Workspace nav → `/workspace`.

---

## API contract (PRD §14)

| Method | Path | Role |
|---|---|---|
| GET | `/api/v1/tenants` | `SUPER_ADMIN` (filters: `q`, `status`, `plan`) |
| POST | `/api/v1/tenants` | `SUPER_ADMIN` → provisions + invites admin |
| GET | `/api/v1/tenants/me` | any tenant user |
| GET | `/api/v1/tenants/{id}` | `SUPER_ADMIN` / own `TENANT_ADMIN` |
| PATCH | `/api/v1/tenants/{id}` | `SUPER_ADMIN` / own `TENANT_ADMIN` |
| PATCH | `/api/v1/tenants/{id}/status` | `SUPER_ADMIN` (reason required for SUSPENDED) |
| PATCH | `/api/v1/tenants/{id}/plan` | `SUPER_ADMIN` |
| GET/PATCH | `/api/v1/tenants/{id}/settings` | `SUPER_ADMIN` / own `TENANT_ADMIN` |
| POST | `/api/v1/tenants/{id}/admins` | `SUPER_ADMIN` |

Error mapping (via `GlobalExceptionHandler`): `TenantNotFoundException`→404,
`DuplicateSlugException`→409, `TenantLimitExceededException`→409,
`InvalidTenantStatusTransitionException`→**422** (matches the project's Student/Document
transition convention; the PRD's "409" was reconciled to 422 for consistency).

---

## Design notes / assumptions

- **Tenant context** is resolved from the authenticated `EduFlowUserDetails` principal
  (the project's existing seam), not the `TenantPrincipal`/`CurrentUser` contract named in
  the document module's integration doc — those were never built. "Own tenant" is verified
  in `TenantService.assertCanAccess` against the principal, never a path param.
- **Status gating** lives in `EduFlowUserDetails.isEnabled()`: a `SUSPENDED`/`INACTIVE`
  tenant blocks all its users at authentication; `ROLE_SUPER_ADMIN` is exempt. The login
  page shows the standard `/login?error` message — a dedicated "workspace not active"
  message is a small follow-up if a custom `AuthenticationFailureHandler` is desired.
- **First-admin invite is stubbed**: on `TenantCreated` (AFTER_COMMIT, REQUIRES_NEW) a
  `TENANT_ADMIN` is created in `PENDING_VERIFICATION` and the set-password link is **logged**.
  The invite token is not yet persisted — swap the log line for a real `NotificationService`
  and a token store when that module lands.
- **Provisioning** seeds limits from the plan (overridable), writes the `tenant_settings`
  row in the same tx, and leaves `driveRootFolderId` null (lazy — PRD §7.4).

---

## Set-password / activation flow (tenant login)

The invite is no longer a placeholder log — it issues a **real, single-use token**:

- `V12__create_password_reset_tokens_table.sql` — `password_reset_tokens` (token, expiry, used_at).
- `user/PasswordResetToken` + repo; `user/PasswordResetService` — `createToken`, `emailForValidToken`,
  `resetPassword` (sets BCrypt password, flips `PENDING_VERIFICATION → ACTIVE`, marks token used).
- `TenantAdminInviteListener` now creates a token and logs a **dynamic** link:
  `{baseUrl}/set-password?token=…`, where `baseUrl` is taken from the current request
  (`ServletUriComponentsBuilder`) and falls back to `eduflow.app.base-url` (default
  `http://localhost:8080`) when there is no request context.
- `web/PasswordSetupController` + `templates/set-password.html` — public `GET/POST /set-password`
  (permitted in `SecurityConfig`); on success redirects to `/login?reset`. `AuthController`
  shows a "Your password has been set" message on `?reset`.
- The activated tenant admin then signs in at the normal `/login`.

**Navigation to workspace settings:** the sidebar **Workspace** link (`TENANT_ADMIN`) plus the
topbar tenant badge (now a link to `/workspace` for tenant admins) both reach
`tenant/workspace.html`.

Verified end-to-end on a live instance: super-admin `POST /api/v1/tenants` → dynamic link in
console → set password → tenant-admin login → workspace loads; consumed token is rejected on reuse.

---

## Step 9 — deferred (future work)

- **Per-tenant Drive root:** the PRD's `DriveFolderProvisioningService` does not exist yet;
  the Drive adapter (`GoogleDriveStorageAdapter`) is a seam that throws until the SDK is
  wired (see `docs/INTEGRATION.md` §2). The `tenant.driveRootFolderId` column + entity field
  are in place as the forward hook: when the provisioning service is built, prefer this value
  and fall back to the global `eduflow.google-drive.root-folder-id`.
- **Super-admin tenant-context switch:** a session-scoped "act within tenant X" feature for
  support/impersonation (PRD §10.4). Not started — keeps the "tenant from context" invariant
  intact and should be explicit/auditable when added.

---

## Try it

1. `docker compose down -v && docker compose up -d` then `./mvnw spring-boot:run`
   (Flyway applies `V11`; `ddl-auto=validate` must pass against the new columns/table).
2. Sign in as the seeded super-admin `admin@eduflow.com` / `Admin@1234`.
3. Visit **Tenants** in the sidebar → **New tenant** → provision one. Watch the log for the
   stubbed admin invite link.
4. Open the tenant → **Change status → SUSPENDED** (with a reason).
5. Sign out, try to sign in as a user of that tenant → blocked. Reactivate → access restored.
6. Sign in as a `TENANT_ADMIN` (`admin@brightfuture.com` / `Demo@1234`) → **Workspace** →
   edit profile/settings.
7. REST: `GET /api/v1/tenants`, `POST /api/v1/tenants`, `PATCH /api/v1/tenants/{id}/status`.
