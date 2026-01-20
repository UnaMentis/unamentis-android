#!/bin/bash
# Pre-commit validation hook for Claude Code
#
# PURPOSE:
#   Blocks git commit if health-check fails.
#   Ensures code quality before commits per CLAUDE.md requirements.
#
# EXIT CODES:
#   0 = Allow the operation
#   2 = Block the operation

set -e

# Read the JSON input from Claude
INPUT=$(cat)

# Parse the command being executed
COMMAND=$(echo "$INPUT" | python3 -c "
import json, sys
data = json.load(sys.stdin)
print(data.get('tool_input', {}).get('command', ''))
" 2>/dev/null || echo "")

# Only validate git commit commands
if echo "$COMMAND" | grep -q "git commit"; then
    cd "$CLAUDE_PROJECT_DIR" || exit 0

    echo "Running pre-commit validation (health-check.sh)..." >&2

    # Run health check (lint + quick tests) as specified in CLAUDE.md
    if [ -f "./scripts/health-check.sh" ]; then
        if ! ./scripts/health-check.sh >/dev/null 2>&1; then
            echo "BLOCKED: Health check failed. Run ./scripts/health-check.sh to see issues." >&2
            exit 2
        fi
    fi

    echo "Pre-commit validation passed!" >&2
fi

exit 0
