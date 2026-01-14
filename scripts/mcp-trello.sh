#!/usr/bin/env bash
# MCP Trello server wrapper with credential loading
#
# This script loads Trello credentials and starts the MCP server.
# Customize load-creds.sh to configure how credentials are loaded.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/load-creds.sh"

validate_credentials

# Use bunx if available (faster), fall back to npx
if command -v bunx &> /dev/null; then
    exec bunx @delorenj/mcp-server-trello "$@"
else
    exec npx -y @delorenj/mcp-server-trello "$@"
fi
