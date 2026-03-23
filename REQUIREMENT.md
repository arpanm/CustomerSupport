# SupportHub — AI-Native Customer Support Platform
## Requirements Document (v1.0)

> **Primary Domain (Phase 1):** Online food delivery (India)
> **Expansion (Phase 2+):** Fashion, Electronics, Grocery (B2C), B2B, B2B2C SaaS white-label

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [System Overview & Architecture](#2-system-overview--architecture)
3. [User Roles & Personas](#3-user-roles--personas)
4. [Data Models](#4-data-models)
5. [Customer UI — Requirements](#5-customer-ui--requirements)
6. [Customer Care Dashboard — Requirements](#6-customer-care-dashboard--requirements)
7. [Admin & Operations Portal — Requirements](#7-admin--operations-portal--requirements)
8. [API Specification](#8-api-specification)
9. [MCP Server — Requirements](#9-mcp-server--requirements)
10. [AI & ML Features](#10-ai--ml-features)
11. [FAQ & CMS Module](#11-faq--cms-module)
12. [Metadata Management](#12-metadata-management)
13. [B2B2C Store Onboarding & Multi-Tenancy](#13-b2b2c-store-onboarding--multi-tenancy)
14. [Notifications & Communications](#14-notifications--communications)
15. [Non-Functional Requirements](#15-non-functional-requirements)
16. [Security & Compliance](#16-security--compliance)
17. [Technology Stack Recommendations](#17-technology-stack-recommendations)
18. [Phased Delivery Roadmap](#18-phased-delivery-roadmap)
19. [Observability & Monitoring](#19-observability--monitoring)
20. [Open Questions & Assumptions](#20-open-questions--assumptions)

---

## 1. Executive Summary

SupportHub is a multi-tenant, AI-native customer support ticket management platform built for the Indian market. It is designed to:

- Serve end customers of food delivery platforms (Phase 1) with self-service ticket creation, tracking, and resolution.
- Empower customer care agents with a rich dashboard, AI-assisted triage, sentiment analysis, and probable resolution suggestions.
- Expose all capabilities via a structured REST API and an MCP (Model Context Protocol) server, enabling tight integration with AI chatbot agents.
- Scale horizontally as a white-label B2B2C SaaS product, allowing any store (fashion, electronics, grocery) to onboard as a tenant with isolated data, custom branding, and configurable ticket taxonomy.

The system is built around four surface areas:
1. **Customer Portal** — web + mobile-responsive UI for end users.
2. **Agent Dashboard** — feature-rich internal tool for customer care executives.
3. **Admin/Ops Portal** — metadata management, tenant onboarding, reporting.
4. **API + MCP Layer** — programmatic access for integrations and AI agents.

---

## 2. System Overview & Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                             │
│  Customer Portal (React PWA)  │  Agent Dashboard (React)        │
│  Admin Portal (React)         │  Mobile App (React Native)      │
└──────────────────┬──────────────────────────────────────────────┘
                   │ HTTPS / REST / WebSocket
┌──────────────────▼──────────────────────────────────────────────┐
│                      API GATEWAY (Kong / AWS API GW)            │
│  Auth (JWT + OAuth2)  │  Rate Limiting  │  Tenant Routing        │
└──────────────────┬──────────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────────┐
│                   CORE SERVICES (Microservices / Modular Monolith)│
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ Ticket Svc   │ │ Customer Svc │ │ Agent / User Svc         │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ Notification │ │ FAQ / CMS    │ │ Metadata / Config Svc    │ │
│  │ Svc          │ │ Svc          │ │                          │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────────────────┐ │
│  │ AI/ML Svc    │ │ Reporting    │ │ Tenant/Onboarding Svc    │ │
│  │ (Sentiment,  │ │ Svc          │ │                          │ │
│  │  Resolution) │ │              │ │                          │ │
│  └──────────────┘ └──────────────┘ └──────────────────────────┘ │
└──────────────────┬──────────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────────┐
│                      DATA LAYER                                  │
│  PostgreSQL (core)  │  Redis (cache/sessions)  │  S3 (files)    │
│  Elasticsearch (search/analytics)  │  Vector DB (pgvector/Pinecone)│
└──────────────────┬──────────────────────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────────────────────┐
│                   MCP SERVER LAYER                               │
│  MCP Tools exposed for AI chatbot agents (Claude, GPT, etc.)    │
│  Tool: create_ticket │ get_ticket │ search_tickets │ add_comment │
│  Tool: get_faq │ get_order_context │ get_resolution_suggestions  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Multi-Tenancy Model

- **Tenant** = a store/brand (e.g., "FoodCo", "FashionBrand X").
- Tenant isolation via `tenant_id` on all database records (row-level security in Postgres).
- Each tenant gets a unique subdomain: `{tenant_slug}.supporthub.in`.
- Tenant-level config: branding (logo, colors), ticket categories, SLA rules, notification templates, FAQ content, and integrations.
- Phase 1: single tenant (internal food delivery brand). Phase 2: full multi-tenancy.

### 2.3 Integration Points

| External System | Integration Type | Purpose |
|---|---|---|
| Order Management System (OMS) | REST API pull | Fetch order details by order_id |
| Customer Identity (Auth) | OAuth2 / OTP | Login via mobile OTP |
| SMS Gateway (MSG91, Kaleyra) | Webhook / REST | SMS notifications |
| WhatsApp Business API | REST | WhatsApp ticket updates |
| Email (SendGrid / AWS SES) | SMTP/API | Email notifications |
| CMS (Strapi / Contentful) | REST/GraphQL | FAQ content management |
| LLM Provider (Anthropic/OpenAI) | REST | Sentiment, resolution suggestions |
| Vector DB | SDK | Semantic search for FAQ |
| Langfuse / OpenTelemetry | SDK | AI observability |

---

## 3. User Roles & Personas

### 3.1 Customer (End User)
- A person who has placed an order on a food delivery platform.
- Authenticates via mobile OTP or social login.
- Can create, view, comment on, and track their own tickets.
- Interacts via Customer Portal (web) or AI chatbot (WhatsApp/web widget).

### 3.2 Customer Care Agent
- An employee of the support center.
- Authenticates via email + password (internal SSO optional).
- Can view all tickets assigned to them or their queue.
- Can respond to tickets, change status, add internal notes, provide resolutions.

### 3.3 Customer Care Team Lead / Supervisor
- Elevated agent with additional permissions.
- Can reassign tickets, view team-level metrics, override SLA flags.

### 3.4 Customer Care Admin
- Full access to the Agent Dashboard.
- Can manage agent accounts, configure queues, set SLA rules, view all reports.

### 3.5 Ops/Super Admin
- Platform-level administrator (Rupantar / SupportHub operations team).
- Can onboard new tenants, configure metadata globally, manage sandbox environments.

### 3.6 AI Chatbot Agent (Non-human)
- Accesses the system via MCP tools.
- Can create tickets on behalf of customers, query FAQ, fetch ticket status, add comments.

---

## 4. Data Models

### 4.1 Tenant

```
Tenant {
  id: UUID (PK)
  slug: string (unique, URL-safe)
  name: string
  display_name: string
  category: enum [food_delivery, fashion, electronics, grocery, general]
  plan: enum [sandbox, starter, growth, enterprise]
  branding: JSONB { logo_url, primary_color, secondary_color, favicon_url }
  contact_email: string
  contact_phone: string
  timezone: string (default: "Asia/Kolkata")
  locale: string (default: "en-IN")
  is_active: boolean
  created_at, updated_at: timestamp
}
```

### 4.2 Customer

```
Customer {
  id: UUID (PK)
  tenant_id: UUID (FK → Tenant)
  external_customer_id: string (from tenant's own system)
  name: string
  phone: string (E.164 format, e.g. +919876543210)
  email: string (nullable)
  preferred_language: enum [en, hi, bn, ta, te, mr, gu, kn, ml]
  profile_metadata: JSONB (any tenant-specific fields)
  is_active: boolean
  created_at, updated_at: timestamp
}
```

### 4.3 Order (Synced / Reference)

```
Order {
  id: UUID (PK)
  tenant_id: UUID (FK)
  external_order_id: string (from OMS)
  customer_id: UUID (FK → Customer)
  status: string (from OMS)
  order_date: timestamp
  delivery_address: JSONB
  items: JSONB (array of {item_id, name, qty, price})
  total_amount: decimal
  payment_method: string
  restaurant_name: string (food domain)
  raw_payload: JSONB (full OMS response, for context)
  fetched_at: timestamp
}
```

### 4.4 Ticket

```
Ticket {
  id: UUID (PK)
  ticket_number: string (human-readable, e.g. "FC-2024-001234")
  tenant_id: UUID (FK)
  customer_id: UUID (FK → Customer)
  order_id: UUID (nullable FK → Order)
  
  title: string
  description: text
  
  category: UUID (FK → TicketCategory)
  sub_category: UUID (nullable FK → TicketSubCategory)
  ticket_type: enum [complaint, inquiry, request, feedback, escalation]
  priority: enum [low, medium, high, critical]
  
  status: enum [
    open,
    pending_customer_response,
    pending_agent_response,
    in_progress,
    escalated,
    resolved,
    closed,
    reopened
  ]
  
  channel: enum [web_portal, chatbot, whatsapp, email, phone, api]
  
  assigned_to: UUID (nullable FK → AgentUser)
  assigned_team: UUID (nullable FK → Team)
  
  tags: string[]
  custom_fields: JSONB (tenant-defined extra fields)
  
  sla_due_at: timestamp (computed from SLA rules)
  sla_breached: boolean
  
  sentiment_score: float (nullable, -1 to 1)
  sentiment_label: enum [positive, neutral, negative, very_negative]
  sentiment_updated_at: timestamp
  
  first_response_at: timestamp
  resolved_at: timestamp
  closed_at: timestamp
  
  created_at, updated_at: timestamp
}
```

### 4.5 TicketActivity (Comments, Notes, Status Changes, Resolutions)

```
TicketActivity {
  id: UUID (PK)
  ticket_id: UUID (FK → Ticket)
  tenant_id: UUID (FK)
  actor_id: UUID (actor who performed the action)
  actor_type: enum [customer, agent, system, ai_bot]
  
  activity_type: enum [
    comment,
    internal_note,
    status_change,
    assignment_change,
    resolution_provided,
    reopened,
    escalated,
    communication_sent,
    attachment_added,
    ai_suggestion_applied
  ]
  
  content: text (markdown supported)
  
  metadata: JSONB {
    old_status, new_status,   -- for status_change
    old_assignee, new_assignee, -- for assignment_change
    resolution_code, resolution_text, -- for resolution_provided
    communication_channel, -- for communication_sent
    attachment_url,        -- for attachment_added
    ai_model, confidence,  -- for ai_suggestion_applied
  }
  
  is_visible_to_customer: boolean (false = internal note)
  
  created_at: timestamp
}
```

### 4.6 AgentUser

```
AgentUser {
  id: UUID (PK)
  tenant_id: UUID (FK)
  email: string
  name: string
  phone: string (nullable)
  role: enum [agent, team_lead, admin, super_admin]
  team_id: UUID (nullable FK → Team)
  is_active: boolean
  last_login_at: timestamp
  created_at, updated_at: timestamp
}
```

### 4.7 Team

```
Team {
  id: UUID (PK)
  tenant_id: UUID (FK)
  name: string
  description: string
  lead_agent_id: UUID (FK → AgentUser)
  categories: UUID[] (ticket categories this team handles)
  is_active: boolean
}
```

### 4.8 TicketCategory / SubCategory

```
TicketCategory {
  id: UUID (PK)
  tenant_id: UUID (FK)
  name: string
  slug: string
  icon: string (optional emoji or icon name)
  description: string
  sla_hours: integer (response SLA)
  resolution_sla_hours: integer
  default_priority: enum
  is_active: boolean
  sort_order: integer
}

TicketSubCategory {
  id: UUID (PK)
  category_id: UUID (FK → TicketCategory)
  tenant_id: UUID (FK)
  name: string
  slug: string
  resolution_template: text (default resolution text for agents)
  faq_tags: string[] (used to pull relevant FAQs)
  is_active: boolean
}
```

### 4.9 FAQEntry

```
FAQEntry {
  id: UUID (PK)
  tenant_id: UUID (FK)
  title: string
  content: text (markdown)
  category_tags: string[]
  sub_category_ids: UUID[] (which ticket sub-categories this FAQ is relevant for)
  is_published: boolean
  embedding: vector(1536) (for semantic search via pgvector)
  cms_external_id: string (nullable, sync reference)
  view_count: integer
  helpful_count: integer
  not_helpful_count: integer
  created_at, updated_at: timestamp
}
```

### 4.10 ResolutionTemplate

```
ResolutionTemplate {
  id: UUID (PK)
  tenant_id: UUID (FK)
  sub_category_id: UUID (nullable FK)
  title: string
  template_text: text (markdown, supports {{variables}})
  variables: JSONB (list of variable names with descriptions)
  is_active: boolean
  used_count: integer
}
```

### 4.11 Notification

```
Notification {
  id: UUID (PK)
  tenant_id: UUID (FK)
  recipient_id: UUID
  recipient_type: enum [customer, agent]
  channel: enum [sms, email, whatsapp, push, in_app]
  template_key: string
  payload: JSONB
  status: enum [pending, sent, delivered, failed]
  sent_at: timestamp
  created_at: timestamp
}
```

### 4.12 SLAPolicy

```
SLAPolicy {
  id: UUID (PK)
  tenant_id: UUID (FK)
  name: string
  applies_to: JSONB { priority?: [], category_ids?: [], ticket_type?: [] }
  first_response_hours: integer
  resolution_hours: integer
  escalation_rules: JSONB [{ after_hours, assign_to_team, notify_agent_ids }]
  is_default: boolean
}
```

---

## 5. Customer UI — Requirements

### 5.1 Authentication

**REQ-CUI-AUTH-01:** Login via mobile phone number + OTP (SMS). OTP validity: 5 minutes, 6 digits.
**REQ-CUI-AUTH-02:** Optional: Google OAuth for customers who have email accounts.
**REQ-CUI-AUTH-03:** Session management via JWT (access token 1h, refresh token 30 days).
**REQ-CUI-AUTH-04:** If customer is already identified via chatbot session token, auto-login without OTP re-entry.
**REQ-CUI-AUTH-05:** "Continue as Guest" is NOT allowed — all ticket actions require authentication.

### 5.2 Home / Landing Page

**REQ-CUI-HOME-01:** Show tenant branding (logo, banner, support headline).
**REQ-CUI-HOME-02:** Prominent "Raise a New Ticket" CTA button.
**REQ-CUI-HOME-03:** Quick access tiles for most common issue categories (e.g., "Wrong Item Delivered", "Order Not Arrived", "Refund Status").
**REQ-CUI-HOME-04:** Search bar to search existing tickets or FAQs.
**REQ-CUI-HOME-05:** Show count of open tickets for logged-in user.

### 5.3 Create New Ticket

**REQ-CUI-CREATE-01:** Step 1 — Select category from a visual tile-based category selector (icons + names).
**REQ-CUI-CREATE-02:** Step 2 — Select sub-category (dropdown or tile, based on sub-category count).
**REQ-CUI-CREATE-03:** Step 3 — Link to an order:
  - Show last 10 orders fetched from OMS with order date, restaurant name, total amount.
  - Allow search by order ID.
  - Option: "This is not related to an order."
**REQ-CUI-CREATE-04:** Step 4 — Ticket form:
  - Title field (min 10 chars, max 200 chars).
  - Description field (min 20 chars, markdown-lite: bold, bullets).
  - File/image attachment (max 3 files, 5MB each, accepted: jpg/png/pdf/mp4).
  - Custom fields rendered dynamically from sub-category config.
**REQ-CUI-CREATE-05:** Show "Similar FAQs" suggestions on the right panel (or below on mobile) as the user types the description, updating in real-time (debounced 800ms).
**REQ-CUI-CREATE-06:** If a FAQ resolves the issue, show a "My issue is resolved" button — record as `self_resolved_via_faq` and abort ticket creation.
**REQ-CUI-CREATE-07:** Confirmation screen post-creation showing ticket number, estimated response time (from SLA), and next steps.
**REQ-CUI-CREATE-08:** Duplicate detection: if an open ticket exists for the same order and same sub-category, warn the user and offer to view existing ticket.

### 5.4 My Tickets — List View

**REQ-CUI-LIST-01:** Display all tickets of the logged-in customer in reverse chronological order by default.
**REQ-CUI-LIST-02:** Each ticket card shows:
  - Ticket number + title
  - Category and sub-category badges
  - Status badge (color-coded: open=blue, in_progress=yellow, resolved=green, closed=grey, escalated=red)
  - Created date and last updated date
  - Linked order ID (if any)
  - Count of unread agent responses
**REQ-CUI-LIST-03:** Filter bar:
  - Status (multi-select)
  - Category (multi-select)
  - Ticket Type (multi-select)
  - Date range (created_at)
  - Has unread response (toggle)
**REQ-CUI-LIST-04:** Search by ticket number or title (client-side for <50 tickets, server-side for more).
**REQ-CUI-LIST-05:** Pagination: 20 tickets per page, infinite scroll on mobile.
**REQ-CUI-LIST-06:** Empty state: show friendly message + "Raise your first ticket" CTA.

### 5.5 Ticket Detail View

**REQ-CUI-DETAIL-01:** Header: ticket number, title, status badge, created date, linked order summary card (if any).
**REQ-CUI-DETAIL-02:** Order context card (if linked): order date, restaurant, items (collapsed list), total, delivery address, current order status from OMS.
**REQ-CUI-DETAIL-03:** Timeline / conversation thread:
  - Chronological activity feed.
  - Customer messages shown on right (blue bubble), agent messages on left (grey bubble).
  - System events (status changes, escalations) shown as inline dividers.
  - Agent name shown as "Support Team" (not real name) on customer-facing view.
  - Resolution activities highlighted with a green "✓ Resolution Provided" banner.
**REQ-CUI-DETAIL-04:** Attachments shown as thumbnails with download links.
**REQ-CUI-DETAIL-05:** Reply box:
  - Rich text (markdown-lite).
  - Attach file button.
  - Send button.
  - Only visible if ticket status is not `closed`.
**REQ-CUI-DETAIL-06:** Actions sidebar / bottom bar:
  - "Mark as Resolved" (only if status is `pending_customer_response` or `in_progress`, changes status to `resolved`).
  - "Reopen Ticket" (if status is `resolved` or `closed`, reopens with mandatory reason comment; max 2 reopens per ticket by default).
  - "Escalate" (if status not already `escalated`; requires mandatory reason; tenant-configurable whether to show this button).
**REQ-CUI-DETAIL-07:** Notification preferences toggle: "Notify me via SMS / WhatsApp for updates."

### 5.6 FAQ / Help Center

**REQ-CUI-FAQ-01:** Dedicated `/help` page with categorized FAQ listing.
**REQ-CUI-FAQ-02:** Full-text + semantic search of FAQs.
**REQ-CUI-FAQ-03:** FAQ detail page with markdown-rendered content, related FAQs, and "Was this helpful?" (👍/👎) feedback.
**REQ-CUI-FAQ-04:** Breadcrumb navigation and category filtering.
**REQ-CUI-FAQ-05:** "Still need help? Raise a ticket" CTA on every FAQ page with pre-filled category.

---

## 6. Customer Care Dashboard — Requirements

### 6.1 Authentication & Session

**REQ-AGT-AUTH-01:** Email + password login.
**REQ-AGT-AUTH-02:** 2FA via OTP on email for admin roles.
**REQ-AGT-AUTH-03:** Session timeout after 8 hours of inactivity.
**REQ-AGT-AUTH-04:** Role-based route guarding.

### 6.2 Agent Dashboard — Home

**REQ-AGT-DASH-01:** Metric cards (top row):
  - My Open Tickets
  - My SLA Breached / At Risk
  - Awaiting My Response
  - Resolved Today (by me)
**REQ-AGT-DASH-02:** Team metric cards (visible to Team Lead+):
  - Team Open Tickets
  - Team SLA Breach Rate (today)
  - Average First Response Time (today)
  - Average Resolution Time (today)
**REQ-AGT-DASH-03:** My Tickets feed (quick access to tickets needing response).
**REQ-AGT-DASH-04:** Real-time updates via WebSocket — new ticket assignments, SLA alerts.
**REQ-AGT-DASH-05:** Notification bell for unread alerts.

### 6.3 Ticket Queue / List View

**REQ-AGT-LIST-01:** Tabbed views:
  - "My Tickets" (assigned to me)
  - "Unassigned" (pool queue)
  - "Team Tickets" (Team Lead+)
  - "All Tickets" (Admin+)
  - "Escalated" (Team Lead+)
**REQ-AGT-LIST-02:** Each ticket row shows:
  - Ticket number, title
  - Customer name, phone (masked: +91 98765 ****), avatar initials
  - Category + sub-category
  - Status, Priority
  - Assigned agent name
  - Created at, Last updated, SLA due time (with color: green/amber/red)
  - Sentiment icon (😊/😐/😠/😡)
  - Source channel icon
**REQ-AGT-LIST-03:** Filter panel:
  - Status (multi-select)
  - Priority (multi-select)
  - Category (multi-select)
  - Ticket Type (multi-select)
  - Assigned Agent (searchable dropdown, Team Lead+)
  - Team (Admin+)
  - Channel (multi-select)
  - Date range (created_at, updated_at, sla_due_at)
  - SLA Breached (toggle)
  - Has unread customer comment (toggle)
  - Sentiment (multi-select)
  - Product Category (if B2B2C tenant has product categories)
**REQ-AGT-LIST-04:** Search:
  - By ticket number (exact)
  - By ticket title (full-text)
  - By customer phone number (exact/partial)
  - By customer email (exact)
  - By order ID (exact)
  - By customer name (partial)
**REQ-AGT-LIST-05:** Sortable columns: created_at, updated_at, sla_due_at, priority.
**REQ-AGT-LIST-06:** Bulk actions (Admin+): bulk assign, bulk status change (e.g., bulk close resolved tickets older than 7 days), bulk export.
**REQ-AGT-LIST-07:** Pagination: 25 per page with page selector. Also support infinite scroll preference.
**REQ-AGT-LIST-08:** Saved filters / views (per agent).

### 6.4 Ticket Detail View — Agent

**REQ-AGT-DETAIL-01:** Split layout — left 60%: conversation thread + reply box. Right 40%: context panels.

**Left Panel — Conversation:**
- Same timeline as customer view, but shows internal notes (yellow background, "Internal" tag).
- Agent can see real customer name and all contact details.
- Show read receipts for customer (if applicable).
- Typing indicator for customer (WebSocket).

**REQ-AGT-DETAIL-02:** Reply / Action composer:
  - "Reply to Customer" tab — visible to customer.
  - "Internal Note" tab — NOT visible to customer.
  - "Send Communication" tab — triggers SMS/WhatsApp/Email with template selector.
  - "Provide Resolution" tab — structured resolution form:
    - Resolution code (dropdown from sub-category resolution codes)
    - Resolution text (rich text, with template picker)
    - Refund/compensation details (JSONB, rendered as form based on tenant config)
  - Rich text editor (bold, italic, bullet, numbered list, link insert).
  - Canned responses picker (search and insert pre-written responses).
  - Attach file.
  - @mention agent (for internal notes).

**REQ-AGT-DETAIL-03:** Ticket Info Panel (right side, top):
  - Status dropdown (inline change with mandatory comment for certain transitions).
  - Priority dropdown.
  - Assigned Agent (reassign with search).
  - Assigned Team (reassign).
  - Category / Sub-category (editable).
  - Tags (multi-select with free-form add).
  - SLA status bar (time remaining / overdue).
  - Channel badge.
  - Ticket type.
  - Custom fields.

**REQ-AGT-DETAIL-04:** Customer Context Panel:
  - Customer name, phone, email, preferred language.
  - Customer's ticket history (mini-list: last 5 tickets with status).
  - Link: "View full customer profile."
  - Sentiment score badge + trend (last 3 tickets).

**REQ-AGT-DETAIL-05:** Order Context Panel (if linked):
  - Full order card (same as customer view).
  - Button: "Refresh from OMS" to pull latest order status.
  - Order items with images (if available from OMS).
  - Delivery partner tracking link (if available).

**REQ-AGT-DETAIL-06:** AI Assistance Panel (right side, bottom):
  - **Sentiment Analysis:** Current ticket sentiment score + label, last updated time, trend graph.
  - **Probable Resolution Suggestions:** Top 3 AI-suggested resolutions ranked by confidence. Each shows:
    - Resolution text preview.
    - Source: FAQ entry or Resolution Template (with link).
    - Confidence % badge.
    - "Apply" button to paste into resolution composer.
  - "Regenerate suggestions" button.
  - "Was this suggestion helpful?" feedback (👍/👎).

**REQ-AGT-DETAIL-07:** Related Tickets panel: tickets from the same customer or same order, with status.

**REQ-AGT-DETAIL-08:** Activity Log (collapsible): full audit trail of all status changes, assignments, notes.

### 6.5 Customer Profile Page

**REQ-AGT-CUST-01:** Agent can navigate to customer profile from ticket detail or search.
**REQ-AGT-CUST-02:** Profile shows:
  - Name, phone, email, language, account creation date.
  - Lifetime ticket stats: total, open, resolved, average resolution time.
  - Sentiment trend across last 20 tickets (sparkline chart).
  - Order history summary (count, last order date, total spend if OMS provides).
**REQ-AGT-CUST-03:** Full ticket list for this customer (paginated, with all list-view filters applicable).
**REQ-AGT-CUST-04:** Agent can add a customer note (internal, not visible to customer).
**REQ-AGT-CUST-05:** Agent can update customer's preferred language.

### 6.6 Reports & Analytics

**REQ-AGT-RPT-01:** Report types:

**a) Ticket Volume Report:**
- Total tickets by date range.
- Breakdown by: status, category, sub-category, channel, priority, ticket type.
- Trend chart (daily/weekly/monthly bars).
- Comparison with previous period.

**b) Disposition / Resolution Report:**
- Resolution rate (resolved / total closed * 100).
- Resolution by category and sub-category.
- Breakdown of resolution codes used.
- Average resolution time by category.
- Reopened ticket rate.

**c) SLA Compliance Report:**
- First response time distribution (histogram).
- Resolution time distribution.
- SLA breach rate overall + by category.
- Breach reason analysis (from agent notes, if tagged).

**d) Agent Performance Report (Team Lead+):**
- Tickets handled per agent.
- Average response time per agent.
- Resolution rate per agent.
- Customer sentiment score per agent.
- CSAT scores (if feedback collected).

**e) Funnel Report:**
- Ticket status funnel: Created → Acknowledged → In Progress → Resolved → Closed.
- Drop-off rates at each stage.
- Average time in each stage.

**f) Customer Sentiment Report:**
- Sentiment distribution (positive/neutral/negative) over time.
- Most negative categories.
- Sentiment by agent.

**REQ-AGT-RPT-02:** Filters available on all reports:
- Date range (presets: today, yesterday, last 7 days, last 30 days, custom).
- Tenant (Super Admin only).
- Category, Sub-category.
- Channel.
- Agent (Team Lead+).
- Team.

**REQ-AGT-RPT-03:** Export: CSV and PDF export of any report.
**REQ-AGT-RPT-04:** Report scheduling: email reports daily/weekly to configured recipients.

---

## 7. Admin & Operations Portal — Requirements

### 7.1 Metadata Management

**REQ-ADMIN-META-01 — Ticket Categories:**
- CRUD for tenant-scoped ticket categories.
- Fields: name, slug, icon (emoji picker), description, default SLA hours, default priority, active/inactive.
- Drag-and-drop reorder.

**REQ-ADMIN-META-02 — Ticket Sub-Categories:**
- CRUD within parent category.
- Fields: name, default resolution template, linked FAQ tags, active/inactive.

**REQ-ADMIN-META-03 — Custom Fields:**
- Admin can define custom fields per sub-category.
- Field types: text, number, dropdown, date, multi-select, boolean.
- Each field: label, key, required/optional, validation rules, visible to customer (yes/no).
- These fields render dynamically on the create-ticket form and ticket detail view.

**REQ-ADMIN-META-04 — Resolution Codes:**
- CRUD for resolution code taxonomy.
- Fields: code, display label, description, mapped sub-categories.

**REQ-ADMIN-META-05 — Canned Responses:**
- CRUD for pre-written agent responses.
- Tags for categorization.
- Variable substitution: `{{customer_name}}`, `{{order_id}}`, `{{ticket_number}}`.
- Visible to agents in the reply composer.

**REQ-ADMIN-META-06 — SLA Policies:**
- CRUD for SLA policies.
- Rule engine: apply to combinations of priority + category + ticket type.
- Escalation chain: after X hours breach, assign to which team/agent and notify whom.

**REQ-ADMIN-META-07 — Team Management:**
- Create/edit teams.
- Assign agents to teams.
- Map teams to ticket categories.
- Set team leads.

**REQ-ADMIN-META-08 — Agent Management:**
- Create/edit/deactivate agent accounts.
- Role assignment.
- Team assignment.
- Password reset trigger.
- View agent activity log.

**REQ-ADMIN-META-09 — Notification Templates:**
- CRUD for notification templates per channel (SMS, Email, WhatsApp).
- Variable substitution support.
- Preview with sample data.
- Map templates to ticket events (created, resolved, status_changed, etc.).

### 7.2 Customer Management

**REQ-ADMIN-CUST-01:** Customer list with search (name, phone, email, external_customer_id), pagination, and filters (active/inactive, date range).
**REQ-ADMIN-CUST-02:** Customer detail page (same as Agent customer profile view + edit capabilities).
**REQ-ADMIN-CUST-03:** Create customer manually (for phone/walk-in scenarios).
**REQ-ADMIN-CUST-04:** Import customers via CSV upload.
**REQ-ADMIN-CUST-05:** Deactivate/reactivate customer.
**REQ-ADMIN-CUST-06:** Merge duplicate customer records (by phone / email matching).

---

## 8. API Specification

### 8.1 API Design Principles

- RESTful JSON API.
- All endpoints versioned under `/api/v1/`.
- All requests require `Authorization: Bearer <jwt>` header.
- All responses include `tenant_id` validation.
- Pagination: cursor-based (`?cursor=<encoded>&limit=<n>`).
- Error format: `{ "error": { "code": "TICKET_NOT_FOUND", "message": "...", "details": {} } }`.
- All timestamps in ISO 8601 UTC.
- Rate limiting headers: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.

### 8.2 Authentication API

```
POST   /api/v1/auth/otp/send          — Send OTP to phone
POST   /api/v1/auth/otp/verify        — Verify OTP, return JWT pair
POST   /api/v1/auth/refresh           — Refresh access token
POST   /api/v1/auth/logout            — Invalidate refresh token
POST   /api/v1/auth/agent/login       — Agent email+password login
POST   /api/v1/auth/agent/2fa/verify  — Agent 2FA OTP verification
```

### 8.3 Customer API

```
GET    /api/v1/customers/me           — Get current customer profile
PUT    /api/v1/customers/me           — Update profile (language, email)
GET    /api/v1/customers/:id          — Get customer (Agent+)
GET    /api/v1/customers              — List customers (Agent+, paginated, searchable)
POST   /api/v1/customers              — Create customer (Admin+)
PUT    /api/v1/customers/:id          — Update customer (Admin+)
DELETE /api/v1/customers/:id          — Deactivate customer (Admin+)
GET    /api/v1/customers/:id/tickets  — Get customer's ticket history
GET    /api/v1/customers/:id/orders   — Get customer's orders from OMS
```

### 8.4 Order API

```
GET    /api/v1/orders/:order_id       — Fetch order (from cache or live OMS)
GET    /api/v1/orders/customer/:id    — List orders for a customer
POST   /api/v1/orders/:order_id/refresh — Force re-fetch from OMS
```

### 8.5 Ticket API

```
POST   /api/v1/tickets                — Create ticket
GET    /api/v1/tickets                — List tickets (Customer: own; Agent+: filtered)
GET    /api/v1/tickets/:id            — Get ticket detail
PUT    /api/v1/tickets/:id            — Update ticket (status, priority, assignment, tags, custom_fields)
DELETE /api/v1/tickets/:id            — Soft-delete (Admin+)

GET    /api/v1/tickets/:id/activities         — Get activity timeline
POST   /api/v1/tickets/:id/activities         — Add comment / internal note

POST   /api/v1/tickets/:id/resolve    — Provide resolution (resolution_code, resolution_text, refund_details)
POST   /api/v1/tickets/:id/reopen     — Reopen ticket with reason
POST   /api/v1/tickets/:id/escalate   — Escalate ticket
POST   /api/v1/tickets/:id/assign     — Assign to agent/team (Agent+)
POST   /api/v1/tickets/:id/communicate — Send SMS/Email/WhatsApp to customer (Agent+)

GET    /api/v1/tickets/:id/ai/sentiment       — Get current sentiment analysis
POST   /api/v1/tickets/:id/ai/sentiment       — Trigger sentiment recompute
GET    /api/v1/tickets/:id/ai/resolution-suggestions — Get AI resolution suggestions

POST   /api/v1/tickets/:id/attachments  — Upload attachment
GET    /api/v1/tickets/:id/attachments  — List attachments
DELETE /api/v1/tickets/:id/attachments/:attachment_id — Delete attachment

GET    /api/v1/tickets/search          — Full-text + filter search
```

**Create Ticket Request Body:**
```json
{
  "category_id": "uuid",
  "sub_category_id": "uuid",
  "order_id": "uuid | null",
  "title": "string",
  "description": "string",
  "channel": "web_portal",
  "attachments": ["url1", "url2"],
  "custom_fields": { "key": "value" }
}
```

**Ticket Status Transition Rules:**

```
open            → pending_agent_response (on agent reply)
open            → in_progress (agent picks up)
open            → escalated (manual escalation)
pending_agent_response → pending_customer_response (customer replies)
pending_customer_response → in_progress (agent replies)
in_progress     → resolved (resolution provided)
in_progress     → escalated
resolved        → closed (auto after 48h if no customer action)
resolved        → reopened (customer reopens)
reopened        → in_progress (agent picks up)
escalated       → in_progress (de-escalated)
closed          → reopened (within 7 days, max 2 times)
```

### 8.6 FAQ API

```
GET    /api/v1/faq                      — List FAQs (paginated, categorized)
GET    /api/v1/faq/:id                  — Get FAQ detail
GET    /api/v1/faq/search               — Full-text + semantic search
POST   /api/v1/faq/:id/feedback         — Submit helpful/not-helpful feedback
POST   /api/v1/faq/sync                 — Trigger CMS sync (Admin+)

GET    /api/v1/faq/suggestions          — Get FAQ suggestions for a query string
   ?q=my order is late&sub_category_id=uuid
   Returns top 5 semantically matched FAQs
```

### 8.7 Metadata API

```
GET/POST/PUT/DELETE /api/v1/categories
GET/POST/PUT/DELETE /api/v1/categories/:id/sub-categories
GET/POST/PUT/DELETE /api/v1/resolution-codes
GET/POST/PUT/DELETE /api/v1/canned-responses
GET/POST/PUT/DELETE /api/v1/sla-policies
GET/POST/PUT/DELETE /api/v1/teams
GET/POST/PUT/DELETE /api/v1/notification-templates
GET/POST/PUT/DELETE /api/v1/custom-fields
```

### 8.8 Reporting API

```
GET    /api/v1/reports/ticket-volume
GET    /api/v1/reports/disposition
GET    /api/v1/reports/sla-compliance
GET    /api/v1/reports/agent-performance
GET    /api/v1/reports/funnel
GET    /api/v1/reports/sentiment
POST   /api/v1/reports/export            — Async export (returns job_id)
GET    /api/v1/reports/export/:job_id    — Poll export status / download
```

### 8.9 Tenant / Admin API

```
GET/POST/PUT/DELETE /api/v1/tenants          — Super Admin only
GET    /api/v1/tenants/:id/stats             — Tenant usage stats
POST   /api/v1/tenants/:id/provision-sandbox — Create sandbox environment
```

### 8.10 Webhook API

Tenants can register webhooks to receive ticket lifecycle events:

```
POST   /api/v1/webhooks                 — Register webhook URL
GET    /api/v1/webhooks                 — List registered webhooks
DELETE /api/v1/webhooks/:id             — Remove webhook
```

Webhook payload format:
```json
{
  "event": "ticket.resolved",
  "tenant_id": "uuid",
  "timestamp": "2024-01-15T10:30:00Z",
  "data": { "ticket": { ... } }
}
```

Events emitted: `ticket.created`, `ticket.status_changed`, `ticket.resolved`, `ticket.escalated`, `ticket.reopened`, `ticket.comment_added`, `ticket.assigned`.

---

## 9. MCP Server — Requirements

The MCP (Model Context Protocol) server exposes SupportHub capabilities as tools that an AI agent (Claude, etc.) can invoke to assist customers or agents in real time. This is the primary integration point for the AI chatbot.

### 9.1 MCP Server Design

- Implements the MCP specification (JSON-RPC 2.0 over HTTP/SSE).
- Auth: MCP client must present a `Bearer` token scoped to a specific tenant + customer (for customer-facing bots) or tenant + agent (for agent-assist bots).
- All tool calls are validated against role permissions before execution.
- Tool calls are logged to Langfuse with trace context for observability.

### 9.2 MCP Tools — Customer-Facing (Chatbot)

```
Tool: create_ticket
Description: Create a new support ticket on behalf of the authenticated customer
Parameters:
  - category_slug: string (required)
  - sub_category_slug: string (required)
  - order_id: string (optional)
  - title: string (required)
  - description: string (required)
Returns: { ticket_id, ticket_number, status, estimated_response_time }

Tool: get_ticket_status
Description: Get the current status and latest update for a ticket
Parameters:
  - ticket_number: string (required)
Returns: { ticket_number, title, status, last_updated, latest_agent_message, resolution }

Tool: list_customer_tickets
Description: List all open or recent tickets for the authenticated customer
Parameters:
  - status_filter: string[] (optional, e.g. ["open", "in_progress"])
  - limit: int (optional, default 5)
Returns: [{ ticket_number, title, status, created_at }]

Tool: add_comment
Description: Add a comment to an existing ticket on behalf of the customer
Parameters:
  - ticket_number: string (required)
  - comment: string (required)
Returns: { activity_id, created_at }

Tool: reopen_ticket
Description: Reopen a resolved ticket with a reason
Parameters:
  - ticket_number: string (required)
  - reason: string (required)
Returns: { ticket_number, new_status }

Tool: search_faq
Description: Search the FAQ knowledge base for help articles
Parameters:
  - query: string (required)
  - limit: int (optional, default 3)
Returns: [{ faq_id, title, content_preview, relevance_score }]

Tool: get_faq_detail
Description: Get the full content of a specific FAQ article
Parameters:
  - faq_id: string (required)
Returns: { title, content_markdown }

Tool: get_order_details
Description: Fetch details of a customer's order
Parameters:
  - order_id: string (required)
Returns: { order_id, status, restaurant_name, items, total, delivery_address, estimated_delivery_time }

Tool: get_order_list
Description: List recent orders for the authenticated customer
Parameters:
  - limit: int (optional, default 5)
Returns: [{ order_id, restaurant_name, total, status, order_date }]

Tool: get_available_categories
Description: Get the list of available ticket categories for the current tenant
Parameters: (none)
Returns: [{ slug, name, description }]

Tool: get_sub_categories
Description: Get sub-categories for a given category
Parameters:
  - category_slug: string (required)
Returns: [{ slug, name }]
```

### 9.3 MCP Tools — Agent Assist (Internal Chatbot)

```
Tool: search_tickets_for_agent
Description: Search tickets by customer phone/email/order_id (for agent use only)
Parameters:
  - customer_phone: string (optional)
  - order_id: string (optional)
  - status: string[] (optional)
Returns: [{ ticket_number, title, status, customer_name }]

Tool: get_ticket_full_detail
Description: Get complete ticket context for an agent
Parameters:
  - ticket_number: string (required)
Returns: Full ticket object with customer, order, activity timeline, AI analysis

Tool: suggest_resolution
Description: Get AI-generated resolution suggestions for a ticket
Parameters:
  - ticket_id: string (required)
Returns: [{ resolution_text, source_type, source_id, confidence }]

Tool: apply_resolution
Description: Apply a resolution to a ticket
Parameters:
  - ticket_id: string (required)
  - resolution_code: string (required)
  - resolution_text: string (required)
Returns: { activity_id, new_status }
```

### 9.4 MCP Resource Endpoints

```
Resource: ticket://{tenant_id}/{ticket_number}
  — Returns ticket JSON (for context injection)

Resource: customer://{tenant_id}/{customer_phone}
  — Returns customer profile JSON

Resource: faq://{tenant_id}
  — Returns paginated FAQ list as structured data

Resource: categories://{tenant_id}
  — Returns category taxonomy
```

### 9.5 MCP Prompt Templates

```
Prompt: customer_support_system_prompt
  — Parameterized system prompt for the chatbot agent, includes:
    - Tenant name and support scope description
    - Currently authenticated customer context
    - Available MCP tools summary
    - Escalation instructions

Prompt: agent_assist_system_prompt
  — System prompt for agent-side AI assist with:
    - Agent role context
    - Available MCP tools
    - Resolution suggestion instructions
```

---

## 10. AI & ML Features

### 10.1 Sentiment Analysis

**REQ-AI-SENT-01:** Sentiment is computed on the aggregate text of: ticket title + description + all customer comments (concatenated).
**REQ-AI-SENT-02:** Analysis runs:
  - At ticket creation.
  - After every new customer comment or ticket description update.
  - Debounced: min 5 minutes between consecutive analyses for the same ticket.
**REQ-AI-SENT-03:** LLM-based sentiment (preferred for Indian language support):
  - Prompt: classify the following customer text into one of: `very_negative (-1.0 to -0.6)`, `negative (-0.6 to -0.2)`, `neutral (-0.2 to 0.2)`, `positive (0.2 to 0.6)`, `very_positive (0.6 to 1.0)`. Return JSON `{ "label": "...", "score": float, "reason": "brief explanation" }`.
  - Model: `claude-haiku` (cost-efficient, fast) with fallback to multilingual BERT-based model for offline/cost cap scenarios.
  - Support Indian languages: English, Hindi, Hinglish (code-mixed), Tamil, Telugu, Kannada, Bengali, Marathi, Gujarati.
**REQ-AI-SENT-04:** Store `sentiment_score`, `sentiment_label`, `sentiment_updated_at` on the Ticket record.
**REQ-AI-SENT-05:** Display in Agent Dashboard:
  - Sentiment badge with color (😡 red = very_negative, 😠 orange = negative, 😐 yellow = neutral, 😊 green = positive).
  - On ticket detail AI Assistance panel: show score, label, reason, and a sparkline of sentiment per comment (history).
**REQ-AI-SENT-06:** Sentiment drives automatic priority escalation: if `very_negative` and current priority < `high`, auto-escalate to `high` and notify assigned agent.

### 10.2 Probable Resolution Suggestions

**REQ-AI-RES-01:** Triggered when agent opens a ticket detail view (lazy-loaded).
**REQ-AI-RES-02:** Two-stage pipeline:
  - **Stage 1 — Retrieval:** Semantic search over FAQEntry and ResolutionTemplate vectors using ticket title + description as query. Retrieve top 10 candidates.
  - **Stage 2 — Ranking:** LLM prompt passes retrieved candidates + full ticket context and asks to rank top 3 with confidence scores and explanations.
**REQ-AI-RES-03:** LLM prompt includes:
  - Ticket title, description, category, sub-category.
  - Order context (if linked): order status, items, any delivery issues.
  - Retrieved FAQ / resolution template candidates.
  - Instruction: "Rank these resolutions by how well they resolve this customer's issue. For each, provide: resolution_text (adapted to this specific ticket), source_type, source_id, confidence (0.0 to 1.0), explanation."
**REQ-AI-RES-04:** Return top 3 ranked suggestions. Display on Agent dashboard with confidence badges.
**REQ-AI-RES-05:** "Apply" button pastes suggestion into resolution composer (editable before sending).
**REQ-AI-RES-06:** Feedback loop: agent thumbs up/down each suggestion → stored for future fine-tuning.
**REQ-AI-RES-07:** Cache suggestions for 10 minutes per ticket (since ticket context rarely changes rapidly).
**REQ-AI-RES-08:** Model: `claude-sonnet` for quality suggestions. Async generation with streaming to show results progressively.

### 10.3 AI Embeddings for Semantic Search

**REQ-AI-EMBED-01:** Embed all FAQEntry content using `text-embedding-3-small` (OpenAI) or equivalent Anthropic embeddings.
**REQ-AI-EMBED-02:** Store vectors in `pgvector` extension column on the `FAQEntry` table.
**REQ-AI-EMBED-03:** Re-embed on FAQ content update.
**REQ-AI-EMBED-04:** Similarity search: cosine similarity, threshold ≥ 0.75.
**REQ-AI-EMBED-05:** Hybrid search: combine vector similarity + BM25 text score (via Elasticsearch or pg_trgm), weighted 60% vector + 40% text.

### 10.4 Duplicate Ticket Detection

**REQ-AI-DUP-01:** On ticket creation, compute embedding of new ticket title+description.
**REQ-AI-DUP-02:** Check cosine similarity against open tickets for the same customer + same tenant.
**REQ-AI-DUP-03:** If similarity > 0.85 AND same sub-category, surface warning to customer: "We found a similar ticket: [TC-XXXX]. Is this the same issue?"
**REQ-AI-DUP-04:** Customer can confirm duplicate (abort creation + link to existing) or proceed (create new).

---

## 11. FAQ & CMS Module

### 11.1 CMS Integration

**REQ-FAQ-CMS-01:** Support Strapi (primary) or Contentful (secondary) as CMS backends.
**REQ-FAQ-CMS-02:** Sync trigger:
  - Manual: Admin presses "Sync from CMS" button.
  - Automated: Webhook from CMS on publish event.
  - Scheduled: Nightly sync at 02:00 IST.
**REQ-FAQ-CMS-03:** Sync operation: pull all published FAQ entries from CMS, upsert into `FAQEntry` table, re-embed updated entries.
**REQ-FAQ-CMS-04:** CMS integration config per tenant: CMS type, API URL, API key (stored encrypted), content type name, field mapping.
**REQ-FAQ-CMS-05:** If no CMS is configured, Admin can create/edit FAQ entries directly in the Admin portal.

### 11.2 FAQ Management in Admin Portal

**REQ-FAQ-ADMIN-01:** CRUD for FAQ entries (when no CMS, or for overrides).
**REQ-FAQ-ADMIN-02:** Markdown editor with preview.
**REQ-FAQ-ADMIN-03:** Tag management and category linking.
**REQ-FAQ-ADMIN-04:** Publish/unpublish toggle.
**REQ-FAQ-ADMIN-05:** Analytics: view count, helpful/not-helpful ratio per FAQ.

### 11.3 FAQ on Customer Portal (see Section 5.6)

### 11.4 FAQ in AI Chatbot (via MCP `search_faq` and `get_faq_detail` tools)

**REQ-FAQ-BOT-01:** Chatbot uses `search_faq` with the customer's natural language query.
**REQ-FAQ-BOT-02:** If a high-confidence FAQ match is found (score > 0.80), bot presents FAQ content as answer.
**REQ-FAQ-BOT-03:** If multiple matches, bot presents top 3 as options.
**REQ-FAQ-BOT-04:** After presenting FAQ: "Did this resolve your issue? (Yes/No)" — if No, bot proceeds to ticket creation flow using `create_ticket`.
**REQ-FAQ-BOT-05:** Track "self-resolved via FAQ" events for reporting.

---

## 12. Metadata Management

(Detailed CRUD requirements provided in Section 7.1. This section summarizes the complete metadata entity inventory.)

### 12.1 Tenant-Scoped Metadata Entities

| Entity | Admin UI | API | Notes |
|---|---|---|---|
| Ticket Categories | ✓ | ✓ | Per tenant |
| Ticket Sub-Categories | ✓ | ✓ | Per category |
| Custom Fields | ✓ | ✓ | Per sub-category |
| Resolution Codes | ✓ | ✓ | Global per tenant |
| Resolution Templates | ✓ | ✓ | Per sub-category |
| Canned Responses | ✓ | ✓ | Per tenant, tagged |
| SLA Policies | ✓ | ✓ | Rule-based |
| Teams | ✓ | ✓ | Per tenant |
| Notification Templates | ✓ | ✓ | Per channel + event |
| FAQ Entries | ✓ | ✓ | CMS-synced or manual |
| Branding Config | ✓ | ✓ | Logo, colors |
| Webhook Registrations | ✓ | ✓ | Per tenant |

### 12.2 Global (Super Admin) Metadata

| Entity | Notes |
|---|---|
| Tenants | Onboarding and config |
| Feature Flags | Per-tenant feature toggles |
| LLM Provider Config | API keys, model routing |
| Embedding Config | Provider, model, dimension |
| CMS Provider Config | Strapi / Contentful per tenant |

---

## 13. B2B2C Store Onboarding & Multi-Tenancy

### 13.1 Tenant Onboarding Flow

**REQ-TENANT-01:** Super Admin creates a new tenant record via Admin Portal.
**REQ-TENANT-02:** Onboarding wizard steps:
  1. **Basic Info:** Tenant name, slug, category, contact email, timezone, preferred language.
  2. **Branding:** Upload logo, set primary/secondary colors, set support portal title.
  3. **Plan Selection:** Sandbox / Starter / Growth / Enterprise.
  4. **Integration Config:**
     - OMS (Order Management System) API: endpoint, auth type (API key/OAuth), field mapping (order_id, customer_id, items, status, etc.).
     - Customer Identity: OTP gateway (which SMS provider), or external auth.
     - Product Catalog API (optional): for electronics/fashion/grocery categories.
     - SCM / Inventory API (optional).
     - FAQ/CMS: CMS provider and credentials.
  5. **Ticket Taxonomy:** Either use a default template (food_delivery / fashion / electronics / grocery) or customize categories and sub-categories.
  6. **SLA Config:** Set default SLAs per priority.
  7. **Notification Setup:** Configure SMS/WhatsApp/Email gateways (per tenant or shared platform gateway).
  8. **Agent Setup:** Create initial admin agent account for the tenant.
  9. **Sandbox Provision:** Seed synthetic data (orders, customers, tickets) for testing.

**REQ-TENANT-03:** Sandbox Environment:
  - Fully isolated data for the tenant (same DB with `tenant_id` partitioning, OR separate schema per tenant for Enterprise plan).
  - Seeded with: 50 synthetic customers, 200 synthetic orders, 30 synthetic tickets across all categories and statuses.
  - Synthetic order data matches tenant's OMS schema.
  - Synthetic FAQ entries from the chosen template.
  - A "sandbox mode" banner on all UIs so agent testers know they're in sandbox.

**REQ-TENANT-04:** Go-live checklist:
  - All integration credentials tested and verified.
  - At least one agent account active.
  - At least 3 ticket categories configured.
  - Notification templates filled for at least: ticket_created, ticket_resolved.
  - Super Admin approval to flip `is_active = true` and `plan != sandbox`.

**REQ-TENANT-05:** Domain config:
  - Customer portal: `{slug}.supporthub.in` (managed sub-domain) OR custom domain with CNAME.
  - Agent dashboard: `agents.{slug}.supporthub.in`.

### 13.2 Default Ticket Taxonomy Templates

**Food Delivery Template:**
- Order Issues: Item Not Delivered, Wrong Item, Missing Item, Stale/Spoiled Food, Packaging Issue
- Delivery Issues: Order Late, Delivery Partner Behavior, Delivered to Wrong Address
- Payment & Refund: Refund Not Received, Double Charged, Coupon Not Applied
- Account Issues: Login Problem, Address Management, Order History

**Fashion Template:**
- Order Issues: Item Not Delivered, Wrong Item/Size, Damaged Item
- Returns & Exchanges: Return Request, Exchange Request, Refund Status
- Payment: Payment Failed, Refund Status, COD Issues
- Product Queries: Size Guide, Authenticity, Stock Query

**Electronics Template:**
- Order Issues: Item Not Delivered, Wrong Product, Missing Accessories
- Technical Support: Product Not Working, Setup Help, Warranty Claim
- Returns & Repair: Return Request, Repair Status, Replacement
- Payment & Warranty: Invoice Request, Warranty Registration

**Grocery Template:**
- Order Issues: Item Not Delivered, Expiry/Quality Issue, Substitution Issue
- Delivery: Late Delivery, Partial Delivery
- Returns & Refunds: Return Perishable, Refund Status
- Account: Subscription Issues, Address

### 13.3 Feature Flags per Tenant

| Feature | Sandbox | Starter | Growth | Enterprise |
|---|---|---|---|---|
| AI Sentiment Analysis | ✓ (demo) | ✓ | ✓ | ✓ |
| AI Resolution Suggestions | ✓ (demo) | ✗ | ✓ | ✓ |
| Chatbot / MCP | ✗ | ✗ | ✓ | ✓ |
| Custom Domain | ✗ | ✗ | ✓ | ✓ |
| Webhooks | ✗ | ✓ | ✓ | ✓ |
| Dedicated Schema (DB) | ✗ | ✗ | ✗ | ✓ |
| White-label Branding | ✗ | Basic | Full | Full |
| Advanced Reports | ✗ | Basic | Full | Full |
| SLA Policies | 1 | 3 | Unlimited | Unlimited |
| Max Agents | 3 | 10 | 50 | Unlimited |

---

## 14. Notifications & Communications

### 14.1 Customer Notifications

**Events → Customer Notifications:**

| Event | SMS | WhatsApp | Email |
|---|---|---|---|
| Ticket Created | ✓ (ticket number) | Optional | Optional |
| Agent First Reply | ✓ | ✓ | ✓ |
| Ticket Resolved | ✓ | ✓ | ✓ |
| Resolution Awaiting Confirmation | ✓ | ✓ | ✓ |
| Ticket Closed (auto) | SMS | — | ✓ |
| Ticket Escalated | — | ✓ | ✓ |
| Ticket Reopened | — | ✓ | ✓ |

**REQ-NOTIF-01:** SMS via MSG91 (primary) or Kaleyra.
**REQ-NOTIF-02:** WhatsApp via official WhatsApp Business API (Meta-approved templates only for outbound).
**REQ-NOTIF-03:** Email via SendGrid or AWS SES.
**REQ-NOTIF-04:** All notifications are templated (see Metadata Management Section 7.1 REQ-ADMIN-META-09).
**REQ-NOTIF-05:** Customer can opt out of each channel independently.
**REQ-NOTIF-06:** Notification delivery status tracked (sent/delivered/failed) in `Notification` table.

### 14.2 Agent Notifications

**Events → Agent Notifications:**

| Event | In-App | Email |
|---|---|---|
| New ticket assigned | ✓ | ✓ |
| Customer replied to my ticket | ✓ | ✓ |
| SLA breach imminent (2h before) | ✓ | ✓ |
| SLA breached | ✓ | ✓ |
| Ticket escalated to me | ✓ | ✓ |
| Ticket @mentioned in note | ✓ | ✓ |

**REQ-NOTIF-07:** In-app notifications via WebSocket push (real-time).
**REQ-NOTIF-08:** Agent-level notification preferences (per event, per channel).

### 14.3 CSAT (Customer Satisfaction) Survey

**REQ-CSAT-01:** After ticket reaches `closed` status, send CSAT survey via SMS/WhatsApp: "How satisfied were you with the resolution? Reply 1-5."
**REQ-CSAT-02:** Response captured as a CSAT score on the ticket.
**REQ-CSAT-03:** If score ≤ 2, auto-reopen the ticket with a note "Customer gave low CSAT score. Please follow up."
**REQ-CSAT-04:** CSAT data shown in Reports section.

---

## 15. Non-Functional Requirements

### 15.1 Performance

**REQ-PERF-01:** API P95 response time < 300ms for all read endpoints under 100 concurrent users.
**REQ-PERF-02:** Ticket list page load < 2 seconds (LCP) on a 4G mobile network.
**REQ-PERF-03:** Full-text search results returned in < 500ms.
**REQ-PERF-04:** AI sentiment analysis completed within 3 seconds asynchronously.
**REQ-PERF-05:** AI resolution suggestions delivered within 5 seconds (streaming allowed).
**REQ-PERF-06:** System must handle 10,000 concurrent customers (Phase 1 target).

### 15.2 Availability

**REQ-AVAIL-01:** 99.9% uptime SLA for production (max 8.7 hours downtime/year).
**REQ-AVAIL-02:** Planned maintenance windows: Sundays 02:00–04:00 IST.
**REQ-AVAIL-03:** AI features (sentiment, resolution) are non-blocking: if AI service is down, ticket operations continue normally, AI fields show "Analysis pending."
**REQ-AVAIL-04:** Read replicas for database to ensure report queries don't impact operational latency.

### 15.3 Scalability

**REQ-SCALE-01:** Stateless API services; horizontal scaling behind a load balancer.
**REQ-SCALE-02:** Database connection pooling via PgBouncer.
**REQ-SCALE-03:** Message queue (RabbitMQ or AWS SQS) for async jobs: AI analysis, notifications, report exports, webhooks.
**REQ-SCALE-04:** CDN (CloudFront / Cloudflare) for all static assets and attachment downloads.
**REQ-SCALE-05:** Elasticsearch for all search and analytics queries (not primary DB).

### 15.4 Localization & Accessibility

**REQ-L10N-01:** UI available in English (default). Framework ready for Hindi localization (i18next or similar).
**REQ-L10N-02:** All dates/times shown in tenant timezone (default: Asia/Kolkata).
**REQ-L10N-03:** Currency in INR with Indian number formatting (e.g., ₹1,23,456.00).
**REQ-L10N-04:** Phone numbers in Indian format (+91).
**REQ-ACCESS-01:** WCAG 2.1 AA compliance for Customer Portal.
**REQ-ACCESS-02:** Keyboard navigation support for Agent Dashboard.

### 15.5 Mobile Responsiveness

**REQ-MOB-01:** Customer Portal: fully responsive, optimized for mobile (Android primary, iOS secondary).
**REQ-MOB-02:** PWA support: installable, offline capability for ticket list view (cached).
**REQ-MOB-03:** Agent Dashboard: responsive but primarily desktop-optimized.

---

## 16. Security & Compliance

**REQ-SEC-01:** All data in transit encrypted via TLS 1.3.
**REQ-SEC-02:** Database encryption at rest (AES-256).
**REQ-SEC-03:** PII fields (phone, email, name) encrypted at the application layer (AES-256-GCM) with key rotation support.
**REQ-SEC-04:** Phone numbers in Agent Dashboard masked by default (last 4 digits visible): +91 98765 ****. Agents can request "reveal" with audit log entry.
**REQ-SEC-05:** JWT access tokens: 1 hour expiry. Refresh tokens: 30 days, stored in httpOnly cookie.
**REQ-SEC-06:** Rate limiting: OTP endpoint: 3 attempts per phone per hour. API: 1000 req/min per tenant, 100 req/min per IP.
**REQ-SEC-07:** SQL injection prevention: parameterized queries via ORM (Prisma/TypeORM).
**REQ-SEC-08:** XSS prevention: input sanitization + CSP headers.
**REQ-SEC-09:** CORS: tenant-specific allowed origins.
**REQ-SEC-10:** S3 attachment URLs: pre-signed URLs (15-minute expiry).
**REQ-SEC-11:** Audit log: all agent actions (status changes, resolutions, data reveals) immutably logged.
**REQ-SEC-12:** GDPR / PDPB (India Personal Data Protection) readiness:
  - Customer data deletion on request (right to erasure): anonymize PII, retain ticket data without PII.
  - Data export on request.
  - Data retention policy: closed tickets archived after 2 years, deleted after 7 years.
**REQ-SEC-13:** LLM API calls: customer PII stripped or pseudonymized before sending to external AI providers.
**REQ-SEC-14:** Vulnerability scanning: OWASP ZAP in CI/CD pipeline, dependency audit (npm audit / pip-audit).
**REQ-SEC-15:** Secret management: AWS Secrets Manager or HashiCorp Vault for all API keys and credentials.

---

## 17. Technology Stack Recommendations

### 17.1 Backend

| Component | Recommendation | Rationale |
|---|---|---|
| Runtime | Node.js (TypeScript) or Python (FastAPI) | Node for unified TS stack; Python if AI/ML heavy |
| API Framework | Fastify (Node) or FastAPI (Python) | Performance, OpenAPI built-in |
| ORM | Prisma (Node) or SQLAlchemy (Python) | Type safety, migrations |
| Primary DB | PostgreSQL 16 + pgvector extension | Relational + vector search |
| Cache | Redis 7 | Session, rate limiting, caching |
| Search | Elasticsearch 8 or OpenSearch | Full-text + aggregations for reports |
| Queue | BullMQ (Node/Redis) or Celery (Python/Redis) | Async jobs |
| File Storage | AWS S3 or Cloudflare R2 | Attachments, exports |
| Auth | Passport.js + JWT or custom FastAPI Auth | — |
| WebSocket | Socket.io (Node) or FastAPI WebSocket | Real-time agent notifications |

### 17.2 Frontend

| Component | Recommendation |
|---|---|
| Framework | React 18 + TypeScript |
| Build | Vite |
| State Management | Zustand + React Query (TanStack) |
| UI Library | shadcn/ui + Tailwind CSS |
| Rich Text Editor | TipTap |
| Charts/Analytics | Recharts or Tremor |
| Form Handling | React Hook Form + Zod |
| Routing | React Router v6 |
| Internationalization | react-i18next |
| Mobile PWA | Vite PWA plugin |

### 17.3 AI/ML

| Component | Recommendation |
|---|---|
| LLM (Sentiment + Resolution) | Anthropic Claude Haiku (sentiment) + Sonnet (resolution) |
| Embeddings | OpenAI text-embedding-3-small or Voyage AI |
| Vector Search | pgvector (Postgres) |
| AI Observability | Langfuse |
| Async AI Jobs | BullMQ queue workers |

### 17.4 Infrastructure

| Component | Recommendation |
|---|---|
| Cloud | AWS (primary) or GCP |
| Container | Docker + Docker Compose (dev), Kubernetes/ECS (prod) |
| CI/CD | GitHub Actions |
| IaC | Terraform |
| Monitoring | Datadog or Grafana + Prometheus |
| Error Tracking | Sentry |
| Logs | CloudWatch or ELK Stack |
| CDN | Cloudflare |
| DNS | Route 53 |

### 17.5 MCP Server

| Component | Recommendation |
|---|---|
| MCP SDK | `@anthropic-ai/model-context-protocol` (TypeScript SDK) |
| Transport | HTTP/SSE (server-side events) |
| Auth | Bearer token (tenant + user scoped) |
| Observability | Langfuse traces per tool call |

---

## 18. Phased Delivery Roadmap

### Phase 0 — Foundation (Weeks 1–3)
- [ ] Project scaffolding: monorepo setup (Turborepo or Nx), CI/CD pipeline.
- [ ] Database schema: all core tables, migrations.
- [ ] Auth service: OTP login (customer), email login (agent), JWT middleware.
- [ ] Core CRUD APIs: Tenant, Customer, Ticket, TicketActivity.
- [ ] Basic Customer Portal: Create ticket, list tickets, ticket detail.
- [ ] Basic Agent Dashboard: Ticket list, ticket detail with reply.

### Phase 1 — Core Ticket Workflow (Weeks 4–7)
- [ ] Full ticket lifecycle: all status transitions, status change validation.
- [ ] SLA engine: SLA policy creation, SLA due date computation, breach detection.
- [ ] Assignment engine: manual assign to agent/team, unassigned queue.
- [ ] Notification service: SMS (MSG91) + in-app WebSocket.
- [ ] Order integration: OMS API fetch, order context panel.
- [ ] Customer portal: all views polished, FAQ page, attachment upload.
- [ ] Agent dashboard: filter + search, canned responses, internal notes.
- [ ] Metadata admin: categories, sub-categories, SLA policies, agents, teams.
- [ ] Basic reports: volume + disposition.

### Phase 2 — AI Features (Weeks 8–11)
- [ ] AI service: sentiment analysis (Claude Haiku), async via queue.
- [ ] Embedding pipeline: FAQ embeddings, pgvector index.
- [ ] AI resolution suggestions: retrieval + ranking (Claude Sonnet).
- [ ] Duplicate ticket detection.
- [ ] AI Assistance panel in Agent dashboard.
- [ ] FAQ CMS sync (Strapi integration).
- [ ] Sentiment-based auto-escalation.
- [ ] Full reports: all 6 report types + export.

### Phase 3 — MCP + Chatbot Integration (Weeks 12–14)
- [ ] MCP server: all 12 customer-facing tools.
- [ ] MCP server: agent-assist tools.
- [ ] MCP resource endpoints.
- [ ] MCP authentication and observability (Langfuse).
- [ ] Chatbot integration: FAQ-first → ticket creation flow.
- [ ] WhatsApp notification channel.
- [ ] CSAT survey flow.

### Phase 4 — Multi-Tenancy & B2B2C Onboarding (Weeks 15–18)
- [ ] Full tenant isolation: row-level security, subdomain routing.
- [ ] Tenant onboarding wizard.
- [ ] Sandbox provisioning with synthetic data seeder.
- [ ] Multi-tenant feature flags.
- [ ] Taxonomy templates (Fashion, Electronics, Grocery).
- [ ] Custom domain support (CNAME).
- [ ] Tenant-level branding.
- [ ] B2B2C Admin portal: tenant management, usage stats.

### Phase 5 — Hardening & Scale (Weeks 19–21)
- [ ] Elasticsearch integration for full-text search + analytics.
- [ ] Redis caching layer.
- [ ] Database read replicas.
- [ ] Rate limiting hardening.
- [ ] PII encryption and masking.
- [ ] PDPB compliance: data deletion, export.
- [ ] Load testing: 10k concurrent users.
- [ ] Security audit: OWASP ZAP scan.
- [ ] Report scheduling (email delivery).
- [ ] Webhook system.

---

## 19. Observability & Monitoring

**REQ-OBS-01:** Structured JSON logging for all API requests (request ID, tenant ID, user ID, latency, status code).
**REQ-OBS-02:** Distributed tracing (OpenTelemetry) across all services.
**REQ-OBS-03:** Business metrics (emitted to Prometheus/Datadog):
  - `tickets_created_total` (by tenant, category, channel)
  - `tickets_resolved_total` (by tenant, category)
  - `sla_breached_total` (by tenant, priority)
  - `ai_sentiment_latency_seconds` (histogram)
  - `ai_resolution_suggestions_latency_seconds` (histogram)
  - `mcp_tool_calls_total` (by tool name, tenant)
  - `notification_sent_total` (by channel, status)
**REQ-OBS-04:** Langfuse traces for every LLM call:
  - Input: anonymized ticket text
  - Output: sentiment label + score, or ranked suggestions
  - Latency, token usage, model version
  - User feedback (thumbs up/down)
**REQ-OBS-05:** Alerts:
  - API error rate > 1% → PagerDuty.
  - AI service failure (100% error rate over 5 min) → Slack alert.
  - DB connection pool exhaustion → PagerDuty.
  - SLA breach rate > 20% for any tenant → Slack alert to ops.
**REQ-OBS-06:** Dashboards in Grafana or Datadog:
  - System health dashboard.
  - Per-tenant business metrics dashboard.
  - AI usage and cost dashboard.

---

## 20. Open Questions & Assumptions

### Assumptions Made

1. Phase 1 targets a single food delivery tenant (internal to Rupantar). Multi-tenancy infra is built from Day 1 but activated in Phase 4.
2. OMS integration is read-only pull (SupportHub does not write back to OMS). Write-back (e.g., trigger a refund) is a future scope.
3. Customer authentication is mobile OTP-based (no username/password). Social logins are optional.
4. All monetary refund/compensation tracking is metadata only — actual payment processing is handled by the tenant's own payment system.
5. Phase 1 AI uses Anthropic Claude APIs (Haiku for sentiment, Sonnet for resolution suggestions). On-device LLM is out of scope for this system but can be explored for chatbot layer separately.
6. MCP server is exposed to an AI chatbot agent that is built separately (e.g., the food delivery app's chatbot). SupportHub provides the MCP server; the chatbot orchestration is external.
7. WhatsApp Business API requires Meta approval for template messages — this needs to be initiated early in Phase 1 for Phase 3 readiness.
8. Elasticsearch can be deferred to Phase 5 using Postgres full-text search (`tsvector`) in earlier phases.

### Open Questions

1. **OMS Integration:** Which OMS system does the food delivery platform use? REST or GraphQL? Do we need to handle partial/failed order fetches gracefully on the ticket creation form?
2. **Chatbot Integration:** Which AI chatbot framework is being used for the food delivery app's bot? What is the expected MCP authentication flow (API key per tenant, or OAuth2)?
3. **Agent SSO:** Is there an existing identity provider (Google Workspace, Okta) for agent authentication?
4. **CSAT Channel:** Should CSAT surveys be sent via WhatsApp or SMS? WhatsApp templates need pre-approval — should CSAT be deferred to Phase 3?
5. **Data Residency:** Is there a requirement for data to be stored in Indian data centres (AWS ap-south-1 Mumbai)?
6. **Escalation to Human from Bot:** When chatbot fails to resolve and creates a ticket, should the bot also book a callback with an agent? If yes, callback scheduling is a separate module.
7. **Product Catalog for Fashion/Electronics:** How will product catalog data flow into ticket context (e.g., for an electronics warranty ticket, agent needs to see product specs)?
8. **Pricing Model for B2B2C:** Ticket volume-based? Agent seat-based? Hybrid? This affects the feature flag and plan config.
9. **LLM Provider Fallback:** If Anthropic API is unavailable, should we fall back to OpenAI GPT-4o or use a self-hosted model for critical sentiment analysis?
10. **Regulatory:** Any DND (Do Not Disturb) registry compliance needed for SMS notifications?

---

*Document Version: 1.0 | Prepared for: Rupantar Technologies | Date: March 2026*
*This document is intended as the primary input for Claude Code to scaffold and build SupportHub end-to-end.*
