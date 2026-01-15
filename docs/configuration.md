# Configuration

All configuration files live under `plugins/RaidExtraction/` after the plugin starts once. Defaults are bundled under `src/main/resources/`.

## Files Overview

### `config.yml`

Global settings and lobby/raid world defaults.

Key highlights (see `src/main/resources/config.yml`):
- `debug`: verbose logging toggle.
- `lobbyWorld` + `lobbySpawn`: where players return after raids.
- `raidWorld`: default raid world name.
- `evac_duration_seconds`: extraction countdown duration.
- `announce_start`: broadcast raid start announcements.

### `raids.yml`

Defines raid IDs, worlds, player limits, durations, loot tables, and evac zones.

See `src/main/resources/raids.yml` and `src/main/java/com/raidextraction/config/model/RaidDefinition.java`.

Key fields:
- `world`: world name for the raid template.
- `min_players` / `max_players`.
- `duration_seconds`.
- `loot_table`: table ID from `loot_tables.yml`.
- `spawn`: optional spawn location override.
- `evac_zones`: list of evac circles (or empty if you use editor-based zones).
- `target_loot_count`: target number of item stacks distributed per raid.

### `loot_tables.yml`

Weighted loot tables used by the loot service.

Each entry includes:
- `id`: internal item ID (maps to `items.yml` when applicable).
- `material`: fallback Bukkit material.
- `weight`, `min_amount`, `max_amount`.

If a loot entry `id` does not exist in `items.yml`, the plugin will spawn a vanilla item using `material` (so seeing a lot of `IRON_SWORD`/`BREAD`/`PAPER` usually means your table is missing the custom IDs).

See `src/main/resources/loot_tables.yml` and `src/main/java/com/raidextraction/loot/LootService.java`.

### `items.yml`

Custom items (weapons, mods, spells, enchants, mana).

See `src/main/resources/items.yml` and `src/main/java/com/raidextraction/item/CustomItemRegistry.java`.

### `locations.yml` (generated)

Output from the in-game map editor: spawn points, evac zones, loot containers, and per-container loot chance.

See `src/main/java/com/raidextraction/editor/MapEditorStorage.java` for schema details.

### `director.yml`

Placeholder for threat curves and future director logic.

See `src/main/resources/director.yml`.

## Common Tweaks

- Update `evac_duration_seconds` to adjust extraction tension.
- Add new raid entries in `raids.yml` and match them to loot tables.
- Use `/raidadmin edit <raidId>` to capture spawn/evac/loot containers into `locations.yml`.
- Adjust per-container spawn chance with `/raidadmin lootchance <percent>`.

## TBD / TODO Notes

- **TBD:** schema documentation for future instanced raid folders (`raid_templates/` and `raid_instances/`). TODO: add once instancing lands.
