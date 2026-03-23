Spawn the code-reviewer sub-agent to review changed code.

Usage: /review [service-name | file-path | (empty = all git-changed files)]

The reviewer checks: correctness vs REQUIREMENT.md, architecture vs ARCHITECTURE.md,
security patterns, test coverage, logging standards, error handling.

Output: REVIEW-NNN tasks in TODO.md grouped by CRITICAL, HIGH, MEDIUM, LOW.

After review: fix all CRITICAL and HIGH issues before marking any task DONE.
