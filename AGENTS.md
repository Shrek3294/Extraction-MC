# Agent Guidance

This repository hosts the Raid Extraction (Paper) plugin. Follow these guardrails when making changes:

## Scope
- These instructions apply to the entire repository unless a nested `AGENTS.md` overrides them.

## Development Principles
- Favor small, cohesive modules that keep listeners thin and delegate to managers/services.
- Keep Bukkit/Paper API usage on the main thread; design pure logic that can be tested without a server.
- Prefer data-driven configuration (YAML) and document any new config keys.
- Tag deferrable work clearly with `TODO(V1)`, `TODO(INTEGRATION)`, or `TODO(V2)`.
- Avoid adding dependencies unless necessary; keep `./gradlew clean build` green.

## Documentation & PR Notes
- Update relevant docs (README, TODO lists) when adding features or changing workflows.
- Summaries should highlight the raid loop (queue → raid → loot → extract → stash) and note cloud/local considerations where relevant.
- Binaries (including the Gradle wrapper JAR) should not be committed; use fetch scripts or documented download steps instead and note
  the approach in commit/PR descriptions.

## Test Reporting & Environment Constraints
- Log any failed or warning test commands in `TODO.md` (or a scoped TODO file) with the exact command, reason, and next action so
  other environments can reproduce.
- Note the same context in PR summaries; keep failures visible until resolved.
- Current known issue: `./scripts/fetch-gradle-wrapper.sh` and any Gradle wrapper invocation fail in this environment because the
  Gradle distribution download is blocked by a proxy (HTTP 403). If you have access to the distribution, place the wrapper JAR
  locally or pre-download the distribution ZIP before running tests.

## File Conventions
- Use Java 21+ compatible code that matches the targeted Paper version.
- Keep config schemas readable with comments for future iteration.

When unsure, keep the implementation minimal and integration-friendly.
