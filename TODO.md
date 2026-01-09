# TODO Roadmap

This TODO tracks the cloud-safe implementation path for the Raid Extraction Paper plugin. Tasks are organized so `./gradlew clean build` should remain green without requiring a running Paper server.

## Phase 8 — Build/Release Automation
- [ ] `TODO(V1)` Add GitHub Actions CI to run `./gradlew clean build` on push/PR with Gradle caching.
- [ ] `TODO(V1)` Add a Gradle release task that outputs to `build/libs/` with a predictable name (append version if helpful).
- [ ] `TODO(V1)` Add a release checklist in `docs/releasing.md` (bump version → build → smoke test → tag).

## Integration Adapters & Local Harness
- [ ] `TODO(INTEGRATION)` Add adapter interfaces (`InventorySnapshotService`, `RegionProvider`, `TeleportService`) so Paper wiring is isolated.
- [ ] `TODO(INTEGRATION)` Add a `/server/` gitignored local harness and a Gradle copy task for a one-command loop.

## Environment / Test Warnings
- `./scripts/fetch-gradle-wrapper.sh` currently fails in this environment because the Gradle distribution download is blocked by
  a proxy (HTTP 403). To proceed, manually place the wrapper JAR or pre-download the distribution ZIP before rerunning.
- `./gradlew clean build --console=plain` will also fail until the wrapper JAR/distribution is available. Re-run once the
  distribution can be fetched to confirm the build.

## Phase 0 — Repository Setup
- [x] Add Gradle build with Paper API (compileOnly) and Java toolchain (21 recommended).
- [x] Create `.gitignore`, LICENSE (MIT), and baseline README.

## Phase 1 — Base Plugin & Metadata
- [x] Add `plugin.yml` with commands (`raid`, `stash`, `raidadmin`) and permissions (`raid.user`, `raid.admin`).
- [x] Implement `RaidExtractionPlugin` lifecycle with logging hooks and configuration initialization.

## Phase 2 — Config Schemas & Loader
- [x] Add `config.yml`, `raids.yml`, `loot_tables.yml`, and `director.yml` resource files with comments.
- [x] Implement `ConfigManager` plus models (`RaidDefinition`, `EvacZoneDefinition`, `LootTableDefinition`, `LootEntry`) and soft validation.

## Phase 3 — Core Logic Skeleton
- [x] Define `RaidState` enum and `RaidInstance` state machine (deploy → raid → extract → end) with pure logic only.
- [x] Add `RaidManager` and `QueueManager` for routing and queue handling.

## Phase 4 — Persistence & Loot (Neutral Formats)
- [x] Implement SQLite-backed `StashRepository` and `StashService` using neutral `ItemData` representation.
- [x] Implement `LootTableRegistry` and `LootService` with weighted rolls and injectable RNG seed.

## Phase 5 — Extraction Logic
- [x] Add `ExtractionService` and `EvacTracker` for countdown handling and idempotent completion.

## Phase 6 — Commands & Listeners (Stubs)
- [x] Provide skeleton commands (`RaidCommand`, `StashCommand`, admin) and listener stubs that delegate to managers.

## Phase 7 — Documentation & Integration Checklist
- [x] Expand README with local Paper testing steps, integration hooks (teleport, inventory snapshot, region checks, GUI stash, chest filling), and cloud/local notes.
- [x] Keep TODOs tagged (`TODO(V1)`, `TODO(INTEGRATION)`, `TODO(V2)`) and update this roadmap as features land.

## Local Development Checklist (Paper Integration)
Use this checklist once a local Paper server is available. The cloud phase is complete; remaining work depends on Bukkit/Paper APIs.

### Server Setup
- [ ] Install Paper 1.21.x and Java 21 locally.
- [ ] Ensure the Gradle wrapper JAR is present (run `./scripts/fetch-gradle-wrapper.sh` or supply the wrapper JAR manually).
- [ ] Build and drop the plugin JAR into `plugins/`.
- [ ] Start the server once to generate `plugins/RaidExtraction/` configs.

### Raid Loop Wiring (queue → raid → loot → extract → stash)
- [ ] `TODO(INTEGRATION)` Hook `/raid join` to trigger queue updates and auto-start raids when min players queued.
- [ ] `TODO(INTEGRATION)` Add raid deployment: teleport players to raid spawns and transition `RaidInstance` to `IN_RAID`.
- [ ] `TODO(INTEGRATION)` Record inventory snapshots and clear inventories on raid start (stash commit only on extraction).
- [ ] `TODO(INTEGRATION)` Spawn loot via `LootService` (chests or drops) per raid region.
- [ ] `TODO(INTEGRATION)` Evac zone detection (movement/region checks) to start/cancel evac countdowns.
- [ ] `TODO(INTEGRATION)` On successful extraction, persist loot to stash and return players to lobby.
- [ ] `TODO(INTEGRATION)` On failure (death/timeout/quit), restore inventory and clear raid state.

### Commands & Admin Tools
- [ ] `TODO(V1)` Extend `/raid status` with ETA, raid state, and player count.
- [ ] `TODO(V1)` Implement `/raidadmin force-extract` (lookup player, mark extraction, stash commit).
- [ ] `TODO(V1)` Add `/raidadmin cancel` to stop raids and restore players.

### Stash UI & Persistence
- [ ] `TODO(V1)` Build stash GUI for browsing/withdrawing items.
- [ ] `TODO(V1)` Convert between `ItemData` and `ItemStack` for stash IO.
- [ ] `TODO(INTEGRATION)` Run stash persistence writes off-thread, then sync results back to the main thread.

### Resilience & Telemetry
- [ ] `TODO(V1)` Rehydrate active raids and evac countdowns on server restart.
- [ ] `TODO(V1)` Add structured logging for raid lifecycle transitions and errors.
- [ ] `TODO(V2)` Add metrics or bossbar timers for countdown visibility.

### QA Checklist
- [ ] Verify min/max player enforcement with queues across multiple raid definitions.
- [ ] Test extraction spam protection and idempotent completion.
- [ ] Validate stash persistence across restarts and crashes.
- [ ] Verify loot roll distribution and weighted tables over large samples.
