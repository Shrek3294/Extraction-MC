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

## Economy & Progression Guardrails (Design Rules)
Use this section as the balancing north star while implementing loot, vendors, and progression.

### 1) Progression model that stays fair (mostly horizontal)
**Player progression tracks**
- **Reputation (per vendor/faction):** Unlocks access (attachments, meds, ammo types, keys), not raw damage multipliers. Rep rises via extracts/quests and drops slightly on deaths (small, not brutal).
- **Stash/Base upgrades:** Unlocks convenience/economy levers (stash size, craft slots, insurance slots, scav stipend frequency). Primary “numbers go up” track without ruining PvP.
- **Weapon mastery:** Small handling bonuses (reload/swap speed, recoil smoothing). Avoid +damage/+armor as mastery rewards.

**Power ceiling rule**
- Cap practical PvP power around **Tier 2–3**. Tier 4 should be economy/quest artifacts, keys, cosmetics, or flex items—not “instantly win fights.”

### 2) Loot economy: make losses recoverable
**Budget Kit Guarantee (must-have):** Every player can always assemble a viable kit in 1–2 runs. Implement **any 2**:
- Scav run (free kit, lower loot multiplier, no rep gain)
- Daily/hourly stipend crate (basic meds + ammo + weak weapon OR barter junk)
- Vendor “budget kit” (fixed cheap loadout, always available)
- Craft-from-junk (common junk → ammo/meds)

### 3) Loot balancing that creates hot zones without destroying fairness
**Server-wide 60 / 30 / 10 rule (per hour):**
- 60% Tier 0–1
- 30% Tier 2
- 10% Tier 3–4

**Map zoning (per `RaidDefinition`):**
- STARTER: consistent Tier 1, low Tier 2
- MID: Tier 1–2 steady
- HOT: Tier 2–3 + rare Tier 4 artifacts
- LOCKED (key door): tiny area, very high value density

### 4) Director system: dynamic scarcity without SBMM
Track rolling counters (SQLite or memory + periodic flush):
- `spawned_count[item_id]`
- `extracted_count[item_id]`
- `lost_count[item_id]` (died/timeout)

**Scarcity multiplier:** If too much extracted → reduce spawn weight 5–15%; if too little exists → increase spawn weight 5–15%. Clamp to **0.6–1.4**.

### 5) Concrete tier + scoring system for fairness
**Loadout Score (raid start):**
- Weapon tier 0/1/2/3 → +0/+8/+16/+24
- Armor tier 0/1/2/3 → +0/+10/+20/+30
- Ammo tier 0/1/2/3 → +0/+6/+12/+18
- Meds tier 0/1/2/3 → +0/+5/+10/+15
- Utility tier 0/1/2/3 → +0/+4/+8/+12

**Total:** ~0–87. Use for optional queue bands later and starter protection.

**Starter protection (avoid SBMM feel):**
- Restrict HOT/LOCKED zones for first N raids (soft gates like alarms + NPC spawns), or
- Spawn closer to safer extracts, or
- Reduce chance of instancing into high-score lobbies (later).

### 6) Loot tables that won’t implode the economy
**Categories to define in `loot_tables.yml`:**
- BARTR_COMMON, BARTR_RARE
- MED_BASIC, MED_GOOD
- AMMO_LOW, AMMO_GOOD, AMMO_AP (AP rare/expensive)
- WEAPON_T1, WEAPON_T2, WEAPON_T3 (T3 low rate)
- ATTACH_T1, ATTACH_T2, ATTACH_T3
- KEYS (very low rate)
- ARTIFACTS (rare; economy/quests)

**Container identity (learnability):**
- Medical: meds only (higher tier chance)
- Ammo box: ammo + small attach chance
- Weapon case: weapon + attachment
- Tech crate: barter + keys + artifacts
- Duffle: mixed low-mid

### 7) Risk tuning: insurance + extracts + keys
**Insurance (balanced):** Insure 1–2 items early. Return only if not extracted by others. Return after delay. Returned items can be damaged, requiring repair junk.

**Keys drive route variety:** Keys rare but not impossible. Key rooms: high value density, louder entry (alarm), more exposure time, limited nearby extracts.

### 8) Starter defaults (numbers to copy into configs)
- `raid_duration_minutes`: 12–18 (map-size dependent)
- `loot_rolls_per_container`:
  - duffle: 1–2
  - ammo: 2–3 (small items)
  - med: 2–3
  - tech: 2–4
  - weapon_case: 1 (weapon) + 1 (attach chance)
- `tier_distribution_by_zone`:
  - STARTER: T0-1 80%, T2 19%, T3 1%, T4 0%
  - MID: T0-1 60%, T2 35%, T3 4%, T4 1%
  - HOT: T0-1 40%, T2 45%, T3 12%, T4 3%
  - LOCKED: T0-1 15%, T2 45%, T3 30%, T4 10%
- `scarcity_multiplier_clamp`: [0.6, 1.4]
- `scarcity_adjust_rate`: 0.05–0.15 per evaluation window
- `evaluation_window_minutes`: 30–60

### 9) TODO phase mapping (cloud-safe vs integration)
**Now (cloud-safe / pure logic):**
- Add tier metadata to loot entries (or infer tier from table name).
- Add director multiplier layer: `finalWeight = baseWeight * multiplier(itemId or category)`.
- Add loot profiles per raid/zone (even if `RegionProvider` is stubbed; simulate region tags).

**Later (Paper wiring):**
- `RegionProvider` resolves zone profile.
- Chest filling uses the profile’s container types + loot tables.

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
