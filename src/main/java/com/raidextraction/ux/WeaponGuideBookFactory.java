package com.raidextraction.ux;

import com.raidextraction.item.ItemsConfig;
import com.raidextraction.item.ModDefinition;
import com.raidextraction.item.WeaponDefinition;
import com.raidextraction.util.TextComponents;
import net.kyori.adventure.text.Component;
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
        ItemStack book = ItemStack.of(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("Weapon Guide");
        meta.setAuthor("RaidExtraction");

        meta.addPages(
                pageIntro(),
                pageRarity(),
                pageCasting(),
                pageUpgrading(),
                pageCoreMods(),
                pageScrolls());

        book.setItemMeta(meta);
        return book;
    }

    private Component pageIntro() {
        return TextComponents.legacy(
                "&3Raid Extraction Weapons&r\n\n"
                        + "This server uses custom weapons with:\n"
                        + "- Rarities\n"
                        + "- Mods (socket items)\n"
                        + "- Spells (right-click)\n"
                        + "- Scrolls (custom enchants)\n\n"
                        + "&7Tip:&r Load the server resource pack for 3D weapon models.");
    }

    private Component pageRarity() {
        return TextComponents.legacy(
                "&3Rarities&r\n\n"
                        + "&7Common&r - starter tier\n"
                        + "&aUncommon&r - small upgrades\n"
                        + "&9Rare&r - build-defining\n"
                        + "&5Epic&r - late-game\n"
                        + "&6Legendary&r - endgame\n"
                        + "&cExotic&r - chase uniques\n\n"
                        + "Higher tiers usually have more mod slots and stronger spell kits.");
    }

    private Component pageCasting() {
        return TextComponents.legacy(
                "&3Casting Spells&r\n\n"
                        + "1) Unlock spell with a CORE mod.\n"
                        + "2) Hold weapon in main hand.\n"
                        + "3) Right-click to cast.\n\n"
                        + "&7Interactables:&r if you're aiming at a chest/door/button, sneak-right-click to cast.\n\n"
                        + "&bMana UI:&r XP bar shows mana when holding a custom weapon.\n"
                        + "&bCooldown UI:&r item cooldown overlay shows when your spell is ready.");
    }

    private Component pageUpgrading() {
        return TextComponents.legacy(
                "&3Mods + Bench&r\n\n"
                        + "Mods are physical loot items.\n\n"
                        + "&bWeapon Bench&r\n"
                        + "- /weapon bench\n"
                        + "- Place weapon + mod\n"
                        + "- Click Apply\n\n"
                        + "&bQuick Apply&r\n"
                        + "- Weapon in main hand\n"
                        + "- Mod in offhand\n"
                        + "- /weapon apply\n\n"
                        + "CORE mods unlock spells.\n"
                        + "Other mods can buff stats later (v2).");
    }

    private Component pageCoreMods() {
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

        return TextComponents.legacy("&3Core Mods&r\n&7(unlock weapon spells)&r\n\n" + body);
    }

    private Component pageScrolls() {
        return TextComponents.legacy(
                "&3Scrolls (Enchants)&r\n\n"
                        + "Scrolls are one-time enchant items.\n"
                        + "Apply them like mods (bench or /weapon apply).\n\n"
                        + "&bCurrent scrolls:&r\n"
                        + "- Backstab\n"
                        + "- Bleed\n"
                        + "- Momentum\n"
                        + "- Grim Harvest\n\n"
                        + "&7Note:&r some enchants only work on certain weapon types.");
    }
}
