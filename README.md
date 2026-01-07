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
Gradle scaffolding is in place for a cloud-safe build, plugin metadata (`plugin.yml`) plus the base `RaidExtractionPlugin` lifecycle have been stubbed, and Phase 2 configuration files now load via `ConfigManager`. See `TODO.md` for the implementation roadmap that keeps `./gradlew clean build` passing during the cloud phase.

## Build & Requirements
- Java 21 toolchain (configured in Gradle).
- Gradle wrapper binary is **not** committed to keep binaries out of the repo. Run `./scripts/fetch-gradle-wrapper.sh` once to
  download it from the configured `distributionUrl` before running any Gradle tasks.
- After bootstrapping the wrapper, run `./gradlew clean build` to compile and execute unit tests.

## Recommended Versions
- **Paper:** latest stable 1.21.x (or 1.20.1 for ecosystem stability).
- **Java:** 21 (match your chosen Paper version).
- **Build Tool:** Gradle (Kotlin DSL or Groovy). Shade dependencies later only if required.

## File/Package Layout (planned)
```
raidextraction/
  build.gradle.kts
  settings.gradle.kts
  src/main/java/com/yourname/raidextraction/
    RaidExtractionPlugin.java
    config/
      ConfigManager.java
      model/
        RaidDefinition.java
        EvacZoneDefinition.java
        LootTableDefinition.java
        LootEntry.java
    raid/
      RaidManager.java
      RaidInstance.java
      RaidState.java
    queue/QueueManager.java
    loot/
      LootService.java
      LootTableRegistry.java
    extraction/
      ExtractionService.java
      EvacTracker.java
    stash/
      StashService.java
      StashInventory.java
      db/
        Database.java
        StashRepository.java
    util/
      ItemStackSerializer.java
      Region.java
      Cuboid.java
      TaskUtil.java
      Msg.java
    listeners/
      PlayerJoinQuitListener.java
      PlayerDeathListener.java
      InventoryListener.java
      MovementListener.java
      CommandListener.java
    commands/
      RaidCommand.java
      StashCommand.java
      AdminCommand.java
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
- **Local integration:** later wire in Bukkit hooks (teleports, inventory snapshot/restore, region checks, GUI stash, chest filling) and validate in a Paper server.

## Local Testing (when integrating)
1. Download the target Paper server JAR (matching version above).
2. Build the plugin: `./gradlew clean build`.
3. Copy the shaded/compiled plugin JAR into `plugins/` in the Paper server directory.
4. Start Paper locally; configure `config.yml`, `raids.yml`, and `loot_tables.yml` under `plugins/RaidExtraction/`.
5. Verify the flow: `/raid join`, start raid, loot, extract, check stash persistence via `/stash`.
6. Test failure cases: death, quit mid-raid, timeout, extraction spam, and server restarts during extraction.

## Common Pitfalls
- Spaghetti state changes: always use the instance to transition states.
- Async misuse: never teleport or modify inventories off-thread.
- Dupes: commit stash only once per extraction; clear raid inventories on death/timeout.
- Region resets: keep environments controlled in V1; disable block breaking if needed.

## License
MIT License (see `LICENSE`).
