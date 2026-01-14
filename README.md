# Raid Extraction (Paper) Plugin
# Extraction-MC

> Extraction-style raid & loot plugin for Paper Minecraft  
> **Core gameplay is complete — we are actively looking for contributors and playtesters.**

## 🚨 Help Wanted Right Now

We are looking for:
- 🎮 **Playtesters** – run raids, find bugs, give balance feedback
- 🗺️ **Map Builders** – create raid arenas & extraction layouts
- ⚖️ **Loot & Balance Designers** – tune loot tables & progression
- 🎨 **UI / UX Polish** – menus, messages, boss bars, feedback
- 🧠 **Java Contributors** – polish, refactors, edge cases

👉 **No Java experience required for playtesting or map building.**
A data-driven Paper plugin that delivers an extraction-style raid loop for Minecraft: queue players, deploy into a raid region, loot, survive PvE (and later PvP), and extract to keep rewards. Player stash data persists via SQLite so only extracted loot is saved.

## Branches & Trunk Migration
- `Cloud`: authoritative trunk for all active development (base new work here).
- `main-cleanup`: staging branch (this branch) to replace the legacy `main` once docs/hygiene land.
- `main` (legacy): old scaffold kept for history; the live plugin code is on `Cloud`/`main-cleanup`, not `main`.

## Branch Audit & Recommendations
- `Cloud`: active trunk; keep as the base for feature work.
- `main-cleanup`: staging branch for the trunk swap; keep in lockstep with `Cloud` until the default branch flips.
- `main`: legacy Docker/Makefile scaffold; keep for history only.
- `Local`: initial scaffold commit that is already merged into `Cloud`; safe to archive after the trunk swap.
- `codex/standardize-hybrid-environment-setup`: Docker/Makefile environment setup (legacy scaffold); archive.
- `codex/merge-pull-request` (raid/queue managers): merged into `Cloud`; archive.
- `codex/merge-pull-request-0fhcfl` (command/listener stubs): merged into `Cloud`; archive.
- `codex/merge-pull-request-a71a2h` (extraction countdown tracking): merged into `Cloud`; archive.
- `codex/merge-pull-request-zw3gpm` (stash persistence + loot services): merged into `Cloud`; archive.
- `codex/merge-pull-request-qu4ovb` (local integration checklist docs): merged into `Cloud`; archive.
- `codex/add-progression-model-and-loot-economy-rules` (economy/progression guardrails): merged into `Cloud`; archive.
- `codex/create-documentation-for-raid-extraction-plugin` (Gradle download warnings + docs): merged into `Cloud`; archive.
- `codex/update-todo.md-with-phase-8-goals` and `...-rrk7ic` (roadmap updates): merged into `Cloud`; archive.
- `codex/work-on-next-cloud-to-do-feature` (raid state machine skeleton): merged into `Cloud`; archive.
- `codex/work-on-next-cloud-to-do-feature-dwyfj8` (queue/raid manager work plus a `Cloud` merge): review diffs vs `Cloud`; if no unique logic, archive.

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

## Commands
- `/raid join|leave|status <raidId>` ?? status shows raid state, player counts, and remaining time for active raids.
- `/raidadmin start <raidId>`, `stop|cancel <activeRaidId>`, `force-extract <playerName|playerUuid>` ?? admin controls for testing and moderation.
- `/raidadmin edit <raidId>`, `exit`, `save`, `validate`, `undo` ?? editor flow for spawns, evac zones, and loot markers.
- `/raidadmin lootchance <percent>` ?? set default chance for new loot markers (and update the targeted container).
- `/raidadmin lootpreview` ?? toggle preview items in all loot containers while editing.
- `/stash` ?? opens a stash GUI for browsing and withdrawing extracted loot (persistence runs off-thread, results synced on the main thread).

## Repository Status
Core raid loop services (queue → raid → loot → extract → stash), stash persistence, extraction countdowns, and command/listener stubs are in place for the cloud-safe build phase. See `TODO.md` for the implementation roadmap and the local development checklist that resumes once a Paper server is available.

## Build & Requirements
- Java 21 toolchain (configured in Gradle and auto-downloaded via the Foojay resolver if Java 21 is missing; first build needs
  network access).
- Gradle wrapper binary is **not** committed to keep binaries out of the repo. Run `./scripts/fetch-gradle-wrapper.sh` (Git Bash
  on Windows) or drop `gradle/wrapper/gradle-wrapper.jar` locally before running any Gradle tasks. Do not commit the wrapper JAR.
- After bootstrapping the wrapper, run `./gradlew clean build` to compile and execute unit tests (`.\gradlew.bat` on Windows).
- Release checklist and version bump steps live in `docs/releasing.md`.

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
    integration/
      InventorySnapshotService.java
      ItemDataMapper.java
      RaidLifecycleCoordinator.java
      RegionProvider.java
      TeleportService.java
      paper/
        PaperInventorySnapshotService.java
        PaperRegionProvider.java
        PaperTeleportService.java
        StashView.java
      stub/
        NoopInventorySnapshotService.java
        NoopRegionProvider.java
        NoopTeleportService.java
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
- **config.yml:** global settings (debug flags, `lobbyWorld`/`lobbySpawn`, `raidWorld`, evac countdown duration).
- **raids.yml:** raid definitions (world, lobby spawn, raid region, player spawns, evac zones, duration).
- **loot_tables.yml:** named loot tables with weighted entries (material, amount range, enchantments later).
- **director.yml:** placeholder for threat curves/events (future).
- **raid_state.yml:** transient snapshot of active raids and evac timers for restart rehydration (auto-managed).

## Development Principles
- Keep listeners thin; delegate logic to services and managers.
- Use pure logic for state transitions; centralize transitions in `RaidInstance#setState`.
- Avoid Bukkit calls off the main thread; async DB work must return to main thread before applying results.
- Track spawned entities and scheduled tasks to ensure cleanup at raid end.
- Use clear TODO tags: `TODO(V1)` for must-have, `TODO(INTEGRATION)` for server wiring, `TODO(V2)` for later features.

## Cloud vs. Local Workflow
- **Cloud phase:** implement pure logic, configuration parsing, and persistence that compiles without running Paper. Keep builds passing with `./gradlew clean build`.
- **Local integration:** wire Bukkit hooks (teleports, inventory snapshot/restore, region checks, GUI stash, chest filling) and validate in a Paper server.

## Local Development (Windows + VS Code)
1. Install Java 21 and VS Code (with Java extensions).
2. Clone the repo and check out `Cloud` for feature work or `main-cleanup` for trunk-migration tasks.
3. From VS Code's terminal (Git Bash preferred), run `./scripts/fetch-gradle-wrapper.sh` to download the wrapper JAR, or place
   `gradle/wrapper/gradle-wrapper.jar` manually; keep it untracked.
4. Build and test: `.\gradlew.bat clean build` (first run will auto-download JDK 21 via Foojay if missing).
5. Create a gitignored `server/` folder at the repo root with a Paper JAR, then run `.\gradlew.bat copyPluginToServer`
   (uses `server/plugins/` by default; override with `-PserverDir=../my-server`).
6. Start Paper (`java -jar paper.jar --nogui` from `server/`), configure files under `plugins/RaidExtraction/`, and iterate using
   VS Code for edits plus the integrated terminal for builds.

## Local Testing (when integrating)
1. Download the target Paper server JAR (matching version above).
2. Ensure the Gradle wrapper is available (run `./scripts/fetch-gradle-wrapper.sh` or provide the wrapper JAR locally).
3. Build the plugin: `./gradlew clean build` (downloads JDK 21 automatically if needed).
4. Copy the plugin JAR into `plugins/` in the Paper server directory (`./gradlew copyPluginToServer -PserverDir=/path/to/server`).
5. Start Paper locally; configure `config.yml`, `raids.yml`, and `loot_tables.yml` under `plugins/RaidExtraction/`.
6. Verify the flow: `/raid join`, `/raidadmin start`, loot, extract, check stash persistence via `/stash`.
7. Test failure cases: death, quit mid-raid, timeout, extraction spam, and server restarts during extraction.

## Integration Checklist (local)
Adapter interfaces (`InventorySnapshotService`, `RegionProvider`, `TeleportService`) are stubbed; implement them when wiring
Paper APIs.
- **Raid deploy:** implement teleport/spawn logic and region checks before switching to `IN_RAID`.
- **Inventory snapshot/restore:** capture inventories on raid start, restore on failure, and commit on extraction.
- **Loot spawn:** fill chests or drop loot using `LootService` and `LootTableRegistry`.
- **Evac zones:** hook movement/listener logic to start/cancel evac countdowns and to mark extraction complete.
- **Stash UI:** `/stash` opens the stash GUI; validate withdraw flows and persistence on a live server.
- **Admin tooling:** `/raidadmin start|stop|cancel|force-extract` available for raid control; verify messaging/state in Paper.
- **Resilience:** handle server restarts by rehydrating active raids and eviction of stale evac timers.

## Common Pitfalls
- Spaghetti state changes: always use the instance to transition states.
- Async misuse: never teleport or modify inventories off-thread.
- Dupes: commit stash only once per extraction; clear raid inventories on death/timeout.
- Region resets: keep environments controlled in V1; disable block breaking if needed.

## License
MIT License (see `LICENSE`).
