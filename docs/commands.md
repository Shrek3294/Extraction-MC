# Commands

This document lists the currently implemented commands and what they do.

## Player Commands

### /raid

- `/raid join <raidId>`: Join the queue for a specific raid definition.
- `/raid leave`: Leave your current raid queue.
- `/raid status`: Show your raid status, active raids, and queue sizes.
- `/raid hud`: Toggle the scoreboard HUD (server name, credits, level) for yourself.

### /stash

- `/stash`: Open your stash UI.
  - Click items in the stash to withdraw into your inventory.
  - Shift-click items from your inventory to deposit into the stash (blocked when the stash is full).

## Lobby Trader (Optional)

When `trader.yml` is enabled, a lobby `Trader` NPC can be right-clicked to:
- Sell configured loot categories for credits.
- Buy a configured kit (default: `starter`).

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
- `/raidadmin lootchance <percent>`: Set the spawn chance for loot chests (default for new markers and update the targeted container).
- `/raidadmin lootpreview`: Toggle temporary chest placement at all saved loot locations in editor mode.

### /weapon

- `/weapon guide`: Get a guide book explaining weapons, rarities, mods, spells, and scrolls.
- `/weapon give <itemId> [amount]`: Give yourself a configured weapon/mod/scroll from `items.yml`.
- `/weapon give <player> <itemId> [amount]`: Give a player a configured weapon/mod/scroll from `items.yml`.
- `/weapon apply`: Apply the mod/enchant scroll in your offhand to the weapon in your main hand.
- `/weapon bench`: Open the weapon bench UI (also applies mods/enchant scrolls).

If another plugin owns `/weapon`, use `/raidextraction:weapon ...` or `/rexweapon ...`.

#### Weapon IDs (copy/paste)

Weapons:
- `/weapon give <player> iron_dagger`
- `/weapon give <player> soldiers_longsword`
- `/weapon give <player> bone_cleaver`
- `/weapon give <player> frostbite_blade`
- `/weapon give <player> infernal_saber`
- `/weapon give <player> stormpiercer_rapier`
- `/weapon give <player> voidreaver`
- `/weapon give <player> dragonfang_greatsword`
- `/weapon give <player> celestial_glaive`
- `/weapon give <player> chronos_blade`
- `/weapon give <player> oblivion_scythe`
- `/weapon give <player> aetherblade`

Core mods (unlock each weapon's spell; put weapon in main hand and core in offhand, then `/weapon apply` or use `/weapon bench`):
- `venom_vial` (Iron Dagger)
- `wind_rune` (Soldier's Longsword)
- `splinter_core` (Bone Cleaver)
- `cryo_core` (Frostbite Blade)
- `blast_rune` (Infernal Saber)
- `voltage_cell` (Stormpiercer Rapier)
- `dark_core` (Voidreaver)
- `inferno_catalyst` (Dragonfang Greatsword)
- `astral_lens` (Celestial Glaive)
- `temporal_core` (Chronos Blade)
- `death_core` (Oblivion Scythe)
- `prismatic_core` (Aetherblade)

Other mods:
- `/weapon give <player> serrated_edge`
- `/weapon give <player> lightweight_grip`

Enchant scrolls (apply like mods):
- `/weapon give <player> scroll_backstab`
- `/weapon give <player> scroll_bleed`
- `/weapon give <player> scroll_momentum`
- `/weapon give <player> scroll_grim_harvest`

#### Spell casting

- Weapons start with spells locked; install the matching core mod to unlock the spell.
- Cast: right-click with the weapon in your main hand.
- If you are aiming at an interactable block (chest/door/button/etc.), sneak-right-click to cast instead.
- Mana UI: when holding a custom weapon, your XP bar becomes your mana bar (level = current mana, bar = %).
- Cooldowns: when you cast, the held item's vanilla cooldown overlay shows when the spell is ready again.

#### Spell debugging

- `/weapon debugspells toggle`: Toggle per-player spell debug.
- `/weapon debugspells <player> toggle`: Toggle for another player.
- When enabled, you get a `[SpellDebug] ...` actionbar and the server console logs why a cast was blocked (spell locked, cooldown, cancelled event, etc.).
