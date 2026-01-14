# TODO

## Release + Build Hygiene (MUST DO BEFORE PROMOTION)
- [x] Keep binaries out of git (`*.jar`, `server/`, `gradle/wrapper/gradle-wrapper.jar`).
  - **Acceptance:** `git ls-files gradle/wrapper/gradle-wrapper.jar` is empty; plugin JARs come from CI/Releases only.
- [x] Provide wrapper JAR fetch helper (`./scripts/fetch-gradle-wrapper.sh`) and document it.
  - **Acceptance:** fresh clone can run `./scripts/fetch-gradle-wrapper.sh` then `./gradlew clean build`.
- [x] Add Windows wrapper-JAR fetch option (`./scripts/fetch-gradle-wrapper.ps1`) or document Git Bash requirement.
  - **Acceptance:** Windows user can fetch `gradle/wrapper/gradle-wrapper.jar` without WSL/Git Bash.
- [x] Ensure Gradle wrapper text files are committed: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`.
  - **Acceptance:** wrapper scripts exist after clone on a clean machine.
- [x] GitHub Actions CI runs `./gradlew clean build` on push/PR.
  - **Acceptance:** CI runs for every push/PR and reports build status.
- [x] Document manual releasing in `docs/releasing.md` (bump, build, smoke test, tag, upload).
- [x] Add GitHub Release workflow (builds from tag and uploads artifacts).
  - [ ] Tag `v0.1.0`.
  - [x] Attach built plugin JAR from CI artifacts (do not commit JARs).
  - [x] Provide a release notes template (`.github/release.yml` or `docs/release-notes-template.md`).
  - **Acceptance:** release is reproducible from CI and download links are from GitHub Releases.
- [ ] Versioning hygiene: single source of truth for version (Gradle or tag) and inject into `plugin.yml` at build time.
  - **Acceptance:** jar name + `plugin.yml` version match the release tag.

## Demo Distribution Plan (LOW FRICTION PLAYTESTING)
**Option A: Separate demo repo (recommended)**
- **Includes:** minimal demo server config, prebuilt plugin from GitHub Releases, README with quick start.
- **Excludes:** secrets, tokens, player data, worlds not owned by project, Mojang-proprietary assets.
- **Tasks:** prepare lobby + 1 small raid map, preconfigured configs, "not for development" notice, download/run instructions.
- **Acceptance:** a playtester can start the server and join the lobby within 5 minutes.

**Option B: Demo branch in the same repo**
- **Includes:** a demo-only branch with curated configs and a small map pack.
- **Excludes:** secrets, tokens, player data, Mojang-proprietary assets.
- **Tasks:** branch warning banner, demo README, preconfigured configs, download/run instructions.
- **Acceptance:** a playtester can start the server and join the lobby within 5 minutes.

**Option C: Release asset "demo server zip"**
- **Includes:** minimal server folder skeleton, demo configs, README, plugin JAR from the release build.
- **Excludes:** secrets, tokens, player data, Mojang-proprietary assets, server binaries.
- **Tasks:** zip build step in release workflow, preconfigured configs, download/run instructions.
- **Acceptance:** a playtester can start the server and join the lobby within 5 minutes.

## Traction Plan (POSTING + COMMUNITY)
- [ ] Create `v0.1.0` Release before posting anywhere.
- [ ] Create 3 GitHub issues labeled: `good first issue`, `help wanted`, `playtesting`.
- [ ] Star the repo.
- [ ] Post targets: r/admincraft, PaperMC Discord, SpigotMC forums.
- [ ] Admincraft compliance notes:
  - Free, source-available, no monetization gating.
  - Avoid framing as "AI-built"; describe as "I've been building".
  - Posting frequency: once per 28 days.
- **Acceptance:** at least 1–2 playtest reports or issue comments from external users.

## Contributor-Friendly Tasks
- [ ] UI polish: boss bar timers, titles/subtitles, action bar feedback.  
  - **Acceptance:** clear feedback in raid start/end and extraction states.  
  - **Where:** UI services + listener feedback hooks.
- [ ] Better logging and debug toggles.  
  - **Acceptance:** toggleable verbose logs for raid lifecycle and loot.  
  - **Where:** logger helpers and config flags.
- [ ] Config validation with clear error messages.  
  - **Acceptance:** missing/invalid config keys produce actionable startup warnings.  
  - **Where:** config loader + validation utilities.
- [ ] Map builder documentation + raid region checklist.  
  - **Acceptance:** step-by-step doc for `/raidadmin edit` and validation.  
  - **Where:** docs + README updates.
- [ ] Balance/loot tuning framework.  
  - **Acceptance:** loot table adjustments documented with examples.  
  - **Where:** `loot_tables.yml` docs + loot manager guidance.
- [ ] Example configs and sample raid pack.  
  - **Acceptance:** a new admin can import sample configs and launch a raid.  
  - **Where:** docs + sample config folder.
- [ ] QA checklist for extraction edge cases.  
  - **Acceptance:** repeatable manual test list with expected outputs.  
  - **Where:** docs + TODO QA section.
- [ ] Paper server setup notes for contributors.  
  - **Acceptance:** quick-start instructions with required Java version.  
  - **Where:** README + docs.

## Build/CI Follow-ups
- [ ] (Owner=BuildRelease, Scope=V2.0) Command `./gradlew clean build` fails in restricted/proxied environments:
  - Gradle distribution download can return HTTP 403 (wrapper cannot bootstrap).
  - Plugin `org.gradle.toolchains.foojay-resolver-convention:0.9.0` may fail to resolve from Gradle Plugin Portal.
  - **Next action:** retry in an environment with access, or document/implement offline-friendly options (pre-downloaded distribution zip, internal mirrors, or removing the plugin if unnecessary).

1. V2.0: Repo / Local Harness / Release Hygiene
   - [x] (Owner=BuildRelease, Scope=V2.0) Add GitHub Actions CI: `./gradlew clean build` (cache Gradle). DoD: CI passes on push + PR; failures block merges.
   - [x] (Owner=BuildRelease, Scope=V2.0) Add `docs/releasing.md` and version bump instructions. DoD: checklist covers bump + build + smoke test + tag.
   - [x] (Owner=DevEx, Scope=V2.0) Document `/server/` local harness + copy task for Windows. DoD: one command builds and copies plugin jar into `/server/plugins/`.

2. V2.0: UI/UX Polish
   - BossBar Raid Timer
     - [x] (Owner=UI, Scope=V2.0) Add BossBar showing remaining raid time for players in raid. DoD: updates every second; disappears on raid end; never leaks across raids.
     - [x] (Owner=UI, Scope=V2.0) Add "Raid Start" title/subtitle + sound (sound done, title/subtitle pending). DoD: fires once per player per raid instance.
   - ActionBar Extraction Countdown
     - [x] (Owner=UI, Scope=V2.0) Add ActionBar countdown while extracting. DoD: shows seconds remaining; cancels instantly if player leaves evac.
     - [x] (Owner=UI, Scope=V2.0) Add extraction progress sound/tick feedback (vanilla sounds). DoD: plays only during active extraction and stops on cancel.
   - Failure/Success Feedback
     - [x] (Owner=UI, Scope=V2.0) Add success feedback (title + sound) and failure feedback (title + sound). DoD: success only on first extraction completion; failure on death/timeout/quit.

3. V2.0: One Real Map Support (No Instancing Yet)
   - [x] (Owner=Config, Scope=V2.0) Add `config.yml` keys: `lobbyWorld`, `lobbySpawn` (x,y,z,yaw,pitch), `raidWorld`. DoD: validated at startup with clear warnings if missing.
   - [x] (Owner=TeleportService, Scope=V2.0) Implement lobby return flow using `TeleportService`. DoD: after raid ends, all players return to lobby spawn reliably.
   - [x] (Owner=RegionProvider, Scope=V2.0) Implement `RegionProvider` for evac zones + raid bounds. DoD: `isInEvacZone(player, raidId)` and `isInRaidBounds(player, raidId)` work.
   - [x] (Owner=RegionProvider, Scope=V2.0) Enforce raid bounds (soft at first). DoD: warning + teleport back or apply effect if player leaves bounds (configurable).
   - [x] (Owner=WorldManager, Scope=V2.0) Hook `server/raid_templates/future` into a dedicated editor world for `raidId: future` (no instancing yet). DoD: template world loads for `/raidadmin edit future` and persists edits in editor config storage.

4. V2.0: Raid Session Rules
   - [x] (Owner=RaidManager, Scope=V2.0) Lock join rules (no mid-raid join unless explicitly allowed). DoD: `/raid join` rejects if raid already in progress.
   - [x] (Owner=RaidManager, Scope=V2.0) Handle disconnect rules clearly. DoD: disconnect counts as failure; inventory restored; raid state cleaned.
   - [x] (Owner=ExtractionService, Scope=V2.0) Add simple anti-exploit checks (extraction spam protection, command spam cooldown). DoD: no double stash commits; logs show blocked attempts.

6. V2.0: Admin Tools
   - [x] (Owner=AdminCommands, Scope=V2.0) Extend `/raid status` with ETA, state, player count, and current raidId. DoD: useful output in lobby + in raid.
   - [x] (Owner=AdminCommands, Scope=V2.0) Implement `/raidadmin force-extract <player>`. DoD: marks extraction success idempotently, commits loot, returns player.
   - [x] (Owner=AdminCommands, Scope=V2.0) Implement `/raidadmin cancel <raidId|active>`. DoD: cancels raid, restores inventories, returns to lobby, cleans instance state.
   - [x] (Owner=AdminCommands, Scope=V2.1) Add `/weapon` admin utilities (give/apply/bench). DoD: give configured items, apply mods/scrolls, bench UI works.

7. V2.0: QA / Smoke Tests (Manual)
   - [ ] (Owner=QA, Scope=V2.0) Run 50+ extraction attempts; verify no double-commit and no dupes. DoD: zero double-commit or dupes observed.
   - [ ] (Owner=QA, Scope=V2.0) Test death/timeout/quit restoration paths repeatedly. DoD: inventory restored and raid state cleaned every time.
   - [ ] (Owner=QA, Scope=V2.0) Loot distribution sanity check (sample size 1000 rolls). DoD: observed distribution matches expected weights.
   - [ ] (Owner=QA, Scope=V2.0) Multi-raid definitions min/max enforcement. DoD: queue and start respect min/max.
   - [ ] (Owner=QA, Scope=V2.0) Restart server mid-raid: ensure safe cleanup behavior (even if fail closed). DoD: no corrupt state; players restored or blocked cleanly.

8. V2.1: Admin Map Editor (In-Game Configuration)
   - Foundation
     - [x] (Owner=MapEditorManager, Scope=V2.1) Add `MapEditorManager` and `EditorSession` tracking. DoD: multiple admins can edit different raids safely (or enforce one editor at a time).
     - [x] (Owner=AdminCommands, Scope=V2.1) Add commands `/raidadmin edit <raidId>`, `/raidadmin edit exit`, `/raidadmin save`, `/raidadmin validate <raidId>`. DoD: validate prints missing spawns/evacs/loot containers.
   - Tools
     - [x] (Owner=MapEditorTools, Scope=V2.1) Tool: set spawn point at admin location (left click = add). DoD: stored per raid; used for player deployment.
     - [x] (Owner=MapEditorTools, Scope=V2.1) Tool: select two corners for cuboid evac zone (pos1/pos2). DoD: evac zone persists; RegionProvider detects it.
     - [x] (Owner=MapEditorTools, Scope=V2.1) Tool: mark chest as loot container for raid. DoD: chest location saved; loot fill uses this list.
     - [x] (Owner=MapEditorTools, Scope=V2.1) Tool: loot marker item (shift-right-click block) + `/raidadmin undo`. DoD: saves loot marker entries into `locations.yml` and undo removes the last saved entry for the editor.
   - Storage
     - [x] (Owner=MapEditorStorage, Scope=V2.1) Create `locations.yml` (or `raid_locations.yml`) for editor output. DoD: editor writes locations here, not directly into `raids.yml` initially.
     - [x] (Owner=MapEditorStorage, Scope=V2.1) Add schema versioning (`schemaVersion: 1`). DoD: warnings on mismatch; migration notes documented.
   - Safety and UX
     - [x] (Owner=MapEditorManager, Scope=V2.1) Prevent editing while raid is active for same raidId. DoD: refuses edit or forces cancel.
     - [x] (Owner=MapEditorUI, Scope=V2.1) Add editor feedback actionbar "Tool: Spawn / Evac / Loot". DoD: clear, low spam, usable.

9. V2.1: Loot Spawns + Loot Feel (Pre-Instancing)
   - Spawn Weights
     - [x] (Owner=LootConfig, Scope=V2.1) Add per-loot-position spawn chance percentage. DoD: each saved loot location supports a configurable chance and rolls during loot fill.
     - [x] (Owner=MapEditorTools, Scope=V2.1) Edit-mode toggle: spawn loot in all chests to preview all possible locations. DoD: command shows every loot container as filled (no weighting) while edit mode is active.
     - [x] (Owner=MapEditorTools, Scope=V2.1) Loot position capture uses the front face of the block (chest front) for orientation. DoD: chest front is consistent with saved position data.
   - Loot Chest Feel (No Resource Pack)
     - [x] (Owner=UX, Scope=V2.1) Option A: GUI "crate roll" animation. DoD: cancel normal chest open, show custom GUI with rolling items, lock slots during roll, then deliver final loot.
     - [x] (Owner=UX, Scope=V2.1) Prevent early-close dupes: closing during roll never awards filler panes. DoD: ESC during animation yields no glass panes; loot only awarded after reveal.
     - [ ] (Owner=UX, Scope=V2.1) Option B: Actionbar/Bossbar roll + sounds. DoD: progress bar + tick sounds, then drop item with particles/sound.
     - [ ] (Owner=UX, Scope=V2.1) Physical-world animation with particles/sounds. DoD: chest open sound + extra effects while rolling; optional floating text if available.
   - Custom Weapons + Mods + Vanilla Pack
     - [x] (Owner=LootConfig, Scope=V2.1) Add `items.yml` with weapon/mod/spell definitions. DoD: weapons and mods are config-driven (material, name, rarity, custom_model_data, spell id).
     - [x] (Owner=LootService, Scope=V2.1) Roll custom items from loot tables by `id`. DoD: loot rolls produce full ItemStacks (serialized into `ItemData` for stash) when `id` exists in `items.yml`.
     - [x] (Owner=Magic, Scope=V2.1) Add melee spell casting (right-click) with cooldown + mana + durability decay. DoD: spells cast only for custom weapons, respect mana/cooldowns, no terrain grief for fireballs.
     - [x] (Owner=Progression, Scope=V2.1) Add Weapon Bench UI + mod socketing + spell unlock cores. DoD: bench applies core mods/scrolls; core mods unlock weapon spell.
     - [x] (Owner=Progression, Scope=V2.1) Add custom enchant scrolls + basic effects. DoD: scrolls apply via bench/apply; effects implemented (backstab, bleed, momentum, grim_harvest).
     - [x] (Owner=ResourcePack, Scope=V2.1) Convert Nongko CIT models into vanilla `CustomModelData` overrides. DoD: models load on vanilla clients and match `items.yml` custom_model_data values.
     - [x] (Owner=ResourcePack, Scope=V2.1) Add Windows build script to output a GitHub-hostable resource pack zip. DoD: zip root contains `pack.mcmeta` + `assets/` with `/` paths; outputs SHA1.

9. V2.2: Stash + Loadout Loop
   - [x] (Owner=Stash, Scope=V2.2) Make player inventory the raid loadout (withdraw from stash to gear up). DoD: player inventory enters raid unchanged; stash access blocked while in raid.
   - [x] (Owner=Stash, Scope=V2.2) Enforce loadout loss rules. DoD: death/timeout/quit clears the at-risk loadout; extraction deposits carried inventory to stash.
   - [x] (Owner=Stash, Scope=V2.2) Add max stash capacity + overflow handling. DoD: `stash.max_stacks` caps stored stacks; extraction overflow stays in player inventory; manual deposits are blocked when full.
   - [ ] (Owner=Progression, Scope=V2.3) Add stash upgrades via level/profile. DoD: stash capacity comes from a persisted player level/profile; configurable capacity tiers; UI shows current capacity; migration notes documented.

10. V2.2: Instanced Raid Worlds (Fresh Map Per Raid)
   - [ ] (Owner=WorldManager, Scope=V2.2) Define folder contract: templates in `/raid_templates/<templateName>/`, instances in `/raid_instances/<raidId>/<instanceId>/`. DoD: documented in README/docs.
   - [ ] (Owner=WorldManager, Scope=V2.2) `loadRaidWorld(templateName)` clones template to new instance folder. DoD: clone completes reliably; instance world loads; returns world name/id.
   - [ ] (Owner=WorldManager, Scope=V2.2) `unloadRaidWorld(worldName)` unloads and deletes instance safely. DoD: no world corruption; no file lock crashes.
   - [ ] (Owner=WorldManager, Scope=V2.2) Deletion fallback strategy (Windows-safe): retry delete; if locked, quarantine folder `_old_<timestamp>` and delete next startup. DoD: server does not hang; disk does not fill silently.
   - [ ] (Owner=TeleportService, Scope=V2.2) Update `TeleportService` to use instance world. DoD: players spawn into correct instance and evac zones match instance.
   - [ ] (Owner=RegionProvider, Scope=V2.2) Update `RegionProvider` to resolve regions per instance world. DoD: evac detection works on cloned worlds.
   - [ ] (Owner=WorldManager, Scope=V2.2) On plugin enable: scan and clean abandoned instance folders (safe delete/quarantine). DoD: prevents buildup across crashes/restarts.

11. V2.3: Lobby Trader + Progression Loop (Optional)
   - [x] (Owner=Trader, Scope=V2.3) Add `credits` to player profile (SQLite). DoD: persisted reliably; safe from dupes.
   - [x] (Owner=Progression, Scope=V2.3) Add XP/level system (extraction-focused). DoD: XP awards are configurable; level is persisted; extraction success updates XP/level; no XP dupes on retries/relogs.
   - [x] (Owner=UI, Scope=V2.3) Add HUD showing server name + money + level. DoD: vanilla UI (scoreboard/tablist/actionbar) shows configured `server_name` plus `credits` and `level`; updates live; togglable per-player and/or config.
   - [x] (Owner=LootConfig, Scope=V2.3) Add item values and categories in loot config. DoD: junk items sell for credits; rare items sell for more.
   - [x] (Owner=Trader, Scope=V2.3) Implement `TraderNPC` interaction (sell junk for credits, buy starter kit). DoD: basic buy/sell loop works; no dupe exploits.
   - [x] (Owner=Trader, Scope=V2.3) Config-driven kit definition in YAML. DoD: easy balancing without code changes.

12. Backlog / Nice-to-Have (Post v2)
   - [ ] (Owner=UI, Scope=Backlog) Bossbar styling + icons (vanilla only). DoD: styling configurable without client mods.
   - [ ] (Owner=LootConfig, Scope=Backlog) Keycards and locked rooms (config-driven). DoD: keycard gating works without dupes.
   - [ ] (Owner=AI, Scope=Backlog) POI guards / dynamic mobs. DoD: basic patrols and spawns run safely.
   - [x] (Owner=UX, Scope=Backlog) Resource pack for custom models (vanilla CustomModelData). DoD: server-hosted pack applies models without OptiFine.
   - [ ] (Owner=Queue, Scope=Backlog) Squads + party queue. DoD: party join and queueing works with existing raid flow.
   - [ ] (Owner=Telemetry, Scope=Backlog) Metrics/telemetry dashboard. DoD: core raid metrics exported and visible.

## Invariants / Safety Rules
- Do not call Bukkit APIs off the main thread.
- Loot commit to stash happens only on successful extraction.
- Extraction is idempotent; no double rewards.
- Failure (death/quit/timeout) clears the at-risk loadout (player inventory).
- Admin cancel restores the pre-raid inventory snapshot (rollback behavior).
- Do not commit local server files: `/server/`, world folders, logs, DBs.

## Logging Requirements
- Keep log lines structured for: raid start/end, extraction start/cancel/success, stash commit success/failure, world clone/load/unload/delete.
