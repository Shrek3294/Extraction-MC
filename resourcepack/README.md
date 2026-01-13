# Resource Pack (Vanilla CustomModelData)

This repo includes a resource pack in `Extraction-MC/resourcepack` that has been wired for **vanilla clients** using `CustomModelData` overrides.

## What Changed

- Weapon models were copied from OptiFine CIT JSONs into vanilla model locations:
  - From: `Extraction-MC/resourcepack/assets/minecraft/optifine/cit/*.json`
  - To: `Extraction-MC/resourcepack/assets/minecraft/models/item/*.json`
- Vanilla override files were added:
  - `Extraction-MC/resourcepack/assets/minecraft/models/item/iron_sword.json`
  - `Extraction-MC/resourcepack/assets/minecraft/models/item/iron_axe.json`
  - `Extraction-MC/resourcepack/assets/minecraft/models/item/netherite_sword.json`

## CustomModelData Mapping (Matches `items.yml`)

**IRON_SWORD**
- `1001` -> `iron_dagger`
- `1002` -> `iron_broadsword`
- `1004` -> `crystal_frostblade`
- `1005` -> `dragon_sword`
- `1006` -> `piercer`
- `1007` -> `nocturne`

**IRON_AXE**
- `1003` -> `skeleton_axe`

**NETHERITE_SWORD**
- `1008` -> `phantomguard_greatsword`
- `1009` -> `heavenly_partisan`
- `1010` -> `solstice`
- `1011` -> `revenants_gravescepter`
- `1012` -> `zenith`

## How The Plugin Ties In

The plugin sets `ItemMeta#setCustomModelData(...)` using the values in `Extraction-MC/src/main/resources/items.yml`.

Quick check in-game (admin):

- Give a weapon: `/weapon give iron_dagger`
- If the resource pack is loaded, the held item shows the 3D model.

## Using As A Server Resource Pack

Minecraft clients only download resource packs from a **URL**, so you need to:

1. Build a zip:
   - Run `Extraction-MC/scripts/build-resourcepack.ps1`
   - It outputs a `.zip` path and a `SHA1` hash.
2. Host the zip somewhere that serves a direct download URL.
3. Set these in your server `server.properties`:
   - `resource-pack=<your url>`
   - `resource-pack-sha1=<sha1 from script>`
   - `require-resource-pack=true` (optional, forces it)

Important: do **not** use GitHub’s auto-generated “Download ZIP” for a folder/repo (it wraps everything in a parent directory). You must host the exact zip produced by the script (its root contains `pack.mcmeta` and `assets/`).

Local testing option: host the zip yourself (LAN) with a simple web server and use that URL.
