# TODO Roadmap

This TODO tracks the cloud-safe implementation path for the Raid Extraction Paper plugin. Tasks are organized so `./gradlew clean build` should remain green without requiring a running Paper server.

## Environment / Test Warnings
- `./scripts/fetch-gradle-wrapper.sh` currently fails in this environment because the Gradle distribution download is blocked by
  a proxy (HTTP 403). To proceed, manually place the wrapper JAR or pre-download the distribution ZIP before rerunning.
- `./gradlew clean build --console=plain` will also fail until the wrapper JAR/distribution is available. Re-run once the
  distribution can be fetched to confirm the build.

## Phase 0 — Repository Setup
- [x] Add Gradle build with Paper API (compileOnly) and Java toolchain (21 recommended).
- [x] Create `.gitignore`, LICENSE (MIT), and baseline README.

## Phase 1 — Base Plugin & Metadata
- [ ] Add `plugin.yml` with commands (`raid`, `stash`, `raidadmin`) and permissions (`raid.user`, `raid.admin`).
- [ ] Implement `RaidExtractionPlugin` lifecycle with logging hooks and configuration initialization.

## Phase 2 — Config Schemas & Loader
- [ ] Add `config.yml`, `raids.yml`, `loot_tables.yml`, and `director.yml` resource files with comments.
- [ ] Implement `ConfigManager` plus models (`RaidDefinition`, `EvacZoneDefinition`, `LootTableDefinition`, `LootEntry`) and soft validation.

## Phase 3 — Core Logic Skeleton
- [ ] Define `RaidState` enum and `RaidInstance` state machine (deploy → raid → extract → end) with pure logic only.
- [ ] Add `RaidManager` and `QueueManager` for routing and queue handling.

## Phase 4 — Persistence & Loot (Neutral Formats)
- [ ] Implement SQLite-backed `StashRepository` and `StashService` using neutral `ItemData` representation.
- [ ] Implement `LootTableRegistry` and `LootService` with weighted rolls and injectable RNG seed.

## Phase 5 — Extraction Logic
- [ ] Add `ExtractionService` and `EvacTracker` for countdown handling and idempotent completion.

## Phase 6 — Commands & Listeners (Stubs)
- [ ] Provide skeleton commands (`RaidCommand`, `StashCommand`, admin) and listener stubs that delegate to managers.

## Phase 7 — Documentation & Integration Checklist
- [ ] Expand README with local Paper testing steps, integration hooks (teleport, inventory snapshot, region checks, GUI stash, chest filling), and cloud/local notes.
- [ ] Keep TODOs tagged (`TODO(V1)`, `TODO(INTEGRATION)`, `TODO(V2)`) and update this roadmap as features land.
