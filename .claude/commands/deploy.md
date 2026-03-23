Run the deployment pipeline for the specified environment.

Usage:
  /deploy dev [service]         — Deploy to dev environment
  /deploy staging [service|all] — Deploy to staging (auto after E2E pass)  
  /deploy prod                  — Deploy to prod (requires manual approval, all gates green)

Pre-flight checks MUST pass before any deployment:
- All unit + integration tests green
- 0 CRITICAL/HIGH security issues
- 0 open P0 tasks in TODO.md for this change
- DB migrations run successfully
- Docker image built and security-scanned (Trivy)

Deployment order: dev → staging → prod. NEVER skip.
