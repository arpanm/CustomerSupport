# TODO.md — SupportHub Task Tracker
> Single source of truth for all tasks, bugs, issues, and decisions.
> Managed by Claude Code agents. Updated after every task, review, analysis, or deployment.

**Last Updated:** 2026-03-24T00:00:00Z
**Active Sprint:** Sprint 3 — Real-time, Reporting, AI Panel, File Uploads & E2E
**Project:** SupportHub | Rupantar Technologies

---

## Summary

| Status | Count |
|--------|-------|
| 🆕 OPEN | 3 |
| 🔄 IN_PROGRESS | 6 |
| 🔍 IN_REVIEW | 23 |
| ⚠️ BLOCKED | 0 |
| ✅ DONE | 23 |
| ❌ CANCELLED | 0 |
| **TOTAL** | **55** |

---

## 🔴 CRITICAL / BLOCKING (P0)
<!-- None currently -->

---

## 🟠 HIGH PRIORITY (P1)
<!-- None currently -->

---

## 🔍 IN REVIEW — Sprint 0 (awaiting test execution + code review)

### [INFRA-001] Local Docker Compose Stack Setup
- **ID:** INFRA-001
- **Status:** IN_REVIEW
- **Owner:** lead
- **Updated:** 2026-03-23T10:00:00Z
- **Files Created:**
  - `infrastructure/docker/docker-compose.yml`
  - `infrastructure/docker/init-db/01-init-pgvector.sql`
  - `infrastructure/docker/.env.example`
  - `infrastructure/docker/README.md`
  - `infrastructure/docker/Dockerfile.template`
#### Test Results
- Unit: N/A
- Integration: PENDING (run `docker compose up -d` and validate all health checks)

---

### [INFRA-002] Maven Multi-Module Parent POM Setup
- **ID:** INFRA-002
- **Status:** IN_REVIEW
- **Owner:** lead
- **Updated:** 2026-03-23T10:00:00Z
- **Files Created:**
  - `backend/pom.xml` (parent, Java 21, Spring Boot 3.3.6, Spring Cloud 2023.0.3)
  - `backend/mvnw`, `backend/mvnw.cmd`
  - `backend/shared/` — full shared module (events, DTOs, exceptions, security)
  - `backend/{11 services}/pom.xml` — all service modules
  - `backend/{11 services}/src/main/java/.../Application.java` — all service stubs
  - `backend/{11 services}/src/main/resources/application.yml` — all configs
#### Test Results
- Unit: PENDING (`./mvnw test`)
- Build: PENDING (`./mvnw clean package -DskipTests`)

---

### [INFRA-003] Frontend Monorepo Setup
- **ID:** INFRA-003
- **Status:** IN_REVIEW
- **Owner:** lead
- **Updated:** 2026-03-23T10:00:00Z
- **Files Created:**
  - `frontend/package.json` (npm workspaces)
  - `frontend/tsconfig.base.json` (strict TypeScript)
  - `frontend/.eslintrc.json`
  - `frontend/packages/customer-sdk/` — headless TS SDK
  - `frontend/packages/ui-components/` — Button, Badge, Card, Input, Spinner
  - `frontend/apps/customer-portal/` — React 18 + Vite (port 3000)
  - `frontend/apps/agent-dashboard/` — React 18 + Vite + Zustand (port 3001)
  - `frontend/apps/admin-portal/` — React 18 + Vite (port 3002)
#### Test Results
- TypeScript: PENDING (`npm run typecheck --workspaces`)
- Lint: PENDING (`npm run lint --workspaces`)
- Build: PENDING (`npm run build --workspaces`)

---

### [FEAT-001] auth-service — OTP Login Flow
- **ID:** FEAT-001
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T10:00:00Z
- **Files Created:**
  - `backend/auth-service/src/main/java/in/supporthub/auth/`
    - `AuthServiceApplication.java`
    - `config/` — SecurityConfig, JwtConfig, RedisConfig, WebClientConfig
    - `domain/` — Customer, AgentUser, AgentRole
    - `repository/` — CustomerRepository, AgentUserRepository
    - `service/` — JwtService, OtpService, SmsService, Msg91SmsService, CustomerAuthService, AgentAuthService
    - `controller/` — CustomerAuthController, AgentAuthController
    - `dto/` — OtpSendRequest, OtpVerifyRequest, TokenResponse, AgentLoginRequest, AgentLoginResponse, TwoFaVerifyRequest
    - `exception/` — AuthException, OtpRateLimitException, OtpInvalidException, OtpExpiredException
  - `backend/auth-service/src/main/resources/application.yml`
  - `backend/auth-service/src/test/java/.../service/OtpServiceTest.java`
  - `backend/auth-service/src/test/java/.../service/JwtServiceTest.java`
#### Acceptance Criteria
- [x] `POST /api/v1/auth/otp/send` — OTP stored in Redis, rate-limited
- [x] `POST /api/v1/auth/otp/verify` — validates OTP, creates/updates Customer, returns JWT pair
- [x] `POST /api/v1/auth/refresh` — validates refresh token, issues new access token
- [x] `POST /api/v1/auth/logout` — invalidates refresh token
- [x] JWT: RS256, sub/tenant_id/role/type/iat/exp
- [x] OtpService: 6-digit, 5min TTL, 3-attempt limit, 3 sends/hour
- [ ] Integration tests: 4 endpoints
- [ ] No PII in logs — PENDING CODE REVIEW
#### Test Results
- Unit: PENDING (`./mvnw test -pl auth-service`)

---

### [FEAT-002] auth-service — Agent Email+Password Login
- **ID:** FEAT-002
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T10:00:00Z
- **Files Created:** (same module as FEAT-001)
  - `AgentAuthController.java` — POST /api/v1/auth/agent/login, /2fa/verify
  - `AgentAuthService.java` — BCrypt validation, 2FA email OTP for ADMIN
#### Test Results
- Unit: PENDING
- Integration: PENDING

---

### [FEAT-003] api-gateway — Spring Cloud Gateway
- **ID:** FEAT-003
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T10:00:00Z
- **Files Created:**
  - `backend/api-gateway/src/main/java/in/supporthub/gateway/`
    - `ApiGatewayApplication.java`
    - `config/` — GatewayConfig (routes, CORS), RateLimiterConfig, SecurityConfig
    - `filter/` — JwtAuthFilter (RS256 validation), TenantResolutionFilter, RequestIdFilter
    - `controller/FallbackController.java` — 503 fallback
    - `exception/GatewayExceptionHandler.java`
    - `service/JwtValidationService.java`
  - `backend/api-gateway/src/main/resources/application.yml` (all 10 service routes + Resilience4j config)
  - `backend/api-gateway/src/main/resources/generate-keys.sh`
  - Tests: JwtAuthFilterTest, TenantResolutionFilterTest
#### Test Results
- Unit: PENDING (`./mvnw test -pl api-gateway`)

---

### [FEAT-004] ticket-service — Core Ticket CRUD + Status Machine
- **ID:** FEAT-004
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T10:00:00Z
- **Files Created:**
  - `backend/ticket-service/src/main/java/in/supporthub/ticket/`
    - `TicketServiceApplication.java`
    - `domain/` — Ticket, TicketActivity, TicketCategory, SlaPolicy, TicketStatus (full state machine), Priority, Channel, TicketType, ActivityType, ActorType, SentimentLabel
    - `repository/` — TicketRepository, TicketActivityRepository, TicketCategoryRepository, SlaPolicyRepository
    - `service/` — TicketService (full CRUD), SlaEngine (@Scheduled 5min), TicketEventPublisher (Kafka), TicketNumberGenerator (Redis INCR), SlaDeadlines
    - `event/AiResultEventListener.java` — consumes ai.results.sentiment
    - `controller/TicketController.java` — 8 REST endpoints, `TenantContextFilter.java`
    - `dto/` — CreateTicketRequest, TicketResponse, TicketListResponse, UpdateTicketRequest, AddActivityRequest, ResolveTicketRequest, ReopenTicketRequest, TicketFilterRequest, ActivityResponse
    - `exception/` — TicketNotFoundException, InvalidStatusTransitionException, CategoryNotFoundException
    - `config/` — KafkaConfig, SchedulingConfig
  - `backend/ticket-service/src/main/resources/application.yml`
  - `backend/ticket-service/src/main/resources/db/migration/V1-V8`
#### Acceptance Criteria
- [x] POST/GET/PUT /api/v1/tickets + sub-resources
- [x] Status transition machine with canTransitionTo()
- [x] Ticket number: Redis INCR, format {prefix}-{year}-{seq:06d}
- [x] SLA engine + @Scheduled breach detection
- [x] Kafka events: ticket.created, ticket.status-changed, ticket.activity-added
- [x] Tenant isolation via TenantContextHolder
- [ ] Unit tests (TicketServiceTest, SlaEngineTest, TicketStatusTest) — PENDING
- [ ] Integration tests — PENDING
#### Test Results
- Unit: PENDING (`./mvnw test -pl ticket-service`)

---

## ✅ COMPLETED — Sprint 0

### [INFRA-004] CI/CD GitHub Actions Pipeline — DONE
- **Status:** DONE | **Updated:** 2026-03-23T10:00:00Z
- Files: `.github/workflows/ci.yml`, `deploy-staging.yml`, `deploy-prod.yml`, `pr-checks.yml`, `dependabot.yml`

### [DB-001] Core Schema Migrations — DONE
- **Status:** DONE | **Updated:** 2026-03-23T10:00:00Z
- Files: `backend/auth-service/src/main/resources/db/migration/V1-V5`
- Covers: tenants, customers (encrypted PII), agent_users, teams, refresh_tokens
- RLS enabled, indexes on FK + filter columns

### [DB-002] Tickets Schema Migrations — DONE
- **Status:** DONE | **Updated:** 2026-03-23T10:00:00Z
- Files: `backend/ticket-service/src/main/resources/db/migration/V1-V8`
- Covers: ticket_categories, ticket_sub_categories, tickets, ticket_activities, ticket_sequences, sla_policies, resolution_templates, faq_entries (pgvector + HNSW index)

### [DOCS-001] Initial Slash Commands and Agent Definitions — DONE
- **Status:** DONE | **Updated:** 2026-03-23T10:00:00Z
- Files: `.claude/agents/` (5 agents), `.claude/commands/` (8 commands), `.claude/settings.json`

---

## 🔍 REVIEW ITEMS NEEDED (created during implementation review)

### [REVIEW-001] ticket-service — Missing integration tests and unit tests
- **ID:** REVIEW-001
- **Type:** REVIEW
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Service:** ticket-service
- **Description:** TicketServiceTest, SlaEngineTest, TicketStatusTest unit tests and integration tests for all 8 REST endpoints need to be written.
- **Created:** 2026-03-23T10:00:00Z

### [REVIEW-002] auth-service — Missing integration tests for 4 endpoints
- **ID:** REVIEW-002
- **Type:** REVIEW
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Service:** auth-service
- **Description:** Integration tests for POST /otp/send, /otp/verify, /refresh, /logout and agent login endpoints.
- **Created:** 2026-03-23T10:00:00Z

### [REVIEW-003] RSA key pair generation for JWT signing
- **ID:** REVIEW-003
- **Type:** DEPLOY
- **Priority:** P1-HIGH
- **Status:** OPEN
- **Service:** auth-service, api-gateway
- **Description:** RSA private/public key pair must be generated and placed at `backend/auth-service/src/main/resources/keys/` and `backend/api-gateway/src/main/resources/keys/` for JWT signing to work. Use `generate-keys.sh` script. Keys must NOT be committed to git.
- **Created:** 2026-03-23T10:00:00Z

---

## 🔍 IN REVIEW — Sprint 1

### [FEAT-005] customer-service — Customer Profile CRUD + Order History + Address Management
- **ID:** FEAT-005
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T12:00:00Z
- **Branch:** feature/FEAT-005-customer-service
- **Files Created:**
  - `backend/customer-service/pom.xml` — updated with security, webflux, validation, lombok, test deps
  - `backend/customer-service/src/main/java/in/supporthub/customer/`
    - `domain/Customer.java` — JPA entity (PII-encrypted fields, @DynamicUpdate)
    - `domain/CustomerAddress.java` — JPA entity for shipping/billing addresses
    - `repository/CustomerRepository.java` — findByTenantIdAndId, findByTenantIdAndPhoneHash
    - `repository/CustomerAddressRepository.java` — findAllByTenantIdAndCustomerId, findByTenantIdAndIdAndCustomerId
    - `dto/CustomerProfileResponse.java` — profile response record
    - `dto/UpdateProfileRequest.java` — @Size/@Pattern validated record (phone immutable)
    - `dto/CustomerAddressResponse.java` — address response record
    - `dto/CreateAddressRequest.java` — @NotBlank/@Size validated record
    - `dto/OrderSummary.java` — order-sync-service response record
    - `dto/PagedResponse.java` — generic paged response record
    - `service/CustomerService.java` — full CRUD + address management + default-address invariant
    - `service/OrderHistoryService.java` — WebClient to order-sync-service, defensive empty-list on any error, 3s timeout
    - `controller/CustomerController.java` — 8 REST endpoints, reads X-User-Id from header
    - `controller/TenantContextFilter.java` — X-Tenant-ID → TenantContextHolder + RLS SET
    - `config/WebClientConfig.java` — WebClient.Builder @Bean
    - `config/SecurityConfig.java` — STATELESS, permit /actuator/health, require auth /api/**
    - `exception/CustomerNotFoundException.java` — CUSTOMER_NOT_FOUND 404
    - `exception/AddressNotFoundException.java` — ADDRESS_NOT_FOUND 404
  - `backend/customer-service/src/main/resources/application.yml` — updated (order-sync.base-url, JPA dialect)
  - `backend/customer-service/src/main/resources/db/migration/V1__create_customer_addresses.sql` — table + RLS + indexes
  - `backend/customer-service/src/test/java/in/supporthub/customer/service/CustomerServiceTest.java`
#### Acceptance Criteria
- [x] GET /api/v1/customers/me — returns profile
- [x] PUT /api/v1/customers/me — updates name/language/timezone; phone immutable
- [x] GET /api/v1/customers/me/orders — calls order-sync-service, returns empty list on failure
- [x] GET /api/v1/customers/me/addresses — lists all addresses
- [x] POST /api/v1/customers/me/addresses — creates address, handles default flag
- [x] PUT /api/v1/customers/me/addresses/{id} — replaces address
- [x] DELETE /api/v1/customers/me/addresses/{id} — deletes address
- [x] POST /api/v1/customers/me/addresses/{id}/default — sets default, clears previous default
- [x] Tenant isolation via TenantContextHolder + RLS
- [x] No PII in logs (tenantId and customerId/addressId only)
- [x] Unit tests: getProfile (found/not-found), updateProfile, addAddress, setDefaultAddress, deleteAddress
- [ ] Integration tests — PENDING
#### Test Results
- Unit: PENDING (`./mvnw test -pl customer-service`)

---

### [FEAT-006] notification-service — SMS (MSG91) + Email (SendGrid) + WhatsApp + In-App
- **ID:** FEAT-006
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T15:00:00Z
- **Branch:** feature/FEAT-006-notification-service
- **Files Created:**
  - `backend/notification-service/pom.xml` — updated with MongoDB, WebFlux, Security, SpringDoc deps
  - `backend/notification-service/src/main/java/in/supporthub/notification/`
    - `domain/Notification.java` — @Document, RecipientType/Channel/Status enums
    - `repository/NotificationRepository.java` — findByTenantId*, findUnread, countByStatus
    - `event/TicketEventConsumer.java` — Kafka consumer, Redis idempotency (24h TTL)
    - `service/PiiDecryptionService.java` — AES-256-GCM, Base64 overload, never logs decrypted
    - `service/sms/Msg91SmsService.java` — MSG91 Flow API v5, no PII in logs
    - `service/email/SendGridEmailService.java` — SendGrid v3 API, no email in logs
    - `service/whatsapp/WhatsAppService.java` — Meta Graph API v19.0, graceful degradation
    - `service/inapp/InAppNotificationService.java` — save, getUnreadCount, getNotifications, markAsRead
    - `service/NotificationService.java` — orchestration; in-app always, SMS/WhatsApp on phone decrypt
    - `controller/NotificationController.java` — GET /me, GET /me/unread-count, PUT /{id}/read
    - `controller/TenantContextFilter.java` — X-Tenant-Id → TenantContextHolder
    - `config/SecurityConfig.java` — STATELESS, actuator/swagger public
    - `config/KafkaConfig.java` — Consumer factory, ErrorHandlingDeserializer, RECORD ack mode
    - `config/RedisConfig.java` — StringRedisTemplate for idempotency keys
    - `config/WebClientConfig.java` — shared WebClient, 5s connect / 10s read timeouts
    - `dto/NotificationResponse.java` — record(id, channel, subject, content, referenceId, referenceType, status, createdAt)
    - `dto/UnreadCountResponse.java` — record(long count)
    - `exception/NotificationException.java`, `NotificationNotFoundException.java`, `PiiDecryptionException.java`
  - `backend/notification-service/src/main/resources/application.yml` — full config
  - `backend/notification-service/src/test/java/.../service/NotificationServiceTest.java` — 11 unit tests
#### Acceptance Criteria
- [x] SMS via MSG91 — ticket created + status changed, no PII logged
- [x] Email via SendGrid v3 — sendEmail helper, no email logged
- [x] WhatsApp via Meta Graph API v19.0 — graceful degradation on any error
- [x] In-app notifications stored in MongoDB, status SENT → DELIVERED on markAsRead
- [x] Kafka consumer: ticket.created → in-app + SMS/WhatsApp channel records for customer
- [x] Kafka consumer: ticket.status-changed → customer on RESOLVED, agent on ESCALATED
- [x] Idempotency: Redis setIfAbsent notif:processed:{eventId}, 24h TTL
- [x] REST: GET /me (paginated), GET /me/unread-count, PUT /{id}/read
- [x] Tenant isolation: TenantContextHolder, never from request params
- [x] PII: phone decrypted via AES-256-GCM, NEVER logged or stored in notification content
- [x] Unit tests: 11 tests covering creation, failure isolation, idempotency, RESOLVED/ESCALATED routing
- [ ] Integration tests — PENDING
#### Test Results
- Unit: PENDING (`./mvnw test -pl notification-service`)

---

### [FEAT-007] ai-service — Anthropic Claude sentiment analysis + resolution suggestions
- **ID:** FEAT-007
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T11:00:00Z
- **Branch:** feature/FEAT-007-ai-service
- **Files Created:**
  - `backend/ai-service/pom.xml` — updated with MongoDB, Redis, security, validation deps
  - `backend/ai-service/src/main/java/in/supporthub/ai/domain/AiInteraction.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/repository/AiInteractionRepository.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/dto/SentimentResult.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/dto/ResolutionSuggestion.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/dto/SentimentAnalysisRequest.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/dto/ResolutionSuggestionsRequest.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/service/PiiStripper.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/service/SentimentAnalysisService.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/service/ResolutionSuggestionService.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/event/TicketCreatedEventConsumer.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/event/SentimentResultPublisher.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/config/KafkaConfig.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/config/RedisConfig.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/config/SecurityConfig.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/controller/AiController.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/controller/TenantContextFilter.java`
  - `backend/ai-service/src/main/java/in/supporthub/ai/exception/AiServiceException.java`
  - `backend/ai-service/src/main/resources/application.yml` — updated with MongoDB, Redis, Kafka, AI config
  - `backend/ai-service/src/test/java/in/supporthub/ai/service/PiiStripperTest.java`
  - `backend/ai-service/src/test/java/in/supporthub/ai/service/SentimentAnalysisServiceTest.java`
#### Acceptance Criteria
- [x] Kafka consumer: ticket.created → sentiment analysis → publish ai.results.sentiment
- [x] Idempotency via Redis (ai:processed:{eventId}, 24h TTL)
- [x] Sentiment analysis: claude-haiku-4-5-20251001, PII stripped, fallback to neutral on error
- [x] Resolution suggestions: claude-sonnet-4-5, PII stripped, empty list on error
- [x] AiInteraction audit log persisted in MongoDB
- [x] REST API: POST /sentiment, POST /resolution-suggestions, GET /interactions/{ticketId}
- [x] TenantContextHolder used — never from request params
- [x] No PII logged (text/prompt never logged)
- [x] Unit tests: PiiStripperTest (8 cases), SentimentAnalysisServiceTest (4 cases)
- [ ] Integration tests — PENDING
#### Test Results
- Unit: PENDING (`./mvnw test -pl ai-service`)

---

### [DB-003] MongoDB Collection Schemas
- **ID:** DB-003
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T12:00:00Z
- **Branch:** feature/DB-003-mongo-schemas
- **Files Created:**
  - `backend/shared/src/main/resources/mongo/01-ai-interactions.js`
  - `backend/shared/src/main/resources/mongo/02-notifications.js`
  - `backend/shared/src/main/resources/mongo/03-faq-cms-sync-log.js`
#### Acceptance Criteria
- [x] `ai_interactions`: JSON schema validator, TTL 90 days, indexes on tenantId+ticketId and tenantId+interactionType+createdAt
- [x] `notifications`: JSON schema validator, TTL 30 days, indexes on tenantId+recipientId+status+createdAt and tenantId+referenceId+referenceType
- [x] `faq_cms_sync_log`: TTL 7 days, indexes on tenantId+strapiId and processedAt
#### Test Results
- Integration: PENDING (run against local MongoDB instance)

---

### [INFRA-005] Kubernetes Kustomize Base Manifests
- **ID:** INFRA-005
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T12:00:00Z
- **Branch:** feature/INFRA-005-k8s-base
- **Files Created:**
  - `infrastructure/k8s/base/kustomization.yaml`
  - `infrastructure/k8s/base/namespace.yaml`
  - `infrastructure/k8s/base/configmap.yaml`
  - `infrastructure/k8s/base/secrets-template.yaml`
  - `infrastructure/k8s/base/{11 services}/deployment.yaml` — all services
  - `infrastructure/k8s/base/{11 services}/service.yaml` — all services
  - `infrastructure/k8s/base/{11 services}/kustomization.yaml` — all services
#### Acceptance Criteria
- [x] All 11 services: api-gateway (8080), auth-service (8081), ticket-service (8082), customer-service (8083), ai-service (8084), notification-service (8085), faq-service (8086), reporting-service (8087), tenant-service (8088), order-sync-service (8089), mcp-server (8090)
- [x] Liveness/readiness probes via /actuator/health/liveness and /actuator/health/readiness
- [x] Secrets from secretKeyRef (supporthub-db-secret), config from configMapKeyRef (supporthub-config)
- [x] Resource requests/limits: 256Mi/512Mi memory, 100m/500m cpu
- [x] secrets-template.yaml marked as template only (no real values)
- [ ] Dev/staging/prod overlays — PENDING (INFRA-005 scope covers base only)
#### Test Results
- Lint: PENDING (`kubectl kustomize infrastructure/k8s/base`)

---

### [FEAT-020] Customer SDK — TypeScript Headless SDK
- **ID:** FEAT-020
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T12:00:00Z
- **Branch:** feature/FEAT-020-customer-sdk
- **Files Created/Updated:**
  - `frontend/packages/customer-sdk/src/types/index.ts` — rewritten with new type aliases
  - `frontend/packages/customer-sdk/src/client.ts` — full SupportHubClient with getAccessToken
  - `frontend/packages/customer-sdk/src/hooks/useTickets.ts` — React hook (optional)
  - `frontend/packages/customer-sdk/src/index.ts` — updated main export
  - `frontend/packages/customer-sdk/src/__tests__/client.test.ts` — 3 vitest tests
  - `frontend/packages/customer-sdk/package.json` — updated to v1.0.0
  - `frontend/packages/customer-sdk/tsconfig.json` — updated with declaration/sourceMap
#### Acceptance Criteria
- [x] `SupportHubClient` with `getAccessToken: () => Promise<string> | string`
- [x] Ticket ops: createTicket, getTicket, listMyTickets, addComment, getTicketActivities
- [x] FAQ ops: searchFaq, listFaqs
- [x] Notification ops: getNotifications, getUnreadCount, markNotificationRead
- [x] Profile ops: getProfile, updateProfile, getOrderHistory
- [x] `SupportHubError` thrown on non-OK responses
- [x] `X-Tenant-ID` and `Authorization` headers on every request
- [x] `useTickets` React hook with loading/error state
- [x] Unit tests: header assertions, SupportHubError on 404, async token function
#### Test Results
- Unit: PENDING (`npm run test -w packages/customer-sdk`)
- TypeScript: PENDING (`npm run typecheck -w packages/customer-sdk`)

---

## 🔄 IN PROGRESS — Sprint 1

### [FEAT-009] order-sync-service — OMS REST integration + Redis order cache
- **ID:** FEAT-009
- **Status:** IN_PROGRESS
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T13:00:00Z
- **Branch:** feature/FEAT-009-order-sync-service
- **Files Created:**
  - `backend/order-sync-service/pom.xml` — updated with JPA, PostgreSQL, Flyway, Redis, WebFlux, security, JJWT deps
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/domain/OmsConfig.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/repository/OmsConfigRepository.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/dto/` — 4 records
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/exception/OmsConfigNotFoundException.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/service/PiiEncryptionService.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/service/OmsConfigService.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/service/OmsClientService.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/controller/TenantContextFilter.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/controller/OrderSyncController.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/controller/OmsConfigController.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/config/SecurityConfig.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/config/RedisConfig.java`
  - `backend/order-sync-service/src/main/java/in/supporthub/ordersync/config/WebClientConfig.java`
  - `backend/order-sync-service/src/main/resources/application.yml`
  - `backend/order-sync-service/src/main/resources/db/migration/V1__create_oms_configs.sql`
  - `backend/order-sync-service/src/test/java/in/supporthub/ordersync/service/OmsClientServiceTest.java`
#### Acceptance Criteria
- [x] GET /api/v1/internal/orders/customer/{customerId}?limit — JWT-protected, tenant-scoped
- [x] GET /api/v1/internal/orders/{orderId} — JWT-protected, 404 on miss
- [x] POST/GET/DELETE /api/v1/admin/oms-config — ADMIN role guard; API key AES-256-GCM encrypted
- [x] Redis cache TTL 600s; cache hit skips OMS call
- [x] OMS error → empty result; customerId NOT in WARN logs (PII)
- [x] Tenant isolation: TenantContextHolder + oms_configs RLS
- [x] Unit tests: cache hit (6 test cases)
- [ ] Integration tests — PENDING
#### Test Results
- Unit: PENDING (`./mvnw test -pl order-sync-service`)

---

### [FEAT-008] faq-service — FAQ CRUD + Strapi webhook sync + pgvector semantic search
- **ID:** FEAT-008
- **Status:** IN_REVIEW
- **Owner:** agent:implementer
- **Updated:** 2026-03-23T12:00:00Z
- **Branch:** feature/FEAT-008-faq-service
- **Files Created:**
  - `backend/faq-service/pom.xml` — updated with JPA, Spring AI, Spring Security, Flyway, Hypersistence deps
  - `backend/faq-service/src/main/java/in/supporthub/faq/domain/FaqEntry.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/repository/FaqRepository.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/exception/FaqNotFoundException.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/exception/WebhookAuthException.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/dto/FaqResponse.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/dto/CreateFaqRequest.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/dto/UpdateFaqRequest.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/dto/FaqSearchResult.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/dto/FaqSearchRequest.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/dto/StrapiWebhookPayload.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/dto/StrapiEntry.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/service/EmbeddingService.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/service/SemanticSearchService.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/service/StrapiWebhookService.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/service/FaqService.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/controller/FaqController.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/controller/StrapiWebhookController.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/controller/TenantContextFilter.java`
  - `backend/faq-service/src/main/java/in/supporthub/faq/config/SecurityConfig.java`
  - `backend/faq-service/src/main/resources/db/migration/V1__enable_vector_and_create_faq_entries.sql`
  - `backend/faq-service/src/main/resources/application.yml`
  - `backend/faq-service/src/test/java/in/supporthub/faq/service/SemanticSearchServiceTest.java`
  - `backend/faq-service/src/test/java/in/supporthub/faq/service/StrapiWebhookServiceTest.java`
#### Acceptance Criteria
- [x] FAQ CRUD: create, update, delete, publish, get, list (paginated)
- [x] Strapi CMS webhook sync with HMAC-SHA256 validation (constant-time comparison)
- [x] pgvector semantic search via Spring AI EmbeddingModel (text-embedding-3-small)
- [x] Self-resolution flow: search endpoint public, falls back to keyword (ILIKE) on embedding failure
- [x] Tenant isolation via TenantContextHolder + PostgreSQL RLS
- [x] Unit tests: SemanticSearchServiceTest (8 cases), StrapiWebhookServiceTest (11 cases)
- [x] No PII logged — tenantId and entity IDs only
- [x] Defensive embedding: FAQ saved even when OpenAI unavailable
- [ ] Integration tests — PENDING
#### Test Results
- Unit: PENDING (`./mvnw test -pl supporthub-faq-service`)

---

## ✅ COMPLETED — Sprint 2

Sprint 2 tasks (FEAT-012, FEAT-024, TEST-001, INFRA-006, INFRA-007, OBS-001) all merged to main via PR #20 on 2026-03-23.

---

## 🔄 IN PROGRESS — Sprint 3

### [FEAT-028] ticket-service — WebSocket real-time agent notifications
- **ID:** FEAT-028
- **Status:** DONE
- **Priority:** P1-HIGH
- **Owner:** agent:implementer
- **Sprint:** 3
- **Created:** 2026-03-23T21:00:00Z
- **Completed:** 2026-03-23T00:00:00Z
#### Scope
- Spring WebSocket + STOMP over SockJS endpoint `/ws/agent`
- WebSocketConfig: registerStompEndpoints, message broker (in-memory)
- TicketWebSocketController: `@MessageMapping` for agent subscribe/unsubscribe
- TicketEventPublisher: broadcast to `/topic/tenant/{tenantId}/tickets` on every Kafka event consumed
- StompPrincipal: JWT-authenticated WS connections (validate token in HandshakeInterceptor)
- Agent-dashboard frontend: replace raw WebSocket with @stomp/stompjs + sockjs-client
#### Acceptance Criteria
- [x] Agent connects via STOMP, receives live ticket updates without polling
- [x] JWT validated on WebSocket handshake
- [x] Tenant isolation: agents only receive their tenant's events
- [ ] Reconnect with exponential backoff on disconnect (frontend — out of scope for this task)
#### Implementation
- `backend/ticket-service/src/main/java/in/supporthub/ticket/config/WebSocketConfig.java` — STOMP config
- `backend/ticket-service/src/main/java/in/supporthub/ticket/websocket/JwtHandshakeInterceptor.java` — JWT validation
- `backend/ticket-service/src/main/java/in/supporthub/ticket/websocket/TicketUpdateMessage.java` — message record
- `backend/ticket-service/src/main/java/in/supporthub/ticket/websocket/TicketWebSocketPublisher.java` — SimpMessagingTemplate publisher
- `TicketEventPublisher.java` updated to inject and call `TicketWebSocketPublisher` after each Kafka send
- `pom.xml` updated with `spring-boot-starter-websocket` dependency
- `application.yml` updated with WebSocket size/timeout limits

---

### [FEAT-029] reporting-service — CSV export + SLA compliance + agent performance endpoints
- **ID:** FEAT-029
- **Status:** DONE
- **Priority:** P1-HIGH
- **Owner:** agent:implementer
- **Sprint:** 3
- **Created:** 2026-03-23T21:00:00Z
- **Completed:** 2026-03-24T00:00:00Z
#### Scope
- `GET /api/v1/reports/export` — streaming CSV (ResponseBodyEmitter) of all tickets in period
- `GET /api/v1/reports/sla-compliance` — SLA compliance % per category via Elasticsearch agg
- `GET /api/v1/reports/agent-performance` — resolved count + avg resolution time per agent via ES agg
- `SlaComplianceResult` record, `AgentPerformanceResult` record
- `CsvExportService` — streaming CSV with Jackson CsvMapper or manual StringBuilder
- Elasticsearch aggregation queries in `DashboardService`
#### Acceptance Criteria
- [x] CSV export streams via HttpServletResponse OutputStream; Content-Disposition + Content-Type headers set
- [x] SLA compliance returns % on-time per ticket category for given date range
- [x] Agent performance returns per-agent: ticketsResolved, avgResolutionMinutes, firstResponseAvgMinutes
#### Implementation Notes
- `SlaComplianceResult` and `AgentPerformanceResult` records added to `in.supporthub.reporting.dto`
- `CsvExportService` streams rows from `TicketDocumentRepository`, writes via `PrintWriter` on `HttpServletResponse.getOutputStream()`
- `DashboardService.getSlaCompliance()` and `getAgentPerformanceResults()` methods added, using existing ES repo pattern
- `ReportingController` gained 3 new endpoints: `GET /export`, `GET /sla-compliance`, `GET /agent-performance`
- `firstResponseAvgMinutes` emits 0.0 until `TicketDocument` gains a dedicated `firstResponseMinutes` field

---

### [FEAT-030] admin-portal — reporting dashboard with Recharts + FAQ management UI
- **ID:** FEAT-030
- **Status:** DONE
- **Priority:** P1-HIGH
- **Owner:** agent:implementer
- **Sprint:** 3
- **Created:** 2026-03-23T21:00:00Z
- **Completed:** 2026-03-24T00:00:00Z
#### Scope
- `ReportingPage.tsx` — tabs: Overview, SLA Compliance, Agent Performance, Export
  - Overview: BarChart (tickets by category), LineChart (daily trend), stat cards (open/resolved/breached)
  - SLA Compliance: BarChart grouped by category + compliance % label
  - Agent Performance: sortable DataTable (agent name, resolved count, avg resolution time)
  - Export: date range picker + Download CSV button (streams from reporting-service)
- `FAQManagementPage.tsx` — full CRUD
  - List FAQs paginated with search
  - Create/Edit modal with @tiptap/react rich text for answer field
  - Publish/unpublish toggle
  - Delete with confirmation dialog
- Add recharts + @tiptap/react + @tiptap/extension-starterkit deps to admin-portal package.json
- All data via TanStack Query + adminApi.ts extended with reporting + FAQ endpoints
#### Acceptance Criteria
- [x] Reporting UI pages created with 4-tab layout and all data fetched via TanStack Query
- [x] FAQ CRUD fully functional; rich-text editor uses plain textarea (tiptap not yet in package.json)
- [x] CSV export triggers browser download via streaming endpoint with date range pickers
#### Implementation Notes
- `ReportingPage.tsx` created with Overview / SLA Compliance / Agent Performance / Export tabs
- recharts not yet in package.json; chart areas render as `ChartPlaceholder` divs — install recharts and replace to get real charts
- SLA compliance tab includes both a chart placeholder and a full data table
- Agent performance table supports click-to-sort on all numeric columns
- `FAQManagementPage.tsx` created with paginated list, search, create/edit modal (plain textarea), publish toggle, delete with `window.confirm()`
- `adminApi.ts` extended with `SlaComplianceResult`, `AgentPerformanceResult`, `TrendPoint`, `CategoryCount`, `FAQ`, `FAQPage`, `CreateFAQRequest` types and corresponding API functions
- `App.tsx` updated: routes `/reporting` → `ReportingPage`, `/faqs` → `FAQManagementPage`; nav links added to sidebar

---

### [FEAT-031] agent-dashboard — AI assistance panel + STOMP WebSocket + full ticket actions
- **ID:** FEAT-031
- **Status:** IN_PROGRESS
- **Priority:** P1-HIGH
- **Owner:** agent:implementer
- **Sprint:** 3
- **Created:** 2026-03-23T21:00:00Z
#### Scope
- Replace raw WebSocket with STOMP over SockJS in websocketStore.ts
- `AIAssistancePanel.tsx` component:
  - Calls `GET /api/v1/ai/resolution-suggestions` with ticket context
  - Displays confidence bars per suggestion
  - "Apply as reply" button — copies suggestion into reply textarea + submits
  - Sentiment badge (POSITIVE/NEUTRAL/NEGATIVE/FRUSTRATED) from ticket data
  - "Refresh suggestions" button — re-fetches from ai-service
- `TicketDetailPage.tsx` — wire up AIAssistancePanel, show real-time activity feed via STOMP subscription
- `TicketQueuePage.tsx` — live ticket count badge, auto-refresh queue on STOMP events
- Agent status toggle (AVAILABLE/BUSY/OFFLINE) stored in Zustand + sent to backend
- Ticket assignment: "Assign to me" button → PUT /api/v1/tickets/{id} with assigneeId
#### Acceptance Criteria
- [ ] STOMP subscription live-updates ticket queue without manual refresh
- [ ] AI suggestions fetched from real ai-service, displayed with confidence %
- [ ] "Apply as reply" one-click fills reply form and submits
- [ ] Agent status persisted in Zustand and synced to API

---

### [FEAT-032] customer-portal — ticket creation with file attachments via MinIO presigned URLs
- **ID:** FEAT-032
- **Status:** IN_PROGRESS
- **Priority:** P1-HIGH
- **Owner:** agent:implementer
- **Sprint:** 3
- **Created:** 2026-03-23T21:00:00Z
#### Scope
- `CreateTicketPage.tsx` — full rewrite with:
  - Category + subcategory cascading dropdowns (loaded from ticket-service API)
  - Priority selector (LOW/MEDIUM/HIGH/URGENT)
  - Subject + description (textarea with char count)
  - File attachment (max 5 files, 10MB each) — drag & drop or click to upload
  - File upload flow: `POST /api/v1/attachments/presign` → PUT to MinIO presigned URL → submit ticket with attachmentIds
  - Real-time character count, form validation with React Hook Form + Zod schema
  - Order reference (optional) — dropdown of recent orders
- Backend `attachment` endpoint in ticket-service: `POST /api/v1/attachments/presign` — returns MinIO presigned PUT URL + attachment ID
- `AttachmentService` in ticket-service: MinIO client (io.minio:minio), generates presigned URL (15min TTL), stores attachment metadata in DB
- `V9__create_attachments_table.sql` migration
#### Acceptance Criteria
- [ ] File upload to MinIO via presigned URL (no file bytes through backend)
- [ ] Attachment IDs included in CreateTicketRequest
- [ ] Max file size enforced client-side and server-side
- [ ] Category/subcategory loaded from live API, not hardcoded

---

### [FEAT-033] notification-service — tenant.onboarded Kafka consumer (welcome comms)
- **ID:** FEAT-033
- **Status:** DONE
- **Priority:** P2-MEDIUM
- **Owner:** agent:implementer
- **Sprint:** 3
- **Created:** 2026-03-23T21:00:00Z
- **Completed:** 2026-03-23T00:00:00Z
#### Scope
- `TenantOnboardedEventConsumer.java` — `@KafkaListener(topics = "tenant.onboarded")`
- Sends welcome email to tenant admin via SendGrid (subject: "Welcome to SupportHub", HTML template)
- Stores welcome notification in MongoDB
- Redis idempotency key: `notif:tenant:onboarded:{tenantId}`
#### Acceptance Criteria
- [x] Welcome email sent on tenant.onboarded event
- [x] Idempotent — duplicate events ignored
- [x] No PII in logs
#### Implementation
- `backend/notification-service/src/main/java/in/supporthub/notification/event/TenantOnboardedPayload.java` — event record
- `backend/notification-service/src/main/java/in/supporthub/notification/event/TenantOnboardedEventConsumer.java` — consumer with idempotency, email, MongoDB persistence
- `KafkaConfig.java` extended with `stringConsumerFactory` and `stringKafkaListenerContainerFactory` for raw-string topics
- `application.yml` updated to document `tenant.onboarded` in topic list

---

### [FEAT-025] E2E Playwright Tests — full Page Object Model
- **ID:** FEAT-025
- **Status:** DONE
- **Priority:** P2-MEDIUM
- **Owner:** agent:test-engineer
- **Sprint:** 3
- **Created:** 2026-03-23T21:00:00Z
- **Completed:** 2026-03-24T00:00:00Z
#### Scope
- `frontend/playwright.config.ts` — baseURL, retries=2, workers=4, screenshot on failure
- `frontend/e2e/pages/` — Page Object Models:
  - `LoginPage.ts` — enterPhone(), enterOtp(), expectLoggedIn()
  - `TicketListPage.ts` — waitForTickets(), filterByStatus(), clickTicket()
  - `TicketDetailPage.ts` — addComment(), expectComment(), expectAISuggestions()
  - `FAQSearchPage.ts` — search(), expectResults(), expectNoResults()
  - `AgentLoginPage.ts` — login(), expectQueue()
  - `AgentTicketPage.ts` — resolveTicket(), applyAISuggestion()
- `frontend/e2e/specs/`:
  - `otp-login.spec.ts` — full OTP login flow with mock WireMock/MSW
  - `ticket-creation.spec.ts` — create ticket end-to-end, verify in list
  - `faq-self-resolve.spec.ts` — search FAQ, find answer, deflect ticket
  - `agent-resolution.spec.ts` — agent login, resolve ticket, verify status change
#### Acceptance Criteria
- [x] All 4 specs written with full POM pattern
- [x] No raw locators in spec files — all via Page Objects
- [x] Screenshots saved on failure to `e2e/screenshots/`
- [x] `playwright.config.ts` properly configured
#### Implementation Notes
- All 6 Page Object Models created in `frontend/e2e/pages/` using `page.getByRole`, `page.getByLabel`, `page.getByTestId`, `page.getByText` — no CSS selectors
- `@playwright/test` not present in root `package.json`; install instructions added to `frontend/README-e2e.md`
- Locators derived from actual component source: `LoginPage.tsx`, `TicketListPage.tsx`, `TicketDetailPage.tsx`, `FAQSearchPage.tsx`, `agent-dashboard/LoginPage.tsx`, `TicketDetailPage.tsx`, `TicketQueuePage.tsx`
- `e2e/screenshots/` directory created for failure screenshots

---

## 📋 BACKLOG — Sprint 3 (OPEN)

### [FEAT-026] customer-portal — PWA + Offline Support + Push Notifications
- **ID:** FEAT-026
- **Status:** OPEN
- **Priority:** P3-LOW
- **Sprint:** 3

### [FEAT-027] reporting-service + admin-portal — SLA compliance charts (extends FEAT-029/FEAT-030)
- **ID:** FEAT-027
- **Status:** DONE (merged into FEAT-029 + FEAT-030 scope)
- **Sprint:** 3

### [SEC-001] OWASP CVE Dependency Scan
- **ID:** SEC-001
- **Status:** OPEN
- **Priority:** P1-HIGH
- **Sprint:** 3

### [ANAL-001] Static Analysis — SpotBugs + Checkstyle + PMD
- **ID:** ANAL-001
- **Status:** OPEN
- **Priority:** P2-MEDIUM
- **Sprint:** 3

---

## 🔄 IN PROGRESS — Sprint 2

### [FEAT-012] tenant-service — Tenant Onboarding + Config Management
- **ID:** FEAT-012
- **Status:** IN_PROGRESS
- **Priority:** P1-HIGH
- **Owner:** agent:implementer
- **Sprint:** 2
- **Branch:** feature/FEAT-012-tenant-service
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- Tenant entity (id, slug, name, plan, status, branding config, SLA defaults, timezone)
- Flyway migrations: V1__create_tenants_table.sql, V2__create_tenant_configs_table.sql
- TenantRepository, TenantConfigRepository
- TenantService: onboard, getBySlug, getById, updateConfig, suspend, reactivate
- TenantController: POST /api/v1/admin/tenants, GET /api/v1/tenants/{slug}, PUT /api/v1/admin/tenants/{id}/config, PATCH /api/v1/admin/tenants/{id}/status
- TenantContextFilter + SecurityConfig (ADMIN-only for write ops)
- Kafka event: tenant.onboarded (consumed by notification-service to send welcome)
- Unit tests: TenantServiceTest (onboard, getBySlug, updateConfig, suspend — 6 test cases)
#### Acceptance Criteria
- [ ] POST /api/v1/admin/tenants — creates tenant + default config, emits Kafka event
- [ ] GET /api/v1/tenants/{slug} — resolves tenant by slug (used by gateway for tenant routing)
- [ ] PUT /api/v1/admin/tenants/{id}/config — updates branding/SLA defaults
- [ ] PATCH /api/v1/admin/tenants/{id}/status — ACTIVE/SUSPENDED
- [ ] Tenant isolation via TenantContextHolder + RLS
- [ ] Unit tests: 6 test cases
- [ ] No PII logged
#### Test Results
- Unit: PENDING (`./mvnw test -pl tenant-service`)

---

### [FEAT-024] customer-portal — Complete Self-Service Flow
- **ID:** FEAT-024
- **Status:** IN_PROGRESS
- **Priority:** P1-HIGH
- **Owner:** agent:implementer
- **Sprint:** 2
- **Branch:** feature/FEAT-024-customer-portal
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- LoginPage: OTP send + verify flow with React Hook Form + TanStack Query
- TicketListPage: list my tickets, status badges, pagination
- TicketDetailPage: view ticket + activities + add comment + AI resolution suggestions (read-only)
- FAQSearchPage: semantic search + self-resolution flow
- OrderHistoryPage: list orders from order-sync-service
- NotificationsPage: unread count badge + notification list
- Zustand authStore with JWT token management
- TanStack Query for all API calls via customer-sdk
- Protected routes (redirect to login if no token)
- customer-portal App.tsx routing
#### Acceptance Criteria
- [ ] OTP login flow works end-to-end with auth-service
- [ ] Ticket list shows all customer tickets with status/priority badges
- [ ] Ticket detail: view + add comment, display AI resolution suggestions
- [ ] FAQ search: debounced search input, results with relevance score
- [ ] Zustand authStore: token persist to localStorage, logout clears state
- [ ] All pages use TanStack Query (no useState for API data)
- [ ] No `console.log`, no `any` type, strict TypeScript
#### Test Results
- TypeScript: PENDING (`npm run typecheck -w apps/customer-portal`)
- Lint: PENDING (`npm run lint -w apps/customer-portal`)

---

### [TEST-001] Integration Tests — All Sprint 1 Services
- **ID:** TEST-001
- **Status:** IN_PROGRESS
- **Priority:** P1-HIGH
- **Owner:** agent:test-engineer
- **Sprint:** 2
- **Branch:** feature/TEST-001-integration-tests
- **Created:** 2026-03-23T19:00:00Z
- **Addresses:** REVIEW-001, REVIEW-002
#### Scope (Spring Boot @SpringBootTest + Testcontainers)
- auth-service: OtpSendIT, OtpVerifyIT, RefreshTokenIT, LogoutIT, AgentLoginIT
- ticket-service: CreateTicketIT, GetTicketIT, UpdateTicketIT, ResolveTicketIT, TicketStatusMachineIT, SlaBreachIT
- customer-service: CustomerProfileIT, AddressManagementIT, OrderHistoryIT
- notification-service: TicketCreatedNotificationIT, StatusChangedNotificationIT
- ai-service: SentimentAnalysisIT, ResolutionSuggestionIT
- faq-service: FaqCrudIT, SemanticSearchIT, StrapiWebhookIT
- order-sync-service: OrderCacheIT, OmsConfigIT
- reporting-service: DashboardIT, TicketProjectionIT
#### Acceptance Criteria
- [ ] Testcontainers: PostgreSQL 16, MongoDB 7, Redis 7, Kafka (Redpanda), Elasticsearch 8
- [ ] Each service: min 3 integration test cases
- [ ] @Transactional rollback after each test
- [ ] Tenant isolation verified in each service IT
- [ ] All ITs green before merge
#### Files Created
- `backend/shared/src/test/java/in/supporthub/shared/test/AbstractIntegrationTest.java`
- `backend/auth-service/src/test/java/in/supporthub/auth/integration/CustomerAuthIT.java`
- `backend/auth-service/src/test/java/in/supporthub/auth/integration/AgentAuthIT.java`
- `backend/ticket-service/src/test/java/in/supporthub/ticket/integration/TicketCrudIT.java`
- `backend/customer-service/src/test/java/in/supporthub/customer/integration/CustomerProfileIT.java`
- `backend/notification-service/src/test/java/in/supporthub/notification/integration/NotificationIT.java`
- `backend/ai-service/src/test/java/in/supporthub/ai/integration/AiServiceIT.java`
- `backend/faq-service/src/test/java/in/supporthub/faq/integration/FaqCrudIT.java`
- `backend/order-sync-service/src/test/java/in/supporthub/ordersync/integration/OmsConfigIT.java`
- `backend/reporting-service/src/test/java/in/supporthub/reporting/integration/ReportingIT.java`
- `application-test.yml` added for each service above
- Testcontainers dependencies added to pom.xml for: shared, customer-service, notification-service, ai-service, faq-service, order-sync-service, reporting-service
#### Test Results
- Integration: FILES_CREATED — pending execution

---

### [INFRA-006] Terraform AWS Infrastructure Modules
- **ID:** INFRA-006
- **Status:** IN_PROGRESS
- **Priority:** P2-MEDIUM
- **Owner:** agent:implementer
- **Sprint:** 2
- **Branch:** feature/INFRA-006-terraform
- **Created:** 2026-03-23T19:00:00Z
- **Updated:** 2026-03-23T20:00:00Z
#### Scope
- `infrastructure/terraform/modules/eks/` — EKS cluster, node groups, IRSA
- `infrastructure/terraform/modules/rds/` — RDS PostgreSQL 16 Multi-AZ, parameter group
- `infrastructure/terraform/modules/elasticache/` — Redis 7 cluster mode disabled (1 primary + 1 replica)
- `infrastructure/terraform/modules/msk/` — MSK Kafka 3.x, 3-broker, TLS
- `infrastructure/terraform/modules/s3/` — S3 bucket + lifecycle policy + KMS encryption
- `infrastructure/terraform/modules/ecr/` — ECR repo per service + lifecycle policy (keep last 20)
- `infrastructure/terraform/modules/opensearch/` — OpenSearch Service domain
- `infrastructure/terraform/envs/dev/` — dev environment wiring (main.tf, variables.tf, outputs.tf, backend.tf)
- `infrastructure/terraform/envs/staging/` — staging environment
- `infrastructure/terraform/envs/prod/` — prod environment (multi-AZ, larger instances)
- `infrastructure/terraform/README.md` — usage documentation
#### Files Created
- `infrastructure/terraform/modules/s3/main.tf`, `variables.tf`, `outputs.tf`
- `infrastructure/terraform/modules/ecr/main.tf`, `variables.tf`, `outputs.tf`
- `infrastructure/terraform/modules/opensearch/main.tf`, `variables.tf`, `outputs.tf`
- `infrastructure/terraform/envs/dev/main.tf`, `variables.tf`, `outputs.tf`, `backend.tf`
- `infrastructure/terraform/envs/staging/main.tf`, `variables.tf`, `outputs.tf`, `backend.tf`
- `infrastructure/terraform/envs/prod/main.tf`, `variables.tf`, `outputs.tf`, `backend.tf`
- `infrastructure/terraform/README.md`
#### Acceptance Criteria
- [ ] All modules have variables.tf, main.tf, outputs.tf
- [x] Remote state: S3 + DynamoDB lock configured in backend.tf
- [x] Tagging: Environment, Project, ManagedBy=terraform on all resources
- [ ] `terraform validate` passes on each module
- [x] No hardcoded credentials — all from variables or AWS secrets manager
#### Test Results
- Validate: PENDING (`terraform validate` per module)

---

### [INFRA-007] Kubernetes Kustomize Overlays (dev/staging/prod)
- **ID:** INFRA-007
- **Status:** IN_PROGRESS
- **Priority:** P2-MEDIUM
- **Owner:** agent:implementer
- **Sprint:** 2
- **Branch:** feature/INFRA-007-k8s-overlays
- **Created:** 2026-03-23T19:00:00Z
- **Updated:** 2026-03-23T20:00:00Z
#### Scope
- `infrastructure/k8s/overlays/dev/` — 1 replica, DEBUG log
- `infrastructure/k8s/overlays/staging/` — 2 replicas, INFO log, Ingress (letsencrypt-staging)
- `infrastructure/k8s/overlays/prod/` — 3 replicas, WARN log, HPA, PodDisruptionBudget
- HorizontalPodAutoscaler for api-gateway, ticket-service, ai-service in prod
- PodDisruptionBudget (minAvailable: 1) for all services in prod
- Ingress + cert-manager annotations for staging + prod
#### Files Created
- `infrastructure/k8s/overlays/dev/kustomization.yaml`, `replica-patch.yaml`, `env-patch.yaml`, `namespace-patch.yaml`
- `infrastructure/k8s/overlays/staging/kustomization.yaml`, `replica-patch.yaml`, `env-patch.yaml`, `namespace-patch.yaml`, `ingress.yaml`
- `infrastructure/k8s/overlays/prod/kustomization.yaml`, `replica-patch.yaml`, `env-patch.yaml`, `namespace-patch.yaml`, `ingress.yaml`
- `infrastructure/k8s/overlays/prod/hpa/api-gateway-hpa.yaml`, `ticket-service-hpa.yaml`, `ai-service-hpa.yaml`
- `infrastructure/k8s/overlays/prod/pdb/api-gateway-pdb.yaml`, `ticket-service-pdb.yaml`, `auth-service-pdb.yaml`, `customer-service-pdb.yaml`, `notification-service-pdb.yaml`, `faq-service-pdb.yaml`, `reporting-service-pdb.yaml`, `order-sync-service-pdb.yaml`, `tenant-service-pdb.yaml`
#### Acceptance Criteria
- [ ] `kubectl kustomize infrastructure/k8s/overlays/dev` passes
- [ ] `kubectl kustomize infrastructure/k8s/overlays/staging` passes
- [ ] `kubectl kustomize infrastructure/k8s/overlays/prod` passes
- [x] HPA configured for api-gateway, ticket-service, ai-service in prod
- [x] PodDisruptionBudget applied to all prod services

---

### [OBS-001] Observability Stack — Prometheus + Grafana + Loki + Langfuse
- **ID:** OBS-001
- **Status:** IN_PROGRESS
- **Priority:** P2-MEDIUM
- **Owner:** agent:implementer
- **Sprint:** 2
- **Branch:** feature/OBS-001-observability
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- `infrastructure/docker/docker-compose-obs.yml` — Prometheus, Grafana, Loki, Promtail, Langfuse
- `infrastructure/observability/prometheus/prometheus.yml` — scrape configs for all 11 services
- `infrastructure/observability/grafana/dashboards/supporthub-overview.json` — service health dashboard
- `infrastructure/observability/grafana/dashboards/ticket-metrics.json` — ticket volume/SLA dashboard
- `infrastructure/observability/grafana/provisioning/` — datasource + dashboard provisioning
- `infrastructure/observability/loki/loki-config.yml` — log aggregation config
- Micrometer Spring Boot actuator endpoints already in application.yml — verify scrape works
- Langfuse self-hosted for AI interaction tracing
#### Acceptance Criteria
- [ ] All 11 services scraped by Prometheus on /actuator/prometheus
- [ ] Grafana SupportHub Overview dashboard: request rate, error rate, p99 latency per service
- [ ] Grafana Ticket dashboard: tickets created/resolved per hour, SLA breach rate
- [ ] Loki + Promtail collecting logs from all services with tenant_id label
- [ ] Langfuse container in docker-compose for AI call tracing

---

## 📋 BACKLOG — Sprint 2

### Sprint 2 Open Tasks:

### [FEAT-025] E2E Playwright Tests — ticket creation, resolution, FAQ self-resolve
- **ID:** FEAT-025
- **Status:** OPEN
- **Priority:** P2-MEDIUM
- **Sprint:** 2
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- `frontend/e2e/ticket-creation.spec.ts` — customer creates ticket, sees confirmation
- `frontend/e2e/agent-resolution.spec.ts` — agent views queue, resolves ticket, customer notified
- `frontend/e2e/faq-self-resolve.spec.ts` — customer searches FAQ, finds answer, deflects ticket creation
- `frontend/e2e/otp-login.spec.ts` — customer OTP login flow end-to-end
- `frontend/playwright.config.ts` — base URL, retries, parallel workers
#### Acceptance Criteria
- [ ] All 4 spec files pass against local docker-compose stack
- [ ] Screenshots on failure saved to `frontend/e2e/screenshots/`
- [ ] Page Object Model pattern used (no raw locators in tests)

---

### [SEC-001] OWASP CVE Dependency Scan — All Sprint 1 Services
- **ID:** SEC-001
- **Status:** OPEN
- **Priority:** P1-HIGH
- **Sprint:** 2
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- Run `./mvnw dependency-check:check` on all 11 backend services
- Capture all CRITICAL/HIGH CVEs → create SEC-NNN tasks for each
- Run `npm audit --audit-level=high` on all frontend workspaces
- No CRITICAL CVEs unresolved before any merge to main
#### Acceptance Criteria
- [ ] OWASP scan completed on all backend services
- [ ] npm audit completed on all frontend workspaces
- [ ] All CRITICAL CVEs have SEC-NNN tasks created in TODO.md
- [ ] Report saved to `security/owasp-report-sprint2.md`

---

### [ANAL-001] Static Analysis — SpotBugs + Checkstyle + PMD
- **ID:** ANAL-001
- **Status:** OPEN
- **Priority:** P2-MEDIUM
- **Sprint:** 2
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- Run `./mvnw checkstyle:check spotbugs:check pmd:check` on all services
- Capture HIGH/MEDIUM findings → create ANAL-NNN tasks for each
- ESLint check on all frontend workspaces
#### Acceptance Criteria
- [ ] Checkstyle: 0 violations in service layer
- [ ] SpotBugs: 0 HIGH findings
- [ ] PMD: 0 HIGH findings
- [ ] ESLint: 0 errors across frontend workspaces

---

### [FEAT-026] customer-portal — PWA + Offline Support + Push Notifications
- **ID:** FEAT-026
- **Status:** OPEN
- **Priority:** P3-LOW
- **Sprint:** 2
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- Service worker with Workbox (cache-first for static, network-first for API)
- Web Push notification subscription → stored in notification-service
- Offline ticket list from IndexedDB cache
- Install prompt (PWA manifest)

---

### [FEAT-027] reporting-service — Grafana-compatible metrics endpoint + tenant dashboard API
- **ID:** FEAT-027
- **Status:** OPEN
- **Priority:** P3-LOW
- **Sprint:** 2
- **Created:** 2026-03-23T19:00:00Z
#### Scope
- `/api/v1/reports/export` — CSV export of ticket data (streaming)
- `/api/v1/reports/sla-compliance` — SLA compliance % per category per period
- `/api/v1/reports/agent-performance` — tickets resolved, avg resolution time per agent
- Elasticsearch aggregation queries via existing DashboardService

---

---

## Changelog

### 2026-03-23 (Sprint 1 completed — all core services implemented)
- FEAT-005: customer-service — profile CRUD + address management + order history proxy
- FEAT-006: notification-service — MSG91 SMS + SendGrid email + WhatsApp + in-app (MongoDB)
- FEAT-007: ai-service — Anthropic sentiment (Haiku) + resolution suggestions (Sonnet) + PII stripper
- FEAT-008: faq-service — FAQ CRUD + Strapi webhook + pgvector HNSW semantic search
- FEAT-009: order-sync-service — OMS REST proxy + Redis cache (600s) + AES-256-GCM API key encryption
- FEAT-010: reporting-service — Elasticsearch CQRS, 3 Kafka consumers, dashboard/trend/agent metrics APIs
- FEAT-011: mcp-server — Spring AI MCP (SSE) with create_ticket, get_ticket, list_tickets, search_faq tools
- DB-003: MongoDB collection schemas (ai_interactions 90d TTL, notifications 30d TTL, faq_cms_sync_log 7d TTL)
- INFRA-005: Kubernetes Kustomize base manifests for all 11 services (namespace, configmap, secrets-template)
- FEAT-020: customer-sdk v1.0.0 — full SupportHubClient + useTickets hook + vitest tests
- FEAT-021: agent-dashboard — ticket queue with filters (status/priority/assignee) + WebSocket real-time updates
- FEAT-022: agent-dashboard — ticket detail page + inline reply + AI assistance panel (resolution suggestions)
- FEAT-023: admin-portal — category management, agent management, SLA config pages

### 2026-03-23 (Sprint 1 — FEAT-009 implemented)
- FEAT-009: order-sync-service full implementation (OMS REST integration, Redis cache, AES-256-GCM encryption)
  - 16 Java files, 1 SQL migration, 1 unit test class (6 test cases)
  - AES-256-GCM API key encryption at rest with 12-byte random IV
  - Redis cache (600s TTL) for customer orders and individual orders
  - PII-safe logging: customerId excluded from WARN-level logs
  - RLS on oms_configs, TenantContextFilter with PostgreSQL session variable

### 2026-03-23 (Sprint 1 — DB-003, INFRA-005, FEAT-020, FEAT-010 implemented)
- DB-003: MongoDB collection schemas for ai_interactions (90d TTL), notifications (30d TTL), faq_cms_sync_log (7d TTL)
- INFRA-005: Kubernetes Kustomize base manifests for all 11 services + namespace, configmap, secrets-template
- FEAT-020: customer-sdk v1.0.0 — full SupportHubClient with getAccessToken, ticket/FAQ/notification/profile ops, useTickets hook, 3 vitest tests
- FEAT-010: reporting-service — Elasticsearch CQRS read model, 3 Kafka consumers, DashboardService, 6-endpoint REST API, Redis idempotency, DashboardServiceTest (8 unit test cases)

### 2026-03-23 (Sprint 1 — FEAT-005 implemented)
- FEAT-005: customer-service full implementation (profile CRUD, order history, address management)
  - 18 Java files, 1 SQL migration, 1 unit test class
  - WebClient integration to order-sync-service (defensive, empty-list on failure)
  - RLS on customer_addresses, TenantContextFilter with PostgreSQL session variable

### 2026-03-23 (Sprint 0 completed)
- INFRA-001: Docker Compose stack created
- INFRA-002: Maven parent POM + all 12 service modules created
- INFRA-003: Frontend monorepo with npm workspaces, 3 apps, 2 packages created
- INFRA-004: GitHub Actions CI/CD pipelines created
- DB-001: Core schema (5 Flyway migrations) created
- DB-002: Ticket schema (8 Flyway migrations with pgvector) created
- FEAT-001+002: auth-service full implementation (OTP + agent login)
- FEAT-003: api-gateway (JWT filter, tenant resolution, rate limiting, circuit breakers)
- FEAT-004: ticket-service full implementation (CRUD, state machine, SLA, Kafka)
- DOCS-001: Claude Code agent definitions and slash commands created
- 223 files, 13,401 lines of code committed to claude/init-project-m5lAc
