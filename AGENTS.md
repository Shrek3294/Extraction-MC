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
- Always clarify whether manual Paper server testing is required; when it is, list the exact commands/interactions to run and the expected results (messages, teleports, inventory changes, timers, stash effects).

## File Conventions
- Use Java 21+ compatible code that matches the targeted Paper version.
- Keep config schemas readable with comments for future iteration.
- When unsure, keep the implementation minimal and integration-friendly.

## Non-Goals / Do Not Do
- Do not rename packages, move modules, or perform large refactors unless explicitly asked.
- Do not change command/permission semantics without updating `plugin.yml` and README.
- Do not delete branches/tags or rewrite git history.
- Do not introduce new dependencies unless required and justified in the PR summary.
- Do not commit binaries (Paper jars, Gradle wrapper jar, server folders, worlds, logs, databases).

## Branch / PR Workflow
- Target branch for changes: `Cloud` (authoritative) with `main-cleanup` as the staging branch for the trunk swap; do not base new work on the legacy `main`.
- Keep PRs small (one task per PR) and prefer incremental commits.
- Use conventional commits: `feat:`, `fix:`, `docs:`, `chore:`.
- If build files/config schemas change, include "Migration Notes" in the PR summary.

## Threading Rules (Paper)
- Main thread only: inventory/ItemStack, teleport, world/entity access, chests/containers, UI (bossbar/scoreboard), permissions.
- Async allowed: SQLite I/O, YAML parsing, pure loot roll math, queue/state-machine calculations.
- Pattern: compute async → apply results sync via scheduler.

## Integration Boundaries
- Core logic should not depend on Bukkit/Paper types (`Player`, `ItemStack`, `Location`).
- Use neutral models (`ItemData`, UUIDs, simple structs) in core.
- Define interfaces in core for Paper-only concerns and implement them in adapter/listener layers.

## Terminology Clarification — `raidId` vs Raid Instances (IMPORTANT)

To avoid confusion during V2 development:

### `raidId` = Persistent Raid / Map Definition
- `raidId` identifies a **persistent map + ruleset**, not a single match.
- It represents the **blueprint** that all players use.
- Admin editing (`/raidadmin edit <raidId>`) always modifies this persistent definition.

A `raidId` owns:
- Template world (e.g. `factory_template`)
- Spawn points
- Evac zones
- Loot container locations
- Raid rules (min/max players, timers, etc.)

Edits to a `raidId` are:
- **Persistent**
- **Shared across all players**
- **Applied to all future raid instances**


### Raid Instance = Temporary Runtime Match
- A raid instance is a **single play session** created from a `raidId`.
- It may run in:
  - a fixed raid world (V2.0), or
  - a cloned instance world (V2.2+).
- Raid instances are **disposable** and must never be edited directly.

A raid instance:
- References exactly one `raidId`
- Reads locations/config from that `raidId`
- Is destroyed or cleaned up after completion

### Editor Rules (Non-Negotiable)
- The map editor **must never modify live raid instances**.
- `/raidadmin edit <raidId>` always:
  - Teleports the admin to the **template world**
  - Loads persistent data for that `raidId`
  - Saves changes to config (`locations.yml`, etc.)
- All players should experience the **same map layout** for a given `raidId`, regardless of how many instances are created.



## Crate UI & Custom Roll Mechanics
The plugin uses a custom "rolling" UI for loot containers instead of vanilla inventory access.

### How it Works
1. **Trigger**: A player right-clicks a `CHEST`, `BARREL`, or `TRAPPED_CHEST` in an active raid.
2. **Listener**: `LootInteractionListener` cancels the vanilla event and checks `RaidInstance.pendingLoot`.
3. **Animation**: `CrateAnimationService` opens a 27-slot inventory and starts a 2-second "rolling" phase.
   - **Rolling**: Random colored glass panes flicker with tick sounds (`BLOCK_NOTE_BLOCK_HAT`).
   - **Reveal**: Actual loot from the `pendingLoot` map is populated into the inventory.
4. **Completion**:
   - Loot is officially "taken" from the `RaidInstance` during the reveal.
   - If the player closes the inventory before taking everything, items are automatically transferred to their main inventory via `InventoryCloseEvent`.

---

## Loot Pool & Generation Management
Loot is non-physical until the roll completes. It is generated at raid start and stored in memory.

### 1. Defining Loot Tables (`loot_tables.yml`)
Add or modify tables here.
```yaml
loot_tables:
  table_id:
    entries:
      - id: internal_id
        material: BUKKIT_MATERIAL
        weight: 10 # Relative weight (e.g., 10 vs 1)
        min_amount: 1
        max_amount: 3
```

### 2. Configuring Raid Loot (`raids.yml`)
Link a raid to a table and set the density.
```yaml
raids:
  raid_id:
    loot_table: table_id
    target_loot_count: 50 # Total item stacks distributed across all chests
```

### 3. Distribution Logic
- `RaidLifecycleCoordinator.spawnRaidLoot` rolls the table `target_loot_count` times.
- Items are distributed **cyclically** across all markers placed via `/raidadmin edit`.
- If no containers are found, loot spawns as physical drops at the world spawn.

---

## TODO Discipline
- Each TODO should include owner component + scope (CLOUD or INTEGRATION) and a definition of done.
- Prefer: `TODO(INTEGRATION): <component> — <done criteria>`
