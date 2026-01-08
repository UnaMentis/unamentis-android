# MCP Server Configuration

This document details the Model Context Protocol (MCP) server setup for the UnaMentis Android project. MCP servers enable Claude Code to perform autonomous build, test, and UI automation tasks.

## Quick Reference

| Server | Purpose | Status Check |
|--------|---------|--------------|
| mobile-mcp | Emulator interaction (tap, swipe, screenshot, install) | `mobile_list_available_devices` |
| gradle-mcp-server | Build, test, task execution | `execute_gradle_task` |

## Prerequisites

| Requirement | Minimum Version | Check Command |
|-------------|-----------------|---------------|
| Node.js | 20+ | `node --version` |
| Java JDK | 17+ | `java --version` |
| npx | Latest | `npx --version` |

## Configured MCP Servers

### 1. mobile-mcp

**Purpose:** Android emulator/device interaction for UI testing and debugging.

**Source:** [@mobilenext/mobile-mcp](https://www.npmjs.com/package/@mobilenext/mobile-mcp)

**Installation:** Runs via npx (no local installation needed).

**Available Tools:**

| Tool | Description |
|------|-------------|
| `mobile_list_available_devices` | List connected emulators and devices |
| `mobile_list_apps` | List installed apps on device |
| `mobile_install_app` | Install APK on device |
| `mobile_launch_app` | Launch app by package name |
| `mobile_terminate_app` | Stop a running app |
| `mobile_take_screenshot` | Capture device screen |
| `mobile_save_screenshot` | Save screenshot to file |
| `mobile_list_elements_on_screen` | Get UI element hierarchy |
| `mobile_click_on_screen_at_coordinates` | Tap at x,y |
| `mobile_double_tap_on_screen` | Double-tap at x,y |
| `mobile_long_press_on_screen_at_coordinates` | Long press at x,y |
| `mobile_swipe_on_screen` | Swipe in direction |
| `mobile_type_keys` | Type text into focused field |
| `mobile_press_button` | Press hardware buttons (HOME, BACK, etc.) |
| `mobile_open_url` | Open URL in device browser |
| `mobile_get_screen_size` | Get device screen dimensions |
| `mobile_set_orientation` | Change screen orientation |
| `mobile_get_orientation` | Get current orientation |

### 2. gradle-mcp-server

**Purpose:** Execute Gradle tasks, run tests, and query project information.

**Source:** [IlyaGulya/gradle-mcp-server](https://github.com/IlyaGulya/gradle-mcp-server)

**Installation Location:** `~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar`

**Available Tools:**

| Tool | Description |
|------|-------------|
| `get_gradle_project_info` | Get project structure, tasks, dependencies |
| `execute_gradle_task` | Run Gradle tasks (build, clean, assemble) |
| `run_gradle_tests` | Execute tests with hierarchical JSON results |

**Common Gradle Tasks:**

```bash
# Build
execute_gradle_task(task: "assembleDebug")
execute_gradle_task(task: "assembleRelease")

# Test
run_gradle_tests(testTask: "test")
run_gradle_tests(testTask: "testDebugUnitTest")

# Lint
execute_gradle_task(task: "ktlintCheck")
execute_gradle_task(task: "detekt")

# Clean
execute_gradle_task(task: "clean")
```

## Configuration File

Location: `.mcp.json` (project root)

```json
{
  "mcpServers": {
    "mobile-mcp": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@mobilenext/mobile-mcp@latest"]
    },
    "gradle-mcp-server": {
      "type": "stdio",
      "command": "/usr/local/opt/openjdk@17/bin/java",
      "args": [
        "-jar",
        "/Users/<username>/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar"
      ]
    }
  }
}
```

> **Note:** The Java path above is for Homebrew-installed OpenJDK 17 on macOS. Adjust the path based on your Java installation:
> - Homebrew (Intel Mac): `/usr/local/opt/openjdk@17/bin/java`
> - Homebrew (Apple Silicon): `/opt/homebrew/opt/openjdk@17/bin/java`
> - System Java: `/usr/bin/java` (if properly linked)
> - Find your Java: `brew --prefix openjdk@17` or `/usr/libexec/java_home -v 17`

## Installation

### Install gradle-mcp-server

```bash
# Create directory
mkdir -p ~/mcp-servers/gradle-mcp-server

# Download latest release
curl -fSL -o ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar \
  "https://github.com/IlyaGulya/gradle-mcp-server/releases/latest/download/gradle-mcp-server-all.jar"

# Verify download
ls -la ~/mcp-servers/gradle-mcp-server/
```

### Update gradle-mcp-server

```bash
# Re-download to get latest version
curl -fSL -o ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar \
  "https://github.com/IlyaGulya/gradle-mcp-server/releases/latest/download/gradle-mcp-server-all.jar"
```

## Verification

### 1. Check MCP Server Status

In Claude Code, verify servers are connected:

```bash
claude mcp list
```

Expected output:
```
mobile-mcp: Connected
gradle-mcp-server: Connected
```

### 2. Test mobile-mcp

```bash
# List available devices (should return empty array if no emulator running)
mobile_list_available_devices
```

### 3. Test gradle-mcp-server

```bash
# Get project info
get_gradle_project_info

# Run a simple task
execute_gradle_task(task: "tasks", args: ["--all"])
```

### 4. Full Integration Test

```bash
# 1. Start emulator
./scripts/launch-emulator.sh Pixel_8_Pro_API_34

# 2. Wait for emulator to boot
adb wait-for-device

# 3. Build app via MCP
execute_gradle_task(task: "assembleDebug")

# 4. Install via MCP
mobile_install_app(path: "app/build/outputs/apk/debug/app-debug.apk")

# 5. Launch via MCP
mobile_launch_app(packageName: "com.unamentis.debug")

# 6. Take screenshot via MCP
mobile_take_screenshot
```

## Round-Trip Debugging Workflow

The MCP servers enable autonomous debugging without manual intervention:

```
1. Build    → execute_gradle_task("assembleDebug")
2. Install  → mobile_install_app(path)
3. Launch   → mobile_launch_app(packageName)
4. Debug    → Check http://localhost:8765/ for logs
5. Screenshot → mobile_take_screenshot
6. Interact → mobile_click_on_screen_at_coordinates(x, y)
7. Analyze  → Review logs, iterate
```

## Troubleshooting

### MCP servers not showing in `claude mcp list`

1. Verify `.mcp.json` syntax:
   ```bash
   cat .mcp.json | python3 -m json.tool
   ```

2. Check Java is available:
   ```bash
   java --version  # Should be 17+
   ```

3. Check Node.js is available:
   ```bash
   node --version  # Should be 20+
   ```

4. Restart Claude Code session (reload VS Code window or restart CLI)

### gradle-mcp-server fails to start

1. Verify JAR exists:
   ```bash
   ls -la ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar
   ```

2. Test JAR manually:
   ```bash
   java -jar ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar --help
   ```

3. Re-download if corrupted:
   ```bash
   curl -fSL -o ~/mcp-servers/gradle-mcp-server/gradle-mcp-server-all.jar \
     "https://github.com/IlyaGulya/gradle-mcp-server/releases/latest/download/gradle-mcp-server-all.jar"
   ```

### mobile-mcp fails to start

1. Clear npm cache:
   ```bash
   npm cache clean --force
   ```

2. Test npx directly:
   ```bash
   npx -y @mobilenext/mobile-mcp@latest --help
   ```

### No devices found by mobile-mcp

1. Check emulator is running:
   ```bash
   adb devices
   ```

2. Start emulator:
   ```bash
   ./scripts/launch-emulator.sh Pixel_8_Pro_API_34
   ```

3. Wait for full boot:
   ```bash
   adb wait-for-device
   adb shell getprop sys.boot_completed  # Returns 1 when ready
   ```

## Adding New MCP Servers

### Option 1: Edit .mcp.json directly

Add new server entry to the `mcpServers` object:

```json
{
  "mcpServers": {
    "existing-server": { ... },
    "new-server": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@package/name@latest"]
    }
  }
}
```

### Option 2: Use Claude CLI

```bash
# Add server with scope (user = global, project = local)
claude mcp add new-server --scope project

# Add with JSON configuration
claude mcp add-json "server-name" '{"command":"...","args":[...]}'
```

## Optional MCP Servers

These servers are NOT currently configured but may be useful:

### GitHub MCP Server

For GitHub integration (issues, PRs, repository operations):

```json
"github": {
  "command": "docker",
  "args": [
    "run", "-i", "--rm",
    "-e", "GITHUB_PERSONAL_ACCESS_TOKEN",
    "ghcr.io/github/github-mcp-server"
  ],
  "env": {
    "GITHUB_PERSONAL_ACCESS_TOKEN": "<your-token>"
  }
}
```

**Requires:** Docker, GitHub Personal Access Token

### Filesystem MCP Server

For broader filesystem access (beyond project directory):

```json
"filesystem": {
  "command": "npx",
  "args": [
    "-y",
    "@modelcontextprotocol/server-filesystem",
    "/path/to/allowed/directory"
  ]
}
```

## References

- [Model Context Protocol Specification](https://modelcontextprotocol.io/)
- [mobile-mcp npm package](https://www.npmjs.com/package/@mobilenext/mobile-mcp)
- [gradle-mcp-server GitHub](https://github.com/IlyaGulya/gradle-mcp-server)
- [GitHub MCP Server](https://github.com/github/github-mcp-server)
- [MCP Servers Collection](https://github.com/modelcontextprotocol/servers)
