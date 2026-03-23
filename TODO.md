# TODO.md — SupportHub Task Tracker
> Single source of truth for all tasks, bugs, issues, and decisions.
> Managed by Claude Code agents. Updated after every task, review, analysis, or deployment.

**Last Updated:** 2026-03-23T18:00:00Z
**Active Sprint:** Sprint 1 — Core Services
**Project:** SupportHub | Rupantar Technologies

---

## Summary

| Status | Count |
|--------|-------|
| 🆕 OPEN | 0 |
| 🔄 IN_PROGRESS | 0 |
| 🔍 IN_REVIEW | 23 |
| ⚠️ BLOCKED | 0 |
| ✅ DONE | 9 |
| ❌ CANCELLED | 0 |
| **TOTAL** | **32** |

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

## 📋 BACKLOG — Sprint 2

### Sprint 2 priorities:
- INFRA-006: Terraform modules (EKS, RDS, ElastiCache, MSK, S3)
- TEST-001: Integration tests for all Sprint 1 services (addresses REVIEW-001, REVIEW-002)
- FEAT-024: customer-portal — self-service ticket creation flow + FAQ self-resolution
- FEAT-025: E2E Playwright tests (ticket creation, agent resolution, FAQ self-resolve flows)
- SEC-001: OWASP dependency scan for all Sprint 1 services — output to TODO.md
- ANAL-001: SpotBugs + Checkstyle scan for Sprint 1 services

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
