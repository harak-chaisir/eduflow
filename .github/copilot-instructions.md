# GitHub Copilot Instructions — EduFlow CRM

> These instructions give GitHub Copilot the full context of this project so that every
> suggestion is consistent with the tech stack, architecture, conventions, and domain model.

---

## 1. Product Overview

**EduFlow CRM** is a **multi-tenant SaaS platform** for education consultancies that manages
the complete student lifecycle from first contact through university enrollment:

```
Lead → Student Registration → Document Collection → Document Verification
     → University Application → Offer Letter → Visa Application → Enrolled
```

Each **tenant** is an independent education consultancy (e.g. "ABC Consultancy", "XYZ Education").
Tenants own all their data — students, staff, documents, applications, universities — and data
is fully isolated between tenants.

### Platform Roles

| Role | Spring Authority | Capability |
|---|---|---|
| Platform Super Admin | `ROLE_SUPER_ADMIN` | Full access across all tenants |
| Consultancy Admin | `ROLE_TENANT_ADMIN` | Full access within their tenant |
| Counselor | `ROLE_COUNSELOR` | Register students, manage applications |
| Documentation Officer | `ROLE_DOC_OFFICER` | Verify and manage documents |
| Visa Officer | `ROLE_VISA_OFFICER` | Manage visa applications |
| Student | `ROLE_STUDENT` | Self-service portal — upload docs, track status |

---

## 2. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 25 |
| Framework | Spring Boot | 4.1.0 |
| Persistence | Spring Data JPA + Hibernate | (managed by Boot) |
| DB Migrations | Flyway | (managed by Boot) |
| Database | PostgreSQL | 17 |
| Security | Spring Security | 6.x |
| Templating | Thymeleaf + thymeleaf-extras-springsecurity6 | (managed by Boot) |
| Validation | Spring Validation (Jakarta Bean Validation) | (managed by Boot) |
| Observability | Spring Actuator | (managed by Boot) |
| Boilerplate | Lombok | (managed by Boot) |
| Build | Maven | 3.x |
| Container | Docker / Docker Compose | — |
| Document Storage | Google Drive API v3 | (planned) |
| PDF Generation | iText / OpenPDF | (planned) |
| Email | Spring Mail (SMTP / SendGrid) | (planned) |
| SMS/WhatsApp | Twilio | (planned) |

---

## 3. Project Structure

```
eduflow/
├── docker-compose.yml
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/eduflow/
    │   │   ├── EduflowApplication.java
    │   │   ├── common/
    │   │   │   ├── BaseEntity.java             # Abstract audited entity — extend for all entities
    │   │   │   └── JpaAuditingConfig.java      # @EnableJpaAuditing + AuditorAware bean
    │   │   ├── tenant/                         # Tenant (consultancy) domain
    │   │   ├── user/                           # Staff user domain
    │   │   ├── role/                           # Role domain
    │   │   ├── student/                        # Student registration & profile
    │   │   ├── document/                       # Document collection, upload, verification
    │   │   ├── university/                     # University & course master data
    │   │   ├── application/                    # University applications
    │   │   ├── offerletter/                    # Offer letter management
    │   │   ├── visa/                           # Visa application module
    │   │   ├── workflow/                       # Configurable workflow engine
    │   │   ├── task/                           # Task assignment & tracking
    │   │   ├── notification/                   # Email / SMS / WhatsApp notifications
    │   │   ├── audit/                          # Audit trail log
    │   │   ├── report/                         # Dashboard & reporting
    │   │   ├── security/                       # Spring Security config, JWT, UserDetailsService
    │   │   ├── config/                         # Google Drive client, Mail config, third-party beans
    │   │   └── exception/                      # @RestControllerAdvice + error DTOs
    │   └── resources/
    │       ├── application.properties
    │       ├── templates/                      # Thymeleaf templates
    │       └── db/migration/                   # Flyway SQL (V{n}__{desc}.sql)
    │           ├── V1__create_tenants_table.sql
    │           ├── V2__create_roles_table.sql
    │           └── V3__create_users_table.sql
    └── test/
        └── java/com/eduflow/
```

### Package conventions

| Package | Purpose |
|---|---|
| `com.eduflow` | Root — only `EduflowApplication` |
| `com.eduflow.common` | Cross-cutting: `BaseEntity`, `JpaAuditingConfig`, utilities |
| `com.eduflow.tenant` | Tenant CRUD, status management |
| `com.eduflow.user` | Staff users (counselors, officers, admins) |
| `com.eduflow.role` | Role definitions |
| `com.eduflow.student` | Student registration, profile, status |
| `com.eduflow.document` | Document types, upload, Drive integration, verification |
| `com.eduflow.university` | University & course master data |
| `com.eduflow.application` | University application workflow |
| `com.eduflow.offerletter` | Offer letter upload & management |
| `com.eduflow.visa` | Visa application, form generation, status |
| `com.eduflow.workflow` | Configurable workflow definitions & transitions |
| `com.eduflow.task` | Task assignment & completion tracking |
| `com.eduflow.notification` | Email, SMS, WhatsApp trigger service |
| `com.eduflow.audit` | Immutable audit event log |
| `com.eduflow.report` | KPI aggregation, funnel reports |
| `com.eduflow.security` | `SecurityConfig`, JWT filter, `EduFlowUserDetailsService` |
| `com.eduflow.config` | Google Drive client, Mail config, third-party beans |
| `com.eduflow.exception` | `GlobalExceptionHandler`, error envelope DTO |

---

## 4. Domain Model & Entity Relationships

```
tenants (1)──<  users              (staff, tenant_id NOT NULL)
tenants (1)──<  students           (tenant_id NOT NULL)
tenants (1)──<  universities       (tenant_id NOT NULL — tenant's university list)
tenants (1)──<  workflow_templates (tenant_id NOT NULL — configurable per tenant)
users   (M)>──< roles              via user_roles
students(1)──<  student_documents  (one student → many docs)
students(1)──<  applications       (one student → many university applications)
applications(1)──< offer_letters   (one application → one or more offer letters)
applications(1)──< visa_applications
students(1)──<  tasks              (tasks assigned for a student)
```

### Core Status Enums

```
TenantStatus         ACTIVE | INACTIVE | SUSPENDED
UserStatus           ACTIVE | INACTIVE | LOCKED | PENDING_VERIFICATION
StudentStatus        LEAD | QUALIFIED | ACTIVE | ENROLLED | INACTIVE
DocumentStatus       PENDING | APPROVED | REJECTED | NEEDS_REVISION
ApplicationStatus    DRAFT | SUBMITTED | UNDER_REVIEW | CONDITIONAL_OFFER |
                     UNCONDITIONAL_OFFER | REJECTED
VisaStatus           NOT_STARTED | DRAFT | SUBMITTED | BIOMETRICS_SCHEDULED |
                     UNDER_REVIEW | APPROVED | REJECTED
TaskStatus           PENDING | IN_PROGRESS | COMPLETED | CANCELLED
```

### Standard audit columns (every table)

```sql
created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
created_by  VARCHAR(255)
updated_by  VARCHAR(255)
```

---

## 5. Base Entity — `com.eduflow.common.BaseEntity`

**All JPA entities MUST extend `BaseEntity`.** It provides:

- `UUID id` — auto-generated by the DB (`GenerationType.UUID`)
- `Instant createdAt` / `updatedAt` — populated by `@CreatedDate` / `@LastModifiedDate`
- `String createdBy` / `updatedBy` — populated from Spring Security context via `JpaAuditingConfig`

```java
@Entity
@Table(name = "students")
public class Student extends BaseEntity {
    // Only domain-specific fields go here — id and audit columns are inherited
}
```

> ⚠️ Never add `id`, `createdAt`, `updatedAt`, `createdBy`, or `updatedBy` fields manually
> to any entity — they are always inherited from `BaseEntity`.

---

## 6. Flyway Migration Rules

- File naming: `V{n}__{snake_case_description}.sql` (double underscore)
- Location: `src/main/resources/db/migration/`
- Next migration number: check the highest `V{n}` already in `db/migration/`
- Always include `COMMENT ON TABLE` and `COMMENT ON COLUMN` for every new table
- Constraint naming conventions:
  - Primary key: `pk_{table}`
  - Foreign key: `fk_{table}_{referenced_table}`
  - Unique: `uq_{table}_{columns}`
  - Check: `chk_{table}_{column}`
  - Index on FK columns: `idx_{table}_{column}`
- Use `TIMESTAMPTZ` (not `TIMESTAMP`) for all datetime columns
- Use `UUID` with `DEFAULT gen_random_uuid()` for all primary keys
- Use `VARCHAR` with explicit lengths; avoid `TEXT` unless genuinely unbounded
- Enum values stored as `VARCHAR` — validate with a `CHECK` constraint
- Flyway owns the schema — never change `ddl-auto` to `create` or `update`
- Never modify an already-applied migration — create a new `V{n+1}` file instead

---

## 7. Domain-Specific Conventions

### 7.1 Multi-Tenancy

- **Every business entity** (student, document, application, university, task, etc.) has a
  `tenant_id UUID NOT NULL` FK referencing `tenants.id`.
- **Every repository query** that returns tenant-scoped data must filter by `tenantId`.
  Never return data across tenant boundaries unless the caller has `ROLE_SUPER_ADMIN`.
- The resolved tenant ID comes from the authenticated principal — never from a request parameter.
- `ROLE_SUPER_ADMIN` is the only role that may omit the tenant filter.

### 7.2 Student Domain (`com.eduflow.student`)

- A student belongs to exactly one tenant.
- `StudentStatus` progresses: `LEAD → QUALIFIED → ACTIVE → ENROLLED`.
- Store interested countries and courses as `@ElementCollection` or a dedicated join table.
- Never expose `passwordHash` in any student response DTO.

### 7.3 Document Domain (`com.eduflow.document`)

- Documents are categorised by `DocumentType` enum:
  `SEE_CERTIFICATE | PLUS_TWO_TRANSCRIPT | BACHELOR_TRANSCRIPT | DEGREE_CERTIFICATE |
   RECOMMENDATION_LETTER | BANK_STATEMENT | SPONSORSHIP_LETTER | TAX_CLEARANCE |
   PASSPORT | CITIZENSHIP | PHOTOGRAPH | IELTS | PTE | TOEFL | OFFER_LETTER | VISA_DOCS`
- Google Drive metadata (file ID, folder ID) is stored in the DB; actual bytes never pass
  through this service.
- Folder structure created on Google Drive per student:
  ```
  Students/
   └── {Student Full Name} ({studentId})/
         ├── Academic/
         ├── Financial/
         ├── Identity/
         ├── English Proficiency/
         ├── Offer Letters/
         └── Visa/
  ```
- `DocumentStatus` transitions: `PENDING → APPROVED | REJECTED | NEEDS_REVISION`
- The officer who verified a document must be stored on the record.

### 7.4 University & Application Domain

- `University` is a tenant-scoped master-data entity (consultancy's working list).
- `Course` belongs to a `University` with intake information and entry requirements.
- `Application` links a `Student` to a `Course`; it carries `ApplicationStatus`.
- `OfferLetter` is a child of `Application`; stores Drive file ID, letter number, and date received.

### 7.5 Visa Domain (`com.eduflow.visa`)

- `VisaApplication` is created after an offer letter is received.
- Form data (personal info, passport info, travel history, financials, accommodation,
  health insurance) is modelled as embedded value objects or related entities.
- Generate PDF documents (visa checklist, cover letter, application summary) via a
  `VisaDocumentGeneratorService`.

### 7.6 Workflow Engine (`com.eduflow.workflow`)

- `WorkflowTemplate` defines the ordered list of stages for a tenant.
- `WorkflowStage` represents a single step (e.g. "Document Verification").
- `StudentWorkflow` tracks which stage a specific student is currently in.
- Tenants can customise their workflow templates via `ROLE_TENANT_ADMIN`.
- Status transitions must be validated against the workflow definition — never skip stages.

### 7.7 Task Domain (`com.eduflow.task`)

- Every task has: `title`, `description`, `assignedTo` (User), `student`, `dueDate`,
  `TaskStatus`, `priority`.
- Tasks must be tenant-scoped.
- `TaskStatus` transitions: `PENDING → IN_PROGRESS → COMPLETED | CANCELLED`.

### 7.8 Notification Domain (`com.eduflow.notification`)

- Notification triggers: `DOCUMENT_MISSING`, `DOCUMENT_APPROVED`, `OFFER_LETTER_RECEIVED`,
  `VISA_SUBMITTED`, `VISA_APPROVED`, `APPLICATION_STATUS_CHANGED`, `TASK_ASSIGNED`.
- Channel enum: `EMAIL | SMS | WHATSAPP`.
- Persist every dispatched notification in a `notifications` table (for audit/retry).
- Use a `NotificationService` abstraction — inject channel-specific implementations
  (`EmailNotificationService`, `SmsNotificationService`, `WhatsAppNotificationService`).

### 7.9 Audit Trail (`com.eduflow.audit`)

- Every state-changing action should produce an `AuditEvent`:
  `who (userId)`, `action (String)`, `entityType`, `entityId`, `oldValue`, `newValue`, `timestamp`.
- The `audit_events` table is **append-only** — no updates or deletes ever.
- Use a Spring `@EventListener` or AOP aspect to record events automatically.

---

## 8. Java / Spring Conventions

### General

- Use `@Getter` / `@Setter` (or `@Data`) — never write manual accessors
- Use `@RequiredArgsConstructor` / `@NoArgsConstructor` / `@AllArgsConstructor` as appropriate
- Use `@Builder` on DTOs and value objects
- Use `@Slf4j` for logging — never `System.out.println`
- All date/time fields: `Instant` — never `java.util.Date`, `java.sql.Timestamp`, or `LocalDateTime`

### JPA Entities

- One entity per file; never nest entity classes
- `@OneToMany(mappedBy = "...", fetch = FetchType.LAZY)` — LAZY is the default for all relationships
- `@ManyToOne(fetch = FetchType.LAZY)` + `@JoinColumn` for FK relationships
- `@Enumerated(EnumType.STRING)` for all enum-mapped columns
- Column names: `snake_case`; field names: `camelCase`

### Repositories

- Extend `JpaRepository<Entity, UUID>`
- Co-located with the entity (e.g. `com.eduflow.student.StudentRepository`)
- Always include `tenantId` in queries for tenant-scoped entities:
  ```java
  List<Student> findByTenantId(UUID tenantId);
  Optional<Student> findByIdAndTenantId(UUID id, UUID tenantId);
  ```
- Prefer Spring Data derived queries; use `@Query` (JPQL, named params) for complex cases

### Services

- `@Service` + `@Transactional` at class level
- `@Transactional(readOnly = true)` on read-only methods
- Never inject a repository into a controller — always via a service
- Throw domain-specific exceptions (e.g. `StudentNotFoundException`, `DocumentVerificationException`)

### Controllers

- `@RestController` for API endpoints; `@Controller` for Thymeleaf views
- REST base path: `/api/v1/`
- Use `@Valid` on `@RequestBody` parameters
- Return `ResponseEntity<T>` with explicit HTTP status codes
- Path conventions:
  ```
  GET    /api/v1/students                    → list (tenant-scoped)
  GET    /api/v1/students/{id}               → get one
  POST   /api/v1/students                    → create
  PATCH  /api/v1/students/{id}/status        → status transition
  GET    /api/v1/students/{id}/documents     → list docs for student
  POST   /api/v1/students/{id}/documents     → upload doc metadata
  ```

### DTOs

- Request suffix: `Request` (e.g. `RegisterStudentRequest`)
- Response suffix: `Response` (e.g. `StudentResponse`)
- `@Builder` + `@Value` for immutable response DTOs
- Place in a `dto` sub-package within the domain package (e.g. `com.eduflow.student.dto`)
- Never include `passwordHash`, `driveAccessToken`, or any credential in a response DTO

### Exception Handling

- `GlobalExceptionHandler` in `com.eduflow.exception` with `@RestControllerAdvice`
- Consistent error envelope:
  ```json
  {
    "status": 404,
    "error": "NOT_FOUND",
    "message": "Student not found with id: <uuid>",
    "timestamp": "2026-06-14T10:00:00Z",
    "path": "/api/v1/students/<uuid>"
  }
  ```

---

## 9. Security Conventions

- Every query on tenant-scoped data **must** filter by the authenticated user's `tenantId`
- Resolve `tenantId` from the `SecurityContextHolder` principal — never from a request param
- `ROLE_SUPER_ADMIN` may bypass tenant filters; all other roles are tenant-scoped
- Passwords hashed with `BCryptPasswordEncoder`
- Never return `password_hash` or any Drive/API credential in a response
- Method-level security with `@PreAuthorize` for role-specific endpoints:
  ```java
  @PreAuthorize("hasRole('COUNSELOR') or hasRole('TENANT_ADMIN')")
  ```

---

## 10. Testing Conventions

- `@SpringBootTest` — full context integration tests
- `@DataJpaTest` — repository slice tests
- `@WebMvcTest` — controller slice tests (mock service layer)
- `@MockitoBean` (Spring Boot 4.x) — replaces the older `@MockBean`
- Test method naming: `methodName_stateUnderTest_expectedBehaviour`
  ```java
  @Test
  void registerStudent_whenEmailAlreadyExists_throwsDuplicateStudentException() { ... }
  ```
- Test class in the same package as the class under test, under `src/test/`
- All repository tests must use a `tenantId` fixture and assert tenant isolation

---

## 11. Docker / Local Development

```bash
# Start the database
docker compose up -d

# Stop the database
docker compose down

# Wipe data and restart fresh
docker compose down -v && docker compose up -d

# Run the application (database must be running first)
./mvnw spring-boot:run
```

**Local DataSource:**

| Property | Value |
|---|---|
| URL | `jdbc:postgresql://localhost:5432/eduflow` |
| Username | `eduflow` |
| Password | `eduflow` |
| Database | `eduflow` |

---

## 12. What Copilot Should Always Do

- ✅ Extend `BaseEntity` for every new JPA entity
- ✅ Use Lombok annotations — never write manual getters/setters/constructors
- ✅ Write a Flyway SQL migration for every schema change
- ✅ Filter **all** tenant-scoped queries by `tenantId` — no exceptions except `ROLE_SUPER_ADMIN`
- ✅ Use `UUID` for all entity IDs and FK parameters
- ✅ Use `Instant` for all date/time fields
- ✅ Annotate service classes with `@Transactional`; mark read-only methods with `@Transactional(readOnly = true)`
- ✅ Use `@Valid` on all incoming request bodies
- ✅ Add Javadoc to public classes and non-trivial public methods
- ✅ Follow the package structure in section 3
- ✅ Record an `AuditEvent` for every state-changing action on student/document/application/visa
- ✅ Use `@Enumerated(EnumType.STRING)` on all enum-mapped JPA columns
- ✅ Validate enum transitions in the service layer (e.g. cannot move directly from `LEAD` to `ENROLLED`)

## 13. What Copilot Should Never Do

- ❌ Add `id`, `createdAt`, `updatedAt`, `createdBy`, or `updatedBy` directly to an entity
- ❌ Use `java.util.Date`, `java.sql.Timestamp`, or `LocalDateTime` for persistence
- ❌ Use `EAGER` fetch type on any JPA relationship without explicit justification
- ❌ Return data across tenant boundaries unless the caller has `ROLE_SUPER_ADMIN`
- ❌ Resolve `tenantId` from a request parameter — always from the security context
- ❌ Inject a `Repository` directly into a `@Controller`
- ❌ Hardcode credentials, API keys, Drive tokens, or environment-specific URLs in source code
- ❌ Use `System.out.println` — use `@Slf4j` + `log.info/debug/warn/error`
- ❌ Modify an already-applied Flyway migration — always create a new versioned file
- ❌ Use Hibernate `ddl-auto` values other than `validate`
- ❌ Return `password_hash`, Drive access tokens, or any credential in an API response
- ❌ Delete or update rows in the `audit_events` table — it is append-only
