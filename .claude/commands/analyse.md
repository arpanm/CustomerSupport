Run static code analysis on the specified target.

Usage:
  /analyse [service]                    — Checkstyle + SpotBugs + PMD
  /analyse frontend/[app]               — ESLint + TypeScript strict check
  /analyse all                          — All services + all frontend apps

Backend: ./mvnw checkstyle:check spotbugs:check pmd:check -pl {service}
Frontend: npx eslint src --max-warnings 0 && npx tsc --noEmit

Output: ANAL-NNN tasks in TODO.md for violations above threshold.
