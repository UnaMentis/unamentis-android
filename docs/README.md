# UnaMentis Android Documentation

Android-specific documentation for the UnaMentis voice AI learning platform.

## Quick Start & Setup

| Document | Description |
|----------|-------------|
| [QUICK_START.md](QUICK_START.md) | Getting started guide |
| [DEV_ENVIRONMENT.md](DEV_ENVIRONMENT.md) | Developer environment setup |
| [MCP_SETUP.md](MCP_SETUP.md) | MCP server configuration for Claude Code |
| [PHYSICAL_DEVICE_SETUP.md](PHYSICAL_DEVICE_SETUP.md) | Physical device testing setup |

## Architecture & Design

| Document | Description |
|----------|-------------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Android app architecture |
| [ANDROID_STYLE_GUIDE.md](ANDROID_STYLE_GUIDE.md) | Kotlin/Compose coding standards |
| [CLIENT_FEATURE_LIST.md](CLIENT_FEATURE_LIST.md) | Feature implementation status |
| [../ANDROID_PORT_SPECIFICATION.md](../ANDROID_PORT_SPECIFICATION.md) | Complete Android port specification |

## Testing

| Document | Description |
|----------|-------------|
| [TESTING.md](TESTING.md) | Testing strategy and structure |
| [TESTING_GUIDE.md](TESTING_GUIDE.md) | Detailed testing guide |

## Security

| Document | Description |
|----------|-------------|
| [CERTIFICATE_PINNING_PLAN.md](CERTIFICATE_PINNING_PLAN.md) | Certificate pinning implementation plan |
| [CERTIFICATE_PINNING_MAINTENANCE.md](CERTIFICATE_PINNING_MAINTENANCE.md) | Certificate pinning maintenance guide |

## AI & Machine Learning

| Document | Description |
|----------|-------------|
| [ai-ml/GLM_ASR_IMPLEMENTATION_PLAN.md](ai-ml/GLM_ASR_IMPLEMENTATION_PLAN.md) | GLM ASR implementation plan |
| [ai-ml/GLM_ASR_ON_DEVICE_GUIDE.md](ai-ml/GLM_ASR_ON_DEVICE_GUIDE.md) | On-device ASR guide |

## Knowledge Bowl

| Document | Description |
|----------|-------------|
| [KNOWLEDGE_BOWL.md](KNOWLEDGE_BOWL.md) | Knowledge Bowl module implementation |

## Project Tracking

| Document | Description |
|----------|-------------|
| [TASK_STATUS.md](TASK_STATUS.md) | Current task status |

---

## Shared Documentation

Cross-cutting documentation lives in the [main UnaMentis repository](https://github.com/UnaMentis/unamentis).
When working locally with Claude Code, these are accessible at `/Users/ramerman/dev/unamentis/docs/`.

| Document | Location | Description |
|----------|----------|-------------|
| Client Feature Spec | [docs/client-spec/](https://github.com/UnaMentis/unamentis/tree/main/docs/client-spec) | Canonical UI/UX specification for all clients |
| Hands-Free Design | [docs/design/HANDS_FREE_FIRST_DESIGN.md](https://github.com/UnaMentis/unamentis/blob/main/docs/design/HANDS_FREE_FIRST_DESIGN.md) | Voice-first interaction design |
| Audio Orchestrator | [docs/design/AUDIO_PLAYBACK_ORCHESTRATOR.md](https://github.com/UnaMentis/unamentis/blob/main/docs/design/AUDIO_PLAYBACK_ORCHESTRATOR.md) | Cross-platform audio pipeline |
| Module Specs | [docs/modules/](https://github.com/UnaMentis/unamentis/tree/main/docs/modules) | Knowledge Bowl, SAT, specialized modules |
| Testing Philosophy | [docs/testing/TESTING.md](https://github.com/UnaMentis/unamentis/blob/main/docs/testing/TESTING.md) | Real-over-mock testing philosophy |
| Mock Inventory | [docs/testing/MOCK_VIOLATIONS_INVENTORY.md](https://github.com/UnaMentis/unamentis/blob/main/docs/testing/MOCK_VIOLATIONS_INVENTORY.md) | Mock violation patterns and remediation |
| API Specification | [docs/api-spec/](https://github.com/UnaMentis/unamentis/tree/main/docs/api-spec) | Server REST API documentation |
| Architecture | [docs/architecture/](https://github.com/UnaMentis/unamentis/tree/main/docs/architecture) | System architecture and design decisions |
| Project Overview | [docs/architecture/PROJECT_OVERVIEW.md](https://github.com/UnaMentis/unamentis/blob/main/docs/architecture/PROJECT_OVERVIEW.md) | Authoritative project overview |
| Feature Flags | [docs/FEATURE_FLAGS.md](https://github.com/UnaMentis/unamentis/blob/main/docs/FEATURE_FLAGS.md) | Feature flag definitions |
| Chaos Engineering | [docs/testing/CHAOS_ENGINEERING_RUNBOOK.md](https://github.com/UnaMentis/unamentis/blob/main/docs/testing/CHAOS_ENGINEERING_RUNBOOK.md) | Voice pipeline resilience testing |
