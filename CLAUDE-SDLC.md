# SupportHub — CLAUDE.md
## Master Orchestration Guide for Claude Code & Multi-Agent Team

> **This file is the project constitution.** Every Claude Code session, every sub-agent, every teammate reads and obeys this document. It is law.

---

## ⚡ QUICK REFERENCE — READ THIS FIRST

```
Before ANY work begins:
  1. Read REQUIREMENT.md + ARCHITECTURE.md (or relevant sections)
  2. Check TODO.md for existing tasks and their status
  3. Create/update tasks in TODO.md before writing a single line of code
  4. Follow the Micro-SDLC cycle for EVERY change, no exceptions

Key files:
  REQUIREMENT.md     — What the system must do
  ARCHITECTURE.md    — How it is built
  TODO.md            — All tasks, issues, status (single source of truth)
  CLAUDE.md          — This file: how we work

Key commands:
  /task              — Create or update a task in TODO.md
  /review            — Spawn code review sub-agent
  /test              — Run full test suite + update TODO.md with failures
  /security          — Run security analysis, add issues to TODO.md
  /analyse           — Run code quality analysis, add issues to TODO.md
  /deploy            — Run deployment pipeline with pre-flight checks
  /sdlc              — Run full Micro-SDLC cycle for current changes
  /status            — Print current TODO.md summary
```

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Repository & File Structure](#2-repository--file-structure)
3. [The Golden Rule: Micro-SDLC Cycle](#3-the-golden-rule-micro-sdlc-cycle)
4. [TODO.md — Task Management System](#4-todomd--task-management-system)
5. [Multi-Agent Team Architecture](#5-multi-agent-team-architecture)
6. [Sub-Agent Routing Rules](#6-sub-agent-routing-rules)
7. [Phase 1: Requirements & Task Decomposition](#7-phase-1-requirements--task-decomposition)
8. [Phase 2: Implementation Standards](#8-phase-2-implementation-standards)
9. [Phase 3: Testing Protocol](#9-phase-3-testing-protocol)
10. [Phase 4: Code Review Protocol](#10-phase-4-code-review-protocol)
11. [Phase 5: Static Analysis & Security Scan](#11-phase-5-static-analysis--security-scan)
12. [Phase 6: Issue Resolution](#12-phase-6-issue-resolution)
13. [Phase 7: Regression & Integration Testing](#13-phase-7-regression--integration-testing)
14. [Phase 8: Deployment Pipeline](#14-phase-8-deployment-pipeline)
15. [Phase 9: Documentation & Sync Back](#15-phase-9-documentation--sync-back)
16. [Java Spring Boot Coding Standards](#16-java-spring-boot-coding-standards)
17. [React / TypeScript Frontend Standards](#17-react--typescript-frontend-standards)
18. [Database & Migration Standards](#18-database--migration-standards)
19. [Kafka Event Standards](#19-kafka-event-standards)
20. [API Design Standards](#20-api-design-standards)
21. [Testing Standards](#21-testing-standards)
22. [Security Standards](#22-security-standards)
23. [Build & Run Commands](#23-build--run-commands)
24. [Environment Variables Reference](#24-environment-variables-reference)
25. [Hooks Configuration](#25-hooks-configuration)
26. [Slash Commands Reference](#26-slash-commands-reference)
27. [REQUIREMENT.md & ARCHITECTURE.md Sync Protocol](#27-requirementmd--architecturemd-sync-protocol)
28. [Escalation & Blocking Rules](#28-escalation--blocking-rules)

---

## 1. Project Overview

**SupportHub** is a multi-tenant, AI-native customer support ticket management platform for the Indian market.

| Attribute | Value |
|---|---|
| Backend | Java 21 + Spring Boot 3.3, Spring AI, Spring Cloud |
| Frontend | React 18 + TypeScript + Vite (Agent Dashboard, Admin PWA), Headless TS SDK (Customer) |
| Primary DB | PostgreSQL 16 + pgvector |
| Document DB | MongoDB 7 |
| Cache | Redis 7 |
| Events | Apache Kafka |
| Search | Elasticsearch 8 |
| CMS | Strapi v5 |
| MCP | Spring AI MCP Server (WebMVC SSE) |
| AI | Anthropic Claude API (claude-haiku-4-5-20251001 for sentiment, claude-sonnet-4-5 for resolution) |
| Observability | Micrometer + Prometheus + Grafana + Langfuse |
| Infra | Docker Compose (dev), Kubernetes + Helm (prod), AWS |

**Source of truth files:**
- `REQUIREMENT.md` — functional and non-functional requirements
- `ARCHITECTURE.md` — all design decisions, service map, data models, flow diagrams
- `TODO.md` — all tasks, bugs, issues, their status and ownership

**CRITICAL:** If there is a conflict between REQUIREMENT.md/ARCHITECTURE.md and any code, the documents win. Update the code to match, or raise a documented change request in TODO.md.

---

## 2. Repository & File Structure

```
supporthub/
├── CLAUDE.md                         ← THIS FILE (read at every session start)
├── REQUIREMENT.md                    ← Requirements source of truth
├── ARCHITECTURE.md                   ← Architecture source of truth
├── TODO.md                           ← Task tracking (single source of truth)
├── .claude/
│   ├── settings.json                 ← Hooks and permissions
│   ├── agents/                       ← Sub-agent definitions
│   │   ├── code-reviewer.md
│   │   ├── security-analyst.md
│   │   ├── test-engineer.md
│   │   ├── db-migration-agent.md
│   │   └── deployment-agent.md
│   └── commands/                     ← Slash commands
│       ├── task.md
│       ├── review.md
│       ├── test.md
│       ├── security.md
│       ├── analyse.md
│       ├── deploy.md
│       ├── sdlc.md
│       └── status.md
├── backend/
│   ├── pom.xml                       ← Parent POM
│   ├── shared/                       ← Shared library
│   ├── api-gateway/
│   ├── auth-service/
│   ├── ticket-service/
│   ├── customer-service/
│   ├── ai-service/
│   ├── notification-service/
│   ├── faq-service/
│   ├── reporting-service/
│   ├── tenant-service/
│   ├── order-sync-service/
│   └── mcp-server/
├── frontend/
│   ├── packages/
│   │   ├── customer-sdk/
│   │   └── ui-components/
│   └── apps/
│       ├── customer-portal/
│       ├── agent-dashboard/
│       └── admin-portal/
├── cms/                              ← Strapi v5
├── infrastructure/
│   ├── docker/
│   ├── terraform/
│   └── k8s/
├── scripts/
│   ├── seed/
│   ├── sandbox/
│   └── analysis/                     ← Code analysis scripts
└── docs/
    ├── api/                          ← OpenAPI specs (auto-generated)
    └── adr/                          ← Architecture Decision Records
```

---

## 3. The Golden Rule: Micro-SDLC Cycle

**EVERY prompt, EVERY change, no matter how small, MUST follow this cycle.**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        MICRO-SDLC CYCLE                                  │
│                                                                          │
│  PROMPT/CHANGE RECEIVED                                                  │
│         │                                                                │
│         ▼                                                                │
│  [1] INTAKE & ALIGNMENT ──────────────────────────────────────────────  │
│       Read REQUIREMENT.md + ARCHITECTURE.md sections                    │
│       Identify conflicts or gaps → update docs or raise issue           │
│         │                                                                │
│         ▼                                                                │
│  [2] TASK DECOMPOSITION ───────────────────────────────────────────────  │
│       Create tasks in TODO.md (parallel where possible, else sequential)│
│       Each task: ID, title, type, owner, status, dependencies           │
│         │                                                                │
│         ▼                                                                │
│  [3] PARALLEL EXECUTION ──────────────────────────────────────────────  │
│       Spawn sub-agents for independent tasks                             │
│       Sequential for dependent tasks                                    │
│       Each task produces: code + unit tests + migration (if needed)     │
│         │                                                                │
│         ▼                                                                │
│  [4] CODE REVIEW ─────────────────────────────────────────────────────  │
│       Code-reviewer sub-agent reviews all changes                       │
│       Issues added to TODO.md as REVIEW type                            │
│         │                                                                │
│         ▼                                                                │
│  [5] STATIC ANALYSIS + SECURITY SCAN ────────────────────────────────   │
│       SpotBugs, Checkstyle, OWASP Dependency Check (backend)            │
│       ESLint, TypeScript strict, npm audit (frontend)                   │
│       Issues added to TODO.md as ANALYSIS or SECURITY type              │
│         │                                                                │
│         ▼                                                                │
│  [6] ISSUE RESOLUTION ────────────────────────────────────────────────  │
│       Fix all CRITICAL and HIGH issues from steps 4+5                   │
│       MEDIUM issues: fix if in changed code, else log and continue      │
│       LOW issues: log in TODO.md, continue                              │
│         │                                                                │
│         ▼                                                                │
│  [7] TEST EXECUTION ──────────────────────────────────────────────────  │
│       Run unit tests + integration tests for changed services           │
│       Run regression suite for affected areas                           │
│       Test failures → add to TODO.md → fix → re-run                    │
│         │                                                                │
│         ▼                                                                │
│  [8] DEPLOYMENT SCRIPTS ──────────────────────────────────────────────  │
│       Generate/update DB migration scripts                              │
│       Update CI/CD pipeline if needed                                   │
│       Update Kubernetes manifests if needed                             │
│         │                                                                │
│         ▼                                                                │
│  [9] SYNC BACK ───────────────────────────────────────────────────────  │
│       Update TODO.md: mark tasks complete                               │
│       If change alters requirements → update REQUIREMENT.md             │
│       If change alters architecture → update ARCHITECTURE.md            │
│       Add ADR entry if significant decision was made                    │
│         │                                                                │
│         ▼                                                                │
│  DONE ✓                                                                  │
└─────────────────────────────────────────────────────────────────────────┘
```

**NON-NEGOTIABLE RULES:**
- Never write code before checking TODO.md for related existing tasks.
- Never skip the code review phase, even for one-liners.
- Never deploy without passing all tests in the regression suite.
- Never close a task without updating TODO.md status.
- Never make an architectural change without updating ARCHITECTURE.md.
- Never add a new feature without cross-checking REQUIREMENT.md.

---

## 4. TODO.md — Task Management System

### 4.1 TODO.md Structure

```markdown
# TODO.md — SupportHub Task Tracker
Last Updated: {ISO timestamp}
Active Sprint: {sprint name}

## Summary
| Status | Count |
|--------|-------|
| 🆕 OPEN | N |
| 🔄 IN_PROGRESS | N |
| 🔍 IN_REVIEW | N |
| ⚠️ BLOCKED | N |
| ✅ DONE | N |
| ❌ CANCELLED | N |

---

## 🔴 CRITICAL / BLOCKING ISSUES
<!-- P0 issues that block deployment or are security vulnerabilities -->

## 🟠 HIGH PRIORITY
<!-- P1 issues, bugs, review findings that must be fixed before merge -->

## 🟡 MEDIUM PRIORITY
<!-- P2 improvements, tech debt, non-blocking issues -->

## 🟢 OPEN TASKS
<!-- Features, stories, sub-tasks in current sprint -->

## 🔄 IN PROGRESS
<!-- Tasks currently being worked on -->

## ✅ COMPLETED (Current Sprint)
<!-- Completed tasks, keep for sprint review -->

## 📋 BACKLOG
<!-- Future work, approved but not yet scheduled -->
```

### 4.2 Task Schema

Every task in TODO.md MUST have these fields:

```markdown
### [TYPE-NNN] Task Title
- **ID:** TYPE-NNN (e.g., FEAT-042, BUG-017, SEC-003, REVIEW-008, ANAL-012, TEST-006, DB-004, DEPLOY-002)
- **Type:** FEAT | BUG | SEC | REVIEW | ANAL | TEST | DB | DEPLOY | DOCS | ADR | INFRA
- **Priority:** P0-CRITICAL | P1-HIGH | P2-MEDIUM | P3-LOW
- **Status:** OPEN | IN_PROGRESS | IN_REVIEW | BLOCKED | DONE | CANCELLED
- **Owner:** lead | agent:code-reviewer | agent:security | agent:test-engineer | human
- **Service:** ticket-service | auth-service | ai-service | all | frontend:agent-dashboard | ...
- **Sprint:** sprint-1 | backlog
- **Blocked By:** [LIST-OF-TASK-IDs] or "none"
- **Blocks:** [LIST-OF-TASK-IDs] or "none"
- **REQUIREMENT Ref:** REQ-TICKET-CREATE-01 (from REQUIREMENT.md)
- **ARCHITECTURE Ref:** Section 7.2 (from ARCHITECTURE.md)
- **Created:** ISO timestamp
- **Updated:** ISO timestamp
- **Branch:** feature/TYPE-NNN-short-description

#### Description
What needs to be done and why.

#### Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2
- [ ] Unit tests written and passing
- [ ] Integration tests written and passing (if applicable)
- [ ] No new CRITICAL/HIGH issues from code review
- [ ] No new CRITICAL/HIGH issues from security scan
- [ ] Migration script created (if schema change)
- [ ] REQUIREMENT.md updated (if requirements changed)
- [ ] ARCHITECTURE.md updated (if architecture changed)

#### Implementation Notes
Any decisions made, alternatives considered, links to relevant docs.

#### Issues Found During Implementation
(Populated automatically by review/analysis sub-agents)
- [ ] REVIEW-NNN: Description

#### Test Results
- Unit: PASS/FAIL (N/N tests)
- Integration: PASS/FAIL (N/N tests)
- Regression: PASS/FAIL (N/N tests)
```

### 4.3 Task ID Ranges

```
FEAT-001 to FEAT-999    Feature implementation tasks
BUG-001 to BUG-999      Bug fixes
SEC-001 to SEC-999      Security issues (from scan or manual)
REVIEW-001 to REVIEW-999 Code review findings
ANAL-001 to ANAL-999    Static analysis findings
TEST-001 to TEST-999    Test failures or missing test coverage
DB-001 to DB-999        Database schema/migration tasks
DEPLOY-001 to DEPLOY-999 Deployment/infra tasks
DOCS-001 to DOCS-999    Documentation tasks
ADR-001 to ADR-999      Architecture Decision Records
INFRA-001 to INFRA-999  Infrastructure tasks
```

### 4.4 Task Status Flow

```
OPEN → IN_PROGRESS → IN_REVIEW → DONE
                  ↓            ↑
               BLOCKED ──────→ IN_PROGRESS (when unblocked)
                               
Any status → CANCELLED (with reason)
```

### 4.5 Parallel vs Sequential Task Rules

**Run in PARALLEL when ALL of these are true:**
- Tasks are in different services with no shared state
- No shared files (no merge conflict risk)
- Task B does not need output from Task A
- Clear file/package boundaries

**Run SEQUENTIALLY when ANY of these is true:**
- Task B depends on output of Task A (mark with `Blocked By`)
- Tasks touch the same file or database schema
- Tasks are in the same service with overlapping code
- Scope unclear (understand first, implement second)

**Example parallel group:**
```
FEAT-010: ticket-service controller layer   ← no dependency on FEAT-011
FEAT-011: auth-service OTP flow             ← no dependency on FEAT-010
FEAT-012: customer-sdk API client           ← no dependency on FEAT-010/011
```

**Example sequential chain:**
```
DB-001: Create tickets table migration     → must complete first
  └─ FEAT-001: TicketService.java          → depends on DB-001
       └─ TEST-001: TicketServiceTest.java  → depends on FEAT-001
            └─ DEPLOY-001: Update k8s      → depends on TEST-001 passing
```

---

## 5. Multi-Agent Team Architecture

### 5.1 Agent Roles

```
┌────────────────────────────────────────────────────────────────────────┐
│                      AGENT TEAM STRUCTURE                               │
│                                                                         │
│  LEAD AGENT (you / main session)                                        │
│    Reads prompt → decomposes into tasks → orchestrates sub-agents      │
│    Synthesizes results → updates TODO.md → reports back                │
│                                                                         │
│  SUB-AGENTS (spawned as needed, use git worktrees):                    │
│  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────────────┐  │
│  │  implementer-1   │  │  implementer-2   │  │  implementer-N        │  │
│  │  (backend svc)   │  │  (frontend)      │  │  (infra/other)        │  │
│  └─────────────────┘  └─────────────────┘  └───────────────────────┘  │
│  ┌─────────────────┐  ┌─────────────────┐  ┌───────────────────────┐  │
│  │  code-reviewer   │  │  security-analyst│  │  test-engineer        │  │
│  │  (after impl)    │  │  (parallel w/    │  │  (runs after impl,    │  │
│  │                  │  │   code review)   │  │   review, & fixes)    │  │
│  └─────────────────┘  └─────────────────┘  └───────────────────────┘  │
│  ┌─────────────────┐  ┌─────────────────┐                              │
│  │  db-migration    │  │  deployment      │                             │
│  │  agent           │  │  agent           │                             │
│  └─────────────────┘  └─────────────────┘                              │
└────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Agent Definitions

Each sub-agent is defined in `.claude/agents/{name}.md`. Their prompts include:

**implementer agent:**
```
You are a senior Java Spring Boot / React engineer working on the SupportHub project.
Your job is to implement the specific task described. 
ALWAYS read: CLAUDE.md, ARCHITECTURE.md section [X], TODO.md task [TASK-ID].
Follow ALL coding standards in CLAUDE.md sections 16-20.
Produce: implementation code + unit tests + migration scripts (if DB change).
When done: update TODO.md task status to IN_REVIEW, list files changed.
NEVER modify files outside your assigned service/module.
```

**code-reviewer agent:**
```
You are a senior code reviewer. Review all changes for the current task.
Check: correctness, ARCHITECTURE.md compliance, REQUIREMENT.md compliance,
code standards (CLAUDE.md sections 16-20), security anti-patterns,
test coverage, error handling, logging, and documentation.
Output: create REVIEW-NNN tasks in TODO.md for each issue found.
Severity: CRITICAL (blocks merge) | HIGH (fix before merge) | MEDIUM | LOW.
Focus: logic errors > security > performance > style.
Do NOT fix issues yourself. Report only.
```

**security-analyst agent:**
```
You are an application security engineer.
Scan all changed code for: injection vulnerabilities, insecure JWT handling,
missing auth checks, PII exposure risks, insecure dependencies (OWASP),
hardcoded secrets, insecure random, missing rate limiting, CORS issues.
Reference CLAUDE.md section 22 for security standards.
Output: create SEC-NNN tasks in TODO.md for each finding.
CVSS severity: CRITICAL | HIGH | MEDIUM | LOW | INFO.
```

**test-engineer agent:**
```
You are a QA engineer. Your job is to run and validate all tests.
Run: unit tests → integration tests → regression tests for affected areas.
For test failures: create TEST-NNN tasks in TODO.md with: test name,
failure message, stack trace snippet, likely root cause, suggested fix.
Also check: coverage >= 80% for service layer, 70% overall.
If coverage drops: create TEST-NNN task flagging the gap.
```

**db-migration-agent:**
```
You are a database engineer. Review all schema changes.
Validate: all new tables have tenant_id, all Flyway scripts are idempotent,
RLS policies are correct, indexes are created for FK and filter columns,
migration is reversible (has DOWN script or rollback plan).
Create DB-NNN issues in TODO.md for any problems.
```

### 5.3 Agent Spawning Protocol

When spawning any sub-agent, ALWAYS include these four components in the spawn prompt:

```
1. CONTEXT: "This is the SupportHub project. Read CLAUDE.md, 
   ARCHITECTURE.md section [X], REQUIREMENT.md section [Y]."
   
2. TASK: "Your specific task is: [TASK-ID] — [task title].
   See TODO.md for full task details."
   
3. SCOPE: "You are responsible for ONLY: [specific files/packages].
   Do NOT touch: [out-of-scope files]."
   
4. OUTPUT: "When complete, you MUST:
   - Update TODO.md: set task [TASK-ID] status to [expected status]
   - List all files created/modified
   - Record test results in task [TASK-ID]
   - Raise any blocking issues as new tasks in TODO.md"
```

---

## 6. Sub-Agent Routing Rules

Use this decision framework for EVERY delegation:

### Parallel Dispatch (run all simultaneously)
All conditions must be met:
- [ ] 3+ unrelated tasks OR tasks clearly in different domains/services
- [ ] No shared files or database tables
- [ ] No output dependency between tasks
- [ ] Clear, non-overlapping file boundaries

### Sequential Dispatch (run one after another, B blocked by A)
Any one condition triggers sequential:
- [ ] Task B needs schema/code/data from Task A
- [ ] Tasks share the same files or DB schema
- [ ] Unclear scope on a task (clarify FIRST, then implement)
- [ ] Integration test can only run after all implementation tasks complete

### Background Dispatch (non-blocking research/analysis)
- Security scan (runs in background while code review runs)
- Static analysis (runs in background while tests run)
- Documentation updates (run after all code is merged)
- REQUIREMENT.md/ARCHITECTURE.md updates (run after task completion)

### Never Parallelize
- DB migrations (always sequential to avoid conflicts)
- Deployments (always sequential: dev → staging → prod)
- Task that modifies TODO.md simultaneously (one writer at a time)

---

## 7. Phase 1: Requirements & Task Decomposition

**Trigger:** Any new prompt or change request received.

### 7.1 Alignment Check (MANDATORY)

Before creating any task, answer these questions:

```
Q1: Does this change align with REQUIREMENT.md?
    - Find the relevant REQ-* identifier
    - If REQ exists: reference it in the task
    - If REQ is new/unclear: add to REQUIREMENT.md FIRST, then create task
    - If REQ contradicts: raise to human (add DOCS task, block implementation)

Q2: Does this change align with ARCHITECTURE.md?
    - Find the relevant section
    - If it follows existing patterns: proceed
    - If it deviates: create ADR task first, get decision, then implement

Q3: Does this change affect existing tasks in TODO.md?
    - Search TODO.md for related tasks
    - If related OPEN task exists: extend it rather than creating duplicate
    - If related IN_PROGRESS task exists: coordinate with its owner

Q4: Is there an existing implementation to understand first?
    - Read existing code in the affected service before writing new code
    - Check existing tests to understand current behavior
```

### 7.2 Task Creation Protocol

For each unit of work identified:

```markdown
1. Assign a Task ID (next available in the appropriate range)
2. Set Type: FEAT for new features, BUG for fixes, DB for schema changes, etc.
3. Set Priority based on impact:
   - P0: Blocks other work, production broken, security critical
   - P1: Required for sprint goal, user-facing bug
   - P2: Improves quality, tech debt, non-blocking bug
   - P3: Nice to have, documentation, minor polish
4. Identify dependencies: what must be done FIRST?
5. Identify parallelism: what CAN be done simultaneously?
6. Reference REQUIREMENT.md + ARCHITECTURE.md sections
7. Write Acceptance Criteria (specific, measurable, testable)
8. Set initial Status: OPEN
9. Assign to appropriate owner/agent
```

### 7.3 Task Decomposition Template

For any prompt, decompose into this structure:

```
Prompt: "Add the ticket creation flow"

Decomposition:
  Sequential group A (foundation):
    DB-001: Create Flyway migration for tickets table (db-migration-agent)
    DB-002: Create Flyway migration for ticket_activities table (db-migration-agent)
  
  Sequential group B (depends on A):
    Parallel sub-group B1 (all can run simultaneously after A completes):
      FEAT-001: TicketRepository + TicketActivityRepository (implementer-1)
      FEAT-002: TicketCategory + TicketSubCategory domain + repos (implementer-2)
      FEAT-003: SLA engine (implementer-3)
    
  Sequential group C (depends on B1):
    FEAT-004: TicketService core (create, update, status transition) (implementer-1)
    FEAT-005: TicketController REST endpoints (implementer-1, after FEAT-004)
  
  Parallel group D (after FEAT-005 complete, run all simultaneously):
    REVIEW-auto: Code review of FEAT-001 to FEAT-005 (code-reviewer)
    SEC-auto: Security scan of FEAT-001 to FEAT-005 (security-analyst)
    TEST-auto: Run and validate unit + integration tests (test-engineer)
```

---

## 8. Phase 2: Implementation Standards

### 8.1 MANDATORY Pre-Implementation Checklist

Before writing any code, verify:
- [ ] Task exists in TODO.md with status IN_PROGRESS
- [ ] Branch created: `git checkout -b feature/TASK-ID-short-description`
- [ ] NEVER commit directly to `main` or `develop`
- [ ] Relevant REQUIREMENT.md sections re-read
- [ ] Relevant ARCHITECTURE.md sections re-read
- [ ] Existing code in the service read and understood
- [ ] Existing tests understood (know what passes today)

### 8.2 Implementation Outputs (ALL required per task)

Every completed implementation task MUST produce:
1. **Source code** — in the correct package per ARCHITECTURE.md
2. **Unit tests** — `*Test.java` or `*.test.tsx`, same package as source
3. **Integration tests** — for any new API endpoint or Kafka flow
4. **Flyway migration** — if schema changed (versioned, idempotent)
5. **OpenAPI annotation updates** — for any REST controller change
6. **Logback/structured log statements** — for key business events
7. **Micrometer metric counters/timers** — for key operations

### 8.3 Implementation Output Structure

For each service, files MUST be placed in the correct locations:

```
# Backend (Java Spring Boot)
Source:      backend/{service}/src/main/java/in/supporthub/{service}/
Tests:       backend/{service}/src/test/java/in/supporthub/{service}/
Migrations:  backend/{service}/src/main/resources/db/migration/
Config:      backend/{service}/src/main/resources/application.yml

# Frontend (React/TypeScript)
Source:      frontend/apps/{app}/src/
Tests:       frontend/apps/{app}/src/**/__tests__/ or *.test.tsx
SDK:         frontend/packages/customer-sdk/src/

# Infrastructure
K8s:         infrastructure/k8s/base/{service}/
Dockerfile:  infrastructure/docker/{service}/Dockerfile
```

---

## 9. Phase 3: Testing Protocol

### 9.1 Test Types Required

| Test Type | When Required | Tool | Pass Threshold |
|---|---|---|---|
| Unit Tests | Every task with logic | JUnit 5 + Mockito | 100% of new code |
| Integration Tests | Every new API endpoint or Kafka consumer | @SpringBootTest + Testcontainers | All new endpoints |
| Contract Tests | Every inter-service REST call | Spring Cloud Contract | All contracts |
| E2E Tests | New user-facing flow (critical paths only) | Playwright | Critical paths |
| Migration Tests | Every Flyway script | Testcontainers + Flyway | Script runs cleanly |
| Security Tests | Every auth/authz change | OWASP ZAP (baseline) | No new CRITICAL/HIGH |
| Load Tests | New endpoints serving >1000 RPM | Gatling | P95 < 300ms at 100 RPS |

### 9.2 Test Execution Order (MUST follow this order)

```
1. Unit tests       → must all pass before proceeding
2. DB migrations    → run migration on fresh Testcontainer DB
3. Integration tests → must all pass before proceeding
4. Contract tests   → must all pass before proceeding
5. Security scan    → no new CRITICAL/HIGH (can run parallel with steps 3-4)
6. Regression suite → run affected service's full test suite
7. E2E tests        → for user-facing flows only
```

### 9.3 Test Failure Protocol

When any test fails:
1. Create `TEST-NNN` task in TODO.md immediately
2. Set Priority: P0 if blocking deployment, P1 otherwise
3. Include in the task: test name, full error message, stack trace, likely cause
4. Do NOT skip or comment out failing tests
5. Do NOT proceed to deployment until P0/P1 test failures are fixed
6. Fix the issue (not the test) — unless the test itself is wrong

### 9.4 Testcontainers Configuration (reuse across services)

```java
// In backend/shared/src/test/java/in/supporthub/shared/test/
@TestConfiguration
public class TestContainersConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("supporthub_test")
            .withUsername("test")
            .withPassword("test");
    }

    @Bean
    @ServiceConnection
    public MongoDBContainer mongo() {
        return new MongoDBContainer("mongo:7-jammy");
    }

    @Bean
    @ServiceConnection
    public RedisContainer redis() {
        return new RedisContainer("redis:7-alpine");
    }

    @Bean
    @ServiceConnection
    public KafkaContainer kafka() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));
    }
}
```

### 9.5 Test Coverage Enforcement

```xml
<!-- In each service pom.xml -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <rules>
            <rule>
                <element>PACKAGE</element>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <value>COVEREDRATIO</value>
                        <minimum>0.80</minimum>  <!-- 80% for service layer -->
                    </limit>
                </limits>
                <includes>
                    <include>in/supporthub/*/service/**</include>
                </includes>
            </rule>
        </rules>
    </configuration>
</plugin>
```

---

## 10. Phase 4: Code Review Protocol

### 10.1 Code Review Trigger

Code review sub-agent is spawned AUTOMATICALLY after every implementation task completes (or batch of parallel implementation tasks).

### 10.2 Code Review Checklist (reviewer MUST check all)

**Correctness:**
- [ ] Logic matches REQUIREMENT.md acceptance criteria
- [ ] Status transition rules (Ticket, etc.) correctly implemented
- [ ] Edge cases handled (null, empty, boundary values)
- [ ] Error handling: all exceptions caught or declared, meaningful messages
- [ ] No TODO/FIXME comments left in production code

**Architecture Compliance:**
- [ ] Package structure matches ARCHITECTURE.md section 7.x
- [ ] Service communicates via correct pattern (REST or Kafka, not both for same operation)
- [ ] No cross-service database queries (each service owns its data)
- [ ] Tenant context propagated on all requests
- [ ] Shared library used for shared DTOs/events, not duplicated

**Security:**
- [ ] All endpoints require authentication (unless explicitly public)
- [ ] Role checks present for admin/agent-only endpoints
- [ ] No PII in logs (phone numbers, emails, names must be masked)
- [ ] No secrets/credentials hardcoded
- [ ] Input validated with `@Valid` on all request bodies
- [ ] SQL queries are parameterized (no string concatenation with user input)

**Performance:**
- [ ] No N+1 queries (use JOIN FETCH or batch load)
- [ ] Cache used where appropriate (tenant config, categories, etc.)
- [ ] Async/Kafka used for cross-service state changes (not synchronous REST)
- [ ] DB indexes exist for all filter columns used in queries

**Testing:**
- [ ] Unit tests cover happy path, error path, and boundary conditions
- [ ] Integration tests for every public REST endpoint
- [ ] No production code changed without corresponding test change
- [ ] Test data uses builders/fixtures, no hardcoded magic values

**Code Quality:**
- [ ] Methods < 30 lines (SRP — single responsibility)
- [ ] No magic numbers/strings (use constants or enums)
- [ ] Proper use of Java Records for immutable DTOs
- [ ] Meaningful variable/method names (no `a`, `b`, `temp`, `data`)
- [ ] No unnecessary dependencies injected

### 10.3 Review Issue Severity

```
CRITICAL: Must fix before ANY merge. Examples:
  - Authentication bypass
  - SQL injection vector
  - NPE in production code path
  - Wrong status transition allowing illegal state
  - PII leak to logs

HIGH: Must fix before merge to main. Examples:
  - Missing error handling on external API call
  - N+1 query on list endpoint
  - Missing role check on sensitive endpoint
  - Test coverage < 80% for service layer

MEDIUM: Fix before next sprint. Examples:
  - Code duplication > 10 lines
  - Magic number/string not extracted to constant
  - Log message missing context (no ticketId, tenantId)
  - Method > 30 lines

LOW: Log and track. Examples:
  - Naming could be clearer
  - Comment missing on complex logic
  - Minor formatting inconsistency
```

---

## 11. Phase 5: Static Analysis & Security Scan

### 11.1 Backend Analysis Tools

Run after every implementation batch:

```bash
# From backend/ directory

# 1. Checkstyle (Google Java Style + custom rules)
./mvnw checkstyle:check -pl {service-name}

# 2. SpotBugs (static bug detection)
./mvnw spotbugs:check -pl {service-name}

# 3. PMD (code quality)
./mvnw pmd:check -pl {service-name}

# 4. OWASP Dependency Check (CVE scan)
./mvnw dependency-check:check -pl {service-name}

# 5. All together (parallel)
./mvnw verify -pl {service-name} -P analysis

# Output: backend/{service}/target/reports/
```

### 11.2 Frontend Analysis Tools

```bash
# From frontend/ directory

# TypeScript strict check
npx tsc --noEmit -p apps/{app}/tsconfig.json

# ESLint
npx eslint apps/{app}/src --max-warnings 0

# npm audit (dependency CVEs)
npm audit --audit-level=high -w apps/{app}

# Bundle size check
npx bundlesize -c bundlesize.config.json
```

### 11.3 Analysis Issue Severity Mapping

```
OWASP CVE CRITICAL (CVSS 9-10)   → SEC-NNN, P0-CRITICAL, blocks deployment
OWASP CVE HIGH (CVSS 7-8.9)      → SEC-NNN, P1-HIGH, must fix before merge
SpotBugs SCARY/HIGH               → ANAL-NNN, P1-HIGH
SpotBugs TROUBLING/MEDIUM         → ANAL-NNN, P2-MEDIUM
Checkstyle violations              → ANAL-NNN, P3-LOW (unless blocking)
npm audit critical                 → SEC-NNN, P0-CRITICAL
npm audit high                     → SEC-NNN, P1-HIGH
```

### 11.4 Analysis Issue Task Format

```markdown
### [SEC-042] CVE-2024-XXXXX in dependency jackson-databind 2.15.0
- **ID:** SEC-042
- **Type:** SEC
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Owner:** implementer
- **Service:** ticket-service
- **CVSS Score:** 7.5 (HIGH)
- **CVE:** CVE-2024-XXXXX
- **Affected Library:** com.fasterxml.jackson.core:jackson-databind:2.15.0
- **Fix:** Upgrade to 2.17.x
- **Found By:** OWASP Dependency Check
- **Created:** 2026-03-23T10:00:00Z
```

---

## 12. Phase 6: Issue Resolution

### 12.1 Issue Triage Priority

Process issues in this strict order:
1. **SEC CRITICAL (P0)** — fix immediately, block everything else
2. **REVIEW CRITICAL (P0)** — fix immediately
3. **TEST failures (P0/P1)** — fix before any other merge
4. **SEC HIGH (P1)** — fix before merge
5. **REVIEW HIGH (P1)** — fix before merge
6. **ANAL HIGH (P1)** — fix before merge
7. **MEDIUM issues** — fix if in changed code, else log and schedule
8. **LOW issues** — log in TODO.md, move to backlog

### 12.2 Fix Protocol

For each issue being fixed:
1. Set issue task status to IN_PROGRESS in TODO.md
2. Create a sub-task if the fix is complex (> 30 min estimate)
3. Fix the code (not the test or the analysis rule)
4. Run the specific test that was failing to confirm fix
5. Re-run the analysis tool to confirm issue is resolved
6. Update issue task status to DONE in TODO.md
7. Add fix description to the task Implementation Notes

### 12.3 Exception Process (for issues that WON'T be fixed now)

For a MEDIUM or LOW issue that will be deferred:
1. Keep the task in TODO.md with status OPEN
2. Move to appropriate priority queue
3. Add a comment: "Deferred because: [reason]. Scheduled for: [sprint/date]"
4. **NEVER defer CRITICAL or HIGH issues**

---

## 13. Phase 7: Regression & Integration Testing

### 13.1 Regression Scope Determination

When a service changes, run regression for ALL of the following:
- The changed service's full test suite
- Any service that calls the changed service via REST (downstream)
- Any service that consumes Kafka events from the changed service

```
Example: ticket-service changes
  → Run: ticket-service full test suite
  → Run: mcp-server tests (calls ticket-service)
  → Run: notification-service tests (consumes ticket.events)
  → Run: reporting-service tests (consumes ticket.events)
  → Run: ai-service tests (consumes ticket.events)
```

### 13.2 Critical Path E2E Tests (MUST pass before any deployment)

These Playwright E2E tests cover the system's most critical paths:

```
e2e/tests/
├── customer/
│   ├── ticket-creation-flow.spec.ts       # Create ticket → receive SMS → view in portal
│   ├── ticket-detail-and-reply.spec.ts    # View ticket, add comment
│   └── faq-self-resolve.spec.ts           # FAQ search → self-resolve → no ticket created
├── agent/
│   ├── ticket-resolution-flow.spec.ts     # Receive ticket → respond → resolve → CSAT
│   ├── ticket-assignment.spec.ts          # Assign/reassign tickets
│   └── ai-suggestion-panel.spec.ts        # AI suggestions load and can be applied
└── admin/
    ├── category-management.spec.ts        # Create/edit categories
    └── agent-management.spec.ts           # Create agent account
```

### 13.3 Pre-Deployment Gate

ALL of these MUST be green before any deployment proceeds:

```
GATE CHECKLIST (automated, blocks deployment if any fail):
  [ ] All unit tests pass (0 failures)
  [ ] All integration tests pass (0 failures)
  [ ] All contract tests pass (0 failures)
  [ ] All critical-path E2E tests pass (0 failures)
  [ ] Code coverage >= 80% (service layer) - 70% overall
  [ ] 0 CRITICAL security issues (OWASP + SpotBugs)
  [ ] 0 HIGH security issues unresolved
  [ ] 0 open P0 tasks in TODO.md for this change
  [ ] 0 open P1 REVIEW issues for this change
  [ ] All DB migrations run successfully (Testcontainer validation)
  [ ] All TODO.md tasks for this feature/fix marked DONE or explicitly deferred
```

---

## 14. Phase 8: Deployment Pipeline

### 14.1 Deployment Environments (ALWAYS in this order)

```
dev → staging → prod

NEVER skip dev → staging → prod order.
NEVER deploy to prod without staging validation.
```

### 14.2 Deployment Checklist per Environment

```markdown
## Pre-Deployment (all environments)
- [ ] Pre-deployment gate (Section 13.3) passed
- [ ] DEPLOY-NNN task created in TODO.md
- [ ] Docker image built and tagged: {service}:{version}
- [ ] Docker image security scanned (Trivy): no CRITICAL CVEs
- [ ] Kubernetes manifests updated (resource limits, env vars)
- [ ] Secrets updated in AWS Secrets Manager (if needed)
- [ ] DB migration script reviewed by db-migration-agent
- [ ] Rollback plan documented in DEPLOY-NNN task

## Database Migration (run BEFORE deploying new service version)
- [ ] Run Flyway migration on target DB
- [ ] Verify migration completes without error
- [ ] Verify existing data is intact (spot check query)

## Service Deployment
- [ ] Apply Kubernetes deployment manifest
- [ ] Watch rollout: kubectl rollout status deployment/{service}
- [ ] Verify health probe passes: /actuator/health returns 200
- [ ] Verify readiness probe passes: /actuator/health/readiness returns 200
- [ ] Smoke test: manually call 1-2 key endpoints

## Post-Deployment
- [ ] Monitor error rate (5 minutes): < 1% HTTP 5xx
- [ ] Monitor P95 latency (5 minutes): < 300ms
- [ ] Check Kafka consumer lag: no spike
- [ ] Check Langfuse: AI calls working if AI service deployed
- [ ] Update TODO.md DEPLOY-NNN task to DONE
```

### 14.3 Rollback Protocol

If any post-deployment check fails:
```bash
# Immediate rollback
kubectl rollout undo deployment/{service-name} -n supporthub-{env}

# Rollback DB migration (if needed — use DOWN migration)
./mvnw flyway:undo -pl {service} -Dflyway.url={db-url}

# Update TODO.md
# Set DEPLOY-NNN status to BLOCKED
# Create BUG-NNN with full deployment failure details
# Set BUG-NNN to P0-CRITICAL
```

### 14.4 CI/CD Pipeline Structure

```yaml
# .github/workflows/ci.yml — auto-triggered on every PR and push

name: SupportHub CI/CD
on:
  pull_request:
    branches: [develop, main]
  push:
    branches: [develop]
    tags: ['v*.*.*']

jobs:
  # PHASE 1: Quality Gates (parallel)
  unit-tests:         runs-on: ubuntu-latest
  integration-tests:  runs-on: ubuntu-latest, needs: [unit-tests]
  security-scan:      runs-on: ubuntu-latest           # parallel with integration
  code-analysis:      runs-on: ubuntu-latest           # parallel with integration
  
  # PHASE 2: Build (after quality gates)
  build-images:       needs: [integration-tests, security-scan, code-analysis]
  
  # PHASE 3: Deploy dev (auto on develop push)
  deploy-dev:         needs: [build-images], if: branch == develop
  
  # PHASE 4: E2E (after dev deploy)
  e2e-tests:          needs: [deploy-dev]
  
  # PHASE 5: Deploy staging (auto after E2E pass)
  deploy-staging:     needs: [e2e-tests]
  
  # PHASE 6: Deploy prod (manual approval gate, only on version tag)
  deploy-prod:        needs: [deploy-staging], if: startsWith(github.ref, 'refs/tags/v')
                      environment: production  # requires manual approval in GitHub
```

### 14.5 DB Migration Script Standards

```sql
-- File naming: V{version}__{description}.sql
-- Example: V7__add_sentiment_columns_to_tickets.sql

-- MUST include: version, description, author, date, change summary
-- MUST be idempotent: safe to run twice without error
-- MUST have a corresponding DOWN script or rollback note

-- ============================================================
-- Migration: V7__add_sentiment_columns_to_tickets
-- Author: implementer-1
-- Date: 2026-03-23
-- Task: FEAT-023
-- Description: Add sentiment_score, sentiment_label columns for AI analysis
-- Rollback: See V7__rollback_sentiment_columns.sql
-- ============================================================

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS sentiment_score     FLOAT,
    ADD COLUMN IF NOT EXISTS sentiment_label     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS sentiment_updated_at TIMESTAMPTZ;

-- Index for filtering by sentiment in agent dashboard
CREATE INDEX IF NOT EXISTS idx_tickets_sentiment_label
    ON tickets(tenant_id, sentiment_label)
    WHERE sentiment_label IS NOT NULL;

COMMENT ON COLUMN tickets.sentiment_score IS 'AI-computed sentiment: -1.0 (very_negative) to 1.0 (very_positive)';
COMMENT ON COLUMN tickets.sentiment_label IS 'very_negative | negative | neutral | positive | very_positive';
```

---

## 15. Phase 9: Documentation & Sync Back

### 15.1 REQUIREMENT.md Sync Protocol

**When to update REQUIREMENT.md:**
- A new requirement was discovered during implementation
- An existing requirement was clarified or modified
- A feature was implemented differently than specified (and the change is accepted)
- A new edge case or constraint was discovered

**How to update:**
1. Create `DOCS-NNN` task in TODO.md
2. Identify the relevant REQ-* section
3. Update the requirement text (do NOT delete old text — use strikethrough and add "Updated:" note)
4. Add a changelog entry at the bottom of REQUIREMENT.md
5. Update TODO.md task to DONE

### 15.2 ARCHITECTURE.md Sync Protocol

**When to update ARCHITECTURE.md:**
- A new service was added
- A service's responsibility changed
- A new data model was added or changed
- A new Kafka topic was added
- A technology choice was changed or added
- A new significant pattern was adopted
- An ADR was made

**How to update:**
1. Create `DOCS-NNN` task (or `ADR-NNN` for decision records)
2. Update the relevant section in ARCHITECTURE.md
3. Add ADR entry in `docs/adr/ADR-NNN-title.md` for significant decisions
4. Commit ARCHITECTURE.md change in the same PR as the code change

### 15.3 ADR Template

```markdown
# ADR-NNN: {Decision Title}

**Date:** 2026-03-23
**Status:** Accepted | Proposed | Deprecated | Superseded by ADR-XXX
**Deciders:** {who made this decision}
**Task Refs:** FEAT-NNN, BUG-NNN

## Context
What situation forced us to make a decision?

## Decision
What did we decide? Be specific.

## Consequences
**Positive:**
- ...

**Negative (accepted trade-offs):**
- ...

## Alternatives Considered
| Alternative | Why Rejected |
|---|---|
| ... | ... |
```

### 15.4 TODO.md Cleanup Protocol

At the end of every sprint (or after every significant feature):
1. Mark all completed tasks as DONE with completion timestamp
2. Move all DONE tasks to "Completed" section
3. Review BLOCKED tasks: are they still blocked? Unblock or cancel
4. Move all OPEN tasks that are not in current sprint to BACKLOG
5. Update the Summary table counts
6. Commit TODO.md to version control

---

## 16. Java Spring Boot Coding Standards

### 16.1 Project & Module Conventions

```
Package naming:     in.supporthub.{service}.{layer}
Service artifact:   supporthub-{service-name}
Java version:       Java 21 (LTS)
Spring Boot:        3.3.x
Spring Cloud:       2023.0.x
Build:              Maven (pom.xml), NOT Gradle
Virtual threads:    spring.threads.virtual.enabled=true in ALL services
```

### 16.2 Layer Conventions

```
Controller  → validates input, delegates to service, maps to DTO response
Service     → owns all business logic, calls repository, publishes events
Repository  → data access ONLY (JPA, Redis, MongoDB), no business logic
Domain      → JPA entities, value objects, enums — NO service dependencies
DTO         → Java Records for immutable request/response objects
Event       → Java Records for Kafka events (in shared module)
Config      → @Configuration classes only
```

### 16.3 Required Annotations & Patterns

```java
// Controllers MUST have:
@RestController
@RequestMapping("/api/v1/{resource}")
@Tag(name = "Ticket API", description = "...")  // OpenAPI
@Slf4j                                           // Lombok logging
@RequiredArgsConstructor                         // Lombok constructor injection

// Services MUST have:
@Service
@Transactional  // class-level for mutation services
@Slf4j
@RequiredArgsConstructor

// Entities MUST have:
@Entity
@Table(name = "table_name")
@DynamicUpdate    // only update changed fields
// All entities need: id, tenantId, createdAt, updatedAt

// All request bodies MUST be validated:
@PostMapping
public ResponseEntity<...> create(@Valid @RequestBody CreateRequest request) { ... }
```

### 16.4 Logging Standards

```java
// ALWAYS include: tenantId, relevant entity ID, action
// NEVER log: phone numbers, emails, passwords, PII fields

// ✅ CORRECT
log.info("Ticket created: ticketId={}, tenantId={}, customerId={}, category={}",
    ticket.getId(), tenantId, customerId, category.getSlug());

// ✅ CORRECT for errors
log.error("Failed to create ticket: tenantId={}, error={}", tenantId, ex.getMessage(), ex);

// ❌ WRONG — PII in log
log.info("Ticket created for customer phone: {}", customer.getPhone());

// ❌ WRONG — no context
log.info("Ticket created");

// ❌ WRONG — string concatenation (use parameterized)
log.info("Ticket created: " + ticket.getId());
```

### 16.5 Error Handling

```java
// Global exception handler in shared module:
// in.supporthub.shared.exception.GlobalExceptionHandler

// Define custom exceptions:
public class TicketNotFoundException extends AppException {
    public TicketNotFoundException(String ticketNumber) {
        super("TICKET_NOT_FOUND", "Ticket not found: " + ticketNumber, HttpStatus.NOT_FOUND);
    }
}

// In services: throw typed exceptions, NEVER catch and swallow
public Ticket getByNumber(String tenantId, String ticketNumber) {
    return ticketRepository.findByTenantIdAndTicketNumber(tenantId, ticketNumber)
        .orElseThrow(() -> new TicketNotFoundException(ticketNumber));
}

// HTTP 4xx errors: client's fault → INFO log
// HTTP 5xx errors: our fault → ERROR log with stack trace
```

### 16.6 Tenant Context (MANDATORY in every service call)

```java
// TenantContextHolder is set by TenantContextFilter before any business logic runs.
// Services MUST use it, NEVER accept tenantId as a user-controlled parameter directly.

// ✅ CORRECT
String tenantId = TenantContextHolder.getTenantId();  // from filter, validated by gateway

// ❌ WRONG — user can spoof tenantId
public ResponseEntity<?> getTicket(@RequestParam String tenantId, ...) { ... }
```

### 16.7 Kafka Event Publishing

```java
// Use the shared TicketEventPublisher, never publish raw Kafka messages in business code.
// ALWAYS use the event records from supporthub-shared.

// ✅ CORRECT
ticketEventPublisher.publishTicketCreated(
    new TicketCreatedEvent(
        UUID.randomUUID().toString(),  // eventId
        tenantId,
        ticket.getId().toString(),
        ticket.getTicketNumber(),
        ...
    )
);

// ❌ WRONG — raw Kafka template in service
kafkaTemplate.send("ticket.events", rawJson);
```

### 16.8 No-Go List (things that MUST NEVER appear in code)

```
❌ System.out.println()           → use @Slf4j log.info()
❌ e.printStackTrace()            → use log.error("...", e)
❌ Thread.sleep() in production   → use Kafka or scheduled tasks
❌ @Autowired on field            → use constructor injection (@RequiredArgsConstructor)
❌ EntityManager.createNativeQuery() with user input  → SQL injection risk
❌ String.format("SELECT ... " + userInput)           → SQL injection risk
❌ ObjectMapper new ObjectMapper()                    → inject @Autowired ObjectMapper
❌ hardcoded "localhost" or IP     → use config properties
❌ hardcoded API keys / passwords  → use AWS Secrets Manager or env vars
❌ @SuppressWarnings("unchecked") without comment explaining why
❌ TODO/FIXME in merged code       → create a TODO.md task instead
❌ Returning null from a public service method        → use Optional<T>
❌ Catching Exception broadly and swallowing it       → catch specific, rethrow or log
```

---

## 17. React / TypeScript Frontend Standards

### 17.1 TypeScript Strict Mode (MANDATORY)

```json
// All tsconfig.json files MUST include:
{
  "compilerOptions": {
    "strict": true,
    "noImplicitAny": true,
    "strictNullChecks": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true
  }
}
```

### 17.2 Component Standards

```typescript
// ✅ CORRECT: Typed props, named export, no `any`
interface TicketCardProps {
  ticket: Ticket;
  onAssign: (agentId: string) => void;
  isLoading?: boolean;
}

export function TicketCard({ ticket, onAssign, isLoading = false }: TicketCardProps) {
  // ...
}

// ❌ WRONG: No types, default export from component file
export default function TicketCard(props: any) { ... }
```

### 17.3 State Management Rules

```
Local component state:   useState / useReducer (UI-only state)
Server state:            TanStack Query (@tanstack/react-query) — NEVER local useState for API data
Global UI state:         Zustand (auth, notifications, filter preferences)
Form state:              React Hook Form
URL state:               useSearchParams (for filter/pagination state)

NEVER use:
  ❌ Redux / Redux Toolkit (use Zustand instead)
  ❌ Context API for frequently-updating data (performance)
  ❌ useState for server data (use TanStack Query)
```

### 17.4 API Call Standards (TanStack Query)

```typescript
// ✅ CORRECT: typed query, error handling, loading state
export function useTicketDetail(ticketNumber: string) {
  return useQuery<TicketDetail, ApiError>({
    queryKey: ['ticket', ticketNumber],
    queryFn: () => ticketApi.getByNumber(ticketNumber),
    staleTime: 30_000,
    retry: (failureCount, error) => {
      if (error.status === 404 || error.status === 403) return false;
      return failureCount < 2;
    },
  });
}

// ✅ CORRECT: mutation with optimistic update
export function useAddComment(ticketId: string) {
  const queryClient = useQueryClient();
  return useMutation<Activity, ApiError, AddCommentRequest>({
    mutationFn: (req) => ticketApi.addComment(ticketId, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticket', ticketId, 'activities'] });
    },
  });
}
```

### 17.5 No-Go List (Frontend)

```
❌ console.log() in production code
❌ any type (use unknown or proper type)
❌ Direct DOM manipulation (use React state)
❌ localStorage / sessionStorage in artifacts (use React state)
❌ Inline styles (use Tailwind utility classes)
❌ Hardcoded API URLs (use environment variables)
❌ Unhandled promise rejections (always .catch() or try/catch in async)
❌ useEffect with missing dependencies (follow exhaustive-deps rule)
❌ key={index} in lists (use stable entity IDs)
```

---

## 18. Database & Migration Standards

### 18.1 Schema Rules

```sql
-- EVERY table MUST have:
id          UUID PRIMARY KEY DEFAULT gen_random_uuid()
tenant_id   UUID NOT NULL REFERENCES tenants(id)  -- for multi-tenancy
created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()

-- EVERY table MUST have Row-Level Security enabled:
ALTER TABLE {table_name} ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON {table_name}
    USING (tenant_id = current_setting('app.current_tenant', true)::uuid);

-- EVERY FK column MUST have an index:
CREATE INDEX idx_{table}_{fk_col} ON {table}({fk_col});

-- EVERY common filter column MUST have an index:
CREATE INDEX idx_{table}_{filter_col} ON {table}(tenant_id, {filter_col});
```

### 18.2 Migration File Rules

```
Naming:       V{N}__{description_with_underscores}.sql
             N is sequential integer, never reuse
Location:     backend/{service}/src/main/resources/db/migration/
Idempotent:   Use IF EXISTS / IF NOT EXISTS
Reversible:   Document rollback in comments or create rollback script
No data loss: Never DROP COLUMN without a deprecation period
```

### 18.3 Forbidden DB Operations

```
❌ DROP TABLE without explicit human approval task in TODO.md
❌ DROP COLUMN (mark as deprecated, anonymize data first)
❌ TRUNCATE in production migrations
❌ Cross-schema queries (each service owns its tables)
❌ Stored procedures with business logic (business logic in Java only)
❌ Non-idempotent migrations (will fail on retry)
```

---

## 19. Kafka Event Standards

### 19.1 Topic Naming

```
Format: {domain}.{event-type-kebab-case}
Examples:
  ticket.created
  ticket.status-changed
  ticket.activity-added
  ai.sentiment-analysis-completed
  faq.content-updated
```

### 19.2 Event Record Schema

```java
// All events MUST extend this pattern (in supporthub-shared):
public record {EventName}(
    String eventId,          // UUID, for idempotency
    String eventType,        // e.g. "ticket.created"
    String tenantId,         // for routing and filtering
    String correlationId,    // for distributed tracing (pass-through from request)
    Instant occurredAt,      // when the event happened, NOT when published
    String schemaVersion,    // "1.0" — increment on breaking changes
    {EventPayload} payload   // typed payload record
) {}
```

### 19.3 Consumer Idempotency Rule

Every Kafka consumer MUST be idempotent:
```java
// Check if event was already processed before doing work:
@KafkaListener(topics = "ticket.created")
public void handleTicketCreated(TicketCreatedEvent event) {
    // Idempotency check
    if (processedEventCache.contains(event.eventId())) {
        log.debug("Skipping duplicate event: eventId={}", event.eventId());
        return;
    }
    // ... process
    processedEventCache.put(event.eventId(), true, Duration.ofHours(24));
}
```

---

## 20. API Design Standards

### 20.1 URL Structure

```
/api/v1/{resource}                    GET (list), POST (create)
/api/v1/{resource}/{id}               GET (get one), PUT (update), DELETE
/api/v1/{resource}/{id}/{sub-resource} GET (list), POST (create sub-resource)
/api/v1/{resource}/{id}/actions/{action} POST (non-CRUD actions: resolve, escalate, assign)

Examples:
GET  /api/v1/tickets
POST /api/v1/tickets
GET  /api/v1/tickets/FC-2024-001234
GET  /api/v1/tickets/FC-2024-001234/activities
POST /api/v1/tickets/FC-2024-001234/activities
POST /api/v1/tickets/FC-2024-001234/actions/resolve
POST /api/v1/tickets/FC-2024-001234/actions/escalate
```

### 20.2 Response Envelope (ALL responses MUST use this)

```java
// Success
record ApiResponse<T>(T data, ResponseMeta meta) {}
record PagedApiResponse<T>(List<T> data, Pagination pagination, ResponseMeta meta) {}
record ResponseMeta(String requestId, Instant timestamp, String apiVersion) {}

// Error
record ApiError(ErrorDetail error, ResponseMeta meta) {}
record ErrorDetail(String code, String message, Map<String, Object> details, String traceId) {}
```

### 20.3 Pagination

```
Query params: ?cursor={base64}&limit={n}&sort={field}&direction={asc|desc}
Default limit: 25. Max limit: 100.
Response includes: cursor (for next page), hasMore, total (if < 10k)

NEVER use offset-based pagination for production queries (performance degrades).
```

---

## 21. Testing Standards

### 21.1 Unit Test Structure (AAA Pattern)

```java
@Test
@DisplayName("Should transition ticket from OPEN to IN_PROGRESS when agent picks up")
void shouldTransitionToInProgressWhenAgentPicksUp() {
    // ARRANGE
    Ticket ticket = TicketFixtures.openTicket(tenantId);
    AgentUser agent = AgentFixtures.activeAgent(tenantId);

    // ACT
    Ticket result = ticketService.assignToAgent(ticket.getId(), agent.getId());

    // ASSERT
    assertThat(result.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
    assertThat(result.getAssignedAgentId()).isEqualTo(agent.getId());
    verify(ticketEventPublisher).publishStatusChanged(any(TicketStatusChangedEvent.class));
}
```

### 21.2 Test Data Fixtures

```java
// ALL test data MUST use fixture factories. No hardcoded values in tests.
// Location: backend/{service}/src/test/java/in/supporthub/{service}/fixtures/

public class TicketFixtures {
    private static final UUID DEFAULT_TENANT = UUID.fromString("test-tenant-uuid");

    public static Ticket openTicket(UUID tenantId) {
        return Ticket.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .ticketNumber("TEST-" + RandomStringUtils.randomNumeric(6))
            .status(TicketStatus.OPEN)
            .priority(Priority.MEDIUM)
            .title("Test ticket " + UUID.randomUUID())
            .description("Test description for unit testing purposes")
            .createdAt(Instant.now())
            .build();
    }

    public static Ticket.Builder builder(UUID tenantId) {
        return openTicket(tenantId).toBuilder();
    }
}
```

### 21.3 Integration Test Annotations

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfig.class)  // from shared test module
@Transactional  // rollback after each test
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TicketControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void shouldCreateTicketAndReturn201() throws Exception {
        mockMvc.perform(
            post("/api/v1/tickets")
                .header("Authorization", "Bearer " + generateTestToken(CUSTOMER_ROLE))
                .header("X-Tenant-ID", TEST_TENANT_ID)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest()))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.ticketNumber").isNotEmpty())
        .andExpect(jsonPath("$.data.status").value("OPEN"));
    }
}
```

---

## 22. Security Standards

### 22.1 Authentication Rules

```
Customer endpoints:   require valid customer JWT (type=customer, audience=api)
Agent endpoints:      require valid agent JWT (type=agent, role>=AGENT)
Admin endpoints:      require valid agent JWT (type=agent, role>=ADMIN)
Super admin:          require valid agent JWT (type=agent, role=SUPER_ADMIN)
MCP endpoints:        require valid MCP JWT (audience=mcp, short-lived 4h)
Internal webhooks:    require HMAC-SHA256 signature header
```

### 22.2 PII Rules

```
NEVER log: phone, email, name, address, order details, financial info
ALWAYS encrypt at rest: phone, email (AES-256-GCM via @Converter)
ALWAYS mask in API responses to agents: show "+91 98765 ****" not full phone
ALWAYS strip PII before sending to LLM (Anthropic API)
ALWAYS use pre-signed URLs for S3 files (15-min expiry, never public ACL)
```

### 22.3 Input Validation Rules

```java
// All request body fields MUST be validated with Jakarta Validation annotations
public record CreateTicketRequest(
    @NotBlank @Size(min = 10, max = 200) String title,
    @NotBlank @Size(min = 20, max = 5000) String description,
    @NotNull UUID categoryId,
    UUID subCategoryId,  // nullable
    UUID orderId,        // nullable
    @Pattern(regexp = "^[a-z_]+$") String channel
) {}
```

### 22.4 OWASP Top 10 Checklist per Feature

Before marking any feature DONE, verify:
- [ ] A01 Broken Access Control: Is every endpoint checking tenant isolation AND role?
- [ ] A02 Cryptographic Failures: Is sensitive data encrypted at rest?
- [ ] A03 Injection: Are all DB queries parameterized? No string concat with user input?
- [ ] A04 Insecure Design: Is there a rate limit on this endpoint?
- [ ] A05 Security Misconfiguration: No debug endpoints exposed?
- [ ] A06 Vulnerable Components: Run `./mvnw dependency-check:check` — no new CRITICAL/HIGH
- [ ] A07 Authentication Failures: JWT validated on every request? Refresh token httpOnly?
- [ ] A08 SSRF: If fetching external URL, is it validated against allowlist?
- [ ] A09 Logging: No PII in logs? Stack traces logged server-side only?
- [ ] A10 SSRF/Forgery: CSRF not applicable (stateless JWT) — but validate Origin header

---

## 23. Build & Run Commands

```bash
# ============================================================
# BACKEND (from /backend directory)
# ============================================================

# Build all services
./mvnw clean package -DskipTests

# Build specific service
./mvnw clean package -pl ticket-service -DskipTests

# Run specific service (with dev profile)
./mvnw spring-boot:run -pl ticket-service -Dspring.profiles.active=dev

# Run unit tests
./mvnw test -pl ticket-service

# Run integration tests (requires Docker for Testcontainers)
./mvnw verify -pl ticket-service

# Run all tests for all services
./mvnw verify

# Run code analysis
./mvnw checkstyle:check spotbugs:check pmd:check -pl ticket-service

# Run OWASP dependency check
./mvnw dependency-check:check -pl ticket-service

# Run Flyway migration (dev)
./mvnw flyway:migrate -pl ticket-service -Dflyway.url=${DB_URL}

# Run Flyway migration (undo last)
./mvnw flyway:undo -pl ticket-service -Dflyway.url=${DB_URL}

# ============================================================
# FRONTEND (from /frontend directory)
# ============================================================

# Install all dependencies
npm ci

# Build all packages and apps
npm run build --workspaces

# Run agent dashboard dev server
npm run dev -w apps/agent-dashboard     # http://localhost:3001

# Run admin portal dev server
npm run dev -w apps/admin-portal        # http://localhost:3002

# Run customer portal dev server
npm run dev -w apps/customer-portal     # http://localhost:3000

# Run unit tests
npm run test --workspaces

# Run TypeScript check (no emit)
npm run typecheck --workspaces

# Run ESLint
npm run lint --workspaces

# Build SDK
npm run build -w packages/customer-sdk

# ============================================================
# CMS (from /cms directory)
# ============================================================
npm run develop    # Strapi dev server: http://localhost:1337
npm run build      # Build Strapi admin panel
npm start          # Production start

# ============================================================
# INFRASTRUCTURE
# ============================================================

# Start full local stack (all databases, Kafka, Elasticsearch)
docker compose -f infrastructure/docker/docker-compose.yml up -d

# Stop all
docker compose -f infrastructure/docker/docker-compose.yml down

# Start only databases
docker compose -f infrastructure/docker/docker-compose.yml up -d postgres redis mongodb

# View logs
docker compose -f infrastructure/docker/docker-compose.yml logs -f ticket-service

# Seed dev data
./mvnw exec:java -pl scripts/seed -Dexec.mainClass="in.supporthub.seed.DevSeedRunner"

# ============================================================
# TESTING COMMANDS
# ============================================================

# Run E2E tests (Playwright, requires all services running)
cd frontend && npx playwright test

# Run E2E tests with UI
cd frontend && npx playwright test --ui

# Run security baseline scan (OWASP ZAP)
docker run -t zaproxy/zap-stable zap-baseline.py -t http://localhost:8080

# Run load test (Gatling)
./mvnw gatling:test -pl backend/load-tests
```

---

## 24. Environment Variables Reference

```bash
# ============================================================
# SHARED (all backend services)
# ============================================================
SPRING_PROFILES_ACTIVE=dev|staging|prod
SERVER_PORT=808X                          # per service (8081-8090)
DB_URL=jdbc:postgresql://localhost:5432/supporthub
DB_USER=supporthub
DB_PASS=                                  # from AWS Secrets Manager in prod
MONGODB_URI=mongodb://localhost:27017/supporthub
REDIS_HOST=localhost
REDIS_PORT=6379
KAFKA_SERVERS=localhost:9092
JWT_PRIVATE_KEY_LOCATION=classpath:keys/private.pem
JWT_PUBLIC_KEY_LOCATION=classpath:keys/public.pem

# ============================================================
# AI SERVICE
# ============================================================
ANTHROPIC_API_KEY=                        # from AWS Secrets Manager ONLY
ANTHROPIC_SENTIMENT_MODEL=claude-haiku-4-5-20251001
ANTHROPIC_RESOLUTION_MODEL=claude-sonnet-4-5
LANGFUSE_PUBLIC_KEY=
LANGFUSE_SECRET_KEY=
LANGFUSE_HOST=https://cloud.langfuse.com

# ============================================================
# NOTIFICATION SERVICE
# ============================================================
MSG91_API_KEY=                            # SMS provider
MSG91_SENDER_ID=
SENDGRID_API_KEY=                         # Email provider
WHATSAPP_ACCESS_TOKEN=                    # Meta Business API

# ============================================================
# STORAGE
# ============================================================
AWS_S3_BUCKET=supporthub-{env}
AWS_REGION=ap-south-1
AWS_ACCESS_KEY_ID=                        # use IAM role in prod, not explicit key
AWS_SECRET_ACCESS_KEY=                    # use IAM role in prod

# ============================================================
# FRONTEND
# ============================================================
VITE_API_BASE_URL=http://localhost:8080
VITE_TENANT_ID=                           # set per deployment
VITE_WS_URL=ws://localhost:8080/ws
VITE_SENTRY_DSN=                          # optional error tracking
```

---

## 25. Hooks Configuration

Hooks in `.claude/settings.json` enforce rules automatically:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write|Create",
        "hooks": [
          {
            "type": "command",
            "command": "[ \"$(git branch --show-current)\" != \"main\" ] && [ \"$(git branch --show-current)\" != \"develop\" ] || { echo '{\"block\": true, \"message\": \"ERROR: Cannot edit files directly on main or develop. Create a feature branch first: git checkout -b feature/TASK-ID-description\"}' >&2; exit 2; }",
            "timeout": 5
          }
        ]
      },
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "command": "FILE=$(echo $CLAUDE_TOOL_INPUT | jq -r '.file_path // .path // \"\"'); if echo \"$FILE\" | grep -qE '(REQUIREMENT|ARCHITECTURE)\\.md$'; then echo '{\"feedback\": \"⚠️  Editing source-of-truth file. Ensure TODO.md has a DOCS task for this change.\"}'; fi",
            "timeout": 5
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "OUTPUT=$(echo $CLAUDE_TOOL_RESULT | jq -r '.output // \"\"'); if echo \"$OUTPUT\" | grep -qiE '(BUILD FAILURE|Tests run:.*Failures:|FAILED)'; then echo '{\"feedback\": \"⚠️  Build or test failure detected. Create a TEST-NNN or BUG-NNN task in TODO.md before continuing.\"}'; fi",
            "timeout": 5
          }
        ]
      }
    ],
    "Stop": [
      {
        "hooks": [
          {
            "type": "agent",
            "prompt": "Check if the current work session has outstanding items: 1) Are there any IN_PROGRESS tasks in TODO.md that weren't completed? 2) Are there failing tests that weren't fixed? 3) Were REQUIREMENT.md or ARCHITECTURE.md updated if needed? Report a brief status summary. If critical items are unfinished, respond with 'reason: [list of unfinished items]' to prevent stopping.",
            "timeout": 60
          }
        ]
      }
    ]
  }
}
```

---

## 26. Slash Commands Reference

All slash commands are defined in `.claude/commands/`. Use them frequently.

### `/sdlc` — Run Full Micro-SDLC Cycle

```
Usage: /sdlc [task-id] or /sdlc (uses current context)
Effect: Runs ALL phases 1-9 for the current change or specified task.
        Spawns parallel sub-agents for review + security + testing.
        Updates TODO.md throughout.
```

### `/task [id] [action]` — Task Management

```
Usage: /task create [type] [title]
       /task update [TASK-ID] [status|priority|field] [value]
       /task list [filter: open|in-progress|blocked]
       /task show [TASK-ID]

Effect: Creates or updates tasks in TODO.md with proper schema.
```

### `/review [file-or-service]` — Code Review

```
Usage: /review ticket-service
       /review backend/ticket-service/src/main/java/...
       /review (all changed files in current git status)

Effect: Spawns code-reviewer sub-agent.
        Outputs REVIEW-NNN tasks in TODO.md for all findings.
        Groups by: CRITICAL, HIGH, MEDIUM, LOW.
```

### `/test [service] [type]` — Run Tests

```
Usage: /test ticket-service unit
       /test ticket-service integration
       /test ticket-service all
       /test frontend agent-dashboard
       /test e2e

Effect: Runs specified test suite.
        Creates TEST-NNN tasks in TODO.md for failures.
        Reports coverage summary.
```

### `/security [target]` — Security Analysis

```
Usage: /security ticket-service
       /security all
       /security deps     (dependency CVE check only)

Effect: Spawns security-analyst sub-agent + runs OWASP tools.
        Creates SEC-NNN tasks in TODO.md for all findings.
```

### `/analyse [target]` — Static Code Analysis

```
Usage: /analyse ticket-service
       /analyse frontend/agent-dashboard
       /analyse all

Effect: Runs Checkstyle, SpotBugs, PMD (backend) + ESLint, TypeScript (frontend).
        Creates ANAL-NNN tasks in TODO.md for violations above threshold.
```

### `/deploy [env] [service]` — Deployment

```
Usage: /deploy dev ticket-service
       /deploy staging all
       /deploy prod (requires all gates green, human approval)

Effect: Runs pre-deployment gate checklist.
        Executes DB migrations.
        Applies Kubernetes manifests.
        Runs smoke tests.
        Updates DEPLOY-NNN task in TODO.md.
```

### `/status` — Project Status

```
Usage: /status
       /status sprint
       /status [service-name]

Effect: Prints TODO.md summary: task counts by status, open blockers,
        recent completions, CI/CD status.
```

### `/sync-docs` — Sync REQUIREMENT.md and ARCHITECTURE.md

```
Usage: /sync-docs

Effect: Reviews recent git changes.
        Identifies requirements or architecture decisions that changed.
        Proposes specific updates to REQUIREMENT.md and/or ARCHITECTURE.md.
        Creates DOCS-NNN tasks for each update.
```

---

## 27. REQUIREMENT.md & ARCHITECTURE.md Sync Protocol

### 27.1 Continuous Alignment Rule

**Every merged feature MUST have either:**
1. A reference to an existing REQ-* in REQUIREMENT.md, OR
2. A new REQ-* added to REQUIREMENT.md as part of the same PR

**Every significant technical decision MUST have either:**
1. A reference to an existing section in ARCHITECTURE.md, OR
2. An updated section in ARCHITECTURE.md as part of the same PR, AND
3. An ADR-NNN task created in TODO.md

### 27.2 Change Impact Matrix

When changing these, also update:

| Changed | Must Also Update |
|---|---|
| New REST endpoint | REQUIREMENT.md (API section), ARCHITECTURE.md (Section 8) |
| New Kafka topic | ARCHITECTURE.md (Section 9.1 topic table) |
| New service | ARCHITECTURE.md (Section 6.1 service map), Section 3 container diagram |
| New DB table | ARCHITECTURE.md (Section 8, schema section) |
| New external integration | ARCHITECTURE.md (Section 2.3 integration table), REQUIREMENT.md |
| New MCP tool | ARCHITECTURE.md (Section 12), REQUIREMENT.md (Section 9.2) |
| New feature flag | ARCHITECTURE.md (Section 13.3 feature flag table) |
| Changed auth flow | ARCHITECTURE.md (Section 15) |
| New tenant config option | ARCHITECTURE.md (Section 16) |

### 27.3 Changelog in REQUIREMENT.md

Append to the bottom of REQUIREMENT.md after every significant change:

```markdown
## Changelog

### 2026-03-24 (Sprint 2)
- REQ-TICKET-005: Added duplicate detection requirement (FEAT-042)
- REQ-AI-007: Clarified sentiment analysis language support list

### 2026-03-23 (Sprint 1)
- Initial requirements documented
```

---

## 28. Escalation & Blocking Rules

### 28.1 When to Stop and Ask a Human

Claude MUST NOT proceed autonomously when:
1. A REQUIREMENT.md conflict cannot be resolved (implementation contradicts stated requirement)
2. An ARCHITECTURE.md deviation is required that has not been approved
3. A database schema change would cause data loss
4. Deploying to production with open P0 tasks
5. A SEC-NNN CRITICAL issue is found and the fix is unclear
6. Two tasks in TODO.md have circular dependencies
7. Test results are ambiguous (some pass locally, fail in CI)
8. An external API contract (OMS, WhatsApp, Anthropic) has changed unexpectedly

In these cases:
1. Create a `BUG-NNN` or `DOCS-NNN` task in TODO.md explaining the blocker
2. Set all blocked tasks to BLOCKED with `Blocked By: [BUG-NNN]`
3. Write a clear summary to the human: "I am blocked on [issue]. I need you to: [specific decision/action]."
4. Do NOT guess or make unilateral architecture/requirement decisions

### 28.2 Context Window Management

When the context window is getting full (> 50% used):
1. Run `/compact` to summarize the current session
2. Ensure TODO.md is fully up to date BEFORE compacting
3. Ensure all in-progress work is either committed or explicitly noted in TODO.md
4. After compacting, re-read CLAUDE.md and TODO.md to re-orient

### 28.3 Session Resumption Protocol

At the START of every new session (after `/resume` or fresh start):
1. Read CLAUDE.md (this file)
2. Read TODO.md — identify IN_PROGRESS tasks from previous session
3. Read the specific REQUIREMENT.md and ARCHITECTURE.md sections relevant to those tasks
4. State: "Resuming from: [list of in-progress tasks]. Continuing with: [next action]."
5. Never assume previous session's context — always re-read the relevant docs

---

*CLAUDE.md Version: 1.0 | SupportHub | Rupantar Technologies*
*This file is committed to version control and must be kept current.*
*Last major update: March 2026*
