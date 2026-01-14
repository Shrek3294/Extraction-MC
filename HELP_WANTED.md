# Help Wanted

This list is derived from the current roadmap in [TODO.md](TODO.md). Each task includes acceptance criteria and file/module hints.

## Good First Issue

1) **Document instanced raid folder contract (V2.2)**
   - **Description:** Add documentation for `/raid_templates/<templateName>/` and `/raid_instances/<raidId>/<instanceId>/` layout.
   - **Acceptance criteria:** Docs include folder contract, lifecycle summary, and update README links.
   - **Hints:** `docs/configuration.md`, `docs/features.md`, `README.md`. (Roadmap: V2.2 WorldManager tasks.)

2) **QA: multi-raid min/max enforcement smoke test**
   - **Description:** Run a manual test covering queue min/max enforcement across multiple raid definitions.
   - **Acceptance criteria:** Provide a brief report with commands run, expected vs actual, and relevant logs.
   - **Hints:** `docs/playtesting.md`, `src/main/java/com/raidextraction/raid/QueueManager.java`.

## Polish / UI

3) **Loot roll actionbar/bossbar option (V2.1 UX Option B)**
   - **Description:** Implement the actionbar/bossbar roll with tick sounds instead of the crate GUI.
   - **Acceptance criteria:** Progress bar + tick sounds during roll; item revealed at completion; no dupes.
   - **Hints:** `src/main/java/com/raidextraction/ux/CrateAnimationService.java`, `listener/LootInteractionListener.java`.

4) **Physical-world loot roll animation (V2.1 UX Option B)**
   - **Description:** Add particle/sound-based loot roll around containers during reveal.
   - **Acceptance criteria:** Visual and audio cues during roll; no item dupes; configurable toggle.
   - **Hints:** `src/main/java/com/raidextraction/ux/CrateAnimationService.java`.

## Map / Content

5) **Instanced raid world cloning (V2.2)**
   - **Description:** Implement template cloning into `/raid_instances/<raidId>/<instanceId>/` and load the instance world.
   - **Acceptance criteria:** Clone succeeds, world loads, and instance unloads/cleans safely.
   - **Hints:** `src/main/java/com/raidextraction/integration/WorldManager.java`.

6) **Locked rooms + keycards (Backlog)**
   - **Description:** Add keycard-gated doors/areas and loot rules.
   - **Acceptance criteria:** Config-driven gating; no dupe exploits; clear messaging.
   - **Hints:** `src/main/java/com/raidextraction/loot/` and `docs/configuration.md`.

## Balance

7) **Loot distribution sanity check (QA V2.0)**
   - **Description:** Sample 1000+ loot rolls and compare against weights.
   - **Acceptance criteria:** Provide observed vs expected distribution with notes.
   - **Hints:** `src/main/java/com/raidextraction/loot/LootService.java`, `loot_tables.yml`.

8) **Economy values for loot items (V2.3)**
   - **Description:** Add item values/categories to config for a future trader loop.
   - **Acceptance criteria:** Config schema documented; values used in balance notes.
   - **Hints:** `src/main/resources/items.yml`, `docs/configuration.md`.

## Docs

9) **Instanced world lifecycle documentation (V2.2)**
   - **Description:** Add a lifecycle flow to docs explaining clone → play → unload → cleanup.
   - **Acceptance criteria:** Clear steps and edge-case notes (Windows file locks).
   - **Hints:** `docs/features.md`, `docs/development.md`.

10) **Manual testing matrix for extraction edge cases (QA V2.0)**
   - **Description:** Extend playtesting docs with explicit scenarios for death, timeout, and disconnect.
   - **Acceptance criteria:** Checklist includes commands, expected messages, and stash outcomes.
   - **Hints:** `docs/playtesting.md`, `TODO.md` QA section.
