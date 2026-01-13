package com.raidextraction.ux;

import com.raidextraction.item.ItemsConfig;
import com.raidextraction.item.ModDefinition;
import com.raidextraction.item.WeaponDefinition;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class WeaponGuideBookFactory {
    private final ItemsConfig itemsConfig;

    public WeaponGuideBookFactory(ItemsConfig itemsConfig) {
        this.itemsConfig = Objects.requireNonNull(itemsConfig, "itemsConfig");
    }

    public ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Weapon Guide");
        meta.setAuthor("RaidExtraction");

        meta.addPage(pageIntro());
        meta.addPage(pageRarity());
        meta.addPage(pageCasting());
        meta.addPage(pageUpgrading());
        meta.addPage(pageCoreMods());
        meta.addPage(pageScrolls());

        book.setItemMeta(meta);
        return book;
    }

    private String pageIntro() {
        return color(ChatColor.DARK_AQUA, "Raid Extraction Weapons")
                + "\n\n"
                + "This server uses custom weapons with:\n"
                + "- Rarities\n"
                + "- Mods (socket items)\n"
                + "- Spells (right-click)\n"
                + "- Scrolls (custom enchants)\n\n"
                + color(ChatColor.GRAY, "Tip:") + " Load the server resource pack for 3D weapon models.";
    }

    private String pageRarity() {
        return color(ChatColor.DARK_AQUA, "Rarities")
                + "\n\n"
                + color(ChatColor.GRAY, "Common") + " - starter tier\n"
                + color(ChatColor.GREEN, "Uncommon") + " - small upgrades\n"
                + color(ChatColor.BLUE, "Rare") + " - build-defining\n"
                + color(ChatColor.DARK_PURPLE, "Epic") + " - late-game\n"
                + color(ChatColor.GOLD, "Legendary") + " - endgame\n"
                + color(ChatColor.RED, "Exotic") + " - chase uniques\n\n"
                + "Higher tiers usually have more mod slots and stronger spell kits.";
    }

    private String pageCasting() {
        return color(ChatColor.DARK_AQUA, "Casting Spells")
                + "\n\n"
                + "1) Unlock spell with a CORE mod.\n"
                + "2) Hold weapon in main hand.\n"
                + "3) Right-click to cast.\n\n"
                + color(ChatColor.GRAY, "Interactables:") + " if you're aiming at a chest/door/button, sneak-right-click to cast.\n\n"
                + color(ChatColor.AQUA, "Mana UI:") + " XP bar shows mana when holding a custom weapon.\n"
                + color(ChatColor.AQUA, "Cooldown UI:") + " item cooldown overlay shows when your spell is ready.";
    }

    private String pageUpgrading() {
        return color(ChatColor.DARK_AQUA, "Mods + Bench")
                + "\n\n"
                + "Mods are physical loot items.\n\n"
                + color(ChatColor.AQUA, "Weapon Bench") + "\n"
                + "- /weapon bench\n"
                + "- Place weapon + mod\n"
                + "- Click Apply\n\n"
                + color(ChatColor.AQUA, "Quick Apply") + "\n"
                + "- Weapon in main hand\n"
                + "- Mod in offhand\n"
                + "- /weapon apply\n\n"
                + "CORE mods unlock spells.\n"
                + "Other mods can buff stats later (v2).";
    }

    private String pageCoreMods() {
        Map<String, String> spellToCore = itemsConfig.mods().values().stream()
                .filter(mod -> mod.unlockSpell() != null && !mod.unlockSpell().isBlank())
                .collect(Collectors.toMap(
                        mod -> mod.unlockSpell().toLowerCase(),
                        ModDefinition::id,
                        (a, b) -> a));

        List<String> lines = itemsConfig.weapons().values().stream()
                .filter(weapon -> weapon.spell() != null && weapon.spell().locked() && weapon.spell().id() != null && !weapon.spell().id().isBlank())
                .sorted(Comparator.comparing(WeaponDefinition::id))
                .map(weapon -> {
                    String core = spellToCore.getOrDefault(weapon.spell().id().toLowerCase(), "(unknown)");
                    return "- " + weapon.id() + " -> " + core;
                })
                .toList();

        String body = lines.isEmpty()
                ? "No core mods configured."
                : String.join("\n", lines);

        return color(ChatColor.DARK_AQUA, "Core Mods")
                + "\n"
                + color(ChatColor.GRAY, "(unlock weapon spells)")
                + "\n\n"
                + body;
    }

    private String pageScrolls() {
        return color(ChatColor.DARK_AQUA, "Scrolls (Enchants)")
                + "\n\n"
                + "Scrolls are one-time enchant items.\n"
                + "Apply them like mods (bench or /weapon apply).\n\n"
                + color(ChatColor.AQUA, "Current scrolls:") + "\n"
                + "- Backstab\n"
                + "- Bleed\n"
                + "- Momentum\n"
                + "- Grim Harvest\n\n"
                + color(ChatColor.GRAY, "Note:") + " some enchants only work on certain weapon types.";
    }

    private String color(ChatColor color, String text) {
        return color + text + ChatColor.RESET;
    }
}

