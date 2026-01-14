#!/bin/bash
# Post-edit lint hook for Claude Code
#
# PURPOSE:
#   Lints Kotlin files after Claude edits them.
#   Reports issues immediately so Claude can fix them.
#
# EXIT CODES:
#   0 = Success (no issues or file type not supported)
#   2 = Lint issues found (reported to Claude)

set -e

# Read the JSON input from Claude
INPUT=$(cat)

# Get the file path that was edited
FILE_PATH=$(echo "$INPUT" | python3 -c "
import json, sys
data = json.load(sys.stdin)
print(data.get('tool_input', {}).get('file_path', ''))
" 2>/dev/null || echo "")

# Skip if no file path
if [ -z "$FILE_PATH" ]; then
    exit 0
fi

cd "$CLAUDE_PROJECT_DIR" || exit 0

case "$FILE_PATH" in
    # Kotlin files - use ktlint via gradle
    *.kt|*.kts)
        # Only lint if the file exists and is in our project
        if [ -f "$FILE_PATH" ]; then
            # Run lint check on the specific file using gradle
            # This is fast since gradle daemon is usually running
            if [ -f "./scripts/lint.sh" ]; then
                if ! ./scripts/lint.sh 2>/dev/null | grep -q "BUILD SUCCESSFUL"; then
                    echo "Lint issues detected. Run ./scripts/lint.sh to see details." >&2
                    # Don't block - just report. The pre-commit hook will block if needed.
                    exit 0
                fi
            fi
        fi
        ;;

    # XML files (layouts, resources)
    *.xml)
        # Android XML doesn't have a quick standalone linter
        # Lint will catch issues on build
        ;;

    # Python files (for scripts)
    *.py)
        if command -v ruff &> /dev/null; then
            if ! ruff check "$FILE_PATH" --quiet 2>/dev/null; then
                echo "Ruff issues in $FILE_PATH. Fix before committing." >&2
                exit 2
            fi
        fi
        ;;

    # Shell scripts
    *.sh)
        if command -v shellcheck &> /dev/null; then
            if ! shellcheck "$FILE_PATH" 2>/dev/null; then
                echo "ShellCheck issues in $FILE_PATH." >&2
                # Don't block for shell scripts
                exit 0
            fi
        fi
        ;;
esac

exit 0
