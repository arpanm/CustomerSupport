# SupportHub — Architecture Document (v1.0)
## High-Level & Low-Level Design

> **Stack:** Java 21 + Spring Boot 3.x · PostgreSQL · MongoDB · Redis · Apache Kafka · React (Headless Customer SDK + Agent/Admin PWAs) · Spring AI MCP Server · Strapi CMS
> **AI:** Anthropic Claude API (claude-haiku-4-5 for sentiment, claude-sonnet-4-5 for resolution) via Spring AI

---

## Table of Contents

1. [Architecture Philosophy & Principles](#1-architecture-philosophy--principles)
2. [System Context Diagram (C4 Level 1)](#2-system-context-diagram-c4-level-1)
3. [Container Diagram (C4 Level 2)](#3-container-diagram-c4-level-2)
4. [Technology Stack Decisions](#4-technology-stack-decisions)
5. [Monorepo & Repository Structure](#5-monorepo--repository-structure)
6. [Backend Microservices — High-Level Design](#6-backend-microservices--high-level-design)
7. [Backend Microservices — Low-Level Design](#7-backend-microservices--low-level-design)
8. [Database Architecture](#8-database-architecture)
9. [Event-Driven Architecture (Kafka)](#9-event-driven-architecture-kafka)
10. [API Gateway & Service Mesh](#10-api-gateway--service-mesh)
11. [Frontend Architecture](#11-frontend-architecture)
12. [MCP Server Architecture](#12-mcp-server-architecture)
13. [AI/ML Pipeline Architecture](#13-aiml-pipeline-architecture)
14. [CMS Architecture (Strapi)](#14-cms-architecture-strapi)
15. [Authentication & Authorization Architecture](#15-authentication--authorization-architecture)
16. [Multi-Tenancy Architecture](#16-multi-tenancy-architecture)
17. [Caching Strategy](#17-caching-strategy)
18. [File Storage Architecture](#18-file-storage-architecture)
19. [Notification Architecture](#19-notification-architecture)
20. [Observability Architecture](#20-observability-architecture)
21. [Deployment Architecture](#21-deployment-architecture)
22. [Security Architecture](#22-security-architecture)
23. [Data Flow Diagrams](#23-data-flow-diagrams)
24. [API Contract Standards](#24-api-contract-standards)
25. [Development Conventions](#25-development-conventions)

---

## 1. Architecture Philosophy & Principles

### 1.1 Guiding Principles

**Domain-Driven Design (DDD):** Services are designed around business domains (Ticket, Customer, Notification, etc.), not technical layers. Each service owns its bounded context, data model, and API.

**Event-Driven First:** All state changes that other services care about are published as events to Kafka. Services never directly call each other for state changes — only for synchronous reads that are truly needed in real-time.

**API-First Design:** All service capabilities are expressed as OpenAPI 3.1 contracts before implementation. The contract is the source of truth for both producers and consumers.

**Database-per-Service:** Each microservice has its own data store (schema isolation in PostgreSQL, separate MongoDB collections, or separate Redis keyspaces). No cross-service DB joins.

**Headless Frontend Model:** The customer-facing UI is exposed as a framework-agnostic JavaScript/TypeScript SDK (headless), decoupled from rendering so it can be embedded into Capacitor mobile apps or any future surface. Agent and Admin UIs are React PWAs.

**Multi-Tenant from Day One:** Tenant isolation is enforced at every layer — API gateway (tenant routing), application (tenant context propagation), database (row-level security + tenant_id), and cache (tenant-namespaced keys).

**AI as an Enhancement Layer:** AI features (sentiment, resolution suggestions) are asynchronous, non-blocking, and failure-tolerant. If the AI service is unavailable, ticket operations work normally without degradation.

**Eventual Consistency Over Strong Consistency:** For cross-service data (e.g., customer profile in a ticket detail), the system uses cached snapshots + async sync rather than distributed transactions. CQRS is applied where query complexity demands it.

### 1.2 Architecture Pattern

The overall backend follows a **Modular Microservices** pattern — not a full micro-service for every CRUD operation, but cohesive services aligned to DDD bounded contexts. Early phases may deploy services as modules within a modular monolith and extract to independent services as scale demands.

```
Phase 1: Modular monolith (ticket + customer + notification in one deployable)
Phase 2: Extract AI Service and MCP Server as independent deployables
Phase 3: Extract Tenant/Onboarding Service as growth demands it
Phase 4: Full microservices with independent scaling per service
```

---

## 2. System Context Diagram (C4 Level 1)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           EXTERNAL ACTORS                                    │
│                                                                              │
│  [End Customer]      [Customer Care Agent]    [Ops/Super Admin]              │
│  Mobile + Web        Browser (Desktop)        Browser (Desktop)              │
│                                                                              │
│  [AI Chatbot Agent]  [Tenant Store Admin]     [External OMS/ERP]             │
│  MCP Client          Browser (Desktop)        REST API                       │
└──────────┬────────────────┬───────────────────────┬────────────────────────-┘
           │                │                       │
           ▼                ▼                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                       SUPPORTHUB PLATFORM                                    │
│                                                                              │
│  Customer SDK/Portal  │  Agent Dashboard  │  Admin Portal  │  MCP Server    │
│  (React Headless)     │  (React PWA)      │  (React PWA)   │  (Spring AI)   │
│                                                                              │
│  ┌─────────────────────────── Backend Services ──────────────────────────┐  │
│  │  API Gateway (Spring Cloud Gateway)                                    │  │
│  │  Ticket Svc │ Customer Svc │ AI Svc │ Notification Svc │ Tenant Svc   │  │
│  │  FAQ Svc    │ Reporting Svc│ Auth Svc│ Order Sync Svc  │              │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  PostgreSQL  │  MongoDB  │  Redis  │  Kafka  │  S3/MinIO  │  Elasticsearch  │
└──────────────────────────────────────────────────────────────────────────────┘
           │                │                       │
           ▼                ▼                       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL SERVICES                                     │
│  Anthropic Claude API  │  MSG91/Kaleyra SMS  │  WhatsApp Business API        │
│  Strapi CMS            │  SendGrid/AWS SES   │  Tenant OMS APIs              │
│  Langfuse              │  Google OAuth       │  AWS S3/CloudFront            │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Container Diagram (C4 Level 2)

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│  CLIENT LAYER                                                                      │
│                                                                                    │
│  ┌─────────────────────┐  ┌──────────────────────┐  ┌───────────────────────┐     │
│  │  Customer SDK        │  │  Agent Dashboard PWA  │  │  Admin Portal PWA     │     │
│  │  @supporthub/sdk     │  │  React + Vite         │  │  React + Vite         │     │
│  │  (Headless TS SDK)   │  │  Port 3001            │  │  Port 3002            │     │
│  │  Embeds in Capacitor │  │  TanStack Query       │  │  TanStack Query       │     │
│  └──────────┬──────────┘  └──────────┬───────────┘  └──────────┬────────────┘     │
└─────────────┼────────────────────────┼─────────────────────────┼──────────────────┘
              │                        │                          │
              └────────────────────────┴──────────────────────────┘
                                       │ HTTPS + WSS
┌──────────────────────────────────────▼────────────────────────────────────────────┐
│  API GATEWAY LAYER                                                                 │
│  Spring Cloud Gateway (Port 8080)                                                  │
│  • JWT validation middleware                                                       │
│  • Tenant resolution (header X-Tenant-ID or subdomain)                            │
│  • Rate limiting (Redis token bucket)                                              │
│  • Request routing to microservices                                                │
│  • WebSocket proxying (for real-time agent notifications)                          │
│  • Circuit breaker (Resilience4j)                                                  │
└──────────────────────────────────────┬────────────────────────────────────────────┘
                                       │ Internal HTTP (service mesh)
┌──────────────────────────────────────▼────────────────────────────────────────────┐
│  MICROSERVICES LAYER                                                               │
│                                                                                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐     │
│  │  auth-service     │  │  ticket-service  │  │  customer-service            │     │
│  │  Port: 8081       │  │  Port: 8082      │  │  Port: 8083                  │     │
│  │  PostgreSQL       │  │  PostgreSQL      │  │  PostgreSQL                  │     │
│  │  Redis (sessions) │  │  Redis (cache)   │  │  Redis (cache)               │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────────┘     │
│                                                                                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐     │
│  │  ai-service       │  │  notification    │  │  faq-service                 │     │
│  │  Port: 8084       │  │  -service        │  │  Port: 8086                  │     │
│  │  MongoDB (AI logs)│  │  Port: 8085      │  │  MongoDB (FAQ docs)          │     │
│  │  Spring AI + MCP  │  │  MongoDB (notifs)│  │  pgvector (embeddings)       │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────────┘     │
│                                                                                    │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────────────────┐     │
│  │  reporting-service│  │  tenant-service  │  │  order-sync-service          │     │
│  │  Port: 8087       │  │  Port: 8088      │  │  Port: 8089                  │     │
│  │  Elasticsearch    │  │  PostgreSQL      │  │  PostgreSQL (order cache)    │     │
│  │  (read models)    │  │  Redis (config)  │  │  Redis (order cache)         │     │
│  └──────────────────┘  └──────────────────┘  └──────────────────────────────┘     │
│                                                                                    │
│  ┌──────────────────────────────────────────────────────────────────────────┐     │
│  │  mcp-server (Port: 8090) — Spring AI MCP over SSE/WebMVC                 │     │
│  │  Exposes: ticket tools, FAQ tools, order tools, customer tools            │     │
│  └──────────────────────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────┬────────────────────────────────────────────┘
                                       │ Kafka (async events)
┌──────────────────────────────────────▼────────────────────────────────────────────┐
│  DATA + MESSAGING LAYER                                                            │
│                                                                                    │
│  ┌───────────────────┐  ┌──────────────────┐  ┌────────────────────────────┐      │
│  │  PostgreSQL 16     │  │  MongoDB 7        │  │  Redis 7 Cluster           │      │
│  │  (Core Relational) │  │  (Documents/Logs) │  │  (Cache + Sessions)        │      │
│  │  + pgvector ext    │  │  + Atlas Search   │  │  + Pub/Sub                 │      │
│  └───────────────────┘  └──────────────────┘  └────────────────────────────┘      │
│                                                                                    │
│  ┌───────────────────┐  ┌──────────────────┐  ┌────────────────────────────┐      │
│  │  Apache Kafka      │  │  Elasticsearch 8  │  │  MinIO / AWS S3            │      │
│  │  (Event Bus)       │  │  (Search + Reports│  │  (File Attachments)        │      │
│  │  3 brokers, 3 ZK   │  │  + Analytics)     │  │                            │      │
│  └───────────────────┘  └──────────────────┘  └────────────────────────────┘      │
└────────────────────────────────────────────────────────────────────────────────────┘

External Integrations:
  Strapi CMS ─── REST API ──► faq-service (webhook + polling sync)
  Anthropic API ─ HTTPS ────► ai-service (via Spring AI)
  SMS/WA/Email ── REST ─────► notification-service
  Tenant OMS ──── REST ─────► order-sync-service
  Langfuse ──────── SDK ─────► ai-service (traces)
```

---

## 4. Technology Stack Decisions

### 4.1 Backend — Java 21 + Spring Boot 3.3

**Why Java 21 LTS:**
- Virtual threads (Project Loom) via Spring Boot 3.3's `spring.threads.virtual.enabled=true` — dramatically improves throughput for I/O-bound workloads (HTTP calls to Anthropic, DB queries) without reactive complexity.
- Record classes for immutable DTOs.
- Pattern matching and sealed classes for clean domain modeling.
- GraalVM native compilation option for faster cold-start if needed in serverless scenarios.

**Key Spring Boot Dependencies per Service:**

```xml
<!-- Core -->
spring-boot-starter-web               (REST APIs, WebSocket)
spring-boot-starter-webflux           (ai-service: non-blocking Anthropic API calls)
spring-boot-starter-data-jpa          (PostgreSQL via Hibernate)
spring-boot-starter-data-mongodb      (MongoDB documents)
spring-boot-starter-data-redis        (Redis Lettuce client)
spring-boot-starter-security          (JWT security)
spring-boot-starter-actuator          (Health + metrics)
spring-boot-starter-validation        (Bean validation)

<!-- Spring Cloud -->
spring-cloud-starter-gateway          (API Gateway)
spring-cloud-starter-circuitbreaker-resilience4j
spring-cloud-loadbalancer

<!-- Kafka -->
spring-kafka                           (Producer + Consumer)

<!-- AI -->
spring-ai-anthropic-spring-boot-starter
spring-ai-starter-mcp-server-webmvc   (MCP Server)
spring-ai-starter-vector-store-pgvector

<!-- Observability -->
micrometer-registry-prometheus
micrometer-tracing-bridge-otel
opentelemetry-exporter-otlp

<!-- Database -->
flyway-core                            (DB migrations)
```

### 4.2 Database Choices

| Database | Services | Use Case | Why |
|---|---|---|---|
| PostgreSQL 16 + pgvector | auth, ticket, customer, tenant, order-sync | Core relational data, embeddings | ACID, row-level security, pgvector for FAQ embeddings, mature Spring Data JPA support |
| MongoDB 7 | ai-service, faq-service, notification-service | AI interaction logs, FAQ rich documents, notification history | Schema-flexible for AI responses, nested FAQ content with Markdown, high write volume for notifications |
| Redis 7 | All services | Session cache, rate limiting, distributed locks, Pub/Sub for WebSocket relay | Sub-millisecond latency, Spring Session, Lettuce reactive client |
| Elasticsearch 8 | reporting-service, ticket-service (search) | Full-text search, aggregation reports, analytics | Superior full-text search vs pg_tsvector at scale, Kibana for ops dashboards |
| Apache Kafka | All services (events) | Async event bus, saga orchestration, audit log | Durable, ordered, consumer group support, exactly-once semantics for critical flows |

**Why MongoDB for AI logs and FAQ:**
- AI interaction logs (sentiment results, resolution suggestion runs) have varying schemas depending on model version and features. MongoDB's document model avoids painful migrations when AI payloads evolve.
- FAQ entries with rich Markdown content, embedded media references, and multi-language content fit naturally as MongoDB documents. Atlas Search provides vector search capability as an alternative/complement to pgvector.

**Why PostgreSQL for core ticket/customer data:**
- Strong ACID guarantees for financial-adjacent operations (refund tracking, SLA breach records).
- Row-level security (RLS) for multi-tenancy enforcement at DB level.
- pgvector extension for FAQ embeddings (avoids separate vector DB infra in Phase 1).
- Foreign key integrity for the complex relational ticket data model.

### 4.3 Frontend Choices

| App | Framework | Notes |
|---|---|---|
| Customer SDK | Vanilla TypeScript (headless) + React hooks for opt-in usage | Framework-agnostic SDK ships as npm package `@supporthub/customer-sdk`. Capacitor apps import it directly |
| Customer Reference App | React 18 + Vite | Reference implementation using the SDK — shows how to build a full customer portal |
| Agent Dashboard | React 18 + Vite + TanStack Query | Desktop-first PWA |
| Admin Portal | React 18 + Vite + TanStack Query | Desktop-first PWA |
| Shared UI Library | React + shadcn/ui + Tailwind CSS | Shared component library used by Agent + Admin |

### 4.4 CMS — Strapi v5

**Why Strapi over Payload or Directus:**
- Strapi has the most mature ecosystem (70k GitHub stars, enterprise-proven).
- Best-in-class RBAC for content editors (non-technical tenant staff can manage FAQ without developer).
- Both REST and GraphQL APIs out of the box — integrates simply with faq-service via REST webhook.
- Self-hosted (critical for Indian data residency requirements).
- Support for PostgreSQL backend (same infra as other services).
- Internationalization support for multilingual FAQs (Hindi, Tamil, etc.).
- Active plugin marketplace for SEO, media, workflows.

**Strapi Deployment:**
- Runs as a standalone Node.js service (Port 1337).
- Uses PostgreSQL (dedicated `strapi_db` database, separate from core services).
- Media stored in MinIO/S3.
- Webhook fires to faq-service on content publish/update.

### 4.5 MCP Server — Spring AI (spring-ai-starter-mcp-server-webmvc)

The official MCP Java SDK, with Spring AI integration, is the clear choice:
- Spring maintains the official MCP Java SDK in collaboration with Anthropic.
- `@McpTool` annotation makes tool registration declarative — no boilerplate.
- `spring-ai-starter-mcp-server-webmvc` provides SSE transport over standard Spring MVC (compatible with any HTTP/2 aware proxy).
- Tenant-scoped tool execution via Spring Security context.
- Dynamic tool registration via `McpSyncServer.addTool()` for per-tenant tool customizations.

---

## 5. Monorepo & Repository Structure

```
supporthub/
├── .github/
│   └── workflows/
│       ├── ci.yml                    # PR validation: lint, test, build
│       ├── deploy-staging.yml
│       └── deploy-prod.yml
│
├── backend/                          # All Java Spring Boot services
│   ├── pom.xml                       # Parent POM (dependency management)
│   ├── api-gateway/                  # Spring Cloud Gateway
│   │   ├── src/
│   │   └── pom.xml
│   ├── auth-service/
│   │   ├── src/
│   │   │   ├── main/java/in/supporthub/auth/
│   │   │   │   ├── AuthServiceApplication.java
│   │   │   │   ├── config/           # SecurityConfig, JwtConfig, RedisConfig
│   │   │   │   ├── controller/       # AuthController, AgentAuthController
│   │   │   │   ├── service/          # OtpService, JwtService, TokenService
│   │   │   │   ├── repository/       # UserRepository, RefreshTokenRepository
│   │   │   │   ├── domain/           # User, RefreshToken, OtpRecord
│   │   │   │   ├── dto/              # LoginRequest, TokenResponse, OtpRequest
│   │   │   │   └── event/            # UserLoggedInEvent
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── db/migration/     # Flyway migrations
│   │   └── pom.xml
│   ├── ticket-service/
│   ├── customer-service/
│   ├── ai-service/
│   ├── notification-service/
│   ├── faq-service/
│   ├── reporting-service/
│   ├── tenant-service/
│   ├── order-sync-service/
│   ├── mcp-server/
│   └── shared/                       # Shared library (DTOs, events, utils)
│       ├── src/main/java/in/supporthub/shared/
│       │   ├── event/                # All Kafka event POJOs
│       │   ├── dto/                  # Shared DTOs
│       │   ├── security/             # TenantContext, JWT utils
│       │   ├── exception/            # GlobalExceptionHandler, AppException
│       │   └── config/               # CommonConfig (Jackson, OpenAPI)
│       └── pom.xml
│
├── frontend/
│   ├── package.json                  # Workspace root (npm workspaces)
│   ├── packages/
│   │   ├── customer-sdk/             # @supporthub/customer-sdk (headless TS)
│   │   │   ├── src/
│   │   │   │   ├── index.ts          # Public API exports
│   │   │   │   ├── client.ts         # SupportHubClient class
│   │   │   │   ├── hooks/            # useTickets, useCreateTicket, useFAQ etc.
│   │   │   │   ├── api/              # API layer (fetch wrappers, typed)
│   │   │   │   ├── types/            # Ticket, Customer, FAQ TypeScript types
│   │   │   │   └── utils/            # OTP auth, token management
│   │   │   ├── tsconfig.json
│   │   │   └── package.json
│   │   └── ui-components/            # @supporthub/ui (shadcn + Tailwind)
│   │       ├── src/components/
│   │       └── package.json
│   ├── apps/
│   │   ├── customer-portal/          # Reference customer app (React)
│   │   │   ├── src/
│   │   │   │   ├── App.tsx
│   │   │   │   ├── pages/            # Home, CreateTicket, TicketList, TicketDetail, FAQ
│   │   │   │   ├── components/
│   │   │   │   └── main.tsx
│   │   │   └── vite.config.ts
│   │   ├── agent-dashboard/          # Agent PWA
│   │   │   ├── src/
│   │   │   │   ├── pages/            # Dashboard, TicketQueue, TicketDetail, Reports, Customers
│   │   │   │   ├── components/       # TicketCard, AgentComposer, AIAssistPanel, SentimentBadge
│   │   │   │   ├── store/            # Zustand stores
│   │   │   │   └── hooks/
│   │   │   └── vite.config.ts
│   │   └── admin-portal/             # Admin PWA
│   │       ├── src/
│   │       │   ├── pages/            # Metadata, Agents, Teams, Reports, Tenants, Onboarding
│   │       │   └── components/
│   │       └── vite.config.ts
│
├── cms/                              # Strapi v5 instance
│   ├── src/
│   │   ├── api/
│   │   │   ├── faq-entry/            # FAQ content type
│   │   │   └── faq-category/         # FAQ category content type
│   │   ├── plugins/                  # Custom Strapi plugins
│   │   └── extensions/
│   ├── config/
│   │   ├── server.js
│   │   ├── database.js               # PostgreSQL config
│   │   └── plugins.js
│   └── package.json
│
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.yml        # Full local dev stack
│   │   ├── docker-compose.test.yml   # Test containers
│   │   └── Dockerfiles per service
│   ├── terraform/
│   │   ├── modules/
│   │   │   ├── eks/                  # Kubernetes cluster
│   │   │   ├── rds/                  # PostgreSQL RDS
│   │   │   ├── elasticache/          # Redis
│   │   │   ├── msk/                  # Managed Kafka (AWS MSK)
│   │   │   └── s3/
│   │   ├── environments/
│   │   │   ├── dev/
│   │   │   ├── staging/
│   │   │   └── prod/
│   │   └── main.tf
│   └── k8s/
│       ├── base/                     # Kustomize base manifests
│       └── overlays/                 # Dev, staging, prod overlays
│
├── docs/
│   ├── REQUIREMENT.md
│   ├── ARCHITECTURE.md               # This file
│   ├── api/                          # OpenAPI specs per service
│   └── adr/                          # Architecture Decision Records
│
└── scripts/
    ├── seed/                         # DB seed scripts (Java CLI)
    └── sandbox/                      # Sandbox tenant provisioning
```

---

## 6. Backend Microservices — High-Level Design

### 6.1 Service Map and Responsibilities

```
┌────────────────────────────────────────────────────────────────────────────┐
│                          SERVICE RESPONSIBILITY MAP                         │
│                                                                             │
│  auth-service         Owns authentication, JWT issuance, OTP flows         │
│  ticket-service       Owns ticket lifecycle, activities, SLA engine        │
│  customer-service     Owns customer profiles, preferences, history         │
│  ai-service           Owns sentiment, resolution suggestions, embeddings   │
│  notification-service Owns SMS/WA/Email/in-app delivery, templates        │
│  faq-service          Owns FAQ content, semantic search, CMS sync          │
│  reporting-service    Owns CQRS read models, aggregation, exports         │
│  tenant-service       Owns tenant config, feature flags, onboarding        │
│  order-sync-service   Owns OMS integration, order caching per tenant       │
│  mcp-server           Exposes MCP tools over SSE for AI chatbot agents     │
│  api-gateway          Routes, auth-validates, rate-limits all traffic      │
└────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Inter-Service Communication Patterns

```
SYNCHRONOUS (REST via Gateway or internal Feign):
  Gateway ──► auth-service       (token validation on every request)
  ticket-service ──► customer-service  (GET customer snapshot at ticket creation)
  ticket-service ──► order-sync-service (GET order details on demand)
  mcp-server ──► ticket-service  (MCP tool invocations proxied to business logic)
  mcp-server ──► faq-service     (semantic FAQ search)

ASYNCHRONOUS (Kafka events):
  ticket-service ──► [ticket.events] ──► ai-service (trigger sentiment analysis)
  ticket-service ──► [ticket.events] ──► notification-service (trigger notifications)
  ticket-service ──► [ticket.events] ──► reporting-service (update CQRS read models)
  customer-service ──► [customer.events] ──► ticket-service (customer profile updates)
  faq-service ──► [faq.events] ──► ai-service (trigger re-embedding on update)
  ai-service ──► [ai.results] ──► ticket-service (write back sentiment result)
```

### 6.3 Shared Library (supporthub-shared)

All services depend on the `shared` module:

```java
// Event POJOs (serialized to Kafka as JSON)
package in.supporthub.shared.event;

public record TicketCreatedEvent(
    String eventId, String tenantId, String ticketId,
    String ticketNumber, String customerId, String subCategoryId,
    String title, String description, Instant occurredAt
) {}

public record TicketStatusChangedEvent(
    String eventId, String tenantId, String ticketId,
    TicketStatus oldStatus, TicketStatus newStatus,
    String actorId, ActorType actorType, Instant occurredAt
) {}

public record TicketActivityAddedEvent(
    String eventId, String tenantId, String ticketId,
    String activityId, ActivityType activityType,
    String content, String actorId, Instant occurredAt
) {}

public record SentimentAnalysisCompletedEvent(
    String eventId, String tenantId, String ticketId,
    SentimentLabel label, float score, String reason, Instant occurredAt
) {}
```

---

## 7. Backend Microservices — Low-Level Design

### 7.1 auth-service

**Responsibilities:** OTP-based customer auth, email+password agent auth, JWT generation, refresh token management.

**Package Structure:**
```
in.supporthub.auth
├── AuthServiceApplication.java
├── config/
│   ├── SecurityConfig.java           # Permit /auth/** unauthenticated
│   ├── JwtConfig.java                # @ConfigurationProperties jwt.*
│   └── RedisConfig.java              # Lettuce connection factory
├── controller/
│   ├── CustomerAuthController.java   # POST /api/v1/auth/otp/send, /verify, /refresh, /logout
│   └── AgentAuthController.java      # POST /api/v1/auth/agent/login, /2fa/verify
├── service/
│   ├── OtpService.java               # Generates 6-digit OTP, stores in Redis with TTL 5min
│   ├── JwtService.java               # Signs/validates JWT using RS256 (private/public key pair)
│   ├── CustomerAuthService.java
│   └── AgentAuthService.java
├── repository/
│   ├── CustomerRepository.java       # Spring Data JPA
│   └── AgentUserRepository.java
├── domain/
│   ├── Customer.java                 # JPA Entity (auth view — minimal fields)
│   └── AgentUser.java                # JPA Entity
└── dto/
    ├── OtpSendRequest.java           # record { String phone }
    ├── OtpVerifyRequest.java         # record { String phone, String otp }
    ├── AgentLoginRequest.java        # record { String email, String password }
    └── TokenResponse.java            # record { String accessToken, String refreshToken, ... }
```

**JWT Payload Structure:**
```json
{
  "sub": "uuid",
  "tenant_id": "uuid",
  "role": "CUSTOMER | AGENT | TEAM_LEAD | ADMIN | SUPER_ADMIN",
  "type": "customer | agent",
  "iat": 1700000000,
  "exp": 1700003600
}
```

**OTP Flow (Redis):**
```
Key:   otp:{phone}:{tenantId}
Value: { "otp": "123456", "attempts": 0 }
TTL:   300 seconds (5 minutes)
Max attempts: 3 (then key deleted, force re-send)
```

**Security Config:**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/actuator/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### 7.2 ticket-service

**Responsibilities:** Full ticket lifecycle (CRUD, status transitions, activities), SLA computation and breach detection, assignment engine.

**Domain Model (JPA Entities):**
```java
@Entity @Table(name = "tickets")
public class Ticket {
    @Id UUID id;
    String ticketNumber;           // FK: auto-generated "FC-2024-001234"
    @Column(name = "tenant_id") UUID tenantId;
    UUID customerId;
    UUID orderId;                  // nullable
    String title;
    @Column(columnDefinition = "TEXT") String description;
    UUID categoryId;
    UUID subCategoryId;            // nullable
    @Enumerated(STRING) TicketType ticketType;
    @Enumerated(STRING) Priority priority;
    @Enumerated(STRING) TicketStatus status;
    @Enumerated(STRING) Channel channel;
    UUID assignedAgentId;          // nullable
    UUID assignedTeamId;           // nullable
    @Type(JsonType.class) Map<String, Object> customFields;
    String[] tags;
    Instant slaFirstResponseDueAt;
    Instant slaResolutionDueAt;
    boolean slaFirstResponseBreached;
    boolean slaResolutionBreached;
    Float sentimentScore;          // -1.0 to 1.0, nullable
    @Enumerated(STRING) SentimentLabel sentimentLabel;
    Instant sentimentUpdatedAt;
    Instant firstRespondedAt;
    Instant resolvedAt;
    Instant closedAt;
    @CreationTimestamp Instant createdAt;
    @UpdateTimestamp Instant updatedAt;
}
```

**Ticket Number Generation:**
```java
// Tenant-specific prefix + year + zero-padded sequence
// Stored in Redis: key = "ticket:seq:{tenantId}:{year}"
// INCR operation (atomic, no gap guarantees needed)
// Format: {prefix}-{year}-{seq:06d}  e.g. FC-2024-001234
```

**Status Transition Machine:**
```java
public enum TicketStatus {
    OPEN, PENDING_AGENT_RESPONSE, PENDING_CUSTOMER_RESPONSE,
    IN_PROGRESS, ESCALATED, RESOLVED, CLOSED, REOPENED;

    private static final Map<TicketStatus, Set<TicketStatus>> VALID_TRANSITIONS = Map.of(
        OPEN, Set.of(PENDING_AGENT_RESPONSE, IN_PROGRESS, ESCALATED),
        PENDING_AGENT_RESPONSE, Set.of(PENDING_CUSTOMER_RESPONSE, IN_PROGRESS),
        PENDING_CUSTOMER_RESPONSE, Set.of(IN_PROGRESS, PENDING_AGENT_RESPONSE),
        IN_PROGRESS, Set.of(RESOLVED, ESCALATED, PENDING_CUSTOMER_RESPONSE),
        ESCALATED, Set.of(IN_PROGRESS, RESOLVED),
        RESOLVED, Set.of(CLOSED, REOPENED),
        CLOSED, Set.of(REOPENED),
        REOPENED, Set.of(IN_PROGRESS, RESOLVED)
    );

    public boolean canTransitionTo(TicketStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
}
```

**SLA Engine:**
```java
@Service
public class SlaEngine {
    // On ticket creation: find matching SLA policy, compute due dates
    public SlaDeadlines compute(Ticket ticket, SlaPolicy policy) {
        Instant base = ticket.getCreatedAt();
        return new SlaDeadlines(
            base.plus(policy.getFirstResponseHours(), HOURS),
            base.plus(policy.getResolutionHours(), HOURS)
        );
    }

    // Scheduled job: every 5 minutes via @Scheduled
    // SELECT tickets WHERE sla_resolution_due_at < NOW() AND status NOT IN (RESOLVED, CLOSED)
    // Publish SlaBreachedEvent to Kafka → notification-service alerts agent
    @Scheduled(fixedDelay = 300_000)
    public void detectBreaches() { ... }
}
```

**Package Structure:**
```
in.supporthub.ticket
├── controller/
│   ├── TicketController.java         # CRUD + lifecycle endpoints
│   ├── TicketActivityController.java # Comment/note/resolution endpoints
│   └── TicketSearchController.java   # Full-text + filter search
├── service/
│   ├── TicketService.java            # Core business logic
│   ├── SlaEngine.java                # SLA computation + breach detection
│   ├── AssignmentEngine.java         # Auto/manual assignment logic
│   ├── DuplicateDetectionService.java# Embedding-based duplicate check
│   └── TicketNumberGenerator.java    # Redis-based ticket number sequences
├── domain/
│   ├── Ticket.java
│   ├── TicketActivity.java
│   ├── TicketCategory.java
│   ├── TicketSubCategory.java
│   ├── SlaPolicy.java
│   └── ResolutionTemplate.java
├── repository/
│   ├── TicketRepository.java         # JPA + @Query for complex filters
│   └── TicketActivityRepository.java
├── event/
│   ├── TicketEventPublisher.java     # Publishes to Kafka
│   └── AiResultEventListener.java    # Consumes ai.results.sentiment
├── dto/
│   ├── CreateTicketRequest.java
│   ├── TicketResponse.java
│   ├── TicketListResponse.java
│   └── TicketFilterRequest.java      # Filter params: status[], category[], dateRange, etc.
└── config/
    ├── KafkaConfig.java
    └── CacheConfig.java
```

### 7.3 ai-service

**Responsibilities:** Sentiment analysis, resolution suggestion generation, FAQ embedding pipeline, duplicate ticket embedding comparison.

**Spring AI Integration:**
```java
@Service
public class SentimentAnalysisService {

    private final ChatClient chatClient; // Spring AI ChatClient wrapping Anthropic

    public SentimentResult analyze(String ticketText, String tenantId) {
        // Strip PII before sending to LLM
        String sanitizedText = piiSanitizer.sanitize(ticketText);

        String systemPrompt = """
            You are a sentiment classifier for customer support tickets in India.
            Support English, Hindi (Devanagari), Hinglish, Tamil, Telugu, Kannada, Bengali, Marathi, Gujarati.
            Analyze the customer text and respond ONLY with a JSON object:
            {
              "label": "very_negative|negative|neutral|positive|very_positive",
              "score": <float -1.0 to 1.0>,
              "reason": "<brief 1-sentence explanation in English>"
            }
            """;

        String response = chatClient
            .prompt()
            .system(systemPrompt)
            .user("Classify this customer text: " + sanitizedText)
            .options(AnthropicChatOptions.builder()
                .model("claude-haiku-4-5-20251001")
                .maxTokens(150)
                .temperature(0.1f)
                .build())
            .call()
            .content();

        return objectMapper.readValue(response, SentimentResult.class);
    }
}
```

```java
@Service
public class ResolutionSuggestionService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;  // pgvector via Spring AI

    public List<ResolutionSuggestion> suggest(TicketContext ctx) {
        // Stage 1: Vector search for candidate FAQs and resolution templates
        List<Document> candidates = vectorStore.similaritySearch(
            SearchRequest.query(ctx.title() + " " + ctx.description())
                .withTopK(10)
                .withSimilarityThreshold(0.70)
                .withFilterExpression("tenant_id == '" + ctx.tenantId() + "'")
        );

        // Stage 2: LLM ranking
        String prompt = buildRankingPrompt(ctx, candidates);
        String response = chatClient
            .prompt()
            .system("You are a customer support resolution specialist for India.")
            .user(prompt)
            .options(AnthropicChatOptions.builder()
                .model("claude-sonnet-4-5")
                .maxTokens(1000)
                .temperature(0.3f)
                .build())
            .call()
            .content();

        return parseRankedSuggestions(response);
    }
}
```

**MongoDB Schema for AI Interaction Logs:**
```json
// Collection: ai_interactions
{
  "_id": "ObjectId",
  "tenantId": "uuid",
  "ticketId": "uuid",
  "interactionType": "SENTIMENT | RESOLUTION_SUGGESTION | EMBEDDING",
  "modelUsed": "claude-haiku-4-5-20251001",
  "inputTokens": 342,
  "outputTokens": 28,
  "latencyMs": 1240,
  "inputHash": "sha256 of sanitized input (not the PII text)",
  "result": { /* typed result JSON */ },
  "feedbackLabel": null,  // "helpful" | "not_helpful" — set later by agent
  "langfuseTraceId": "trace-uuid",
  "createdAt": "ISODate"
}
```

### 7.4 notification-service

**Responsibilities:** Receives Kafka events, resolves notification templates, delivers via SMS/WhatsApp/Email/in-app.

**Event Consumer:**
```java
@Component
public class TicketEventConsumer {

    @KafkaListener(topics = "ticket.events", groupId = "notification-service")
    public void handleTicketEvent(TicketEvent event) {
        switch (event.eventType()) {
            case TICKET_CREATED       -> notifyTicketCreated(event);
            case TICKET_STATUS_CHANGED -> notifyStatusChange(event);
            case TICKET_RESOLVED      -> notifyResolved(event);
            case SLA_BREACH_IMMINENT  -> notifyAgentSlaWarning(event);
            // ...
        }
    }
}
```

**Notification Routing:**
```
Customer Notification:
  SMS via MSG91 API (primary)
  WhatsApp via Meta Cloud API (secondary, if customer opted in)
  Email via SendGrid (optional)

Agent Notification:
  In-App via Redis Pub/Sub → WebSocket relay (api-gateway subscribes, pushes to connected agent)
  Email via SendGrid (configurable per event per agent)
```

**MongoDB Schema for Notifications:**
```json
// Collection: notifications
{
  "_id": "ObjectId",
  "tenantId": "uuid",
  "ticketId": "uuid",
  "recipientId": "uuid",
  "recipientType": "customer | agent",
  "channel": "sms | whatsapp | email | in_app",
  "templateKey": "ticket.created.customer",
  "renderedBody": "Your ticket FC-2024-001234 has been created...",
  "status": "pending | sent | delivered | failed",
  "providerMessageId": "msg91-message-id",
  "sentAt": "ISODate",
  "deliveredAt": "ISODate",
  "failureReason": null,
  "createdAt": "ISODate"
}
```

### 7.5 faq-service

**Responsibilities:** FAQ CRUD, Strapi CMS sync, semantic search using pgvector, embedding management.

**Embedding Pipeline:**
```java
@Service
public class FaqEmbeddingService {

    private final EmbeddingModel embeddingModel;   // Spring AI → Anthropic or OpenAI embedding
    private final VectorStore vectorStore;          // Spring AI pgvector store

    public void embedAndStore(FaqEntry faq) {
        String content = faq.getTitle() + "\n\n" + faq.getContent();
        // Spring AI VectorStore handles embedding + storage atomically
        Document doc = Document.from(content)
            .withId(faq.getId().toString())
            .withMetadata(Map.of(
                "tenant_id", faq.getTenantId().toString(),
                "category_tags", String.join(",", faq.getCategoryTags()),
                "faq_id", faq.getId().toString()
            ));
        vectorStore.add(List.of(doc));
    }
}
```

**Strapi Webhook Consumer (REST endpoint):**
```java
@RestController
@RequestMapping("/internal/cms/webhook")
public class CmsWebhookController {

    @PostMapping("/strapi")
    public ResponseEntity<Void> handleStrapiWebhook(
        @RequestBody StrapiWebhookPayload payload,
        @RequestHeader("Strapi-Signature") String signature
    ) {
        // Validate HMAC signature
        webhookValidator.validate(payload, signature);
        // Async: pull full FAQ from Strapi, upsert, re-embed
        faqSyncService.syncFromCms(payload.getEntry().getId(), payload.getTenantId());
        return ResponseEntity.ok().build();
    }
}
```

### 7.6 reporting-service

**CQRS Read Model Strategy:**

The reporting-service maintains its own denormalized read models in Elasticsearch, built by consuming Kafka events. This eliminates report query load from the operational PostgreSQL.

```
Kafka Event → Consumer → Transform → Elasticsearch Index

ticket.events → TicketReadModelBuilder → tickets-{tenantId} index
ai.results.sentiment → SentimentReadModelBuilder → sentiments-{tenantId} index
```

**Elasticsearch Index Mapping (tickets):**
```json
{
  "mappings": {
    "properties": {
      "ticketId": { "type": "keyword" },
      "tenantId": { "type": "keyword" },
      "ticketNumber": { "type": "keyword" },
      "title": { "type": "text", "analyzer": "standard" },
      "customerPhone": { "type": "keyword" },
      "customerName": { "type": "text" },
      "orderId": { "type": "keyword" },
      "status": { "type": "keyword" },
      "category": { "type": "keyword" },
      "subCategory": { "type": "keyword" },
      "priority": { "type": "keyword" },
      "assignedAgentId": { "type": "keyword" },
      "sentimentLabel": { "type": "keyword" },
      "sentimentScore": { "type": "float" },
      "slaBreached": { "type": "boolean" },
      "channel": { "type": "keyword" },
      "createdAt": { "type": "date" },
      "resolvedAt": { "type": "date" },
      "firstRespondedAt": { "type": "date" }
    }
  }
}
```

**Report Aggregation Example (Ticket Volume):**
```java
// Elasticsearch aggregation via Spring Data Elasticsearch
@Repository
public class TicketReportRepository {
    public TicketVolumeReport getVolumeByStatus(String tenantId, Instant from, Instant to) {
        Query query = new NativeQuery(QueryBuilders.bool()
            .filter(termQuery("tenantId", tenantId))
            .filter(rangeQuery("createdAt").gte(from).lte(to))
            .build()
        );
        // Add terms aggregation by status, category, channel...
    }
}
```

---

## 8. Database Architecture

### 8.1 PostgreSQL Schema (Core Services)

**Schema Design:**
```sql
-- Multi-tenancy: Row-Level Security on all tables
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tickets
    USING (tenant_id = current_setting('app.current_tenant')::uuid);

-- ticket_number sequence per tenant per year
CREATE TABLE ticket_sequences (
    tenant_id   UUID NOT NULL,
    year        INTEGER NOT NULL,
    current_seq INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, year)
);

-- Categories (tenant-scoped)
CREATE TABLE ticket_categories (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES tenants(id),
    name                  VARCHAR(100) NOT NULL,
    slug                  VARCHAR(100) NOT NULL,
    icon                  VARCHAR(50),
    sla_first_response_h  INTEGER NOT NULL DEFAULT 4,
    sla_resolution_h      INTEGER NOT NULL DEFAULT 24,
    default_priority      VARCHAR(20) NOT NULL DEFAULT 'medium',
    sort_order            INTEGER NOT NULL DEFAULT 0,
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(tenant_id, slug)
);

-- FAQ entries (with pgvector for embeddings)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE faq_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    title           VARCHAR(500) NOT NULL,
    content         TEXT NOT NULL,
    category_tags   TEXT[],
    is_published    BOOLEAN DEFAULT FALSE,
    embedding       VECTOR(1536),  -- Anthropic/OpenAI embedding dimension
    cms_external_id VARCHAR(100),
    view_count      INTEGER DEFAULT 0,
    helpful_count   INTEGER DEFAULT 0,
    not_helpful_count INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

-- HNSW index for fast ANN search
CREATE INDEX faq_embedding_idx ON faq_entries
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

### 8.2 Flyway Migration Strategy

Each service has its own Flyway migrations in `src/main/resources/db/migration/`:
```
V1__create_tickets_table.sql
V2__create_activities_table.sql
V3__add_sentiment_columns.sql
V4__add_faq_embedding_index.sql
```

Migrations run on service startup. Cross-service migrations are forbidden — each service only migrates its own schema/tables.

### 8.3 MongoDB Collections

**ai-service:**
- `ai_interactions` — AI call logs, token usage, feedback
- `sentiment_history` — Per-ticket sentiment progression over time

**faq-service:**
- `faq_cms_sync_log` — History of CMS sync operations, errors

**notification-service:**
- `notifications` — Outbound notification history
- `notification_templates` — Rich template documents (multi-channel, multi-language)

### 8.4 Redis Key Namespacing

```
# OTP
otp:{tenantId}:{phone}                    → TTL 300s

# JWT refresh tokens
refresh:{tokenId}                         → TTL 30 days

# Session (Spring Session)
spring:session:{sessionId}                → TTL 8h (agents)

# Ticket cache
ticket:{tenantId}:{ticketId}              → TTL 10min
ticket:list:{tenantId}:{customerId}       → TTL 5min

# Order cache
order:{tenantId}:{orderId}                → TTL 15min

# AI suggestion cache
ai:suggestions:{tenantId}:{ticketId}      → TTL 10min

# Rate limiting (token bucket)
ratelimit:{tenantId}:{ip}                 → sliding window counter

# WebSocket agent presence
ws:agent:{agentId}:session                → TTL 30s (heartbeat)

# Tenant config cache
tenant:config:{tenantId}                  → TTL 1h

# Ticket sequence
ticket:seq:{tenantId}:{year}              → INCR counter
```

---

## 9. Event-Driven Architecture (Kafka)

### 9.1 Topic Design

```
Topic naming: {domain}.{event-type}  (kebab-case)

TICKET DOMAIN:
  ticket.created          Partitions: 12  Replication: 3
  ticket.status-changed   Partitions: 12  Replication: 3
  ticket.activity-added   Partitions: 12  Replication: 3
  ticket.escalated        Partitions: 6   Replication: 3
  ticket.resolved         Partitions: 6   Replication: 3
  ticket.sla-breached     Partitions: 6   Replication: 3

AI DOMAIN:
  ai.sentiment-requested  Partitions: 6   Replication: 3
  ai.results              Partitions: 6   Replication: 3

FAQ DOMAIN:
  faq.updated             Partitions: 3   Replication: 3
  faq.embedding-requested Partitions: 3   Replication: 3

CUSTOMER DOMAIN:
  customer.updated        Partitions: 6   Replication: 3

NOTIFICATION DOMAIN:
  notification.requested  Partitions: 12  Replication: 3
  notification.delivered  Partitions: 6   Replication: 3
```

**Partition Key Strategy:** All ticket events use `tenantId + ticketId` as the partition key (hashed). This ensures all events for a single ticket are ordered within the same partition.

### 9.2 Consumer Groups

```
ticket.events consumers:
  notification-service    (group: notification-ticket-consumer)
  ai-service              (group: ai-ticket-consumer)
  reporting-service       (group: reporting-ticket-consumer)

ai.results consumers:
  ticket-service          (group: ticket-ai-result-consumer)
  reporting-service       (group: reporting-ai-result-consumer)

faq.updated consumers:
  ai-service              (group: ai-faq-embedding-consumer)
```

### 9.3 Kafka Event Schema (Avro-like, implemented as Java Records + Jackson)

```java
// Base event envelope
public record KafkaEventEnvelope<T>(
    String eventId,           // UUID
    String eventType,         // e.g. "ticket.created"
    String tenantId,
    String correlationId,     // For distributed tracing
    Instant occurredAt,
    String schemaVersion,     // "1.0"
    T payload
) {}

// Event payloads are typed POJOs in supporthub-shared
```

**Exactly-once semantics for critical flows (SLA breach detection):**
```
Kafka producer: enable.idempotence=true, acks=all, max.in.flight.requests.per.connection=5
Consumer: isolation.level=read_committed
Idempotency guard: ticket-service checks ticket.sla_first_response_breached before updating
  to prevent duplicate breach records from re-delivered events
```

### 9.4 Saga Pattern for Complex Workflows

**Ticket Creation Saga:**
```
1. ticket-service: Create Ticket (DB write) → Publish ticket.created
2. order-sync-service: [Consume] Validate order_id if present → Publish order.validated
3. ai-service: [Consume] Queue sentiment analysis job
4. notification-service: [Consume] Send creation notification to customer
5. reporting-service: [Consume] Add to Elasticsearch read model
```

All saga steps are compensating (idempotent). If notification fails, it retries independently without rolling back the ticket.

---

## 10. API Gateway & Service Mesh

### 10.1 Spring Cloud Gateway Configuration

```yaml
# api-gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20
                key-resolver: "#{@ipKeyResolver}"

        - id: ticket-service
          uri: lb://ticket-service
          predicates:
            - Path=/api/v1/tickets/**
          filters:
            - JwtAuthFilter          # Validates JWT, injects X-User-Id, X-Tenant-Id headers
            - TenantResolutionFilter  # Resolves tenant from subdomain if not in header
            - name: CircuitBreaker
              args:
                name: ticket-service-cb
                fallbackUri: forward:/fallback/ticket

        - id: mcp-server
          uri: lb://mcp-server
          predicates:
            - Path=/mcp/**
          filters:
            - McpAuthFilter          # Validates MCP Bearer token, scopes to tenant+user

      default-filters:
        - AddRequestHeader=X-Request-Id, #{T(java.util.UUID).randomUUID().toString()}
        - AddResponseHeader=X-Response-Time, {responseTime}
```

### 10.2 Tenant Resolution Filter

```java
@Component
public class TenantResolutionFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Option 1: X-Tenant-ID header (for mobile apps that send it explicitly)
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-ID");

        // Option 2: Subdomain extraction (supporthub.in subdomains)
        if (tenantId == null) {
            String host = exchange.getRequest().getURI().getHost();
            // customer.acme.supporthub.in → resolve tenant "acme"
            tenantId = tenantResolver.fromSubdomain(host);
        }

        if (tenantId == null) return unauthorized(exchange);

        ServerWebExchange mutated = exchange.mutate()
            .request(r -> r.header("X-Tenant-ID", tenantId))
            .build();
        return chain.filter(mutated);
    }
}
```

### 10.3 Circuit Breaker Configuration (Resilience4j)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      ticket-service-cb:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
      ai-service-cb:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s  # AI outages can be longer
  timeout:
    instances:
      ai-calls:
        timeout-duration: 10s  # Max wait for sentiment/suggestion
```

---

## 11. Frontend Architecture

### 11.1 Customer SDK — Headless TypeScript Package

The `@supporthub/customer-sdk` is a **framework-agnostic SDK**, not a UI component library. It handles auth, API calls, state, and event subscriptions. The rendering is left entirely to the embedding application.

```typescript
// packages/customer-sdk/src/client.ts
export class SupportHubClient {
  private baseUrl: string;
  private tenantId: string;
  private accessToken: string | null = null;

  constructor(config: SupportHubConfig) {
    this.baseUrl = config.baseUrl;
    this.tenantId = config.tenantId;
  }

  // Auth
  async sendOtp(phone: string): Promise<void>
  async verifyOtp(phone: string, otp: string): Promise<AuthTokens>
  async refreshToken(): Promise<AuthTokens>
  async logout(): Promise<void>

  // Tickets
  async createTicket(req: CreateTicketRequest): Promise<Ticket>
  async listTickets(filter?: TicketFilter): Promise<PaginatedResponse<Ticket>>
  async getTicket(ticketNumber: string): Promise<TicketDetail>
  async addComment(ticketNumber: string, comment: string): Promise<Activity>
  async reopenTicket(ticketNumber: string, reason: string): Promise<Ticket>
  async resolveTicket(ticketNumber: string): Promise<Ticket>

  // FAQ
  async searchFaq(query: string): Promise<FaqEntry[]>
  async getFaqCategories(): Promise<FaqCategory[]>

  // Orders (for ticket creation context)
  async getRecentOrders(limit?: number): Promise<Order[]>
  async getOrder(orderId: string): Promise<Order>

  // Categories
  async getCategories(): Promise<TicketCategory[]>
}

// React hooks (optional, tree-shakeable)
// packages/customer-sdk/src/react/hooks.ts
export function useTickets(filter?: TicketFilter) { ... }
export function useCreateTicket() { ... }
export function useTicketDetail(ticketNumber: string) { ... }
export function useFaqSearch(query: string) { ... }
export function useOtpAuth() { ... }
```

**Capacitor Integration Example:**
```typescript
// In the existing Capacitor food delivery app:
import { SupportHubClient } from '@supporthub/customer-sdk';

const support = new SupportHubClient({
  baseUrl: 'https://api.supporthub.in',
  tenantId: 'your-food-delivery-tenant-id',
});

// Auth handoff (if customer already logged in to main app):
await support.setAuthToken(existingJwtFromMainApp);

// Open support ticket for an order:
const ticket = await support.createTicket({
  categorySlug: 'order-issues',
  subCategorySlug: 'wrong-item',
  orderId: currentOrder.id,
  title: 'Received wrong item',
  description: 'I ordered biryani but received pasta',
});
```

### 11.2 Agent Dashboard Architecture

```
agent-dashboard/src/
├── App.tsx                         # Router setup
├── layouts/
│   ├── DashboardLayout.tsx         # Sidebar + topbar layout
│   └── AuthLayout.tsx
├── pages/
│   ├── DashboardHome.tsx           # Metric cards + my tickets feed
│   ├── TicketQueue.tsx             # Tabbed queue (my/unassigned/team/all)
│   ├── TicketDetail.tsx            # Split-pane: conversation + context panels
│   ├── CustomerProfile.tsx         # Customer history + stats
│   ├── Reports.tsx                 # Report tab container
│   └── Settings.tsx
├── components/
│   ├── ticket/
│   │   ├── TicketCard.tsx
│   │   ├── TicketFilterBar.tsx
│   │   ├── TicketTimeline.tsx      # Activity feed
│   │   ├── ReplyComposer.tsx       # Tabbed: reply / internal note / resolution
│   │   ├── TicketInfoPanel.tsx     # Right panel: status, priority, assignee
│   │   └── SlaIndicator.tsx
│   ├── ai/
│   │   ├── SentimentBadge.tsx      # Emoji + color coded badge
│   │   ├── AIAssistPanel.tsx       # Sentiment + resolution suggestions panel
│   │   └── ResolutionSuggestionCard.tsx
│   ├── customer/
│   │   ├── CustomerContextPanel.tsx
│   │   └── CustomerTicketHistory.tsx
│   ├── order/
│   │   └── OrderContextCard.tsx
│   └── common/
│       ├── StatusBadge.tsx
│       └── PriorityBadge.tsx
├── stores/
│   ├── authStore.ts                # Zustand: agent auth state
│   ├── notificationStore.ts        # Zustand: in-app notifications (WebSocket)
│   └── filterStore.ts              # Zustand: persisted ticket queue filter state
└── hooks/
    ├── useTicketQueue.ts            # TanStack Query: list + infinite scroll
    ├── useTicketDetail.ts           # TanStack Query: single ticket + polling
    ├── useWebSocket.ts              # WebSocket connection + auto-reconnect
    └── useAiSuggestions.ts          # TanStack Query: AI suggestions (lazy)
```

**Real-Time Agent Notifications (WebSocket):**
```typescript
// hooks/useWebSocket.ts
// Agent dashboard connects to: wss://api.supporthub.in/ws/agent/{agentId}
// API Gateway proxies to a Redis Pub/Sub subscriber
// notification-service publishes to Redis channel: agent:{agentId}:notifications
// Gateway subscribes via Lettuce reactive, streams over WebSocket to browser

export function useWebSocket(agentId: string) {
  const ws = useRef<WebSocket>();
  const { addNotification } = useNotificationStore();

  useEffect(() => {
    ws.current = new WebSocket(`wss://api.supporthub.in/ws/agent/${agentId}`);
    ws.current.onmessage = (e) => {
      const notification = JSON.parse(e.data);
      addNotification(notification);
      // Also invalidate TanStack Query cache for relevant ticket
      if (notification.type === 'TICKET_UPDATED') {
        queryClient.invalidateQueries({ queryKey: ['ticket', notification.ticketId] });
      }
    };
    // Reconnect on disconnect with exponential backoff
  }, [agentId]);
}
```

### 11.3 Shared State and Data Fetching

**TanStack Query (React Query) Configuration:**
```typescript
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,          // 30 seconds before refetch
      gcTime: 5 * 60 * 1000,     // 5 minutes in cache
      retry: 2,
      refetchOnWindowFocus: true,
    },
  },
});
```

---

## 12. MCP Server Architecture

### 12.1 Spring AI MCP Server Setup

The `mcp-server` is a dedicated Spring Boot service that implements the MCP protocol using `spring-ai-starter-mcp-server-webmvc`. It delegates all business logic calls to other microservices via REST (Feign clients).

```java
// mcp-server/src/main/java/in/supporthub/mcp/McpServerApplication.java
@SpringBootApplication
public class McpServerApplication {

    @Bean
    public ToolCallbackProvider customerTools(CustomerTicketTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public ToolCallbackProvider faqTools(FaqTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }

    @Bean
    public ToolCallbackProvider orderTools(OrderTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
```

**Tool Implementations:**
```java
@Service
public class CustomerTicketTools {

    @McpTool(description = """
        Create a new support ticket on behalf of the authenticated customer.
        Use this when the customer has a specific complaint or inquiry that needs agent attention.
        Always confirm the category and describe the issue clearly before calling this tool.
        """)
    public CreateTicketResult createTicket(
        @McpToolParam(description = "Category slug, e.g. 'order-issues'", required = true)
        String categorySlug,
        @McpToolParam(description = "Sub-category slug, e.g. 'wrong-item'", required = true)
        String subCategorySlug,
        @McpToolParam(description = "Order ID if this ticket is related to an order", required = false)
        String orderId,
        @McpToolParam(description = "Brief title for the ticket, max 200 characters", required = true)
        String title,
        @McpToolParam(description = "Detailed description of the issue", required = true)
        String description
    ) {
        // Extract tenant + customer from MCP security context
        McpSecurityContext ctx = McpSecurityContextHolder.get();
        return ticketServiceClient.createTicket(
            ctx.getTenantId(), ctx.getCustomerId(),
            new CreateTicketRequest(categorySlug, subCategorySlug, orderId, title, description, "chatbot")
        );
    }

    @McpTool(description = "Get the current status and latest update for a specific ticket number")
    public TicketStatusResult getTicketStatus(
        @McpToolParam(description = "Ticket number, e.g. 'FC-2024-001234'", required = true)
        String ticketNumber
    ) { ... }

    @McpTool(description = "List the customer's recent open or in-progress tickets")
    public List<TicketSummary> listMyTickets(
        @McpToolParam(description = "Filter by status: open, in_progress, resolved, all", required = false)
        String statusFilter,
        @McpToolParam(description = "Max number of tickets to return, default 5", required = false)
        Integer limit
    ) { ... }
}
```

### 12.2 MCP Authentication

```java
@Configuration
public class McpSecurityConfig {

    // MCP clients present: Authorization: Bearer {mcpToken}
    // mcpToken is a tenant-scoped + user-scoped JWT issued by auth-service
    // with audience: "mcp" to distinguish from regular API tokens

    @Bean
    public McpServerSecurityInterceptor mcpSecurityInterceptor(JwtService jwtService) {
        return exchange -> {
            String token = extractBearerToken(exchange);
            JwtClaims claims = jwtService.validateMcpToken(token);
            McpSecurityContextHolder.set(new McpSecurityContext(
                claims.getTenantId(),
                claims.getUserId(),
                claims.getRole()
            ));
        };
    }
}
```

### 12.3 MCP Resource Endpoints

```java
@Service
public class McpResourceProvider {

    @McpResource(
        uri = "ticket://{tenantId}/{ticketNumber}",
        description = "Full ticket details as structured JSON context"
    )
    public McpResourceContent getTicketResource(
        @PathVariable String tenantId,
        @PathVariable String ticketNumber
    ) {
        TicketDetail detail = ticketServiceClient.getByNumber(tenantId, ticketNumber);
        return McpResourceContent.text(objectMapper.writeValueAsString(detail));
    }

    @McpResource(
        uri = "categories://{tenantId}",
        description = "Available support ticket categories for this tenant"
    )
    public McpResourceContent getCategoriesResource(@PathVariable String tenantId) {
        List<Category> cats = ticketServiceClient.getCategories(tenantId);
        return McpResourceContent.text(objectMapper.writeValueAsString(cats));
    }
}
```

### 12.4 MCP Prompt Templates

```java
@Service
public class McpPromptProvider {

    @McpPrompt(
        name = "customer_support_system_prompt",
        description = "System prompt for customer-facing support chatbot"
    )
    public McpPromptContent buildSystemPrompt(
        @McpPromptParam("tenant_name") String tenantName,
        @McpPromptParam("customer_name") String customerName
    ) {
        return McpPromptContent.of("""
            You are a helpful customer support assistant for %s.
            You are talking to %s.
            
            Your capabilities:
            - Search the FAQ to answer common questions
            - Check the status of existing tickets
            - Create new support tickets when needed
            - Add comments to existing tickets
            - Fetch order details
            
            Always try to resolve via FAQ first before creating a ticket.
            Be empathetic, concise, and always confirm details before taking actions.
            For refunds or financial actions, always create a ticket — never promise outcomes directly.
            """.formatted(tenantName, customerName));
    }
}
```

---

## 13. AI/ML Pipeline Architecture

### 13.1 Sentiment Analysis Pipeline

```
[Kafka: ticket.activity-added OR ticket.created]
        │
        ▼
[ai-service: KafkaConsumer]
        │  Deduplication check: "Have I processed this ticketId in last 5 min?" (Redis TTL key)
        │
        ▼
[SentimentAnalysisService]
        │  1. Aggregate all customer text for ticket from DB
        │  2. Strip PII (phone, email regex substitution)
        │  3. Call Anthropic claude-haiku-4-5-20251001 via Spring AI ChatClient
        │  4. Parse JSON response → SentimentResult
        │  5. Log interaction to MongoDB ai_interactions
        │  6. Log to Langfuse (trace with input hash, output, latency, tokens)
        │
        ▼
[Kafka: ai.results (SentimentAnalysisCompletedEvent)]
        │
        ▼
[ticket-service: AiResultEventListener]
        │  UPDATE tickets SET sentiment_score=?, sentiment_label=?, sentiment_updated_at=NOW()
        │  WHERE id=?
        │
        ▼
[Auto-escalation check]
        │  IF new_label = 'very_negative' AND priority < 'HIGH'
        │  → Auto-escalate priority, notify assigned agent
        │
        ▼
[WebSocket push to agent dashboard]
        Updated sentiment badge live-updates in agent's ticket detail view
```

### 13.2 Resolution Suggestion Pipeline

```
[Agent opens TicketDetail in dashboard]
        │
        ▼
[GET /api/v1/tickets/{id}/ai/resolution-suggestions]
        │  Check Redis cache: ai:suggestions:{tenantId}:{ticketId} → return if hit (TTL 10min)
        │
        ▼
[ai-service: ResolutionSuggestionService]
        │
        ├─ Stage 1: Vector Similarity Search (pgvector)
        │    Query: title + description embedding
        │    Filter: tenant_id = ctx.tenantId
        │    Top-K: 10, cosine similarity ≥ 0.70
        │    Sources: faq_entries + resolution_templates (tagged with metadata.source_type)
        │
        ├─ Stage 2: LLM Ranking (claude-sonnet-4-5 with streaming)
        │    Build ranked prompt: ticket context + 10 candidate resolutions
        │    Stream response chunks back to client (SSE)
        │    Parse final JSON: [{resolution_text, source_type, source_id, confidence, explanation}]
        │
        │  Cache result in Redis: ai:suggestions:{tenantId}:{ticketId} TTL 10min
        │
        ▼
[Response: top 3 ranked suggestions with confidence]
```

### 13.3 Embedding Pipeline (FAQ)

```
[Strapi publishes FAQ / Admin saves FAQ in portal]
        │
        ▼
[faq-service: Webhook handler / Manual trigger]
        │  Upsert FAQ entry in PostgreSQL faq_entries table
        │
        ▼
[Publish: faq.updated Kafka event]
        │
        ▼
[ai-service: FaqEmbeddingConsumer]
        │  1. Fetch full FAQ text from faq-service API
        │  2. Generate embedding via Spring AI EmbeddingModel
        │     Model: text-embedding-3-small (OpenAI) or claude-3-haiku embedding
        │     Input: title + "\n\n" + content (truncated to 8192 tokens)
        │  3. Store Document in pgvector VectorStore (Spring AI)
        │     Metadata: { tenant_id, faq_id, category_tags }
        │
        ▼
[pgvector index updated — available for similarity search immediately]
```

### 13.4 Duplicate Detection Pipeline

```
[Customer creates ticket]
        │
        ▼
[ticket-service: DuplicateDetectionService (async)]
        │  1. Embed: new ticket title + description
        │  2. pgvector search: cosine > 0.85, same customer, same tenant, status IN (OPEN, IN_PROGRESS)
        │  3. If match found → publish ticket.duplicate-detected event
        │
        ▼
[Customer portal: receives duplicate warning via WebSocket or polling]
        Shows: "We found a similar open ticket: FC-2024-001200. Is this the same issue?"
        Customer confirms → abort creation / Customer proceeds → create new with duplicate_of_id link
```

---

## 14. CMS Architecture (Strapi)

### 14.1 Strapi Content Types

**FAQEntry (Collection Type):**
```json
{
  "kind": "collectionType",
  "collectionName": "faq_entries",
  "attributes": {
    "title": { "type": "string", "required": true },
    "content": { "type": "richtext" },
    "slug": { "type": "uid", "targetField": "title" },
    "category_tags": { "type": "json" },
    "is_featured": { "type": "boolean", "default": false },
    "language": { "type": "enumeration", "enum": ["en", "hi", "ta", "te", "kn", "bn", "mr"] },
    "tenant_slug": { "type": "string", "required": true },
    "helpful_score": { "type": "integer", "default": 0 },
    "view_count": { "type": "integer", "default": 0 }
  }
}
```

**FAQCategory (Collection Type):**
```json
{
  "kind": "collectionType",
  "collectionName": "faq_categories",
  "attributes": {
    "name": { "type": "string", "required": true },
    "slug": { "type": "uid", "targetField": "name" },
    "icon": { "type": "string" },
    "sort_order": { "type": "integer" },
    "faq_entries": { "type": "relation", "relation": "oneToMany", "target": "api::faq-entry.faq-entry" }
  }
}
```

### 14.2 Strapi → faq-service Sync Flow

```
Option A: Webhook (preferred for real-time):
  Strapi Admin publishes FAQ → Strapi lifecycle hook triggers
  → HTTP POST to faq-service /internal/cms/webhook/strapi
  → faq-service validates HMAC-SHA256 signature
  → Fetches full FAQ via Strapi REST API GET /api/faq-entries/{id}?populate=*
  → Upserts to PostgreSQL faq_entries
  → Publishes faq.updated to Kafka → ai-service re-embeds

Option B: Scheduled Sync (backup for missed webhooks):
  Nightly 02:00 IST via Spring @Scheduled in faq-service
  → Pull all entries updated in last 25 hours from Strapi
  → Delta upsert

Option C: Manual sync button in Admin Portal:
  POST /api/v1/admin/faq/sync-cms
  → Triggers full sync (Strapi paginated pull → bulk upsert)
  → Returns job_id for async status polling
```

### 14.3 Strapi RBAC for Tenant Staff

```
Role: FAQ_EDITOR_{tenantSlug}
  Permissions: 
    - faq-entry: find, findOne, create, update, publish, unpublish
    - faq-category: find, findOne
  Restricted to: entries where tenant_slug = {tenantSlug}

Role: FAQ_ADMIN
  Full access to all content types, all tenants
```

---

## 15. Authentication & Authorization Architecture

### 15.1 JWT Token Flow

```
CUSTOMER FLOW:
  POST /api/v1/auth/otp/send   → { phone, tenantId }
    └─ auth-service generates 6-digit OTP, stores in Redis, sends SMS via MSG91
  
  POST /api/v1/auth/otp/verify → { phone, otp, tenantId }
    └─ Validates OTP from Redis
    └─ Creates/updates Customer record
    └─ Returns: { accessToken (1h), refreshToken (30d, httpOnly cookie) }

AGENT FLOW:
  POST /api/v1/auth/agent/login → { email, password }
    └─ BCrypt password validation
    └─ If ADMIN role: send 2FA OTP to email, return { requires2FA: true }
    └─ Else: return tokens

MCP FLOW:
  POST /api/v1/auth/mcp-token  → (requires valid customer JWT)
    └─ Issues short-lived MCP-scoped JWT (audience: "mcp", 4h expiry)
    └─ MCP server validates audience claim to reject regular API tokens
```

### 15.2 Role Permission Matrix

```
Resource               CUSTOMER  AGENT  TEAM_LEAD  ADMIN  SUPER_ADMIN
------------------------------------------------------------------
Own tickets (CRUD)       ✓        ✓        ✓         ✓       ✓
All tickets in tenant    ✗        own Q    team      all     all
Assign tickets           ✗        ✗        ✓         ✓       ✓
View agent metrics       ✗        own      team      all     all
Customer PII reveal      ✗        ✓(audit) ✓(audit)  ✓       ✓
Metadata management      ✗        ✗        ✗         ✓       ✓
Tenant management        ✗        ✗        ✗         own     all
Sandbox provisioning     ✗        ✗        ✗         ✗       ✓
```

### 15.3 Tenant Context Propagation

```java
// All downstream services receive X-Tenant-ID via gateway header
// Services extract via TenantContextHolder:

@Component
public class TenantContextFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String tenantId = req.getHeader("X-Tenant-ID");
        TenantContextHolder.setTenantId(tenantId);
        // Also set PostgreSQL session variable for RLS:
        entityManager.createNativeQuery("SET app.current_tenant = :tid")
            .setParameter("tid", tenantId).executeUpdate();
        try { filterChain.doFilter(req, res); }
        finally { TenantContextHolder.clear(); }
    }
}
```

---

## 16. Multi-Tenancy Architecture

### 16.1 Data Isolation Strategy

**Phase 1–3: Shared Schema, Row-Level Security (Postgres)**
- All tables include `tenant_id UUID NOT NULL`.
- PostgreSQL Row-Level Security (RLS) enforces isolation at DB level.
- Application also enforces via `@PreFilter` and service-level checks.
- Redis keys namespaced: `{namespace}:{tenantId}:{key}`.

**Phase 4 (Enterprise Plan): Schema-per-tenant**
- Enterprise tenants get a dedicated PostgreSQL schema: `tenant_{slug}.*`.
- Flyway runs per-tenant migrations.
- Connection routing via AbstractRoutingDataSource based on TenantContextHolder.

### 16.2 Tenant Configuration Cache

```java
// tenant-service provides config; other services cache it in Redis
// Cache key: tenant:config:{tenantId}  TTL: 1 hour

public record TenantConfig(
    String tenantId, String slug, String name, String category,
    String timezone, String locale, BrandingConfig branding,
    FeatureFlags features, IntegrationConfig integrations,
    SlaDefaults slaDefaults
) {}
```

### 16.3 Subdomain Routing

```
customer.foodco.supporthub.in  → tenant: foodco, app: customer-portal
agents.foodco.supporthub.in    → tenant: foodco, app: agent-dashboard
admin.foodco.supporthub.in     → tenant: foodco, app: admin-portal

Custom domain (Enterprise):
myhelp.foodco.in → CNAME → supporthub-loadbalancer.in → SNI routing → tenant: foodco
```

---

## 17. Caching Strategy

### 17.1 Cache Layers

```
L1: In-process cache (Caffeine) — per-service, TTL 30s
    Used for: tenant config, category lists, SLA policies (rarely changing)
    Library: Caffeine via Spring Cache @Cacheable

L2: Redis — distributed, multi-service sharing
    Used for: ticket detail, customer profile, order data, AI suggestions, OTP, sessions
    Library: Spring Data Redis + Lettuce

L3: CDN (CloudFront) — static assets + public FAQ pages
    Used for: FAQ HTML pages (server-side rendered or static), JS bundles, images
```

### 17.2 Cache Invalidation

```
Ticket updated → DELETE ticket:{tenantId}:{ticketId} from Redis
                 DELETE ticket:list:{tenantId}:{customerId}
                 (Kafka event → cache-aside invalidation in ticket-service consumer)

FAQ published  → DELETE faq:* keys for tenant (pattern delete via SCAN)

Tenant config updated → DELETE tenant:config:{tenantId}
                        Publish config.updated Kafka event → all services clear L1 cache
```

---

## 18. File Storage Architecture

### 18.1 Storage Infrastructure

**Development/On-Premise:** MinIO (S3-compatible, self-hosted)
**Production:** AWS S3 with CloudFront CDN

### 18.2 File Upload Flow

```
1. Client requests upload URL:
   POST /api/v1/uploads/presign
   → ticket-service calls S3 GeneratePresignedUrl (PUT, 5min expiry)
   → Returns: { uploadUrl, fileKey }

2. Client uploads directly to S3 (bypasses backend):
   PUT {uploadUrl}  with file bytes

3. Client confirms upload:
   POST /api/v1/tickets/{id}/attachments
   Body: { fileKey, fileName, mimeType, size }
   → ticket-service validates key, creates Attachment record

4. File access:
   GET /api/v1/uploads/{fileKey}/download
   → ticket-service generates GetObject presigned URL (15min expiry)
   → 302 Redirect to presigned URL
```

### 18.3 File Organization

```
S3 Bucket: supporthub-{env}
├── tenants/{tenantId}/
│   ├── tickets/{ticketId}/attachments/{uuid}/{filename}
│   ├── branding/logo.{ext}
│   └── exports/reports/{date}/{exportId}.csv
├── cms/                    # Strapi media
│   └── uploads/{year}/{month}/
```

---

## 19. Notification Architecture

### 19.1 Notification Service Internals

```java
@Service
public class NotificationDispatcher {

    // Channel → Provider mapping (tenant-configurable)
    private final SmsProvider smsProvider;         // MSG91 or Kaleyra
    private final WhatsAppProvider waProvider;      // Meta Cloud API
    private final EmailProvider emailProvider;      // SendGrid or AWS SES
    private final InAppNotifier inAppNotifier;      // Redis Pub/Sub → WebSocket

    public void dispatch(NotificationRequest req) {
        // 1. Resolve template
        String renderedBody = templateEngine.render(req.templateKey(), req.variables());

        // 2. Check customer opt-out preferences
        if (!isOptedIn(req.recipientId(), req.channel())) return;

        // 3. Dispatch by channel
        String providerMsgId = switch (req.channel()) {
            case SMS       -> smsProvider.send(req.phone(), renderedBody);
            case WHATSAPP  -> waProvider.sendTemplate(req.phone(), req.waTemplateId(), req.variables());
            case EMAIL     -> emailProvider.send(req.email(), req.subject(), renderedBody);
            case IN_APP    -> inAppNotifier.push(req.recipientId(), req.payload());
        };

        // 4. Log to MongoDB
        notificationRepository.save(new Notification(..., providerMsgId, "sent"));

        // 5. Webhook to tenant if registered
        if (tenantWebhookRegistry.hasWebhook(req.tenantId(), "notification.sent")) {
            webhookDispatcher.dispatch(...);
        }
    }
}
```

### 19.2 WhatsApp Template Management

WhatsApp Business API requires Meta-approved message templates for outbound messages. Templates are stored in MongoDB and mapped to notification events:

```json
{
  "templateKey": "ticket.resolved.customer.whatsapp",
  "waTemplateName": "ticket_resolved_v1",  // Meta-approved name
  "waTemplateLanguage": "en",
  "parameters": ["customer_name", "ticket_number", "resolution_summary"]
}
```

---

## 20. Observability Architecture

### 20.1 Three Pillars

**Logs (Structured JSON via Logback → CloudWatch/ELK):**
```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "service": "ticket-service",
  "version": "1.2.0",
  "tenantId": "uuid",
  "requestId": "uuid",
  "userId": "uuid",
  "traceId": "otel-trace-id",
  "spanId": "otel-span-id",
  "message": "Ticket created",
  "ticketId": "uuid",
  "ticketNumber": "FC-2024-001234",
  "durationMs": 45
}
```

**Metrics (Micrometer → Prometheus → Grafana):**
```
# Custom business metrics (via @Timed, Counter, Gauge in Spring Actuator)
supporthub_tickets_created_total{tenant, category, channel}
supporthub_tickets_resolved_total{tenant, category}
supporthub_sla_breached_total{tenant, priority, category}
supporthub_ai_sentiment_duration_seconds{model, tenant}
supporthub_ai_resolution_suggestions_duration_seconds{model, tenant}
supporthub_mcp_tool_calls_total{tool_name, tenant, status}
supporthub_kafka_consumer_lag{topic, consumer_group}
supporthub_notification_sent_total{channel, tenant, status}
```

**Traces (OpenTelemetry → Jaeger or AWS X-Ray):**
```java
// Auto-instrumented via spring-boot-starter-actuator + micrometer-tracing-bridge-otel
// All HTTP requests, Kafka producer/consumer, and DB queries traced automatically

// Manual span for AI calls:
@Observed(name = "ai.sentiment.analyze", contextualName = "sentiment-analysis")
public SentimentResult analyze(String text) { ... }
```

### 20.2 Langfuse AI Observability

```java
@Service
public class LangfuseTracedAiService {

    private final Langfuse langfuse;

    public SentimentResult analyzeWithTrace(String ticketId, String text) {
        var trace = langfuse.trace()
            .name("sentiment-analysis")
            .metadata(Map.of("ticketId", ticketId, "tenantId", currentTenant()))
            .start();

        var generation = trace.generation()
            .name("claude-haiku-sentiment")
            .model("claude-haiku-4-5-20251001")
            .input(text)
            .start();

        try {
            SentimentResult result = callAnthropic(text);
            generation.output(result.toString())
                .usage(result.inputTokens(), result.outputTokens())
                .end();
            return result;
        } catch (Exception e) {
            generation.level(ObservationLevel.ERROR).statusMessage(e.getMessage()).end();
            throw e;
        } finally {
            trace.end();
        }
    }
}
```

### 20.3 Alerting Rules

```yaml
# Prometheus alerting rules
groups:
  - name: supporthub-critical
    rules:
      - alert: HighApiErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
        for: 2m
        labels: { severity: critical }

      - alert: AiServiceDown
        expr: up{job="ai-service"} == 0
        for: 1m
        labels: { severity: warning }

      - alert: KafkaConsumerLagHigh
        expr: kafka_consumer_lag > 10000
        for: 5m
        labels: { severity: warning }

      - alert: SlaBreachRateHigh
        expr: rate(supporthub_sla_breached_total[1h]) > 0.20
        for: 10m
        labels: { severity: warning }
```

---

## 21. Deployment Architecture

### 21.1 Kubernetes Manifest Pattern

```yaml
# Per-service Deployment (example: ticket-service)
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ticket-service
  namespace: supporthub-prod
spec:
  replicas: 3
  selector:
    matchLabels: { app: ticket-service }
  template:
    spec:
      containers:
        - name: ticket-service
          image: supporthub/ticket-service:1.2.0
          resources:
            requests: { cpu: "500m", memory: "512Mi" }
            limits:   { cpu: "1000m", memory: "1Gi" }
          env:
            - name: SPRING_DATASOURCE_URL
              valueFrom: { secretKeyRef: { name: db-secret, key: url } }
            - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
              value: kafka-broker:9092
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8082 }
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8082 }
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ticket-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ticket-service
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 70 }
```

### 21.2 Service Scaling Strategy

| Service | Min Replicas | Max Replicas | Scale Trigger |
|---|---|---|---|
| api-gateway | 3 | 20 | CPU 70% |
| ticket-service | 3 | 10 | CPU 70% |
| auth-service | 2 | 8 | CPU 70% / RPS |
| ai-service | 2 | 6 | CPU 80% (LLM calls are CPU-light) |
| notification-service | 2 | 8 | Kafka consumer lag |
| faq-service | 2 | 6 | CPU 70% |
| reporting-service | 2 | 4 | CPU 70% |
| mcp-server | 2 | 6 | CPU 70% |
| strapi cms | 2 | 4 | CPU 70% |

### 21.3 Environment Configuration

```
dev:     docker-compose (all services local)
staging: K8s single-region, 1 replica each, shared PostgreSQL
prod:    K8s multi-AZ (ap-south-1: 3 AZs), RDS Multi-AZ, MSK 3-broker, ElastiCache cluster
```

### 21.4 CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/ci.yml
on: [push, pull_request]
jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21' }
      - run: mvn -B test -pl ticket-service,auth-service,...
      - run: mvn -B verify -pl ticket-service  # Integration tests with Testcontainers

  frontend:
    runs-on: ubuntu-latest
    steps:
      - run: npm ci
      - run: npm run test --workspaces
      - run: npm run build --workspaces

  # On merge to main → staging deploy
  # On tag v*.*.* → prod deploy with manual approval gate
```

---

## 22. Security Architecture

### 22.1 Network Security

```
Internet → CloudFront CDN → ALB (HTTPS only, TLS 1.3) → API Gateway
                                                              │
Internal traffic: mTLS between services (Kubernetes cert-manager)
                  Services communicate via Kubernetes Service DNS
                  No direct pod-to-pod access outside service mesh

Database access: Services in private subnets, RDS/ElastiCache in DB subnet group
                 Security groups: only allow traffic from service pods on specific ports
```

### 22.2 PII Protection

```java
// PII fields encrypted at application layer (AES-256-GCM) before DB storage
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String plaintext) {
        return aesEncryptionService.encrypt(plaintext);  // Returns Base64 ciphertext
    }
    @Override
    public String convertToEntityAttribute(String ciphertext) {
        return aesEncryptionService.decrypt(ciphertext);
    }
}

// Applied to sensitive fields:
@Entity
public class Customer {
    @Convert(converter = EncryptedStringConverter.class)
    private String phone;

    @Convert(converter = EncryptedStringConverter.class)
    private String email;
}
```

### 22.3 Secrets Management

```
AWS Secrets Manager → EKS Pod (via AWS Secrets Manager CSI Driver)
Secret rotation: RDS password rotated every 30 days automatically
Application reads via Spring Cloud AWS: @Value("${spring.datasource.password}")
Anthropic API key: stored in AWS Secrets Manager, injected at pod startup
```

---

## 23. Data Flow Diagrams

### 23.1 Ticket Creation Flow

```
Customer (mobile app with Capacitor)
    │  Uses @supporthub/customer-sdk
    │  POST /api/v1/tickets { category, description, orderId, ... }
    │
    ▼
API Gateway
    │  Validates JWT, extracts tenantId, userId
    │  Routes to ticket-service
    ▼
ticket-service
    │  1. Validate category + sub-category (from L1 cache)
    │  2. Fetch order snapshot (order-sync-service REST call, timeout 2s, cached)
    │  3. Generate ticket number (Redis INCR)
    │  4. Compute SLA due dates (SlaEngine)
    │  5. Persist Ticket to PostgreSQL
    │  6. Publish ticket.created → Kafka
    │  7. Return TicketResponse (synchronous)
    │
    ├──[Kafka]──► notification-service → SMS to customer
    ├──[Kafka]──► ai-service → Queue sentiment analysis (async, ~1-3s)
    └──[Kafka]──► reporting-service → Update Elasticsearch read model
```

### 23.2 Agent Ticket Resolution Flow

```
Agent (browser, agent-dashboard PWA)
    │  Opens TicketDetail page
    │
    ├─1─► GET /api/v1/tickets/{id}  →  ticket-service (full ticket + activities)
    ├─2─► GET /api/v1/tickets/{id}/ai/resolution-suggestions  →  ai-service (streaming)
    │                │
    │                └─ pgvector search → LLM ranking → stream 3 suggestions
    │
    │  Agent reviews suggestions, edits resolution text, clicks "Send Resolution"
    │
    └─3─► POST /api/v1/tickets/{id}/resolve
              { resolution_code, resolution_text, refund_details }
              →  ticket-service
                  │  1. Validate transition: IN_PROGRESS → RESOLVED
                  │  2. Create TicketActivity (resolution_provided)
                  │  3. Update ticket status + resolved_at
                  │  4. Publish ticket.resolved → Kafka
                  │
                  ├──[Kafka]──► notification-service → SMS + WhatsApp to customer
                  └──[Kafka]──► reporting-service → Update funnel + disposition metrics
```

### 23.3 AI Chatbot Interaction Flow (via MCP)

```
End User (WhatsApp / Web Chat Widget)
    │
    ▼
AI Chatbot Agent (external, e.g. Claude claude-sonnet-4-5 with MCP tools)
    │  System prompt from MCP: "customer_support_system_prompt"
    │  User: "My order FC-2024-0056 was wrong item"
    │
    │─1─► MCP Tool: search_faq("wrong item delivered food")
    │         → faq-service: pgvector search → Returns top 3 FAQs
    │         Bot presents FAQ: "If you received wrong item, here's what to do..."
    │
    │  User: "No, I need a replacement or refund"
    │
    │─2─► MCP Tool: get_order_details("FC-2024-0056")
    │         → order-sync-service: returns order context
    │
    │─3─► MCP Tool: create_ticket(...)
    │         → ticket-service: creates ticket, returns FC-2024-001234
    │         Bot: "I've created ticket FC-2024-001234. Our team will respond within 2 hours."
    │
    ▼
MCP Server logs trace to Langfuse: all tool calls, latency, tokens used
```

---

## 24. API Contract Standards

### 24.1 Request/Response Envelope

```json
// Success response (single object)
{
  "data": { ... },
  "meta": {
    "requestId": "uuid",
    "timestamp": "ISO8601",
    "apiVersion": "1.0"
  }
}

// Success response (paginated list)
{
  "data": [ ... ],
  "pagination": {
    "cursor": "base64EncodedCursor",
    "hasMore": true,
    "limit": 25,
    "total": 142
  },
  "meta": { ... }
}

// Error response
{
  "error": {
    "code": "TICKET_NOT_FOUND",
    "message": "Ticket with number FC-2024-999999 not found",
    "details": { "ticketNumber": "FC-2024-999999" },
    "traceId": "otel-trace-uuid"
  },
  "meta": { ... }
}
```

### 24.2 Standard HTTP Status Codes

```
200 OK              — Successful GET/PUT
201 Created         — Successful POST (ticket created, etc.)
204 No Content      — Successful DELETE
400 Bad Request     — Validation failure (includes field-level details)
401 Unauthorized    — Missing or invalid JWT
403 Forbidden       — Insufficient role permissions
404 Not Found       — Resource doesn't exist (or tenant isolation: resource exists but not visible)
409 Conflict        — State conflict (e.g., invalid status transition)
429 Too Many Requests — Rate limit exceeded
500 Internal Server Error — Unhandled exception (with traceId for debugging)
503 Service Unavailable   — Circuit breaker open
```

### 24.3 OpenAPI Generation

Each service exposes `/v3/api-docs` and `/swagger-ui` via springdoc-openapi:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

Annotations:
```java
@Operation(summary = "Create a new support ticket",
           description = "Creates ticket and publishes ticket.created event")
@ApiResponse(responseCode = "201", description = "Ticket created",
             content = @Content(schema = @Schema(implementation = TicketResponse.class)))
@ApiResponse(responseCode = "409", description = "Duplicate ticket detected")
@PostMapping
public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
    @Valid @RequestBody CreateTicketRequest request) { ... }
```

---

## 25. Development Conventions

### 25.1 Package Naming

```
in.supporthub.{service}.{layer}

Layers: controller, service, repository, domain, dto, event, config, exception

Example:
in.supporthub.ticket.service.TicketService
in.supporthub.ticket.domain.Ticket
in.supporthub.ticket.dto.CreateTicketRequest
in.supporthub.ticket.event.TicketEventPublisher
```

### 25.2 Configuration Properties

```yaml
# application.yml base (all services)
server:
  port: ${SERVER_PORT:8080}
spring:
  application.name: ticket-service
  threads.virtual.enabled: true    # Java 21 virtual threads
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASS}
  jpa:
    open-in-view: false            # Prevent N+1 in web layer
    properties.hibernate.default_schema: ${TENANT_SCHEMA:public}
  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    producer.properties:
      enable.idempotence: true
      acks: all

management:
  endpoints.web.exposure.include: health,info,metrics,prometheus
  tracing.sampling.probability: 1.0  # 100% in dev, 10% in prod

supporthub:
  jwt.public-key-location: classpath:keys/public.pem
  tenant.cache-ttl-minutes: 60
```

### 25.3 Test Strategy

```
Unit Tests:        JUnit 5 + Mockito — service layer business logic
Integration Tests: @SpringBootTest + Testcontainers (real PostgreSQL, Redis, Kafka)
Contract Tests:    Spring Cloud Contract — verify API contracts between services
E2E Tests:         Playwright (frontend) — critical user journeys only
AI Tests:          Langfuse eval dataset — regression testing of sentiment/suggestion quality
```

### 25.4 Code Quality Gates (CI)

```
Checkstyle: Google Java Style Guide
SpotBugs: Static analysis
JaCoCo: Code coverage minimum 80% for service layer
OWASP Dependency Check: No critical CVEs in dependencies
SonarQube: Code smell and security hotspot gating
```

---

## Appendix A: Local Development Setup

```bash
# Clone + start all services
git clone https://github.com/rupantar/supporthub
cd supporthub

# Start infrastructure (Postgres, MongoDB, Redis, Kafka, MinIO, Elasticsearch)
docker-compose -f infrastructure/docker/docker-compose.yml up -d

# Start backend services (Maven multi-module)
cd backend
./mvnw spring-boot:run -pl api-gateway &
./mvnw spring-boot:run -pl auth-service &
./mvnw spring-boot:run -pl ticket-service &
# ...or use Intellij IDEA Run Configurations

# Start Strapi CMS
cd cms && npm run develop

# Start frontend apps
cd frontend
npm install
npm run dev -w apps/agent-dashboard   # Port 3001
npm run dev -w apps/admin-portal      # Port 3002
npm run dev -w apps/customer-portal   # Port 3000

# Seed development data
cd backend && ./mvnw exec:java -Dexec.mainClass="in.supporthub.scripts.DevSeed"
```

## Appendix B: Architecture Decision Records (ADRs)

| ADR | Decision | Rationale |
|---|---|---|
| ADR-001 | Java 21 + Virtual Threads over WebFlux | Simpler code, same throughput for I/O-bound work. Avoids reactive complexity for team. |
| ADR-002 | PostgreSQL + pgvector over dedicated vector DB | Avoids operational overhead of separate Pinecone/Weaviate. pgvector with HNSW index meets Phase 1–3 needs (<1M vectors per tenant). Revisit at 10M+ vectors. |
| ADR-003 | Strapi CMS over Payload | Strapi's RBAC for non-technical content editors is superior. Larger community, more stable at scale. Payload's Next.js dependency adds frontend coupling we don't want. |
| ADR-004 | Headless SDK for customer frontend | Allows embedding in existing Capacitor apps without complete rewrite. Separates API logic from rendering. SDK can be versioned and published to npm. |
| ADR-005 | Kafka over RabbitMQ | Durable log for audit trail, consumer group replay for reporting-service bootstrapping, better fit for time-ordered ticket events. RabbitMQ would require additional setup for equivalent durability. |
| ADR-006 | Spring AI MCP Server over custom MCP implementation | Official SDK maintained by Spring + Anthropic. @McpTool annotation removes boilerplate. SSE transport is production-grade. Avoids custom protocol implementation risk. |
| ADR-007 | Row-Level Security for multi-tenancy (Phase 1-3) | Lower operational overhead than schema-per-tenant. RLS at DB level adds defense-in-depth. Application-level tenant checks plus DB-level RLS = two independent isolation layers. |
| ADR-008 | CQRS for reporting (Elasticsearch read model) | Decouples analytics from operational load on PostgreSQL. Enables rich aggregations (histograms, date ranges, multi-field facets) that are expensive in Postgres at scale. |
| ADR-009 | MongoDB for AI interaction logs and notifications | Schema evolution without migrations for AI payload changes. High write volume for notifications fits MongoDB's append-heavy workload. Atlas Search available if pgvector hits limits. |
| ADR-010 | claude-haiku for sentiment, claude-sonnet for resolution | Haiku: 3x cheaper, 2x faster — sufficient for binary sentiment classification. Sonnet: better reasoning for ranking complex resolution candidates. Cost-optimal per use case. |

---

*Document Version: 1.0 | Prepared by: SupportHub Architecture Team | Rupantar Technologies*
*Date: March 2026 | For use with Claude Code end-to-end implementation*
