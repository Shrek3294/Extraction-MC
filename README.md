# Raid Extraction (Paper) Plugin

A data-driven Paper plugin that delivers an extraction-style raid loop for Minecraft: queue players, deploy into a raid region, loot, survive PvE (and later PvP), and extract to keep rewards. Player stash data persists via SQLite so only extracted loot is saved.

## Project Goals
- **Core loop:** queue → deploy → raid → extract → stash commit.
- **Data-driven design:** YAML configs define raids, loot tables, and evac zones for quick iteration.
- **Cloud-safe build:** keep logic modular and testable without running a server; target Java 21 and modern Paper.

## High-Level Architecture
- **Queue & Matchmaking:** manage queues and create raid instances.
- **Raid Instances:** state machine controlling lifecycle (LOBBY → DEPLOYING → IN_RAID → EXTRACTING → ENDED), timers, players, and cleanup hooks.
- **Loot System:** YAML-defined loot tables, weighted rolls, and neutral item models for cloud builds.
- **Extraction System:** evac zone countdowns, idempotent completion, and stash commits on success.
- **Persistence:** SQLite-backed stash storage in the plugin data folder with simple per-player blobs.
- **Reset/Cleanup:** restore players to lobby, cancel tasks, reset containers, and clear entities/effects.

## Repository Status
Core raid loop services (queue → raid → loot → extract → stash), stash persistence, extraction countdowns, and command/listener stubs are in place for the cloud-safe build phase. See `TODO.md` for the implementation roadmap and the local development checklist that resumes once a Paper server is available.

## Build & Requirements
- Java 21 toolchain (configured in Gradle).
- Gradle wrapper binary is **not** committed to keep binaries out of the repo. Run `./scripts/fetch-gradle-wrapper.sh` once to
  download it from the configured `distributionUrl` before running any Gradle tasks.
- After bootstrapping the wrapper, run `./gradlew clean build` to compile and execute unit tests.

## Recommended Versions
- **Paper:** latest stable 1.21.x (or 1.20.1 for ecosystem stability).
- **Java:** 21 (match your chosen Paper version).
- **Build Tool:** Gradle (Kotlin DSL or Groovy). Shade dependencies later only if required.

## File/Package Layout
```
raidextraction/
  build.gradle.kts
  settings.gradle.kts
  src/main/java/com/raidextraction/
    RaidExtractionPlugin.java
    command/
      RaidCommand.java
      StashCommand.java
      RaidAdminCommand.java
    config/
      ConfigManager.java
      model/
        RaidDefinition.java
        EvacZoneDefinition.java
        LootTableDefinition.java
        LootEntry.java
    extraction/
      EvacTracker.java
      ExtractionService.java
    listener/
      RaidListener.java
      ExtractionListener.java
    loot/
      LootService.java
      LootTableRegistry.java
    raid/
      RaidManager.java
      RaidInstance.java
      RaidState.java
      QueueManager.java
    stash/
      ItemData.java
      SQLiteStashRepository.java
      StashRepository.java
      StashService.java
  src/main/resources/
    plugin.yml
    config.yml
    raids.yml
    loot_tables.yml
    director.yml
```

## Config Files
- **config.yml:** global settings (debug flags, lobby spawn, defaults).
- **raids.yml:** raid definitions (world, lobby spawn, raid region, player spawns, evac zones, duration).
- **loot_tables.yml:** named loot tables with weighted entries (material, amount range, enchantments later).
- **director.yml:** placeholder for threat curves/events (future).

## Development Principles
- Keep listeners thin; delegate logic to services and managers.
- Use pure logic for state transitions; centralize transitions in `RaidInstance#setState`.
- Avoid Bukkit calls off the main thread; async DB work must return to main thread before applying results.
- Track spawned entities and scheduled tasks to ensure cleanup at raid end.
- Use clear TODO tags: `TODO(V1)` for must-have, `TODO(INTEGRATION)` for server wiring, `TODO(V2)` for later features.

## Cloud vs. Local Workflow
- **Cloud phase:** implement pure logic, configuration parsing, and persistence that compiles without running Paper. Keep builds passing with `./gradlew clean build`.
- **Local integration:** wire Bukkit hooks (teleports, inventory snapshot/restore, region checks, GUI stash, chest filling) and validate in a Paper server.

## Local Testing (when integrating)
1. Download the target Paper server JAR (matching version above).
2. Ensure the Gradle wrapper is available (run `./scripts/fetch-gradle-wrapper.sh` or provide the wrapper JAR locally).
3. Build the plugin: `./gradlew clean build`.
4. Copy the plugin JAR into `plugins/` in the Paper server directory.
5. Start Paper locally; configure `config.yml`, `raids.yml`, and `loot_tables.yml` under `plugins/RaidExtraction/`.
6. Verify the flow: `/raid join`, `/raidadmin start`, loot, extract, check stash persistence via `/stash`.
7. Test failure cases: death, quit mid-raid, timeout, extraction spam, and server restarts during extraction.

## Integration Checklist (local)
- **Raid deploy:** implement teleport/spawn logic and region checks before switching to `IN_RAID`.
- **Inventory snapshot/restore:** capture inventories on raid start, restore on failure, and commit on extraction.
- **Loot spawn:** fill chests or drop loot using `LootService` and `LootTableRegistry`.
- **Evac zones:** hook movement/listener logic to start/cancel evac countdowns and to mark extraction complete.
- **Stash UI:** provide a GUI or command flow to inspect stash contents and withdraw items.
- **Admin tooling:** extend `/raidadmin` for force-extract, raid cancel, and diagnostic status.
- **Resilience:** handle server restarts by rehydrating active raids and eviction of stale evac timers.

## Common Pitfalls
- Spaghetti state changes: always use the instance to transition states.
- Async misuse: never teleport or modify inventories off-thread.
- Dupes: commit stash only once per extraction; clear raid inventories on death/timeout.
- Region resets: keep environments controlled in V1; disable block breaking if needed.

## License
MIT License (see `LICENSE`).
