# Development Guide

This document covers local development workflows for the plugin.

## Requirements

- Java 21+
- Paper 1.21.x for integration testing
- Gradle wrapper (download locally; do not commit the wrapper JAR)

## Build & Test

```bash
./scripts/fetch-gradle-wrapper.sh
./gradlew clean build
```

> Note: Gradle wrapper downloads can fail in this environment due to proxy restrictions (HTTP 403). See `AGENTS.md` for details.

## Local Paper Server Workflow

1. Create a local `server/` directory (gitignored).
2. Place `paper.jar` in `server/`.
3. Build and copy the plugin:
   ```bash
   ./gradlew clean build
   ./gradlew copyPluginToServer -PserverDir=./server
   ```
4. Start Paper:
   ```bash
   cd server
   java -jar paper.jar --nogui
   ```
5. Configure `plugins/RaidExtraction/` and restart.

## Debugging Tips

- Use Paper's `/timings` or Spark (if installed) to locate performance issues.
- Use `debug: true` in `config.yml` for more verbose logs.
- The `/weapon debugspells` command enables spell debug actionbar + console traces.

## Recommended IDE Settings

- **IntelliJ IDEA** or **VS Code** with Java extensions.
- Enable "format on save" and organize imports.
- Use Java 21 language level.

## Manual Testing Checklist (Paper)

When you run a manual test, capture:
- Commands used and expected results.
- Player messages, teleports, inventory changes, extraction timers, stash UI behaviors.
- Logs around raid start/end and extraction.

## TBD / TODO Notes

- **TBD:** integration test harness automation. TODO: document a repeatable dev harness once CI supports Paper integration tests.
