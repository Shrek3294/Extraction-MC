# TODO

1. V2.0: Repo / Local Harness / Release Hygiene
   - [ ] (Owner=BuildRelease, Scope=V2.0) Add GitHub Actions CI: `./gradlew clean build` (cache Gradle). DoD: CI passes on push + PR; failures block merges.
   - [ ] (Owner=BuildRelease, Scope=V2.0) Add `docs/releasing.md` and version bump instructions. DoD: checklist covers bump + build + smoke test + tag.
   - [ ] (Owner=DevEx, Scope=V2.0) Document `/server/` local harness + copy task for Windows. DoD: one command builds and copies plugin jar into `/server/plugins/`.

2. V2.0: UI/UX Polish
   - BossBar Raid Timer
     - [ ] (Owner=UI, Scope=V2.0) Add BossBar showing remaining raid time for players in raid. DoD: updates every second; disappears on raid end; never leaks across raids.
     - [ ] (Owner=UI, Scope=V2.0) Add "Raid Start" title/subtitle + sound. DoD: fires once per player per raid instance.
   - ActionBar Extraction Countdown
     - [ ] (Owner=UI, Scope=V2.0) Add ActionBar countdown while extracting. DoD: shows seconds remaining; cancels instantly if player leaves evac.
     - [ ] (Owner=UI, Scope=V2.0) Add extraction progress sound/tick feedback (vanilla sounds). DoD: plays only during active extraction and stops on cancel.
   - Failure/Success Feedback
     - [ ] (Owner=UI, Scope=V2.0) Add success feedback (title + sound) and failure feedback (title + sound). DoD: success only on first extraction completion; failure on death/timeout/quit.

3. V2.0: One Real Map Support (No Instancing Yet)
   - [ ] (Owner=Config, Scope=V2.0) Add `config.yml` keys: `lobbyWorld`, `lobbySpawn` (x,y,z,yaw,pitch), `raidWorld`. DoD: validated at startup with clear warnings if missing.
   - [ ] (Owner=TeleportService, Scope=V2.0) Implement lobby return flow using `TeleportService`. DoD: after raid ends, all players return to lobby spawn reliably.
   - [ ] (Owner=RegionProvider, Scope=V2.0) Implement `RegionProvider` for evac zones + raid bounds. DoD: `isInEvacZone(player, raidId)` and `isInRaidBounds(player, raidId)` work.
   - [ ] (Owner=RegionProvider, Scope=V2.0) Enforce raid bounds (soft at first). DoD: warning + teleport back or apply effect if player leaves bounds (configurable).

4. V2.0: Stash v1 Completion
   - [ ] (Owner=StashService, Scope=V2.0) Implement `ItemData <-> ItemStack` conversion reliably. DoD: round-trip conversion does not lose name/lore/enchantments where supported.
   - [ ] (Owner=StashUI, Scope=V2.0) Build stash GUI (basic chest UI). DoD: `/stash` opens GUI; click withdraw moves item to inventory if space; updates stash.
   - [ ] (Owner=StashUI, Scope=V2.0) Add pagination and a close button. DoD: supports > 54 items; no dupes; no item loss.
   - [ ] (Owner=StashService, Scope=V2.0) Move stash DB writes off-thread + sync UI updates back to main thread. DoD: no main-thread SQLite writes; no async Bukkit calls; no race dupes.

5. V2.0: Raid Session Rules
   - [ ] (Owner=RaidManager, Scope=V2.0) Lock join rules (no mid-raid join unless explicitly allowed). DoD: `/raid join` rejects if raid already in progress.
   - [ ] (Owner=RaidManager, Scope=V2.0) Handle disconnect rules clearly. DoD: disconnect counts as failure; inventory restored; raid state cleaned.
   - [ ] (Owner=ExtractionService, Scope=V2.0) Add simple anti-exploit checks (extraction spam protection, command spam cooldown). DoD: no double stash commits; logs show blocked attempts.

6. V2.0: Admin Tools
   - [ ] (Owner=AdminCommands, Scope=V2.0) Extend `/raid status` with ETA, state, player count, and current raidId. DoD: useful output in lobby + in raid.
   - [ ] (Owner=AdminCommands, Scope=V2.0) Implement `/raidadmin force-extract <player>`. DoD: marks extraction success idempotently, commits loot, returns player.
   - [ ] (Owner=AdminCommands, Scope=V2.0) Implement `/raidadmin cancel <raidId|active>`. DoD: cancels raid, restores inventories, returns to lobby, cleans instance state.

7. V2.0: QA / Smoke Tests (Manual)
   - [ ] (Owner=QA, Scope=V2.0) Run 50+ extraction attempts; verify no double-commit and no dupes. DoD: zero double-commit or dupes observed.
   - [ ] (Owner=QA, Scope=V2.0) Test death/timeout/quit restoration paths repeatedly. DoD: inventory restored and raid state cleaned every time.
   - [ ] (Owner=QA, Scope=V2.0) Loot distribution sanity check (sample size 1000 rolls). DoD: observed distribution matches expected weights.
   - [ ] (Owner=QA, Scope=V2.0) Multi-raid definitions min/max enforcement. DoD: queue and start respect min/max.
   - [ ] (Owner=QA, Scope=V2.0) Restart server mid-raid: ensure safe cleanup behavior (even if fail closed). DoD: no corrupt state; players restored or blocked cleanly.

8. V2.1: Admin Map Editor (In-Game Configuration)
   - Foundation
     - [ ] (Owner=MapEditorManager, Scope=V2.1) Add `MapEditorManager` and `EditorSession` tracking. DoD: multiple admins can edit different raids safely (or enforce one editor at a time).
     - [ ] (Owner=AdminCommands, Scope=V2.1) Add commands `/raidadmin edit <raidId>`, `/raidadmin edit exit`, `/raidadmin save`, `/raidadmin validate <raidId>`. DoD: validate prints missing spawns/evacs/loot containers.
   - Tools
     - [ ] (Owner=MapEditorTools, Scope=V2.1) Tool: set spawn point at admin location (left click = add). DoD: stored per raid; used for player deployment.
     - [ ] (Owner=MapEditorTools, Scope=V2.1) Tool: select two corners for cuboid evac zone (pos1/pos2). DoD: evac zone persists; RegionProvider detects it.
     - [ ] (Owner=MapEditorTools, Scope=V2.1) Tool: mark chest as loot container for raid. DoD: chest location saved; loot fill uses this list.
   - Storage
     - [ ] (Owner=MapEditorStorage, Scope=V2.1) Create `locations.yml` (or `raid_locations.yml`) for editor output. DoD: editor writes locations here, not directly into `raids.yml` initially.
     - [ ] (Owner=MapEditorStorage, Scope=V2.1) Add schema versioning (`schemaVersion: 1`). DoD: warnings on mismatch; migration notes documented.
   - Safety and UX
     - [ ] (Owner=MapEditorManager, Scope=V2.1) Prevent editing while raid is active for same raidId. DoD: refuses edit or forces cancel.
     - [ ] (Owner=MapEditorUI, Scope=V2.1) Add editor feedback actionbar "Tool: Spawn / Evac / Loot". DoD: clear, low spam, usable.

9. V2.2: Instanced Raid Worlds (Fresh Map Per Raid)
   - [ ] (Owner=WorldManager, Scope=V2.2) Define folder contract: templates in `/raid_templates/<templateName>/`, instances in `/raid_instances/<raidId>/<instanceId>/`. DoD: documented in README/docs.
   - [ ] (Owner=WorldManager, Scope=V2.2) `loadRaidWorld(templateName)` clones template to new instance folder. DoD: clone completes reliably; instance world loads; returns world name/id.
   - [ ] (Owner=WorldManager, Scope=V2.2) `unloadRaidWorld(worldName)` unloads and deletes instance safely. DoD: no world corruption; no file lock crashes.
   - [ ] (Owner=WorldManager, Scope=V2.2) Deletion fallback strategy (Windows-safe): retry delete; if locked, quarantine folder `_old_<timestamp>` and delete next startup. DoD: server does not hang; disk does not fill silently.
   - [ ] (Owner=TeleportService, Scope=V2.2) Update `TeleportService` to use instance world. DoD: players spawn into correct instance and evac zones match instance.
   - [ ] (Owner=RegionProvider, Scope=V2.2) Update `RegionProvider` to resolve regions per instance world. DoD: evac detection works on cloned worlds.
   - [ ] (Owner=WorldManager, Scope=V2.2) On plugin enable: scan and clean abandoned instance folders (safe delete/quarantine). DoD: prevents buildup across crashes/restarts.

10. V2.3: Lobby Trader + Progression Loop (Optional)
   - [ ] (Owner=Trader, Scope=V2.3) Add `credits` to player profile (SQLite). DoD: persisted reliably; safe from dupes.
   - [ ] (Owner=LootConfig, Scope=V2.3) Add item values and categories in loot config. DoD: junk items sell for credits; rare items sell for more.
   - [ ] (Owner=Trader, Scope=V2.3) Implement `TraderNPC` interaction (sell junk for credits, buy starter kit). DoD: basic buy/sell loop works; no dupe exploits.
   - [ ] (Owner=Trader, Scope=V2.3) Config-driven kit definition in YAML. DoD: easy balancing without code changes.

11. Backlog / Nice-to-Have (Post v2)
   - [ ] (Owner=UI, Scope=Backlog) Bossbar styling + icons (vanilla only). DoD: styling configurable without client mods.
   - [ ] (Owner=LootConfig, Scope=Backlog) Keycards and locked rooms (config-driven). DoD: keycard gating works without dupes.
   - [ ] (Owner=AI, Scope=Backlog) POI guards / dynamic mobs. DoD: basic patrols and spawns run safely.
   - [ ] (Owner=UX, Scope=Backlog) Resource pack for custom models/sounds (optional). DoD: optional pack does not break default clients.
   - [ ] (Owner=Queue, Scope=Backlog) Squads + party queue. DoD: party join and queueing works with existing raid flow.
   - [ ] (Owner=Telemetry, Scope=Backlog) Metrics/telemetry dashboard. DoD: core raid metrics exported and visible.

## Invariants / Safety Rules
- Do not call Bukkit APIs off the main thread.
- Loot commit to stash happens only on successful extraction.
- Extraction is idempotent; no double rewards.
- Failure (death/quit/timeout) restores pre-raid inventory snapshot.
- Do not commit local server files: `/server/`, world folders, logs, DBs.

## Logging Requirements
- Keep log lines structured for: raid start/end, extraction start/cancel/success, stash commit success/failure, world clone/load/unload/delete.
