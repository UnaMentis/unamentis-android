# Task Status

This document is the single source of truth for what's being worked on. **All AI agents must update this file before starting and after completing work.**

## Protocol

1. **Before ANY work**: Read this file first
2. **Claim your task**: Add an entry to "Currently Active" with your agent/tool name and timestamp
3. **Check for conflicts**: Do not work on tasks another agent has already claimed
4. **Update on completion**: Move your task to "Completed" section with notes
5. **Note blockers**: If you hit a blocker, add it to "Blocked/Pending" section

---

## Currently Active

| Task | Agent | Started | Notes |
|------|-------|---------|-------|
| | | | |

---

## Blocked/Pending

| Task | Blocked By | Notes |
|------|------------|-------|
| | | |

---

## Completed

| Task | Agent | Completed | Notes |
|------|-------|-----------|-------|
| Documentation update | Claude Code | 2026-01-19 | Updated all docs with Phase 7, test counts, placeholder URLs |
| Quality infrastructure setup | Claude Code | 2026-01-15 | Added CI/CD, testing guides, style guides, AGENTS.md, test helpers |

---

## How to Use

### Starting Work

```markdown
| Implement feature X | Claude Code | 2026-01-15 14:30 | Working on core logic |
```

### Completing Work

Move from "Currently Active" to "Completed":

```markdown
| Implement feature X | Claude Code | 2026-01-15 15:00 | Feature complete, tests passing |
```

### Noting Blockers

```markdown
| Feature Y | Waiting for API key | Need ANTHROPIC_API_KEY to test LLM integration |
```

---

## Why This Matters

Without coordination:
- Two agents might implement the same feature differently
- One agent might break code another is actively working on
- Time gets wasted on duplicate effort

With coordination:
- Clear visibility into who's doing what
- Async collaboration between different AI tools
- Audit trail of progress
