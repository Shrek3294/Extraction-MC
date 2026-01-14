# Playtesting Guide

Help us validate the raid loop and balance. The most valuable reports include **repro steps**, **logs**, **config snippets**, and **version info**.

## What to Test

### Core raid loop
- Queue → deploy → raid → extract → stash.
- Multiple raids active at once (if configured).
- Queue min/max rules.

### Extraction flow
- Countdown starts on entering evac zone, cancels on exit.
- Successful extraction commits loot and returns player to lobby.
- Failure paths: death, timeout, disconnect.

### Loot & balance
- Loot quantity per raid (compare `target_loot_count`).
- Weight distribution feels in line with `loot_tables.yml`.
- Loot container chance per marker.

### Map editor tools
- Spawn points, evac zones, loot containers saved to `locations.yml`.
- `/raidadmin validate <raidId>` warnings match expectations.

## How to Capture Logs

- Paper logs: `logs/latest.log`
- Include the plugin version (from `plugins/RaidExtraction/` or `/version` in server console).

## Version Info to Include

- Minecraft server version
- Paper build number
- Java version
- Plugin version
- Config files changed (list or attach)

## Report Template

```
**Server version:**
**Paper build:**
**Java version:**
**Plugin version:**

**What were you doing?**
(Queue/raid/extract/editor/etc.)

**Steps to reproduce:**
1.
2.
3.

**Expected result:**

**Actual result:**

**Configs changed:**
- config.yml:
- raids.yml:
- loot_tables.yml:
- items.yml:
- locations.yml:

**Logs:**
(Attach relevant section from logs/latest.log)

**Map context:**
(World name, raidId, evac zone name, loot container coordinates)
```

## TBD / TODO Notes

- **TBD:** standardized telemetry or metrics export. TODO: update once metrics/telemetry features exist.
