# AI Agent Guidance for Extraction-MC

**Project:** Paper Minecraft plugin implementing data-driven raid-to-extraction gameplay loop.  
**Core Stack:** Java 21, Paper 1.21.x, Gradle, SQLite, YAML configuration.

## Architecture Overview

The plugin orchestrates the raid lifecycle across five coordinated subsystems:

1. **Raid Management** (`raid/`) — Instantiates raid worlds, manages state machine (queue → active → extracting → complete), handles player entry/exit.
2. **Loot System** (`loot/`) — Rolls YAML-driven loot tables at raid start, distributes stacks across chest markers, drives the "crate animation" UI on right-click.
3. **Extraction Flow** (`extraction/`) — Enforces extraction zone geometry, cooldowns, and idempotent completion; grants stash credits on success.
4. **Item Pipeline** (`item/`, `magic/`) — Custom weapons/mods/spells defined in `items.yml`, applied via `/weapon` commands, persisted to stash.
5. **Stash** (`stash/`) — SQLite-backed player inventory; survives raid wipes and server restarts.

**Key Insight:** Loot is **non-physical** (stored in memory) until a chest is opened—the `CrateAnimationService` rolls items visually then populates them into inventory.

## Build, Test, Deploy

```bash
# Fetch wrapper (required first-time setup)
./scripts/fetch-gradle-wrapper.sh

# Build, test, and copy to local Paper server
./gradlew clean build
./gradlew copyPluginToServer -PserverDir=./server

# Start Paper server (requires paper.jar in server/)
cd server && java -jar paper.jar --nogui
```

**Known Issue:** Gradle wrapper downloads may fail (HTTP 403 proxy block). Pre-download the Gradle distribution or wrapper JAR if needed.

## Configuration & Data Schemas

All configuration lives under `plugins/RaidExtraction/` at runtime:
- `raids.yml` — Raid definitions (names, loot tables, evac duration, spawn points).
- `loot_tables.yml` — Item pool definitions (material, weight, min/max amounts).
- `items.yml` — Custom weapons, mods, and spell definitions.
- `config.yml` — Lobby/raid worlds, debug flags.

**Edit Pattern:** Mods to these YAML files are loaded on server restart or via `/raidadmin` in-game commands.

## Threading Model (Non-Negotiable)

**Main Thread Only:** Player inventory, ItemStack, teleport, world/entity access, chests/containers, UI (actionbar, bossbar), permissions checks.

**Async Safe:** SQLite I/O, YAML parsing, pure loot roll math, queue calculations.

**Execution Pattern:**
```
compute async (e.g., roll loot) → queue sync result application (via Bukkit scheduler)
```

See `RaidLifecycleCoordinator.spawnRaidLoot()` for an example.

## Critical Distinctions: `raidId` vs Raid Instance

**`raidId` = Persistent Raid Map Definition**
- Identifies a blueprint (e.g., `factory_raid`) that all players share.
- Owns spawn points, evac zones, loot container locations, and rules.
- Edits via `/raidadmin edit <raidId>` modify the persistent definition in config.
- **All future instances** of that `raidId` use the updated layout.

**Raid Instance = Single Runtime Match**
- Ephemeral session created from a `raidId`.
- Runs in a fixed raid world or cloned instance world.
- Instance-local state: pending loot, player positions, extraction status.
- **Never edit instances directly**; map edits must go through the persistent `raidId`.

**Editor Guarantee:** `/raidadmin edit` always teleports to the template world and saves to config, ensuring consistency.

## Crate UI & Loot Reveal

**Trigger:** Right-click chest/barrel in active raid → `LootInteractionListener` cancels vanilla access.

**Flow:**
1. `CrateAnimationService` opens 27-slot inventory.
2. 2-second animation: colored glass panes flicker (tick sounds).
3. Reveal: Actual loot from `RaidInstance.pendingLoot` populates the inventory.
4. Close: Items auto-transfer to main inventory if player closes early.

**Implication:** Loot is never physical items in the world until placed in inventory.

## Code Patterns & Integration Boundaries

**Listeners Stay Thin:** `listener/` classes delegate to managers/services; avoid game logic in event handlers.

**Core ≠ Bukkit Types:** Core logic uses neutral models (`ItemData`, UUIDs, simple POJOs) and does not depend on Bukkit/Paper API.

**Adapter Layer:** Define interfaces in core (e.g., `StashRepository`) and implement them in adapter layers (e.g., `SQLiteStashRepository`).

**Example:** `ExtractionService` (core) knows nothing about `Player` objects—it works with UUIDs and returns extraction results to `ExtractionListener`.

## Development Principles

- **Data-Driven:** Use YAML for raid/loot definitions; avoid hardcoded values.
- **Small Modules:** Keep listeners thin, delegate to coordinating services.
- **Testable Logic:** Pure functions for loot rolls, state validation, timer math.
- **TODO Discipline:** Tag deferrable work: `TODO(V1)`, `TODO(INTEGRATION)`, `TODO(V2)` with component and done criteria.
- **Minimal Dependencies:** Only add deps if necessary; keep `./gradlew clean build` green.

## Terminology & Command Surface

| Command | Scope | Permission |
|---------|-------|-----------|
| `/raid join \|leave\|status\|hud` | Join queue, check raid, toggle HUD. | `raid.user` |
| `/stash` | Open stash UI. | `raid.user` |
| `/weapon guide\|bench\|apply\|give\|debugspells` | Weapon utilities + admin give. | `raid.user` / `raid.admin` |
| `/raidadmin edit\|start\|stop\|exit\|save\|validate` | Edit raid map, control instances, debug. | `raid.admin` |

See [docs/commands.md](docs/commands.md) for full reference.

## Testing & Failure Modes

**Unit Tests:** Run via `./gradlew test` (JUnit 5, no Paper server required).

**Manual Integration Tests:** Requires local Paper server.
- Capture: Commands, player messages, teleports, inventory changes, extraction timers, stash effects.
- Document failures in `TODO.md` with exact command, reason, and reproduction steps.
- Reference any known env-specific issues (e.g., proxy-blocked Gradle downloads) in PR summaries.

## Do Not

- Rename packages or perform large refactors without explicit request.
- Change command/permission semantics without updating `plugin.yml` and README.
- Commit binaries: Paper JAR, Gradle wrapper JAR, server folders, worlds, logs, SQLite databases.
- Delete branches/tags or rewrite history.
- Add dependencies without justification in PR summary.

## Branch Workflow

**Source of Truth:** `main-cleanup`  
**Commit Style:** Conventional commits (`feat:`, `fix:`, `docs:`, `chore:`).  
**PR Size:** One task per PR, incremental commits.  
**Merge Notes:** Include migration details if build files or config schemas change.
