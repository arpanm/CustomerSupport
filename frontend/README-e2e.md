# E2E Test Suite — Setup Notes

## Dependency: @playwright/test

`@playwright/test` is **not** listed in `/frontend/package.json` or any workspace `package.json` at the time this suite was created.

Before running the E2E tests you must install it:

```bash
# From /frontend
npm install -D @playwright/test
npx playwright install chromium
```

Or add it as a dev-dependency to the root `package.json`:

```json
"devDependencies": {
  "@playwright/test": "^1.44.0"
}
```

## Running the tests

```bash
# Requires all services running (customer-portal :3000, agent-dashboard :3001)
cd frontend
npx playwright test

# Interactive UI mode
npx playwright test --ui

# Single spec
npx playwright test e2e/specs/otp-login.spec.ts
```

## Directory structure

```
frontend/
  playwright.config.ts        # Playwright configuration
  e2e/
    pages/                    # Page Object Models
      LoginPage.ts            # Customer OTP login (customer-portal)
      TicketListPage.ts       # Customer ticket list
      TicketDetailPage.ts     # Customer ticket detail + comment/AI suggestions
      FAQSearchPage.ts        # FAQ semantic search
      AgentLoginPage.ts       # Agent email/password login (agent-dashboard)
      AgentTicketPage.ts      # Agent ticket resolution + AI assistance
    specs/                    # Test specifications
      otp-login.spec.ts
      ticket-creation.spec.ts
      faq-self-resolve.spec.ts
      agent-resolution.spec.ts
    screenshots/              # Failure screenshots (gitignored)
```

## Test environment assumptions

- **Customer portal** runs at `http://localhost:3000` (override with `BASE_URL` env var)
- **Agent dashboard** runs at `http://localhost:3001`
- Fixed OTP `123456` is returned for any phone number in the test environment
- At least one open ticket exists in the agent queue for `agent-resolution.spec.ts`
