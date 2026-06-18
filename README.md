# EduFlow CRM

> **Multi-Tenant Education Consultancy Management Platform**
>
> Manage the complete student lifecycle — from first enquiry through to university enrollment —
> in a single, collaborative, paperless platform.

---

## Table of Contents

1. [Product Vision](#1-product-vision)
2. [Key Features](#2-key-features)
3. [Technology Stack](#3-technology-stack)
4. [Architecture Overview](#4-architecture-overview)
5. [Domain Model](#5-domain-model)
6. [User Roles & Permissions](#6-user-roles--permissions)
7. [Getting Started](#7-getting-started)
8. [Project Structure](#8-project-structure)
9. [Database Migrations](#9-database-migrations)
10. [API Overview](#10-api-overview)
11. [Module Breakdown](#11-module-breakdown)
12. [Roadmap](#12-roadmap)
13. [Contributing](#13-contributing)

---

## 1. Product Vision

Education consultancies today rely on a patchwork of spreadsheets, WhatsApp threads, email
folders, and Google Drive links. EduFlow CRM replaces all of that with a structured,
auditable, multi-user platform.

**Student journey managed end-to-end:**

```
Lead Enquiry
    │
    ▼
Student Registration
    │
    ▼
Document Collection  ──►  Document Verification
    │
    ▼
University Application
    │
    ▼
Offer Letter Received
    │
    ▼
Visa Application
    │
    ▼
Enrolled ✓
```

---

## 2. Key Features

| Feature | Description |
|---|---|
| **Multi-Tenancy** | Each consultancy is a fully isolated tenant with its own students, staff, and data |
| **Student CRM** | Full student profile: personal info, education background, interested countries & courses |
| **Document Management** | Collect, organise, and verify documents; automatic Google Drive folder creation per student |
| **University Applications** | Apply students to universities; track application status from Draft to Offer |
| **Offer Letter Management** | Record and store offer letters with Drive integration |
| **Visa Module** | End-to-end visa application workflow; generate PDF checklists, cover letters, and summaries |
| **Configurable Workflows** | Tenants define their own stage-based student workflows |
| **Task Management** | Assign and track tasks across counselors, documentation officers, and visa officers |
| **Notifications** | Email, SMS, and WhatsApp notifications triggered by key events |
| **Audit Trail** | Immutable, append-only log of every action on every record |
| **Reporting Dashboard** | Student funnel KPIs: leads → qualified → applied → offer letters → visas → enrolled |
| **Student Portal** | Students upload documents, check status, and download offer letters themselves |

---

## 3. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 25 |
| Framework | Spring Boot | 4.1.0 |
| Persistence | Spring Data JPA + Hibernate | (managed by Boot) |
| DB Migrations | Flyway | (managed by Boot) |
| Database | PostgreSQL | 17 |
| Security | Spring Security | 6.x |
| Templating | Thymeleaf + thymeleaf-extras-springsecurity6 | (managed by Boot) |
| Validation | Jakarta Bean Validation | (managed by Boot) |
| Observability | Spring Actuator | (managed by Boot) |
| Boilerplate | Lombok | (managed by Boot) |
| Build | Maven | 3.x |
| Container | Docker / Docker Compose | — |
| Document Storage | Google Drive API v3 | *(planned)* |
| PDF Generation | iText / OpenPDF | *(planned)* |
| Email | Spring Mail / SendGrid | *(planned)* |
| SMS / WhatsApp | Twilio | *(planned)* |

---

## 4. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        EduFlow CRM                              │
│                                                                 │
│  ┌────────────┐   ┌────────────┐   ┌────────────────────────┐  │
│  │  Thymeleaf │   │  REST API  │   │   Student Portal       │  │
│  │  (Staff UI)│   │ /api/v1/*  │   │   (ROLE_STUDENT)       │  │
│  └─────┬──────┘   └─────┬──────┘   └───────────┬────────────┘  │
│        └────────────────┴─────────────────────┘               │
│                         │                                       │
│              ┌──────────▼──────────┐                           │
│              │  Spring Security    │  (JWT + BCrypt)           │
│              │  Multi-Tenant Auth  │                           │
│              └──────────┬──────────┘                           │
│                         │                                       │
│   ┌──────────┬──────────┼──────────┬──────────┬────────────┐  │
│   │ Student  │ Document │University│   Visa   │  Workflow  │  │
│   │ Service  │ Service  │  Service │  Service │  Engine    │  │
│   └────┬─────┴─────┬────┴─────┬────┴────┬─────┴─────┬──────┘  │
│        │           │          │         │           │           │
│   ┌────▼───────────▼──────────▼─────────▼───────────▼──────┐  │
│   │              Spring Data JPA  +  Flyway                 │  │
│   └──────────────────────────┬──────────────────────────────┘  │
│                              │                                  │
│                    ┌─────────▼──────────┐                      │
│                    │   PostgreSQL 17     │                      │
│                    └────────────────────┘                      │
└─────────────────────────────────────────────────────────────────┘
```

**Multi-tenancy model:** shared database, shared schema, with `tenant_id` on every
business table. All service-layer queries are filtered by the authenticated user's tenant.

---

## 5. Domain Model

```
tenants ──< users              (staff accounts per consultancy)
tenants ──< students           (student profiles per consultancy)
tenants ──< universities       (university master list per consultancy)
tenants ──< workflow_templates (configurable per consultancy)
users   >──< roles             (via user_roles junction)
students ──< student_documents
students ──< applications ──< offer_letters
                        └──< visa_applications
students ──< tasks
```

### Status enums

| Entity | States |
|---|---|
| Tenant | `ACTIVE` \| `INACTIVE` \| `SUSPENDED` |
| User | `ACTIVE` \| `INACTIVE` \| `LOCKED` \| `PENDING_VERIFICATION` |
| Student | `LEAD` \| `QUALIFIED` \| `ACTIVE` \| `ENROLLED` \| `INACTIVE` |
| Document | `PENDING` \| `APPROVED` \| `REJECTED` \| `NEEDS_REVISION` |
| Application | `DRAFT` \| `SUBMITTED` \| `UNDER_REVIEW` \| `CONDITIONAL_OFFER` \| `UNCONDITIONAL_OFFER` \| `REJECTED` |
| Visa | `NOT_STARTED` \| `DRAFT` \| `SUBMITTED` \| `BIOMETRICS_SCHEDULED` \| `UNDER_REVIEW` \| `APPROVED` \| `REJECTED` |
| Task | `PENDING` \| `IN_PROGRESS` \| `COMPLETED` \| `CANCELLED` |

---

## 6. User Roles & Permissions

| Role | Authority | Key Capabilities |
|---|---|---|
| Platform Super Admin | `ROLE_SUPER_ADMIN` | Manage all tenants, full system access |
| Consultancy Admin | `ROLE_TENANT_ADMIN` | Manage staff, configure workflows, view all reports |
| Counselor | `ROLE_COUNSELOR` | Register students, create applications, assign tasks |
| Documentation Officer | `ROLE_DOC_OFFICER` | Verify documents, request revisions |
| Visa Officer | `ROLE_VISA_OFFICER` | Manage visa applications, generate PDF forms |
| Student | `ROLE_STUDENT` | Upload documents, view status, download offer letters |

> All roles except `ROLE_SUPER_ADMIN` are scoped to a single tenant.

---

## 7. Getting Started

### Prerequisites

- Java 25
- Maven 3.x
- Docker & Docker Compose

### 1. Clone the repository

```bash
git clone https://github.com/your-org/eduflow.git
cd eduflow
```

### 2. Start the database

```bash
docker compose up -d
```

The PostgreSQL 17 container will start at `localhost:5432` with:

| Property | Value |
|---|---|
| Database | `eduflow` |
| Username | `eduflow` |
| Password | `eduflow` |

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Flyway migrations run automatically on startup. The application will be available at
`http://localhost:8080`.

### 4. Verify health

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

### Useful development commands

```bash
# Stop the database
docker compose down

# Wipe all data and restart fresh
docker compose down -v && docker compose up -d

# Run tests
./mvnw test

# Package (skip tests)
./mvnw package -DskipTests
```

---

## 8. Project Structure

```
eduflow/
├── docker-compose.yml
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/eduflow/
    │   │   ├── EduflowApplication.java
    │   │   ├── common/              # BaseEntity, JpaAuditingConfig
    │   │   ├── tenant/              # Tenant domain
    │   │   ├── user/                # Staff users
    │   │   ├── role/                # Roles
    │   │   ├── student/             # Student profiles
    │   │   ├── document/            # Documents & Drive integration
    │   │   ├── university/          # Universities & courses
    │   │   ├── application/         # University applications
    │   │   ├── offerletter/         # Offer letters
    │   │   ├── visa/                # Visa applications & PDF generation
    │   │   ├── workflow/            # Configurable workflow engine
    │   │   ├── task/                # Task management
    │   │   ├── notification/        # Email / SMS / WhatsApp
    │   │   ├── audit/               # Audit event log
    │   │   ├── report/              # Reporting & KPIs
    │   │   ├── security/            # Security config, JWT, UserDetailsService
    │   │   ├── config/              # Third-party client beans
    │   │   └── exception/           # GlobalExceptionHandler
    │   └── resources/
    │       ├── application.properties
    │       ├── templates/           # Thymeleaf HTML templates
    │       └── db/migration/        # Flyway SQL migrations
    └── test/
        └── java/com/eduflow/       # Mirror of main package structure
```

Each domain package follows the same internal layout:

```
{domain}/
├── {Domain}.java              # JPA entity
├── {Domain}Repository.java    # Spring Data repository
├── {Domain}Service.java       # Business logic + @Transactional
├── {Domain}Controller.java    # REST / MVC controller
└── dto/
    ├── Create{Domain}Request.java
    ├── Update{Domain}Request.java
    └── {Domain}Response.java
```

---

## 9. Database Migrations

Migrations live in `src/main/resources/db/migration/` and follow Flyway naming conventions:

```
V{version}__{description}.sql
```

| File | Description |
|---|---|
| `V1__create_tenants_table.sql` | Tenants table with slug, status, audit columns |
| `V2__create_roles_table.sql` | Roles table; seeds 4 default system-wide roles |
| `V3__create_users_table.sql` | Users table + `user_roles` junction table |

### Rules

- **Never** modify an already-applied migration — always create a new `V{n+1}` file.
- Every table must have: UUID PK, audit columns (`created_at`, `updated_at`, `created_by`, `updated_by`), and `COMMENT ON TABLE` / `COMMENT ON COLUMN`.
- All FK columns must have an index named `idx_{table}_{column}`.

---

## 10. API Overview

All REST endpoints are prefixed with `/api/v1/`.

### Tenants (Super Admin only)
```
GET    /api/v1/tenants
POST   /api/v1/tenants
GET    /api/v1/tenants/{id}
PATCH  /api/v1/tenants/{id}/status
```

### Students
```
GET    /api/v1/students                        list (tenant-scoped, pageable)
POST   /api/v1/students                        register new student
GET    /api/v1/students/{id}                   student detail
PUT    /api/v1/students/{id}                   full update
PATCH  /api/v1/students/{id}/status            status transition
```

### Documents
```
GET    /api/v1/students/{id}/documents         list all docs
POST   /api/v1/students/{id}/documents         register doc metadata (Drive file ID)
PATCH  /api/v1/documents/{id}/verify           approve / reject / request revision
```

### University Applications
```
GET    /api/v1/students/{id}/applications      list applications
POST   /api/v1/students/{id}/applications      create application
PATCH  /api/v1/applications/{id}/status        update status
POST   /api/v1/applications/{id}/offer-letter  attach offer letter
```

### Visa Applications
```
GET    /api/v1/applications/{id}/visa          get visa application
POST   /api/v1/applications/{id}/visa          create visa application
PATCH  /api/v1/visa/{id}/status                update status
GET    /api/v1/visa/{id}/pdf/{type}            download generated PDF
```

### Tasks
```
GET    /api/v1/tasks                           list tasks (assignee / student filter)
POST   /api/v1/tasks                           create & assign task
PATCH  /api/v1/tasks/{id}/status               update status
```

### Reporting
```
GET    /api/v1/reports/funnel                  student funnel KPIs
GET    /api/v1/reports/applications            application stats by status
GET    /api/v1/reports/visas                   visa stats by status
```

### Error Response Format

```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Student not found with id: 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "timestamp": "2026-06-14T10:00:00Z",
  "path": "/api/v1/students/3fa85f64-5717-4562-b3fc-2c963f66afa6"
}
```

---

## 11. Module Breakdown

### Student Module
Manages the full student profile: personal information, contact details, education history,
interested countries and courses, and lifecycle status (`LEAD → QUALIFIED → ACTIVE → ENROLLED`).

### Document Module
Handles document collection and verification. Stores Google Drive file/folder IDs.
Automatically creates a structured Drive folder tree when a student is registered.
Documents progress through: `PENDING → APPROVED | REJECTED | NEEDS_REVISION`.

### University & Application Module
Maintains each tenant's university and course master data. Students are applied to courses;
the application tracks status from `DRAFT` through to `UNCONDITIONAL_OFFER` or `REJECTED`.

### Offer Letter Module
Attached to an accepted application. Stores the Drive file ID, letter reference number,
and date received.

### Visa Module
Activated after an offer letter is issued. Collects all visa form data (personal info,
passport, travel history, financials, accommodation, health insurance) and generates PDF
documents: visa checklist, cover letter, and application summary.

### Workflow Engine
Each tenant configures a `WorkflowTemplate` defining their own ordered stages.
A `StudentWorkflow` record tracks which stage each student is currently in.
Stage transitions are validated — students cannot skip stages.

### Task Module
Counselors and admins assign tasks to staff members, linked to a specific student.
Priority levels and due dates are tracked. Tasks move through:
`PENDING → IN_PROGRESS → COMPLETED | CANCELLED`.

### Notification Module
Event-driven notifications dispatched by key workflow transitions.
Supported channels: **Email**, **SMS**, **WhatsApp**.
Every dispatched notification is persisted for audit and retry.

| Trigger | Default Channel |
|---|---|
| Document missing reminder | Email + WhatsApp |
| Document approved | Email |
| Offer letter received | Email + SMS |
| Visa submitted | Email |
| Visa approved | Email + SMS + WhatsApp |
| Task assigned | Email |

### Audit Trail
Append-only log of every state-changing action. Records who did what, when, on which
record, and what the previous value was. The `audit_events` table is never updated or deleted.

### Reporting Dashboard
Aggregated KPIs including:
- Student funnel (leads → qualified → applied → offer letters → visas → enrolled)
- Applications by status
- Visa approvals and rejections
- Revenue / commission tracking *(future)*

---

## 12. Roadmap

### V1 (Current)
- [x] Multi-tenant infrastructure (tenants, users, roles)
- [x] PostgreSQL + Flyway migrations
- [x] Base entity with JPA auditing
- [ ] Student registration & profile management
- [ ] Document collection & verification
- [ ] University & course master data
- [ ] University application workflow
- [ ] Offer letter management
- [ ] Visa application module
- [ ] PDF generation (visa checklist, cover letter)
- [ ] Google Drive integration
- [ ] Configurable workflow engine
- [ ] Task management
- [ ] Email / SMS / WhatsApp notifications
- [ ] Audit trail
- [ ] Reporting dashboard
- [ ] Student self-service portal

### V2 (Planned)
- [ ] Lead management (website, Facebook, referral sources)
- [ ] Sub-agent management & commission tracking
- [ ] University portal (direct application review)
- [ ] Document OCR (passport, transcript, offer letter parsing)
- [ ] AI visa form autofill
- [ ] AI chat assistant
- [ ] SaaS billing (Starter / Professional / Enterprise tiers)
- [ ] White-labelling support

---

## 13. Contributing

1. Check out the Copilot instructions at [`.github/copilot-instructions.md`](.github/copilot-instructions.md) before writing any code.
2. Every schema change requires a new Flyway migration — never modify an existing one.
3. All JPA entities must extend `BaseEntity`.
4. All tenant-scoped queries must filter by `tenantId`.
5. Run the test suite before submitting a PR:
   ```bash
   ./mvnw test
   ```

---

*Built with ☕ and Spring Boot 4 · Java 25 · PostgreSQL 17*

