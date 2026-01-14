# Contributing to Raid Extraction (Extraction-MC)

Thanks for helping build an extraction-style raid experience for Paper! Contributions are welcome across code, maps, balance, and documentation.

## Ways to Contribute

- **Code:** features, bug fixes, performance improvements, tests.
- **Maps & content:** raid templates, evac layouts, loot routes, encounter pacing.
- **Configs & balance:** loot tables, spawn chances, item tuning.
- **Playtests:** repro steps, logs, regression notes, balance feedback.
- **Docs:** onboarding, guides, templates, and troubleshooting.

If you want a starter task, check [HELP_WANTED.md](HELP_WANTED.md).

## Development Setup

### Requirements

- **Java:** 21+
- **Gradle:** use the provided wrapper (download the wrapper JAR locally — do not commit it)

### Build

```bash
./scripts/fetch-gradle-wrapper.sh
./gradlew clean build
```

> Note: In this environment, Gradle wrapper invocations can fail due to a blocked distribution download. See `AGENTS.md` for details.

### Local Paper Test Server (manual)

1. Download a Paper 1.21.x jar into a local `server/` folder (gitignored).
2. Build the plugin:
   ```bash
   ./gradlew clean build
   ```
3. Copy the plugin jar:
   ```bash
   ./gradlew copyPluginToServer -PserverDir=./server
   ```
4. Start Paper:
   ```bash
   cd server
   java -jar paper.jar --nogui
   ```
5. Configure `plugins/RaidExtraction/` (`config.yml`, `raids.yml`, `loot_tables.yml`, `items.yml`) and restart.

## Branching & PR Rules

- **Branch base:** use `Cloud` (authoritative) or `main-cleanup` for staging; avoid the legacy `main` branch.
- **One PR per topic:** keep changes focused.
- **Commit style:** conventional commit-ish prefixes are preferred (`feat:`, `fix:`, `docs:`, `chore:`).
- **Docs required:** if behavior changes, update the relevant docs.

## Code Style Guidance

- Keep listeners thin; delegate to managers/services.
- Keep Bukkit/Paper API usage on the main thread.
- Use Java 21-compatible features only.
- Avoid large refactors and new dependencies unless required.

## Where to Ask Questions

- If GitHub Discussions are enabled, use Discussions.
- Otherwise, open an Issue with your question.

## Before You Open a PR

- Run relevant tests (`./gradlew clean build`) when possible.
- Add notes for any manual testing needed (Paper server steps + expected results).
- Update docs and TODOs if you change workflows or introduce new config keys.
