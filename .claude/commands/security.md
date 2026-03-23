Run security analysis on the specified target.

Usage:
  /security [service]           — OWASP Dependency Check + code review for security issues
  /security all                 — All services
  /security deps                — Dependency CVE check only: ./mvnw dependency-check:check

Output: SEC-NNN tasks in TODO.md for all findings with CVSS scores.
CRITICAL/HIGH findings MUST be fixed before any merge or deployment.
