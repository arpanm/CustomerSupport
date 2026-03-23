# TODO.md — SupportHub Task Tracker
> Single source of truth for all tasks, bugs, issues, and decisions.
> Managed by Claude Code agents. Updated after every task, review, analysis, or deployment.

**Last Updated:** 2026-03-23T00:00:00Z
**Active Sprint:** Sprint 0 — Foundation
**Project:** SupportHub | Rupantar Technologies

---

## Summary

| Status | Count |
|--------|-------|
| 🆕 OPEN | 12 |
| 🔄 IN_PROGRESS | 0 |
| 🔍 IN_REVIEW | 0 |
| ⚠️ BLOCKED | 0 |
| ✅ DONE | 0 |
| ❌ CANCELLED | 0 |
| **TOTAL** | **12** |

---

## 🔴 CRITICAL / BLOCKING (P0)
<!-- None currently -->

---

## 🟠 HIGH PRIORITY (P1)
<!-- None currently -->

---

## 🟢 OPEN TASKS — Sprint 0: Foundation

### [INFRA-001] Local Docker Compose Stack Setup
- **ID:** INFRA-001
- **Type:** INFRA
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** lead
- **Service:** infrastructure
- **Sprint:** sprint-0
- **Blocked By:** none
- **Blocks:** DB-001, FEAT-001 through FEAT-010
- **REQUIREMENT Ref:** Section 17.4 (Technology Stack)
- **ARCHITECTURE Ref:** Section 5 (Repo Structure), Appendix A
- **Created:** 2026-03-23T00:00:00Z
- **Updated:** 2026-03-23T00:00:00Z
- **Branch:** feature/INFRA-001-docker-compose-stack

#### Description
Set up the complete local development Docker Compose stack including all required infrastructure services.

#### Acceptance Criteria
- [ ] `infrastructure/docker/docker-compose.yml` starts all services cleanly
- [ ] PostgreSQL 16 with pgvector extension available on port 5432
- [ ] MongoDB 7 available on port 27017
- [ ] Redis 7 available on port 6379
- [ ] Apache Kafka (3 brokers) available on port 9092
- [ ] Elasticsearch 8 available on port 9200
- [ ] MinIO (S3-compatible) available on port 9000
- [ ] Strapi CMS available on port 1337
- [ ] All services have health checks configured
- [ ] README with `docker compose up -d` instructions

---

### [INFRA-002] Maven Multi-Module Parent POM Setup
- **ID:** INFRA-002
- **Type:** INFRA
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** lead
- **Service:** backend
- **Sprint:** sprint-0
- **Blocked By:** none
- **Blocks:** FEAT-001 through FEAT-010
- **REQUIREMENT Ref:** Section 17.1 (Backend Tech Stack)
- **ARCHITECTURE Ref:** Section 5 (Monorepo Structure)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/INFRA-002-parent-pom

#### Description
Create the Maven parent POM for the multi-module backend project with all shared dependency management, plugin configuration, and code quality tooling.

#### Acceptance Criteria
- [ ] `backend/pom.xml` with all modules declared
- [ ] Java 21, Spring Boot 3.3.x, Spring Cloud 2023.0.x managed
- [ ] Virtual threads enabled in all services
- [ ] JaCoCo configured (80% service layer threshold)
- [ ] Checkstyle (Google Style + custom rules) configured
- [ ] SpotBugs configured
- [ ] PMD configured
- [ ] OWASP Dependency Check configured
- [ ] Flyway configured
- [ ] `./mvnw clean verify` passes on empty modules
- [ ] `supporthub-shared` module created with base classes

---

### [INFRA-003] Frontend Monorepo Setup (npm workspaces)
- **ID:** INFRA-003
- **Type:** INFRA
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** lead
- **Service:** frontend
- **Sprint:** sprint-0
- **Blocked By:** none
- **Blocks:** FEAT-020 through FEAT-030
- **REQUIREMENT Ref:** Section 17.3 (Frontend Tech Stack)
- **ARCHITECTURE Ref:** Section 11 (Frontend Architecture)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/INFRA-003-frontend-monorepo

#### Description
Set up the npm workspaces monorepo with Vite, React 18, TypeScript strict mode, Tailwind CSS, shadcn/ui, TanStack Query, and Zustand across all frontend apps.

#### Acceptance Criteria
- [ ] `frontend/package.json` with workspaces configured
- [ ] `packages/customer-sdk/` — TypeScript library skeleton
- [ ] `packages/ui-components/` — shadcn/ui + Tailwind base
- [ ] `apps/customer-portal/` — React + Vite app
- [ ] `apps/agent-dashboard/` — React + Vite app
- [ ] `apps/admin-portal/` — React + Vite app
- [ ] TypeScript strict mode on all apps
- [ ] ESLint + Prettier configured
- [ ] `npm run build --workspaces` succeeds
- [ ] `npm run test --workspaces` runs (even if no tests yet)

---

### [INFRA-004] CI/CD GitHub Actions Pipeline
- **ID:** INFRA-004
- **Type:** INFRA
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** lead
- **Service:** infrastructure
- **Sprint:** sprint-0
- **Blocked By:** INFRA-001, INFRA-002, INFRA-003
- **Blocks:** DEPLOY-001
- **REQUIREMENT Ref:** Section 18.5 (CI/CD)
- **ARCHITECTURE Ref:** Section 21.4 (CI/CD Pipeline)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/INFRA-004-cicd-pipeline

#### Description
Create GitHub Actions workflows for CI (test + analysis) and CD (deploy to dev/staging/prod).

#### Acceptance Criteria
- [ ] `.github/workflows/ci.yml` — runs on PR: unit tests, integration tests, security scan, code analysis
- [ ] `.github/workflows/deploy-staging.yml` — auto-deploy to staging on develop merge
- [ ] `.github/workflows/deploy-prod.yml` — manual approval gate, tag-triggered prod deploy
- [ ] Docker image build + push to ECR
- [ ] Testcontainers work in GitHub Actions environment
- [ ] Failure annotations appear on PR diffs

---

### [DB-001] Core Schema: Tenants, Customers, AgentUsers Tables
- **ID:** DB-001
- **Type:** DB
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** agent:db-migration-agent
- **Service:** auth-service, tenant-service, customer-service
- **Sprint:** sprint-0
- **Blocked By:** INFRA-001, INFRA-002
- **Blocks:** FEAT-001, FEAT-002, FEAT-003
- **REQUIREMENT Ref:** Section 4.2 (Customer), 4.6 (AgentUser), 4.1 (Tenant)
- **ARCHITECTURE Ref:** Section 8.1 (PostgreSQL Schema), Section 18.1 (Schema Rules)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/DB-001-core-auth-schema

#### Description
Create Flyway migration scripts for the foundational tables: tenants, customers, agent_users, teams, refresh_tokens.

#### Acceptance Criteria
- [ ] `V1__create_tenants_table.sql` — with tenant branding JSONB, plan enum, timezone
- [ ] `V2__create_customers_table.sql` — with encrypted phone/email columns, preferred_language
- [ ] `V3__create_agent_users_table.sql` — with role enum, bcrypt_password
- [ ] `V4__create_teams_table.sql`
- [ ] `V5__create_refresh_tokens_table.sql`
- [ ] All tables: id UUID PK, tenant_id FK, created_at, updated_at
- [ ] RLS enabled and policies created on all tables
- [ ] Indexes on tenant_id, phone, email for customer; email for agent_users
- [ ] Migration runs cleanly on PostgreSQL 16 via Testcontainer
- [ ] Rollback notes documented in each migration file

---

### [DB-002] Core Schema: Tickets, TicketActivities, Categories Tables
- **ID:** DB-002
- **Type:** DB
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** agent:db-migration-agent
- **Service:** ticket-service
- **Sprint:** sprint-0
- **Blocked By:** DB-001
- **Blocks:** FEAT-004, FEAT-005
- **REQUIREMENT Ref:** Section 4.4 (Ticket), 4.5 (TicketActivity), 4.8 (TicketCategory)
- **ARCHITECTURE Ref:** Section 7.2 (ticket-service domain model), Section 8.1
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/DB-002-tickets-schema

#### Description
Create Flyway migration scripts for ticket domain tables.

#### Acceptance Criteria
- [ ] `V6__create_ticket_categories_table.sql`
- [ ] `V7__create_ticket_sub_categories_table.sql`
- [ ] `V8__create_tickets_table.sql` — all columns from ARCHITECTURE.md Section 7.2
- [ ] `V9__create_ticket_activities_table.sql`
- [ ] `V10__create_ticket_sequences_table.sql` — for ticket number generation
- [ ] `V11__create_sla_policies_table.sql`
- [ ] `V12__create_resolution_templates_table.sql`
- [ ] `V13__create_faq_entries_table.sql` — with vector(1536) embedding column
- [ ] HNSW index on faq_entries.embedding
- [ ] All tables: RLS enabled, tenant_id, appropriate indexes
- [ ] pgvector extension enabled in migration

---

### [FEAT-001] auth-service — OTP Login Flow
- **ID:** FEAT-001
- **Type:** FEAT
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** agent:implementer
- **Service:** auth-service
- **Sprint:** sprint-0
- **Blocked By:** DB-001, INFRA-002
- **Blocks:** FEAT-004 (ticket creation needs auth)
- **REQUIREMENT Ref:** REQ-CUI-AUTH-01 through REQ-CUI-AUTH-05
- **ARCHITECTURE Ref:** Section 7.1 (auth-service), Section 15.1 (JWT Token Flow)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/FEAT-001-otp-auth

#### Description
Implement customer OTP authentication flow: send OTP via SMS (MSG91), verify OTP, issue JWT pair.

#### Acceptance Criteria
- [ ] `POST /api/v1/auth/otp/send` — stores OTP in Redis (6-digit, 5min TTL, 3-attempt limit)
- [ ] `POST /api/v1/auth/otp/verify` — validates OTP, creates/updates Customer, returns JWT pair
- [ ] `POST /api/v1/auth/refresh` — validates refresh token, issues new access token
- [ ] `POST /api/v1/auth/logout` — invalidates refresh token in Redis
- [ ] JWT: RS256, contains sub, tenant_id, role, type, iat, exp
- [ ] Access token: 1h expiry. Refresh token: 30d, httpOnly cookie
- [ ] Rate limit: 3 OTP sends per phone per hour
- [ ] Unit tests: OtpService, JwtService (100% service layer coverage)
- [ ] Integration tests: all 4 endpoints tested
- [ ] No PII in logs
- [ ] OpenAPI annotations on all endpoints

---

### [FEAT-002] auth-service — Agent Email+Password Login
- **ID:** FEAT-002
- **Type:** FEAT
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** agent:implementer
- **Service:** auth-service
- **Sprint:** sprint-0
- **Blocked By:** DB-001, INFRA-002
- **Blocks:** none (parallel with FEAT-001)
- **REQUIREMENT Ref:** REQ-AGT-AUTH-01 through REQ-AGT-AUTH-04
- **ARCHITECTURE Ref:** Section 7.1, Section 15.1
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/FEAT-002-agent-auth

#### Description
Implement agent email+password authentication with optional 2FA for admin roles.

#### Acceptance Criteria
- [ ] `POST /api/v1/auth/agent/login` — BCrypt password validation, returns JWT or 2FA prompt
- [ ] `POST /api/v1/auth/agent/2fa/verify` — validates emailed OTP for ADMIN roles
- [ ] Session timeout after 8h inactivity (Spring Session with Redis)
- [ ] Unit + integration tests for both endpoints

---

### [FEAT-003] api-gateway — Spring Cloud Gateway Setup
- **ID:** FEAT-003
- **Type:** FEAT
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** agent:implementer
- **Service:** api-gateway
- **Sprint:** sprint-0
- **Blocked By:** INFRA-002
- **Blocks:** all other FEAT tasks (gateway must route requests)
- **REQUIREMENT Ref:** Section 15.2 (Security) — Rate limiting, JWT validation
- **ARCHITECTURE Ref:** Section 10 (API Gateway)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/FEAT-003-api-gateway

#### Description
Set up Spring Cloud Gateway with JWT validation filter, tenant resolution filter, rate limiting (Redis), and circuit breakers (Resilience4j).

#### Acceptance Criteria
- [ ] JwtAuthFilter: validates JWT on every request, injects X-User-Id and X-Tenant-ID headers
- [ ] TenantResolutionFilter: extracts tenant from subdomain or X-Tenant-ID header
- [ ] Rate limiting: 1000 req/min per tenant, 100 req/min per IP (Redis token bucket)
- [ ] Routes configured for all backend services
- [ ] Circuit breaker configured (Resilience4j) for each downstream service
- [ ] WebSocket proxy for agent dashboard real-time notifications
- [ ] Health endpoint: GET /actuator/health returns 200
- [ ] Request ID header injected on every request
- [ ] Integration tests: JWT rejection (401), tenant missing (400), rate limit (429)

---

### [FEAT-004] ticket-service — Core Ticket CRUD + Status Machine
- **ID:** FEAT-004
- **Type:** FEAT
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** agent:implementer
- **Service:** ticket-service
- **Sprint:** sprint-0
- **Blocked By:** DB-002, FEAT-003
- **Blocks:** FEAT-005, FEAT-006, FEAT-007
- **REQUIREMENT Ref:** REQ-CUI-CREATE-01 through REQ-CUI-CREATE-08, REQ-AGT-DETAIL-01 through REQ-AGT-DETAIL-03
- **ARCHITECTURE Ref:** Section 7.2 (ticket-service LLD), Section 4.4 (Ticket domain model)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/FEAT-004-ticket-service-core

#### Description
Implement core ticket-service: Ticket entity, TicketActivity, status transition machine, SLA engine, ticket number generator, Kafka event publishing.

#### Acceptance Criteria
- [ ] `POST /api/v1/tickets` — create ticket with all fields from ARCHITECTURE.md Section 7.2
- [ ] `GET /api/v1/tickets` — list with filters (status, category, priority, date range, sentiment)
- [ ] `GET /api/v1/tickets/{ticketNumber}` — get detail
- [ ] `PUT /api/v1/tickets/{ticketNumber}` — update (status, priority, assignment, tags)
- [ ] `POST /api/v1/tickets/{ticketNumber}/activities` — add comment/note/resolution
- [ ] `POST /api/v1/tickets/{ticketNumber}/actions/resolve` — provide resolution
- [ ] `POST /api/v1/tickets/{ticketNumber}/actions/reopen` — reopen with reason
- [ ] `POST /api/v1/tickets/{ticketNumber}/actions/escalate` — escalate
- [ ] Status transition machine: all valid and invalid transitions per ARCHITECTURE.md Section 7.2
- [ ] Ticket number generation: Redis INCR, format {PREFIX}-{YEAR}-{SEQ:06d}
- [ ] SLA engine: compute due dates, @Scheduled breach detection every 5 min
- [ ] Kafka events published: ticket.created, ticket.status-changed, ticket.activity-added
- [ ] Tenant isolation: all queries filtered by tenantId from TenantContextHolder
- [ ] Unit tests: TicketService, SlaEngine, TicketStatusTransition (100% service layer)
- [ ] Integration tests: all endpoints, status transitions, SLA computation

---

### [DOCS-001] Initial Slash Commands and Agent Definitions
- **ID:** DOCS-001
- **Type:** DOCS
- **Priority:** P2-MEDIUM
- **Status:** OPEN
- **Owner:** lead
- **Service:** all
- **Sprint:** sprint-0
- **Blocked By:** none
- **Blocks:** none
- **REQUIREMENT Ref:** N/A
- **ARCHITECTURE Ref:** N/A (Claude Code config)
- **Created:** 2026-03-23T00:00:00Z
- **Branch:** feature/DOCS-001-claude-commands-agents

#### Description
Create all `.claude/agents/` and `.claude/commands/` files referenced in CLAUDE.md, plus the `.claude/settings.json` hooks configuration.

#### Acceptance Criteria
- [ ] `.claude/settings.json` with hooks from CLAUDE.md Section 25
- [ ] `.claude/agents/code-reviewer.md`
- [ ] `.claude/agents/security-analyst.md`
- [ ] `.claude/agents/test-engineer.md`
- [ ] `.claude/agents/db-migration-agent.md`
- [ ] `.claude/agents/implementer.md`
- [ ] `.claude/commands/sdlc.md`
- [ ] `.claude/commands/task.md`
- [ ] `.claude/commands/review.md`
- [ ] `.claude/commands/test.md`
- [ ] `.claude/commands/security.md`
- [ ] `.claude/commands/analyse.md`
- [ ] `.claude/commands/deploy.md`
- [ ] `.claude/commands/status.md`

---

## 🔄 IN PROGRESS
<!-- No tasks currently in progress -->

---

## ✅ COMPLETED (Sprint 0)
<!-- No tasks completed yet -->

---

## 📋 BACKLOG — Sprint 1 (Planned)

### Planned for Sprint 1 (after Sprint 0 foundation complete):
- FEAT-005: customer-service implementation
- FEAT-006: notification-service (SMS + in-app)
- FEAT-007: AI service — sentiment analysis pipeline
- FEAT-008: faq-service — CRUD + Strapi sync
- FEAT-009: order-sync-service — OMS integration
- FEAT-010: reporting-service — Elasticsearch CQRS read models
- FEAT-011: MCP server — customer-facing tools
- FEAT-020: customer-sdk (TypeScript headless SDK)
- FEAT-021: agent-dashboard — ticket queue view
- FEAT-022: agent-dashboard — ticket detail + AI assistance panel
- FEAT-023: admin-portal — metadata management
- DB-003: MongoDB collections setup (ai_interactions, notifications)
- INFRA-005: Kubernetes base manifests (Kustomize)
- INFRA-006: Terraform modules (RDS, ElastiCache, MSK, EKS)

---

## Changelog

### 2026-03-23
- TODO.md created with Sprint 0 foundation tasks
- Initial 12 tasks: 4 INFRA, 2 DB, 5 FEAT, 1 DOCS
- Based on REQUIREMENT.md v1.0 and ARCHITECTURE.md v1.0
