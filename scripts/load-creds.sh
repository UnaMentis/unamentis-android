#!/usr/bin/env bash
# Load MCP credentials for Slack and Trello
#
# CUSTOMIZE THIS FILE for your environment!
#
# Option 1: Hardcode credentials (not recommended for shared machines)
# Option 2: Load from a secrets manager
# Option 3: Load from encrypted file
# Option 4: Load from environment file

set -euo pipefail

# =============================================================================
# OPTION 1: Direct credentials (edit these values)
# =============================================================================
# Uncomment and fill in your credentials:
#
# export SLACK_BOT_TOKEN="xoxb-your-slack-bot-token"
# export SLACK_TEAM_ID="T12345678"
# export TRELLO_API_KEY="your-trello-api-key"
# export TRELLO_TOKEN="your-trello-token"

# =============================================================================
# OPTION 2: Load from environment file (recommended for development)
# =============================================================================
# Create ~/.config/comms-mcp/creds.env with:
#   SLACK_BOT_TOKEN=xoxb-...
#   SLACK_TEAM_ID=T...
#   TRELLO_API_KEY=...
#   TRELLO_TOKEN=...
#
CREDS_FILE="${HOME}/.config/comms-mcp/creds.env"
if [[ -f "$CREDS_FILE" ]]; then
    set -a  # automatically export all variables
    source "$CREDS_FILE"
    set +a
fi

# =============================================================================
# OPTION 3: Load from 1Password CLI (example)
# =============================================================================
# if command -v op &> /dev/null; then
#     export SLACK_BOT_TOKEN=$(op read "op://Vault/SlackBot/token")
#     export SLACK_TEAM_ID=$(op read "op://Vault/SlackBot/team_id")
#     export TRELLO_API_KEY=$(op read "op://Vault/Trello/api_key")
#     export TRELLO_TOKEN=$(op read "op://Vault/Trello/token")
# fi

# =============================================================================
# Validation
# =============================================================================
validate_credentials() {
    local missing=()
    [[ -z "${SLACK_BOT_TOKEN:-}" ]] && missing+=("SLACK_BOT_TOKEN")
    [[ -z "${SLACK_TEAM_ID:-}" ]] && missing+=("SLACK_TEAM_ID")
    [[ -z "${TRELLO_API_KEY:-}" ]] && missing+=("TRELLO_API_KEY")
    [[ -z "${TRELLO_TOKEN:-}" ]] && missing+=("TRELLO_TOKEN")

    if [[ ${#missing[@]} -gt 0 ]]; then
        echo "ERROR: Missing credentials: ${missing[*]}" >&2
        echo "Please configure credentials in $0" >&2
        exit 1
    fi
}

# Export for use by wrapper scripts
export -f validate_credentials
