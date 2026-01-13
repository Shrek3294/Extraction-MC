package com.raidextraction.command;

import com.raidextraction.item.CustomItemFactory;
import com.raidextraction.item.CustomItemRegistry;
import com.raidextraction.item.WeaponUpgradeService;
import com.raidextraction.ux.WeaponBenchView;
import com.raidextraction.ux.WeaponGuideBookFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class WeaponCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final CustomItemRegistry registry;
    private final CustomItemFactory factory;
    private final WeaponUpgradeService upgradeService;
    private final WeaponBenchView benchView;
    private final WeaponGuideBookFactory guideBookFactory;
    private final NamespacedKey debugSpellsKey;

    public WeaponCommand(JavaPlugin plugin, CustomItemRegistry registry, CustomItemFactory factory,
            WeaponUpgradeService upgradeService, WeaponBenchView benchView, WeaponGuideBookFactory guideBookFactory) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.factory = Objects.requireNonNull(factory, "factory");
        this.upgradeService = Objects.requireNonNull(upgradeService, "upgradeService");
        this.benchView = Objects.requireNonNull(benchView, "benchView");
        this.guideBookFactory = Objects.requireNonNull(guideBookFactory, "guideBookFactory");
        this.debugSpellsKey = new NamespacedKey(plugin, "debug_spells");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /weapon <give|apply|bench>");
            return true;
        }
        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "guide" -> handleGuide(sender);
            case "give" -> handleGive(sender, args);
            case "apply" -> handleApply(sender);
            case "bench" -> handleBench(sender);
            case "debugspells" -> handleDebugSpells(sender, args);
            default -> {
                sender.sendMessage("Usage: /weapon <guide|bench|apply|give|debugspells>");
                yield true;
            }
        };
    }

    private boolean handleGuide(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player-only.");
            return true;
        }
        ItemStack book = guideBookFactory.createGuideBook();
        player.getInventory().addItem(book);
        player.sendMessage("Weapon guide added to your inventory.");
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raid.admin")) {
            sender.sendMessage("You don't have permission to use /weapon give.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /weapon give <itemId> [amount] OR /weapon give <player> <itemId> [amount]");
            return true;
        }

        Player target = null;
        String itemId;
        int amount = 1;

        if (args.length >= 3) {
            Player maybeTarget = sender.getServer().getPlayerExact(args[1]);
            if (maybeTarget != null) {
                target = maybeTarget;
                itemId = args[2];
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException ignored) {
                        amount = 1;
                    }
                }
            } else {
                itemId = args[1];
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }
        } else {
            itemId = args[1];
        }

        if (target == null) {
            if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage("Console usage: /weapon give <player> <itemId> [amount]");
                return true;
            }
        }

        if (registry.getWeapon(itemId).isPresent()) {
            ItemStack weapon = factory.createWeapon(registry.getWeapon(itemId).get());
            target.getInventory().addItem(weapon);
            sender.sendMessage("Gave weapon " + itemId + " to " + target.getName() + ".");
            return true;
        }
        if (registry.getMod(itemId).isPresent()) {
            ItemStack mod = factory.createMod(registry.getMod(itemId).get(), amount);
            target.getInventory().addItem(mod);
            sender.sendMessage("Gave mod " + itemId + " x" + amount + " to " + target.getName() + ".");
            return true;
        }
        sender.sendMessage("Unknown item id: " + itemId);
        sender.sendMessage("If another plugin owns /weapon, try /raidextraction:weapon or /rexweapon.");
        return true;
    }

    private boolean handleApply(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player-only.");
            return true;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        ItemStack mod = player.getInventory().getItemInOffHand();
        var result = upgradeService.applyMod(player, weapon, mod);
        if (!result.ok()) {
            player.sendMessage("Upgrade failed: " + result.message() + " (hold weapon in main hand, mod/scroll in offhand)");
        }
        return true;
    }

    private boolean handleBench(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player-only.");
            return true;
        }
        benchView.open(player);
        return true;
    }

    private boolean handleDebugSpells(CommandSender sender, String[] args) {
        if (!sender.hasPermission("raid.admin")) {
            sender.sendMessage("You don't have permission to use /weapon debugspells.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /weapon debugspells <on|off|toggle|status> OR /weapon debugspells <player> <on|off|toggle|status>");
            return true;
        }

        Player target;
        String mode;

        if (args.length >= 3) {
            Player maybeTarget = plugin.getServer().getPlayerExact(args[1]);
            if (maybeTarget != null) {
                target = maybeTarget;
                mode = args[2].toLowerCase();
            } else {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Console usage: /weapon debugspells <player> <on|off|toggle|status>");
                    return true;
                }
                target = player;
                mode = args[1].toLowerCase();
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console usage: /weapon debugspells <player> <on|off|toggle|status>");
                return true;
            }
            target = player;
            mode = args[1].toLowerCase();
        }

        boolean current = isDebugSpells(target);
        boolean next = switch (mode) {
            case "on" -> true;
            case "off" -> false;
            case "toggle" -> !current;
            case "status" -> current;
            default -> {
                sender.sendMessage("Mode must be on|off|toggle|status.");
                yield current;
            }
        };

        if (!"status".equals(mode)) {
            target.getPersistentDataContainer().set(debugSpellsKey, PersistentDataType.BYTE, (byte) (next ? 1 : 0));
        }

        sender.sendMessage("Spell debug for " + target.getName() + ": " + (next ? "ON" : "OFF"));
        return true;
    }

    private boolean isDebugSpells(Player player) {
        Byte value = player.getPersistentDataContainer().get(debugSpellsKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }
}
