# Raid Extraction (Paper) — Extraction-MC

**10-second pitch:** Raid Extraction is a data-driven Paper plugin that delivers an extraction-style loop in Minecraft: queue → deploy into a raid, loot under pressure, extract to keep rewards, and stash progress in SQLite. The core systems are live and ready for contributors to expand maps, balance loot, and polish the UX.

## Play the Demo

Want to try Raid Extraction right now? Play the demo: https://github.com/Shrek3294/raid-extraction-demo


[https://youtu.be/-F6NsGQA4EQ]

## Help Wanted Right Now
## Join the Discord Here https://discord.gg/HMZ9DBEs

We are looking for:
- **Playtesters** — run raids, find bugs, give balance feedback.
- **Map builders** — create raid arenas, extraction layouts, and loot routes.
- **Loot/balance designers** — tune loot tables, rarity, and progression pace.
- **Java contributors** — tackle features, fixes, and integration polish.
- **Documentation helpers** — improve onboarding, guides, and playtest reports.

If you want to help but don’t know where to start, see [HELP_WANTED.md](HELP_WANTED.md).

## Features

- Full raid lifecycle with queueing, raid state machine, extraction, and cleanup. (See `src/main/java/com/raidextraction/raid/RaidInstance.java`, `RaidManager.java`, and `QueueManager.java`.)
- Extraction flow with cooldowns, actionbar feedback, and idempotent completion. (See `src/main/java/com/raidextraction/extraction/ExtractionService.java`.)
- Loot generation from YAML-driven tables and in-memory loot distribution. (See `src/main/java/com/raidextraction/loot/LootService.java` and `src/main/resources/loot_tables.yml`.)
- Custom item pipeline for weapons, mods, and spells driven by `items.yml`. (See `src/main/java/com/raidextraction/item/CustomItemRegistry.java` and `src/main/resources/items.yml`.)
- Stash persistence backed by SQLite. (See `src/main/java/com/raidextraction/stash/SQLiteStashRepository.java`.)
- Map editor tooling for spawn points, evac zones, and loot containers. (See `src/main/java/com/raidextraction/editor/MapEditorManager.java` and `MapEditorStorage.java`.)

## Quickstart (Server Admin)

> Full walkthroughs live in [docs/quickstart.md](docs/quickstart.md).

1. Install **Paper** (recommended 1.21.x) and **Java 21**.
2. Drop the latest plugin jar into `plugins/` and start the server once.
3. Stop the server and edit configs in `plugins/RaidExtraction/`:
   - `config.yml` for lobby/raid worlds and evac duration.
   - `raids.yml` for raid definitions.
   - `loot_tables.yml` and `items.yml` for loot configuration.
4. Start the server and use `/raid join <raidId>` to queue.

> Note: Download plugin jars from **GitHub Releases** rather than committed artifacts; the repo should not contain built plugin jars.

## Commands & Permissions

A complete list lives in [docs/commands.md](docs/commands.md). Here is a quick reference:

| Command | Purpose | Permission |
| --- | --- | --- |
| `/raid` | Queue, leave, or check raid status. | `raid.user` |
| `/stash` | Open your stash UI. | `raid.user` |
| `/weapon` | Weapon guide, bench, apply mods, admin gives. | `raid.user` / `raid.admin` |
| `/raidadmin` | Admin raid controls + editor tooling. | `raid.admin` |

## Configuration

Configuration lives under `plugins/RaidExtraction/`. See [docs/configuration.md](docs/configuration.md) for file-by-file detail and examples.

## Playtesting Guide

See [docs/playtesting.md](docs/playtesting.md) for scenarios, log capture, and reporting templates.

## Roadmap

Short-term priorities focus on polishing the raid loop (queue → raid → loot → extract → stash), improving loot feel, and expanding map content. See [TODO.md](TODO.md) for the full roadmap and [TODO-completed.md](TODO-completed.md) for completed milestones.

## Before Posting Checklist

Before posting publicly (Discord/forums/Reddit), review the release, demo, and traction checklists in [TODO.md](TODO.md).

## License

MIT — see [LICENSE](LICENSE).
