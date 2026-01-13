package com.raidextraction.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public record ItemsConfig(
        Map<String, WeaponDefinition> weapons,
        Map<String, ModDefinition> mods,
        Map<String, EnchantDefinition> enchants,
        Map<String, SpellDefinition> spells,
        int manaMax,
        int manaRegenPerSecond
) {
    public static ItemsConfig empty() {
        return new ItemsConfig(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), 100, 0);
    }

    public static ItemsConfig parse(FileConfiguration config, Logger logger) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(logger, "logger");

        ConfigurationSection root = config.getConfigurationSection("items");
        if (root == null) {
            logger.warning("items.yml missing 'items' section; no custom items loaded.");
            return empty();
        }

        Map<String, WeaponDefinition> weapons = parseWeapons(root.getConfigurationSection("weapons"), logger);
        Map<String, ModDefinition> mods = parseMods(root.getConfigurationSection("mods"), logger);
        Map<String, EnchantDefinition> enchants = parseEnchants(config.getConfigurationSection("enchants"), logger);
        Map<String, SpellDefinition> spells = parseSpells(config.getConfigurationSection("spells"), logger);
        int manaMax = Math.max(0, config.getInt("mana.max", 100));
        int manaRegen = Math.max(0, config.getInt("mana.regen_per_second", 0));
        return new ItemsConfig(
                Collections.unmodifiableMap(weapons),
                Collections.unmodifiableMap(mods),
                Collections.unmodifiableMap(enchants),
                Collections.unmodifiableMap(spells),
                manaMax,
                manaRegen);
    }

    private static Map<String, WeaponDefinition> parseWeapons(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, WeaponDefinition> weapons = new HashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection weapon = section.getConfigurationSection(id);
            if (weapon == null) {
                continue;
            }
            Material material = Material.matchMaterial(weapon.getString("material", ""));
            if (material == null) {
                logger.warning("items.yml weapon " + id + " has invalid material.");
                continue;
            }
            Rarity rarity = Rarity.parse(weapon.getString("rarity", "COMMON"), Rarity.COMMON);
            String name = weapon.getString("name", id);
            int modelData = weapon.getInt("custom_model_data", 0);
            WeaponType weaponType = WeaponType.parse(weapon.getString("weapon_type", "SWORD"), WeaponType.SWORD);
            int modSlots = weapon.getInt("mod_slots", 0);

            ConfigurationSection spell = weapon.getConfigurationSection("spell");
            String spellId = spell != null ? spell.getString("id", "") : "";
            boolean locked = spell != null && spell.getBoolean("locked", true);
            if (spellId == null || spellId.isBlank()) {
                spellId = "";
                locked = false;
            }
            WeaponDefinition definition = new WeaponDefinition(
                    id,
                    material,
                    rarity,
                    name,
                    modelData,
                    weaponType,
                    modSlots,
                    new WeaponDefinition.SpellSpec(spellId, locked));
            weapons.put(id, definition);
        }
        return weapons;
    }

    private static Map<String, ModDefinition> parseMods(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, ModDefinition> mods = new HashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection mod = section.getConfigurationSection(id);
            if (mod == null) {
                continue;
            }
            Material material = Material.matchMaterial(mod.getString("material", ""));
            if (material == null) {
                logger.warning("items.yml mod " + id + " has invalid material.");
                continue;
            }
            Rarity rarity = Rarity.parse(mod.getString("rarity", "COMMON"), Rarity.COMMON);
            String name = mod.getString("name", id);
            int modelData = mod.getInt("custom_model_data", 0);
            ModCategory category = ModCategory.parse(mod.getString("category", "UTILITY"), ModCategory.UTILITY);
            String unlockSpell = mod.getString("unlock_spell", "");
            String enchantId = mod.getString("enchant_id", "");
            int enchantLevel = mod.getInt("enchant_level", 1);
            ModDefinition definition = new ModDefinition(id, material, rarity, name, modelData, category, unlockSpell, enchantId, enchantLevel);
            mods.put(id, definition);
        }
        return mods;
    }

    private static Map<String, EnchantDefinition> parseEnchants(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, EnchantDefinition> enchants = new HashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection enchant = section.getConfigurationSection(id);
            if (enchant == null) {
                continue;
            }
            String name = enchant.getString("name", id);
            int maxLevel = Math.max(1, enchant.getInt("max_level", 1));
            var rawTypes = enchant.getStringList("allowed_weapon_types");
            var allowed = rawTypes.stream()
                    .map(type -> WeaponType.parse(type, null))
                    .filter(Objects::nonNull)
                    .toList();
            enchants.put(id, new EnchantDefinition(id, name, maxLevel, allowed));
        }
        return enchants;
    }

    private static Map<String, SpellDefinition> parseSpells(ConfigurationSection section, Logger logger) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, SpellDefinition> spells = new HashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection spell = section.getConfigurationSection(id);
            if (spell == null) {
                continue;
            }
            long cooldownMs = Math.max(0, spell.getLong("cooldown_ms", 0));
            int manaCost = Math.max(0, spell.getInt("mana_cost", 0));
            spells.put(id, new SpellDefinition(id, cooldownMs, manaCost));
        }
        return spells;
    }
}
