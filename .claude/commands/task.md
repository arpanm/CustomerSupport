Create or update a task in TODO.md following the task schema from CLAUDE-SDLC.md.

Subcommands:
- /task create [TYPE] [title] — Creates new task with next available ID for that type
- /task update [TASK-ID] [field] [value] — Updates a field (status, priority, owner, etc.)
- /task list [filter] — Lists tasks: open, in-progress, blocked, all
- /task show [TASK-ID] — Shows full task details

Task schema MUST include: ID, Type, Priority, Status, Owner, Service, Sprint, Blocked By, Blocks, REQUIREMENT Ref, ARCHITECTURE Ref, Created, Updated, Branch, Description, Acceptance Criteria.
