# Features

Raid Extraction is an extraction-style raid loop on Paper that emphasizes data-driven configuration and a persistent stash. The core loop is **queue → raid → loot → extract → stash**, with integration layers separated from core logic.

## Gameplay Loop

1. **Queue** — players join a raid queue with `/raid join <raidId>`.
2. **Raid** — when enough players queue, a raid instance starts and players deploy to the raid world.
3. **Loot** — loot rolls at raid start and is distributed across configured containers.
4. **Extract** — players enter evac zones and complete a timed extraction.
5. **Stash** — extracted loot is persisted to SQLite; failures restore pre-raid inventory.

See `src/main/java/com/raidextraction/raid/RaidManager.java`, `QueueManager.java`, and `RaidInstance.java` for the state machine and queue flow.

## Major Systems

### Raid lifecycle + state machine

Raid instances transition between lifecycle states and track timing, players, and cleanup. See `src/main/java/com/raidextraction/raid/RaidInstance.java` and `RaidManager.java`.

### Extraction & evac tracking

Extraction is idempotent and tied to evac zones with countdown feedback. See `src/main/java/com/raidextraction/extraction/ExtractionService.java` and `EvacTracker.java`.

### Loot tables + distribution

Loot tables live in `loot_tables.yml`, and rolls are generated and distributed per raid. See `src/main/java/com/raidextraction/loot/LootService.java` and `LootTableRegistry.java`.

### Crate roll UI

Loot containers use a custom rolling UI instead of vanilla chest access. See `src/main/java/com/raidextraction/ux/CrateAnimationService.java` and `listener/LootInteractionListener.java`.

### Custom items (weapons, mods, spells)

Custom items are defined in `items.yml` and mapped into ItemStacks. See `src/main/java/com/raidextraction/item/CustomItemRegistry.java` and `CustomItemFactory.java`.

### Stash persistence

Extracted loot is stored per player in SQLite. See `src/main/java/com/raidextraction/stash/SQLiteStashRepository.java` and `StashService.java`.

### Map editor tooling

Admins can edit raid templates by setting spawn points, evac zones, and loot containers. Data is stored in `locations.yml`. See `src/main/java/com/raidextraction/editor/MapEditorManager.java` and `MapEditorStorage.java`.

## Config-Driven Behavior

Core behavior is controlled by YAML files in `plugins/RaidExtraction/`, including raid definitions, loot tables, and custom items. See `src/main/resources/config.yml`, `raids.yml`, `loot_tables.yml`, and `items.yml`.

## TBD / Future Work

- **PvP support:** not yet confirmed. **TBD**. TODO: clarify PvP rules and add a dedicated section once implemented.
- **Instanced raid worlds:** folder contract and cleanup plan are in the roadmap. **TBD**. TODO: document instance lifecycle once implemented.
