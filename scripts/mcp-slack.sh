#!/usr/bin/env bash
# MCP Slack server wrapper with credential loading
#
# This script loads Slack credentials and starts the MCP server.
# Customize load-creds.sh to configure how credentials are loaded.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/load-creds.sh"

validate_credentials

exec npx -y @modelcontextprotocol/server-slack "$@"
