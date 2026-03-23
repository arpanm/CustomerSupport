Run the full Micro-SDLC cycle for the current changes or specified task.

Steps:
1. Read REQUIREMENT.md + ARCHITECTURE.md sections relevant to the change
2. Verify or create tasks in TODO.md with status IN_PROGRESS
3. Implement the change following all standards in CLAUDE-SDLC.md
4. Spawn code-reviewer agent: review all changed files
5. Spawn security-analyst agent: scan changed code (parallel with review)
6. Fix all CRITICAL and HIGH issues from review + security
7. Run tests: ./mvnw test (unit) + ./mvnw verify (integration) for changed services
8. Fix any test failures — add TEST-NNN tasks for each failure
9. Update TODO.md: mark task DONE, list files changed, record test results
10. If change affects requirements: update REQUIREMENT.md
11. If change affects architecture: update ARCHITECTURE.md

Usage: /sdlc [task-id]
