# SupportHub

Multi-tenant, AI-native customer support platform built for the Indian market. Serves food delivery (Phase 1), expanding to fashion, electronics, and grocery as a white-label B2B2C SaaS.

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Key Features](#2-key-features)
3. [Architecture](#3-architecture)
4. [Repository Structure](#4-repository-structure)
5. [Tech Stack](#5-tech-stack)
6. [Local Setup](#6-local-setup)
7. [Development Commands](#7-development-commands)
8. [Production Setup](#8-production-setup)
9. [CI/CD Pipeline](#9-cicd-pipeline)
10. [Environment Variables](#10-environment-variables)

---

## 1. Project Overview

SupportHub exposes four surface areas:

| Surface | Description |
|---|---|
| **Customer Portal** | Mobile-first React PWA — OTP login, ticket creation, FAQ self-service |
| **Agent Dashboard** | Desktop-first React PWA — queue management, AI assistance, real-time updates |
| **Admin / Ops Portal** | Tenant config, agent management, SLA rules, reports |
| **API + MCP Layer** | REST API through Spring Cloud Gateway + Spring AI MCP server for chatbot agents |

**Primary users:** End customers, customer care agents, ops admins, AI chatbot agents (via MCP).

---

## 2. Key Features

- **Ticket lifecycle** — create, assign, status machine (open → in-progress → resolved → closed), reopen, escalate
- **AI sentiment analysis** — Anthropic Claude Haiku classifies each ticket's sentiment in real-time (supports English, Hindi, Hinglish, Tamil, Telugu, and more)
- **AI resolution suggestions** — Claude Sonnet ranks the top 3 probable resolutions using RAG over FAQ + resolution templates
- **Semantic FAQ search** — pgvector cosine similarity + Elasticsearch BM25 hybrid search
- **MCP server** — exposes `create_ticket`, `get_ticket`, `list_tickets`, `search_faq` tools over SSE for any MCP-compatible AI agent
- **Multi-tenancy** — full data isolation via `tenant_id` + PostgreSQL Row-Level Security on every table
- **Real-time** — STOMP over WebSocket for live ticket updates on the agent dashboard
- **Notifications** — SMS (MSG91), WhatsApp (Meta Business API), Email (SendGrid), in-app
- **File attachments** — MinIO / AWS S3 with 15-minute presigned URLs
- **CQRS reporting** — Kafka consumers project ticket events into Elasticsearch read models

---

## 3. Architecture

### System Context

```
  [Customer]          [Agent]           [Admin]         [AI Chatbot]
      │                  │                  │                │
      └──────────────────┴──────────────────┴────────────────┘
                                  │ HTTPS / WSS
                        ┌─────────▼──────────┐
                        │   API Gateway :8080 │  ← JWT auth, tenant routing,
                        │  Spring Cloud GW    │    rate limiting, circuit breaker
                        └─────────┬──────────┘
                ┌─────────────────┼─────────────────────┐
                │                 │                       │
        ┌───────▼──────┐  ┌───────▼──────┐   ┌──────────▼──────┐
        │ auth   :8081 │  │ ticket :8082 │   │ customer :8083  │
        │ ai     :8084 │  │ notif  :8085 │   │ faq      :8086  │
        │ report :8087 │  │ tenant :8088 │   │ orders   :8089  │
        │ mcp    :8090 │  └──────────────┘   └─────────────────┘
        └──────────────┘
                │ Kafka (async events)
    ┌───────────┴──────────────────────────────────────────┐
    │  PostgreSQL  MongoDB  Redis  Elasticsearch  MinIO     │
    └──────────────────────────────────────────────────────┘
```

### Key Patterns

| Pattern | Implementation |
|---|---|
| **Multi-tenancy** | `tenant_id` on every table + PostgreSQL RLS + `TenantContextHolder` propagated from gateway |
| **Database-per-service** | No cross-service DB queries; each service owns its schema |
| **Event-driven** | Kafka for all cross-service state changes — never synchronous REST for side effects |
| **CQRS** | `reporting-service` maintains Elasticsearch read models from Kafka events |
| **AI non-blocking** | Sentiment and resolution run asynchronously; ticket ops continue if AI is down |
| **Virtual threads** | `spring.threads.virtual.enabled=true` on all services (Java 21 Project Loom) |

### Kafka Event Flow

```
ticket-service  ──► ticket.created         ──► ai-service (sentiment)
                                            ──► notification-service (customer notif)
                                            ──► reporting-service (ES projection)

ai-service      ──► sentiment.completed    ──► ticket-service (update fields)
                                            ──► reporting-service (ES update)

ticket-service  ──► ticket.status.changed  ──► notification-service
                                            ──► reporting-service

tenant-service  ──► tenant.onboarded       ──► notification-service (welcome comms)
```

---

## 4. Repository Structure

```
supporthub/
├── run-local.sh                        ← single-command local runner
├── backend/                            ← Java 21 + Spring Boot 3.3 (Maven multi-module)
│   ├── pom.xml                         ← parent POM
│   ├── shared/                         ← shared DTOs, events, exceptions, security
│   ├── api-gateway/                    ← Spring Cloud Gateway — port 8080
│   ├── auth-service/                   ← OTP + agent login, JWT — port 8081
│   ├── ticket-service/                 ← ticket CRUD, SLA, WebSocket — port 8082
│   ├── customer-service/               ← customer profiles, addresses — port 8083
│   ├── ai-service/                     ← sentiment + resolution via Anthropic — port 8084
│   ├── notification-service/           ← SMS / WhatsApp / Email / in-app — port 8085
│   ├── faq-service/                    ← FAQ CRUD, pgvector search, Strapi sync — port 8086
│   ├── reporting-service/              ← Elasticsearch CQRS, CSV export — port 8087
│   ├── tenant-service/                 ← tenant onboarding, config — port 8088
│   ├── order-sync-service/             ← OMS proxy, Redis order cache — port 8089
│   └── mcp-server/                     ← Spring AI MCP tools over SSE — port 8090
│
├── frontend/                           ← npm workspaces (Node 20)
│   ├── apps/
│   │   ├── customer-portal/            ← React 18 + Vite — port 3000
│   │   ├── agent-dashboard/            ← React 18 + Vite — port 3001
│   │   └── admin-portal/               ← React 18 + Vite — port 3002
│   └── packages/
│       ├── customer-sdk/               ← headless TypeScript SDK (@supporthub/customer-sdk)
│       └── ui-components/              ← shared shadcn/ui + Tailwind component library
│
├── infrastructure/
│   ├── docker/
│   │   ├── docker-compose.yml          ← infra stack (Postgres, Mongo, Redis, Kafka, ES, MinIO, Strapi)
│   │   ├── docker-compose.services.yml ← all 11 services + 3 frontend apps
│   │   ├── .env.example                ← copy to .env and fill secrets
│   │   ├── nginx/spa.conf              ← nginx SPA config for frontend containers
│   │   ├── Dockerfile.template         ← multi-stage Java build template
│   │   └── init-db/                    ← PostgreSQL init scripts (pgvector extension)
│   ├── k8s/                            ← Kubernetes Kustomize manifests
│   └── terraform/                      ← AWS infrastructure (EKS, RDS, ElastiCache, MSK)
│
└── .github/workflows/
    ├── ci.yml                          ← PR validation (test, lint, build, OWASP scan)
    ├── deploy-staging.yml
    └── deploy-prod.yml
```

### Backend Service Layout (per service)

```
{service}/src/main/java/in/supporthub/{service}/
├── {Service}Application.java
├── config/          ← Spring configs (Security, Kafka, Redis, etc.)
├── controller/      ← REST controllers (validate + delegate only)
├── service/         ← Business logic (@Transactional)
├── repository/      ← Spring Data interfaces (data access only)
├── domain/          ← JPA entities, enums (no service deps)
├── dto/             ← Java Records (request/response/event)
├── event/           ← Kafka producers + consumers
└── exception/       ← Typed exceptions extending AppException
```

---

## 5. Tech Stack

### Backend

| Layer | Technology |
|---|---|
| Language | Java 21 (virtual threads, records, pattern matching) |
| Framework | Spring Boot 3.3, Spring Cloud 2023.0 |
| API Gateway | Spring Cloud Gateway (JWT, tenant routing, rate limiting, circuit breaker) |
| AI | Spring AI — Anthropic Claude Haiku (sentiment), Claude Sonnet (resolution) |
| MCP | spring-ai-starter-mcp-server-webmvc (SSE transport) |
| ORM | Spring Data JPA + Hibernate, Spring Data MongoDB |
| Migrations | Flyway |
| Messaging | Apache Kafka (Confluent) |
| Security | Spring Security, JJWT, RSA key-pair JWT |
| Observability | Micrometer + Prometheus + OpenTelemetry + Langfuse (LLM traces) |
| Build | Maven Wrapper (`./mvnw`) |

### Frontend

| Layer | Technology |
|---|---|
| Language | TypeScript (strict mode) |
| Framework | React 18 + Vite |
| Server state | TanStack Query |
| Global state | Zustand |
| Forms | React Hook Form |
| UI components | shadcn/ui + Tailwind CSS |
| Real-time | STOMP over WebSocket (agent dashboard) |
| Testing | Vitest + Testing Library |
| E2E | Playwright |

### Data Stores

| Store | Version | Used By |
|---|---|---|
| PostgreSQL + pgvector | 16 | auth, ticket, customer, tenant, order-sync, faq (embeddings) |
| MongoDB | 7 | ai-service (interaction logs), notification-service (history), faq-service (documents) |
| Redis | 7 | All services — cache, sessions, rate limiting, idempotency |
| Elasticsearch | 8 | reporting-service (CQRS read models, full-text search) |
| Apache Kafka | 7.6 (Confluent) | All services — async event bus |
| MinIO / AWS S3 | Latest | ticket-service — file attachments |

### Infrastructure

| Component | Dev | Production |
|---|---|---|
| Container runtime | Docker Compose | Kubernetes (EKS) + Helm |
| IaC | — | Terraform (AWS) |
| Database | Docker | AWS RDS PostgreSQL Multi-AZ |
| Cache | Docker | AWS ElastiCache Redis |
| Messaging | Docker | AWS MSK (Managed Kafka) |
| Search | Docker | AWS OpenSearch |
| Storage | MinIO (Docker) | AWS S3 + CloudFront |
| CMS | Docker (Node) | ECS Fargate |
| Registry | Local | AWS ECR |

---

## 6. Local Setup

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Docker Desktop (or Docker Engine + Compose v2) | Docker 24+, Compose v2 |
| Java (Eclipse Temurin recommended) | 21 |
| Node.js | 18 (20 recommended) |
| Git | Any recent |

### One-Command Start

```bash
git clone <repo-url>
cd supporthub

# First run: copies .env.example → infrastructure/docker/.env
# Builds all JARs + frontend, starts every service
./run-local.sh
```

That's it. The script handles everything in order:

1. Checks prerequisites
2. Copies `.env.example` → `infrastructure/docker/.env` on first run
3. Builds all 11 Spring Boot JARs (`./mvnw clean package -DskipTests`)
4. Builds all 3 frontend apps (`npm run build --workspaces`)
5. Starts the infrastructure stack (Postgres, Mongo, Redis, Kafka, Elasticsearch, MinIO, Strapi)
6. Waits for each infra container to pass its healthcheck
7. Starts all 11 microservices + 3 nginx-served frontend apps
8. Polls each service `/actuator/health` until healthy
9. Prints the access URL table

### Useful Flags

```bash
./run-local.sh --skip-build    # restart without rebuilding (fast after first run)
./run-local.sh --infra-only    # start only Postgres/Mongo/Redis/Kafka/ES/MinIO
./run-local.sh --down          # stop and remove all containers
```

### Access URLs (after startup)

| Service | URL |
|---|---|
| Customer Portal | http://localhost:3000 |
| Agent Dashboard | http://localhost:3001 |
| Admin Portal | http://localhost:3002 |
| API Gateway | http://localhost:8080 |
| Auth Service Swagger | http://localhost:8081/swagger-ui.html |
| Ticket Service Swagger | http://localhost:8082/swagger-ui.html |
| MinIO Console | http://localhost:9001 (user: `minioadmin` / pass: `minioadmin`) |
| Strapi CMS | http://localhost:1337/admin |
| Elasticsearch | http://localhost:9200 |

### First-Time Configuration

**Set your Anthropic API key** (required for AI features):

```bash
# Edit the generated .env file
nano infrastructure/docker/.env

# Set this line:
ANTHROPIC_API_KEY=sk-ant-your-real-key-here
```

AI features (sentiment analysis, resolution suggestions) will show "Analysis pending" until the key is set. All other ticket operations work without it.

**Create a tenant** (required before raising tickets):

```bash
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{"name":"Demo Store","slug":"demo","planType":"TRIAL"}'
```

### Running Services Locally (without Docker)

Use `--infra-only` to start just the backing stores, then run services natively:

```bash
./run-local.sh --infra-only

# Backend (from /backend)
./mvnw spring-boot:run -pl auth-service   -Dspring.profiles.active=dev
./mvnw spring-boot:run -pl ticket-service -Dspring.profiles.active=dev
# ... repeat per service

# Frontend (from /frontend)
npm run dev -w apps/customer-portal   # http://localhost:3000
npm run dev -w apps/agent-dashboard   # http://localhost:3001
npm run dev -w apps/admin-portal      # http://localhost:3002
```

---

## 7. Development Commands

### Backend

```bash
cd backend

./mvnw clean package -DskipTests                            # build all services
./mvnw clean package -pl ticket-service -DskipTests         # build one service
./mvnw test -pl ticket-service                              # unit tests
./mvnw verify -pl ticket-service                            # integration tests (needs Docker)
./mvnw test -pl ticket-service -Dtest=TicketServiceTest     # single test class
./mvnw checkstyle:check spotbugs:check pmd:check -P analysis # static analysis
./mvnw dependency-check:check -pl ticket-service            # OWASP CVE scan
```

### Frontend

```bash
cd frontend

npm ci                                      # install all workspace dependencies
npm run build --workspaces                  # build all apps + packages
npm run test --workspaces                   # run all Vitest tests
npm run lint --workspaces                   # ESLint all packages
npm run typecheck --workspaces              # TypeScript strict check

npm run dev -w apps/customer-portal         # dev server on :3000
npm run dev -w apps/agent-dashboard         # dev server on :3001
npm run dev -w apps/admin-portal            # dev server on :3002
```

### E2E Tests (Playwright)

```bash
# Requires all services running (./run-local.sh first)
cd frontend
npx playwright test
npx playwright test --ui                    # interactive UI mode
```

### Docker Compose (manual)

```bash
# Infrastructure only
docker compose -f infrastructure/docker/docker-compose.yml \
  --env-file infrastructure/docker/.env up -d

# All services + frontend
docker compose -f infrastructure/docker/docker-compose.services.yml \
  --env-file infrastructure/docker/.env up -d

# View logs
docker logs -f supporthub-api-gateway
docker logs -f supporthub-ticket-service

# Stop everything
docker compose -f infrastructure/docker/docker-compose.services.yml down
docker compose -f infrastructure/docker/docker-compose.yml down
```

---

## 8. Production Setup

### Infrastructure Provisioning (Terraform)

```bash
cd infrastructure/terraform

# Initialise
terraform init

# Plan (review changes before applying)
terraform plan -var-file=environments/prod.tfvars

# Apply
terraform apply -var-file=environments/prod.tfvars
```

Terraform provisions: EKS cluster, RDS PostgreSQL Multi-AZ, ElastiCache Redis, MSK Kafka, OpenSearch, S3 buckets, ECR repositories, VPC, ALB, IAM roles.

### Container Images

Build and push to ECR via CI/CD (triggered on merge to `main`). To build manually:

```bash
# Authenticate to ECR
aws ecr get-login-password --region ap-south-1 | \
  docker login --username AWS --password-stdin <account>.dkr.ecr.ap-south-1.amazonaws.com

# Build a service image
docker build \
  -f infrastructure/docker/Dockerfile.template \
  --build-arg SERVICE=ticket-service \
  -t <account>.dkr.ecr.ap-south-1.amazonaws.com/supporthub/ticket-service:v1.0.0 \
  backend/

docker push <account>.dkr.ecr.ap-south-1.amazonaws.com/supporthub/ticket-service:v1.0.0
```

### Kubernetes Deployment

```bash
# Apply base manifests
kubectl apply -k infrastructure/k8s/base/

# Apply environment overlay
kubectl apply -k infrastructure/k8s/overlays/prod/

# Verify rollout
kubectl rollout status deployment/ticket-service -n supporthub
kubectl get pods -n supporthub
```

### Deployment Order

Always follow this order to avoid migration / dependency failures:

```
1. Infrastructure (RDS, Redis, Kafka, ES) — must be healthy
2. DB migrations  — Flyway runs automatically on service startup
3. tenant-service — must be up before any authenticated requests
4. auth-service   — must be up before api-gateway validates JWTs
5. Core services  — ticket, customer, faq, notification, reporting (parallel)
6. ai-service     — can be delayed; ticket ops work without it
7. mcp-server     — can be delayed; chatbot integrations work without it
8. api-gateway    — last; routes to all services above
9. Frontend apps  — after gateway is healthy
```

### Secrets Management

All secrets are stored in **AWS Secrets Manager**. Services load them at startup via `spring-cloud-aws-secrets-manager`. Never store secrets in `application.yml` or Kubernetes manifests.

Required secrets per service (example for `ticket-service`):

```
/supporthub/prod/ticket-service/db-password
/supporthub/prod/ticket-service/redis-password
/supporthub/prod/shared/jwt-public-key
```

### Health Checks

Every service exposes `/actuator/health` (Spring Boot Actuator). The Kubernetes liveness and readiness probes are configured in `infrastructure/k8s/base/{service}/deployment.yaml`.

```bash
# Check gateway health
curl http://localhost:8080/actuator/health

# Check all pods
kubectl get pods -n supporthub

# Tail logs for a service
kubectl logs -f deployment/ticket-service -n supporthub
```

### Production Environment Variables

Key variables that differ from dev defaults:

| Variable | Dev Default | Production |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://postgres:5432/supporthub` | RDS endpoint |
| `REDIS_HOST` | `redis` | ElastiCache endpoint |
| `KAFKA_SERVERS` | `kafka:29092` | MSK bootstrap servers |
| `ELASTICSEARCH_URL` | `http://elasticsearch:9200` | OpenSearch endpoint |
| `AWS_S3_ENDPOINT` | `http://minio:9000` | *(remove — uses real S3)* |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| `ANTHROPIC_API_KEY` | *(set in .env)* | AWS Secrets Manager |

---

## 9. CI/CD Pipeline

Every pull request to `develop` or `main` runs the full pipeline in parallel:

```
PR / Push
    │
    ├── backend-unit-tests          (./mvnw test)
    │       │
    │       └── backend-integration-tests  (./mvnw verify, Testcontainers)
    │
    ├── backend-security-scan       (OWASP Dependency Check — fails on CVSS ≥ 8)
    ├── backend-code-analysis       (Checkstyle + SpotBugs + PMD)
    │
    └── frontend-build-test         (typecheck + lint + vitest + build)
            │
            └── build-images        (Docker build → ECR push, on develop/tags only)
                    │
                    └── deploy-staging   (Kubernetes rolling update)
                            │
                            └── deploy-prod  (manual approval gate)
```

Workflow files: `.github/workflows/ci.yml`, `deploy-staging.yml`, `deploy-prod.yml`

---

## 10. Environment Variables

Full reference is in `infrastructure/docker/.env.example`. Key variables:

```bash
# ── PostgreSQL ──────────────────────────────────────
POSTGRES_USER=supporthub
POSTGRES_PASSWORD=supporthub_dev_password
DB_URL=jdbc:postgresql://localhost:5432/supporthub

# ── MongoDB ─────────────────────────────────────────
MONGODB_URI=mongodb://supporthub:supporthub_dev_password@localhost:27017/supporthub?authSource=admin

# ── Redis ───────────────────────────────────────────
REDIS_HOST=localhost
REDIS_PASSWORD=supporthub_dev_password

# ── Kafka ───────────────────────────────────────────
KAFKA_SERVERS=localhost:9092

# ── Elasticsearch ───────────────────────────────────
ELASTICSEARCH_URI=http://localhost:9200

# ── MinIO / S3 ──────────────────────────────────────
AWS_S3_ENDPOINT=http://localhost:9000     # remove in prod (uses real S3)
AWS_S3_BUCKET=supporthub-dev
AWS_ACCESS_KEY_ID=minioadmin
AWS_SECRET_ACCESS_KEY=minioadmin

# ── AI (required for sentiment + resolution) ────────
ANTHROPIC_API_KEY=sk-ant-REPLACE_ME
ANTHROPIC_SENTIMENT_MODEL=claude-haiku-4-5-20251001
ANTHROPIC_RESOLUTION_MODEL=claude-sonnet-4-5

# ── Notifications (optional in dev) ─────────────────
MSG91_API_KEY=
SENDGRID_API_KEY=
WHATSAPP_ACCESS_TOKEN=

# ── Observability (optional) ────────────────────────
LANGFUSE_PUBLIC_KEY=
LANGFUSE_SECRET_KEY=
```

Copy and edit before running:

```bash
cp infrastructure/docker/.env.example infrastructure/docker/.env
```
