# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Status

**SupportHub** is currently in **Sprint 0 — Foundation** (specification phase). No source code exists yet. All 12 initial tasks are OPEN in `TODO.md`.

---

## Source of Truth Files

Read these before starting any work:

| File | Purpose |
|---|---|
| `REQUIREMENT.md` | Functional & non-functional requirements (REQ-* identifiers) |
| `ARCHITECTURE.md` | System design, service map, data models, patterns |
| `TODO.md` | All tasks, bugs, issues (single source of truth) |
| `CLAUDE.md` | This file: how we work |

**If code conflicts with REQUIREMENT.md or ARCHITECTURE.md, the documents win.**

---

## Before Writing Any Code

1. Check `TODO.md` for related existing tasks
2. Create/update a task in `TODO.md` (set status `IN_PROGRESS`)
3. Create a feature branch: `git checkout -b feature/TASK-ID-short-description`
4. Never commit directly to `main` or `develop`

---

## Build & Run Commands

### Backend (Java 21 + Spring Boot 3.3, Maven)

```bash
# From /backend
./mvnw clean package -DskipTests                        # Build all services
./mvnw clean package -pl ticket-service -DskipTests     # Build specific service
./mvnw spring-boot:run -pl ticket-service -Dspring.profiles.active=dev
./mvnw test -pl ticket-service                          # Unit tests
./mvnw verify -pl ticket-service                        # Integration tests (needs Docker)
./mvnw test -pl ticket-service -Dtest=TicketServiceTest # Single test class
./mvnw verify                                           # All tests, all services
./mvnw checkstyle:check spotbugs:check pmd:check -pl ticket-service
./mvnw dependency-check:check -pl ticket-service        # OWASP CVE scan
./mvnw flyway:migrate -pl ticket-service -Dflyway.url=${DB_URL}
```

### Frontend (React 18 + TypeScript, npm workspaces)

```bash
# From /frontend
npm ci                                          # Install all dependencies
npm run build --workspaces                      # Build everything
npm run test --workspaces                       # All tests
npm run lint --workspaces                       # ESLint all packages
npm run typecheck --workspaces                  # TypeScript strict check
npm run dev -w apps/agent-dashboard             # Dev server: http://localhost:3001
npm run dev -w apps/admin-portal                # Dev server: http://localhost:3002
npm run dev -w apps/customer-portal             # Dev server: http://localhost:3000
```

### Infrastructure

```bash
# Full local stack (Postgres, MongoDB, Redis, Kafka, Elasticsearch, MinIO, Strapi)
docker compose -f infrastructure/docker/docker-compose.yml up -d
docker compose -f infrastructure/docker/docker-compose.yml down
```

### E2E Tests (Playwright, requires all services running)

```bash
cd frontend && npx playwright test
cd frontend && npx playwright test --ui
```

---

## Architecture Overview

### Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.3, Spring AI, Spring Cloud |
| Primary DB | PostgreSQL 16 + pgvector |
| Document DB | MongoDB 7 |
| Cache | Redis 7 |
| Events | Apache Kafka |
| Search | Elasticsearch 8 |
| CMS | Strapi v5 |
| AI | Anthropic Claude API (haiku for sentiment, sonnet for resolution) |
| Frontend | React 18 + TypeScript + Vite |
| Infra | Docker Compose (dev), Kubernetes + Helm (prod), AWS |

### Microservices (all in `/backend`)

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Spring Cloud Gateway, JWT validation, rate limiting |
| `auth-service` | 8081 | OTP login (customers), email+password (agents), JWT issuance |
| `ticket-service` | 8082 | Ticket CRUD, status machine, SLA engine |
| `customer-service` | 8083 | Customer profiles and preferences |
| `ai-service` | 8084 | Sentiment analysis, resolution suggestions via Anthropic |
| `notification-service` | 8085 | SMS (MSG91), WhatsApp, Email (SendGrid) |
| `faq-service` | 8086 | FAQ CRUD, semantic search, Strapi sync |
| `reporting-service` | 8087 | CQRS read models, Elasticsearch |
| `tenant-service` | 8088 | Multi-tenancy config, onboarding |
| `order-sync-service` | 8089 | OMS integration |
| `mcp-server` | 8090 | MCP tools over SSE for AI agents |
| `shared` | — | DTOs, Kafka events, exceptions, test utilities |

### Frontend Apps (all in `/frontend`)

| App/Package | Port | Description |
|---|---|---|
| `apps/customer-portal` | 3000 | React reference app for customers |
| `apps/agent-dashboard` | 3001 | React PWA for support agents |
| `apps/admin-portal` | 3002 | React PWA for ops/admin |
| `packages/customer-sdk` | — | Headless TypeScript SDK (no React dependency) |
| `packages/ui-components` | — | Shared shadcn/ui + Tailwind component library |

### Key Architectural Patterns

- **Multi-tenancy first**: `tenant_id` on every DB table, Row-Level Security via PostgreSQL RLS
- **Database-per-service**: No cross-service DB queries; each service owns its schema
- **Event-driven async**: Kafka for all cross-service state changes (never synchronous REST for side effects)
- **CQRS**: `reporting-service` maintains Elasticsearch read models from Kafka events
- **API-first**: OpenAPI annotations on all controllers, contracts before implementation
- **Tenant context propagation**: `TenantContextHolder` populated by gateway filter; never accept `tenantId` as a user-controlled parameter

---

## Java Coding Standards (Key Rules)

Package: `in.supporthub.{service}.{layer}`

- **Layer responsibilities**: Controller (validate + delegate) → Service (business logic) → Repository (data only) → Domain (entities/enums, no service deps)
- **DTOs**: Java Records for immutable request/response/event objects
- **Injection**: Constructor injection only (`@RequiredArgsConstructor`), never `@Autowired` on fields
- **Transactions**: `@Transactional` at class level on mutation services
- **Logging**: Always include `tenantId` and entity IDs; never log PII (phone, email, name)
- **Errors**: Throw typed exceptions (`TicketNotFoundException extends AppException`), never swallow
- **Tenant context**: `TenantContextHolder.getTenantId()` — never from request parameters
- **Kafka**: Use `TicketEventPublisher` and shared event records, never raw `kafkaTemplate.send()`
- **Virtual threads**: `spring.threads.virtual.enabled=true` in all services

Hard prohibitions: `System.out.println`, `e.printStackTrace`, `@Autowired` on fields, hardcoded `localhost`/secrets, `null` returns from public service methods, catching and swallowing `Exception`.

## React/TypeScript Standards (Key Rules)

- **TypeScript strict mode** mandatory in all `tsconfig.json`
- **Server state**: TanStack Query (never `useState` for API data)
- **Global UI state**: Zustand (no Redux)
- **Forms**: React Hook Form
- **No**: `console.log`, `any` type, inline styles, `key={index}`, unhandled promise rejections

## Database Standards (Key Rules)

Every table must have: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `tenant_id UUID NOT NULL`, `created_at TIMESTAMPTZ`, `updated_at TIMESTAMPTZ`, and RLS enabled with a tenant isolation policy.

Migration files: `V{N}__{description}.sql`, sequential, idempotent (`IF EXISTS`/`IF NOT EXISTS`), in `backend/{service}/src/main/resources/db/migration/`.

Never `DROP TABLE/COLUMN` without an explicit human-approved TODO.md task.

---

## Slash Commands

| Command | Purpose |
|---|---|
| `/task create [type] [title]` | Create task in TODO.md |
| `/task update [ID] [field] [value]` | Update task status/priority |
| `/review [service]` | Spawn code-reviewer sub-agent → REVIEW-NNN tasks |
| `/test [service] [unit\|integration\|all]` | Run tests → TEST-NNN tasks for failures |
| `/security [service]` | OWASP scan → SEC-NNN tasks |
| `/analyse [service]` | Static analysis → ANAL-NNN tasks |
| `/deploy [env] [service]` | Run deployment pipeline |
| `/status` | Print TODO.md summary |
| `/sdlc` | Run full Micro-SDLC cycle for current changes |

---

## Non-Negotiable Rules

- Every endpoint checks **tenant isolation AND role**
- **80% line coverage** minimum for service layer (70% overall)
- No CRITICAL/HIGH security issues unresolved before merge
- DB migrations always run **before** deploying new service version
- Deployment order: **dev → staging → prod**, never skip
- All tasks completed → TODO.md updated → REQUIREMENT.md/ARCHITECTURE.md synced if changed
