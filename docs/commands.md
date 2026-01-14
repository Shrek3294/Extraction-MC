# Commands & Permissions

This document lists the currently implemented commands and permissions. See `src/main/resources/plugin.yml` and the command classes in `src/main/java/com/raidextraction/command/` for the authoritative source.

> If you discover a mismatch, please file an issue and note the server + plugin versions.

## Command Table (Quick Reference)

| Command | Description | Permission |
| --- | --- | --- |
| `/raid` | Join/leave queues and check raid status. | `raid.user` |
| `/stash` | Open your stash UI. | `raid.user` |
| `/weapon` | Weapon guide, bench, apply mods, admin gives, spell debug. | `raid.user` / `raid.admin` |
| `/raidadmin` | Admin raid controls and map editor tools. | `raid.admin` |

## Player Commands

### /raid

- `/raid join <raidId>`: Join the queue for a specific raid definition.
- `/raid leave`: Leave your current raid queue.
- `/raid status`: Show your raid status, active raids, and queue sizes.
- `/raid hud`: Toggle the scoreboard HUD (server name, credits, level) for yourself.

See `src/main/java/com/raidextraction/command/RaidCommand.java`.

### /stash

- `/stash`: Open your stash UI.
  - Click items in the stash to withdraw into your inventory.
  - Shift-click items from your inventory to deposit into the stash (blocked when the stash is full).

## Lobby Trader (Optional)

When `trader.yml` is enabled, a lobby `Trader` NPC can be right-clicked to:
- Sell configured loot categories for credits.
- Buy a configured kit (default: `starter`).

See `src/main/java/com/raidextraction/command/StashCommand.java`.

### /weapon

- `/weapon guide`: Get a guide book explaining weapons, rarities, mods, spells, and scrolls.
- `/weapon give <itemId> [amount]`: Give yourself a configured weapon/mod/scroll from `items.yml`.
- `/weapon give <player> <itemId> [amount]`: Give a player a configured weapon/mod/scroll from `items.yml`.
- `/weapon apply`: Apply the mod/enchant scroll in your offhand to the weapon in your main hand.
- `/weapon bench`: Open the weapon bench UI (also applies mods/enchant scrolls).
- `/weapon debugspells <on|off|toggle|status>`: Toggle spell debug for yourself.
- `/weapon debugspells <player> <on|off|toggle|status>`: Toggle spell debug for another player.

See `src/main/java/com/raidextraction/command/WeaponCommand.java`.

If another plugin owns `/weapon`, use `/raidextraction:weapon ...` or `/rexweapon ...`.

## Admin Commands

### /raidadmin

- `/raidadmin start <raidId>`: Start a raid from the queue if enough players are ready.
- `/raidadmin stop <activeRaidId>`: Stop a specific active raid instance by id.
- `/raidadmin cancel <activeRaidId|raidId|active>`: Cancel a raid instance and return players to the lobby.
- `/raidadmin force-extract <playerName|playerUuid>`: Force a player to extract and commit their loot.
- `/raidadmin edit <raidId>`: Enter editor mode for a raid definition.
- `/raidadmin exit`: Exit editor mode and return to the lobby.
- `/raidadmin save`: Save editor data to `locations.yml`.
- `/raidadmin validate <raidId>`: Validate editor data (spawns, evac zones, loot containers).
- `/raidadmin undo`: Remove the last saved loot marker for the active editor session.
- `/raidadmin debugbounds`: Toggle bounds debug actionbar for the current player.
- `/raidadmin lootchance <percent>`: Set the spawn chance for loot containers (default for new markers and update the targeted container).
- `/raidadmin lootpreview`: Toggle temporary chest placement at all saved loot locations in editor mode.
- `/raidadmin setlobby`: Set lobby spawn to the caller's current location.

See `src/main/java/com/raidextraction/command/RaidAdminCommand.java`.

## Permissions

- `raid.user`: Allows participation in raids and stash access.
- `raid.admin`: Allows administrative control over raids and extraction states.

See `src/main/resources/plugin.yml`.

## TBD / TODO Notes

- **TBD:** full command descriptions for future raid instance management and instancing controls. TODO: update once V2.2 instancing commands are implemented.
