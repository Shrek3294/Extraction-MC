# Loot Design Brainstorm

This is a working design doc for making raid loot feel varied, build-defining, and not repetitive.

## Current State (In This Repo)

- `Extraction-MC/src/main/resources/loot_tables.yml` supports weighted rolls of `material` + `amount`.
- `LootService` currently returns `ItemData(material, amount)` only, so rolled loot can't yet have custom names, lore, stats, or models.
- The codebase already supports storing full item metadata in stash via `ItemDataMapper` (it serializes an `ItemStack` into a Base64 tag), so we can extend loot generation later to produce real `ItemStack`s and persist them safely.

## Design Goals

- **Different every chest:** multiple kinds of "good outcomes", not one best-in-slot item.
- **Readable rarity:** players can tell "how good" something is at a glance.
- **Playstyle variety:** melee, melee-with-spells, utility, and support.
- **Low repetition:** reduce "I keep getting the same thing".
- **Config-driven:** most content should be addable via YAML (new items, weights, stat ranges).

## Rarity (Simple, Recognizable, Configurable)

Pick a small set of tiers with consistent rules:

| Tier | Color vibe | Drop intent | Stat budget | Extra rules |
|---|---|---:|---:|---|
| Common | White/Gray | very frequent | baseline | no affixes |
| Uncommon | Green | frequent | +5-10% | 1 minor affix |
| Rare | Blue | uncommon | +10-20% | 1 major + 1 minor |
| Epic | Purple | rare | +20-35% | 2 major |
| Legendary | Gold | very rare | +35-55% | signature perk |
| Exotic | Red | ultra rare | custom | unique behavior/identity |

"Stat budget" means: you don't need complicated math at first; just keep a consistent idea of how much better each tier is allowed to be.

## Weapon Families (So Loot Feels Like Builds)

Even if the underlying mechanics start simple (vanilla items), designing around families helps variety.

### Melee

Identity = risk/reward and utility.

- **Knife / Shiv:** fast, low damage, bleed fantasy.
- **Hatchet / Axe:** slower, higher burst, armor cracking fantasy.
- **Machete:** mid speed, reliable, cleave fantasy.
- **Baton:** low damage, stun/knockback fantasy.

Vanilla base items to start with: `WOODEN_SWORD`, `STONE_SWORD`, `IRON_SWORD`, `IRON_AXE`, `TRIDENT`.

### Melee + Spells (Cast From Weapons)

Great option if you want "melee only" but still want range/variety without bows.

Core loop:

- Weapons still have normal melee hits.
- Right-click casts a spell based on upgrades/enchants/mods.
- Spells cost a resource (mana/energy), have cooldowns, and should avoid terrain grief.

Suggested mapping (simple + recognizable):

| Upgrade hook | Spell vibe | Example effect |
|---|---|---|
| `FIRE_ASPECT` | fireball | launch `SmallFireball`, ignite on hit, no block damage |
| `KNOCKBACK` | force push | short-range cone knockback + slow |
| `SWEEPING_EDGE` | shockwave | short-range pierce line + small damage |
| `SMITE` | holy bolt | extra vs undead, brief glow |
| `UNBREAKING` | efficiency | reduce energy cost / durability drain per cast |
| `MENDING` | sustain | energy restore on kill / on-hit chance |

This doesn't need to be hard-coded forever; later it can be configured per weapon template.

## Stats: What To Roll (Keep It Small At First)

Start with a small, consistent stat sheet you can show in lore.

### Core stats (all weapons)

- `Damage` (melee hit scaling)
- `Speed` (attack speed / cadence)
- `Durability` (or Condition)

### Spell stats (melee+spells weapons)

- `Spell power` (damage/effect strength)
- `Cast cooldown`
- `Energy cost`
- `Range`

### Utility stats

- `Heal`
- `Duration`

Later, when you want deeper builds:

- `Crit chance`
- `Armor pen`
- `Bleed / burn / slow chance`
- `Stamina drain`

## Affixes (The #1 "Not The Same Again" Lever)

Make a pool of prefixes/suffixes that modify the same base weapon into different roles:

- **Prefixes (identity):** `Rusty`, `Tuned`, `Military`, `Prototype`, `Bloodied`, `Balanced`, `Heavy`, `Lightweight`
- **Suffixes (specialization):** `of Precision`, `of Haste`, `of Impact`, `of Leeching`, `of Stability`, `of Scavenging`

Rules of thumb:

- Affixes should rarely be strictly better; try to create tradeoffs.
- Keep affixes per rarity tier (see rarity table) so Rare actually feels different than Common.

## Mods + Stat Rerolls (Progression Without Power-Creep)

Two separate systems work well together.

### Weapon mods (add/replace parts)

- Mods are items you loot/craft ("Grip", "Edge", "Core", "Guard").
- A weapon has mod slots based on rarity (e.g. Common 0-1, Rare 2, Legendary 3).
- Each mod changes 1-2 stats (often with a tradeoff), and may unlock/change the spell.

Example mods:

- **Ember Core:** +fire spell power, +ignite chance, +energy cost.
- **Stability Grip:** -cast cooldown, -melee speed.
- **Serrated Edge:** +bleed chance, -durability.
- **Runic Guard:** +block/parry window, -spell range.

### Stat rerolls (re-randomize within a tier)

- Rerolls should not change rarity; they only re-roll stat ranges within that weapon's tier.
- Rerolls use a consumable ("Recalibration Kit") so they're valuable.
- Optional guardrails: limited reroll count per item, or reroll cost scaling.

## Anti-Repetition (So Players Don't Feel Farmed By RNG)

You can mix-and-match these:

- **Category rolls:** roll 1 item from each category (weapon/utility/consumable) instead of 3 from one bucket.
- **Bag without replacement:** build a "bag" list based on weights, shuffle, then draw without replacement until empty.
- **Recent-drop weight cooldown:** reduce an item's weight for a player for the next N chests after they receive it.
- **Pity timer:** if a player opens X chests without a Rare+, increase Rare+ chance until they hit one.
- **Duplicate conversion:** duplicate weapons become "parts" or "scrap" used for mods/rerolls.

## Resource Pack: How Custom Weapon Looks Work (Quickstart)

The usual approach is **CustomModelData**. The server gives a normal vanilla item (like a sword) but sets `CustomModelData` to swap the model/texture client-side via a resource pack.

### Tools

- **Blockbench:** easiest way to make item models.
- Any image editor for textures (16x16 or higher, depending on style).

### High-level steps

1. Pick a base vanilla item to override (example: `IRON_SWORD`).
2. Create a resource pack folder with a `pack.mcmeta`.
3. Add a model for the base item with overrides for your IDs (custom model data numbers).
4. Add your textures and custom models.
5. Give items with matching `CustomModelData` (via plugin, or commands while testing).
6. Distribute the pack to players (server resource pack URL, or manual install).

### Practical conventions (recommended)

- Reserve `CustomModelData` ranges per category, e.g.:
  - 1000-1999: swords
  - 2000-2999: axes
  - 3000-3999: polearms/scythes
  - 9000+: uniques/exotics
- Keep the same base item per family (all swords use `IRON_SWORD`, etc.) so your pack stays manageable.

### Distribution note

Minecraft servers typically require the resource pack to be hosted as a direct download URL, then referenced in `server.properties` via `resource-pack=` (and optionally `resource-pack-sha1=`).

## Example Weapon Concepts (For Content Brainstorming)

These are identity-first concepts - each one should feel different even before deep mechanics exist.

### Melee

- **Rust Shiv (Common):** fast, low damage, cheap.
- **Crowbar (Uncommon):** medium, bonus knockback.
- **Hatchet (Rare):** slow, high burst.
- **Shock Baton (Epic):** low damage, stun fantasy.
- **Warlord's Machete (Legendary):** cleave fantasy, signature perk.

### Melee + Spells

- **Cinderbrand (Rare):** right-click small fireball, high energy cost.
- **Runeblade (Epic):** short shockwave cone, low cooldown.
- **Gravetouch (Legendary):** smite bolt, bonus vs undead, sustain perk.

## Implementation Notes (Paper Plugin)

If you build this, the clean approach is:

- Tag weapons with `PersistentDataContainer` keys (rarity, stats, mods, spell id).
- Listen for right-click in `PlayerInteractEvent`, check held item, enforce cooldown + energy.
- Spawn projectiles/effects (e.g. `SmallFireball`, `Snowball` with metadata, ray-trace), then handle hit in `ProjectileHitEvent` / `EntityDamageByEntityEvent`.
- For safety, disable terrain grief (`yield=0`, non-incendiary) and rate limit casts.

## Next Implementation Steps (If/When You Want To Build This)

Smallest path from "materials only" to "custom weapons + spells":

1. Add an item-template layer in config (example `items.yml`) that defines name/lore/enchants/customModelData/stats/mod slots/spell id.
2. Update loot table entries to reference templates (by id) instead of raw `material` only.
3. In loot generation, create an `ItemStack` from the template, then convert it to `ItemData` via `ItemDataMapper.toItemData(stack)` so stash keeps the metadata.
4. Add a simple cast system (right-click -> spawn projectile/effect) with cooldown + energy cost + no terrain grief.
