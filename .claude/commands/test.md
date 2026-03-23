Run the test suite for a service and report results in TODO.md.

Usage:
  /test [service] unit          — ./mvnw test -pl {service}
  /test [service] integration   — ./mvnw verify -pl {service}  
  /test [service] all           — unit + integration + coverage check
  /test frontend [app]          — npm run test -w apps/{app}
  /test e2e                     — npx playwright test (requires all services running)

On failure: create TEST-NNN task with full failure details.
On coverage drop below threshold: create TEST-NNN flagging gap.
