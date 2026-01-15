package com.raidextraction.command;

import com.raidextraction.config.ConfigManager;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.editor.MapEditorManager;
import com.raidextraction.integration.RaidLifecycleCoordinator;
import com.raidextraction.integration.WorldManager;
import com.raidextraction.raid.RaidInstance;
import com.raidextraction.raid.RaidManager;
import com.raidextraction.profile.PlayerProfileService;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class RaidAdminCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final RaidManager raidManager;
    private final RaidLifecycleCoordinator raidLifecycleCoordinator;
    private final ConfigManager configManager;
    private final WorldManager worldManager;
    private final MapEditorManager mapEditorManager;
    private final PlayerProfileService profileService;

    public RaidAdminCommand(JavaPlugin plugin, RaidManager raidManager,
            RaidLifecycleCoordinator raidLifecycleCoordinator, ConfigManager configManager, WorldManager worldManager,
            MapEditorManager mapEditorManager, PlayerProfileService profileService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.raidLifecycleCoordinator = Objects.requireNonNull(raidLifecycleCoordinator, "raidLifecycleCoordinator");
        this.configManager = Objects.requireNonNull(configManager, "configManager");
        this.worldManager = Objects.requireNonNull(worldManager, "worldManager");
        this.mapEditorManager = Objects.requireNonNull(mapEditorManager, "mapEditorManager");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(
                    "Usage: /raidadmin <start|stop|cancel|force-extract|edit|exit|save|validate|undo|debugbounds"
                            + "|lootchance|lootpreview|setlobby|givecredits> "
                            + "[raidId|player|amount]");
            return true;
        }
        String action = args[0].toLowerCase();
        return switch (action) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            case "cancel" -> handleCancel(sender, args);
            case "force-extract" -> handleForceExtract(sender, args);
            case "edit" -> handleEdit(sender, args);
            case "exit" -> handleExit(sender, args);
            case "save" -> handleSave(sender, args);
            case "validate" -> handleValidate(sender, args);
            case "undo" -> handleUndo(sender, args);
            case "debugbounds" -> handleDebugBounds(sender, args);
            case "lootchance" -> handleLootChance(sender, args);
            case "lootpreview" -> handleLootPreview(sender, args);
            case "setlobby" -> handleSetLobby(sender, args);
            case "givecredits" -> handleGiveCredits(sender, args);
            default -> {
                if ("join".equals(action)) {
                    sender.sendMessage("Use /raid join <raidId> to queue as a player.");
                    yield true;
                }
                sender.sendMessage(
                        "Unknown subcommand. Use /raidadmin <start|stop|cancel|force-extract|edit|exit|save|validate|undo|debugbounds|lootchance|lootpreview|setlobby|givecredits>.");
                yield true;
            }
        };
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin start <raidId>");
            return true;
        }
        String raidId = args[1];
        if (!raidManager.definitions().containsKey(raidId)) {
            sender.sendMessage("Unknown raid id: " + raidId);
            sender.sendMessage("Available raids: " + String.join(", ", raidManager.definitions().keySet()));
            return true;
        }
        boolean created = raidLifecycleCoordinator.startFromQueue(raidId).isPresent();
        sender.sendMessage(created
                ? "Raid created from queue for " + raidId + "."
                : "Not enough players queued for " + raidId + ".");
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin stop <activeRaidId>");
            return true;
        }
        String raidId = args[1];
        boolean removed = raidLifecycleCoordinator.stopRaid(raidId, "Raid stopped by an admin.");
        if (removed) {
            sender.sendMessage("Ended raid " + raidId + ".");
        } else {
            String ids = raidManager.activeRaids().isEmpty()
                    ? "(none)"
                    : raidManager.activeRaids().stream()
                            .map(activeRaid -> activeRaid.id() + " (" + activeRaid.definition().id() + ")")
                            .toList().toString();
            sender.sendMessage("No active raid with id " + raidId + ". Active raids: " + ids);
        }
        return true;
    }

    private boolean handleCancel(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin cancel <activeRaidId|raidId|active>");
            return true;
        }
        String target = args[1];
        RaidInstance raid = resolveActiveRaid(target, sender);
        if (raid == null) {
            return true;
        }
        boolean removed = raidLifecycleCoordinator.stopRaid(raid.id(), "Raid cancelled by an admin.");
        if (removed) {
            sender.sendMessage("Cancelled raid " + raid.id() + " (" + raid.definition().id()
                    + ") and returned players to the lobby.");
        } else {
            String ids = raidManager.activeRaids().isEmpty()
                    ? "(none)"
                    : raidManager.activeRaids().stream()
                            .map(activeRaid -> activeRaid.id() + " (" + activeRaid.definition().id() + ")")
                            .toList().toString();
            sender.sendMessage("No active raid matching " + target + ". Active raids: " + ids);
        }
        return true;
    }

    private boolean handleForceExtract(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin force-extract <playerName|playerUuid>");
            return true;
        }
        UUID targetId = resolvePlayer(args[1]);
        if (targetId == null) {
            sender.sendMessage("Could not find player '" + args[1] + "'. Provide an online name or UUID.");
            return true;
        }
        boolean success = raidLifecycleCoordinator.forceExtract(targetId);
        sender.sendMessage(success
                ? "Force-extracted player " + args[1] + " and committed their raid inventory to stash."
                : "Player is not currently in an active raid.");
        return true;
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use the map editor.");
            return true;
        }
        if (args.length >= 2 && "exit".equalsIgnoreCase(args[1])) {
            return handleExit(sender, args);
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin edit <raidId>");
            return true;
        }
        String raidId = args[1];

        // Check if raid definition exists
        RaidDefinition raidDefinition = raidManager.definitions().get(raidId);
        if (raidDefinition == null) {
            sender.sendMessage("Unknown raid id: " + raidId);
            sender.sendMessage("Available raids: " + String.join(", ", raidManager.definitions().keySet()));
            return true;
        }

        // Check if raid is currently active
        boolean isActive = raidManager.activeRaids().stream()
                .anyMatch(raid -> raid.definition().id().equals(raidId));
        if (isActive) {
            sender.sendMessage("Cannot edit raid '" + raidId + "' while it is active.");
            return true;
        }

        // Check if already being edited
        if (worldManager.isBeingEdited(raidId)) {
            sender.sendMessage("Raid '" + raidId + "' is already being edited.");
            return true;
        }

        // Get the raid world
        String worldName = raidDefinition.world();
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            sender.sendMessage("§eRaid world '" + worldName + "' is not loaded. Attempting to load...");
            world = plugin.getServer().createWorld(new org.bukkit.WorldCreator(worldName));

            if (world == null) {
                sender.sendMessage(
                        "§cFailed to load raid world '" + worldName + "'. Check if the world folder exists.");
                return true;
            }
        }

        // Enter editor mode
        worldManager.enterEditorMode(raidId, worldName);
        mapEditorManager.enterSession(player, raidId, worldName);

        // Teleport player
        if (raidDefinition.spawn() != null) {
            var s = raidDefinition.spawn();
            player.teleport(new org.bukkit.Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
            sender.sendMessage("§7Teleported to configured raid spawn: " + s.x() + ", " + s.y() + ", " + s.z());
        } else {
            player.teleport(world.getSpawnLocation());
            sender.sendMessage("§7Teleported to world spawn (no raid spawn configured).");
        }

        sender.sendMessage("§aEntered editor mode for raid: §e" + raidId);
        sender.sendMessage("§7World: §f" + worldName);
        sender.sendMessage("§7Use §f/raidadmin exit §7to exit editor mode.");
        sender.sendMessage("§cWarning: Changes to the world will affect all future raids!");

        return true;
    }

    private boolean handleSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can save editor data.");
            return true;
        }
        if (!mapEditorManager.isEditing(player)) {
            sender.sendMessage("You are not currently in editor mode.");
            return true;
        }
        mapEditorManager.save();
        String raidId = mapEditorManager.getEditingRaidId(player);
        sender.sendMessage("Editor data saved for raid " + (raidId != null ? raidId : "(unknown)") + ".");
        return true;
    }

    private boolean handleValidate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin validate <raidId>");
            return true;
        }
        String raidId = args[1];
        if (!raidManager.definitions().containsKey(raidId)) {
            sender.sendMessage("Unknown raid id: " + raidId);
            sender.sendMessage("Available raids: " + String.join(", ", raidManager.definitions().keySet()));
            return true;
        }
        var validation = mapEditorManager.validate(raidId);
        boolean missing = false;
        if (!validation.hasSpawn()) {
            sender.sendMessage("Missing spawn point.");
            missing = true;
        }
        if (validation.evacCount() == 0) {
            sender.sendMessage("Missing evac zones.");
            missing = true;
        }
        if (validation.lootCount() == 0) {
            sender.sendMessage("Missing loot containers.");
            missing = true;
        }
        if (!missing) {
            sender.sendMessage("Editor validation passed for " + raidId + ".");
        }
        return true;
    }

    private boolean handleExit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use the map editor.");
            return true;
        }

        // Find which raid the player is editing (if any)
        String currentWorld = player.getWorld().getName();
        String editingRaidId = mapEditorManager.getEditingRaidId(player);

        if (editingRaidId == null) {
            // Fallback: Check if player is in a world belonging to a raid definition
            // This handles cases where plugin reloaded and lost session state.
            for (RaidDefinition def : raidManager.definitions().values()) {
                if (currentWorld.equalsIgnoreCase(def.world())) {
                    editingRaidId = def.id();
                    break;
                }
            }
        }

        if (editingRaidId == null) {
            sender.sendMessage("You are not currently in editor mode.");
            return true;
        }

        // Exit editor mode
        worldManager.exitEditorMode(editingRaidId);
        mapEditorManager.exitSession(player);

        // Teleport player back to lobby
        player.teleport(plugin.getServer().getWorld(configManager.getLobbyWorld()).getSpawnLocation());

        sender.sendMessage("§aExited editor mode for raid: §e" + editingRaidId);
        sender.sendMessage("§7Returned to lobby.");

        return true;
    }

    private boolean handleUndo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can undo editor actions.");
            return true;
        }
        if (!mapEditorManager.isEditing(player)) {
            sender.sendMessage("You are not currently in editor mode.");
            return true;
        }
        boolean removed = mapEditorManager.undoLast(player);
        if (removed) {
            sender.sendMessage("AaLast loot marker removed.");
        } else {
            sender.sendMessage("AcNo loot markers to undo.");
        }
        return true;
    }

    private boolean handleDebugBounds(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can toggle debug mode.");
            return true;
        }

        boolean enabled = raidLifecycleCoordinator.toggleBoundsDebug(player.getUniqueId());
        if (enabled) {
            sender.sendMessage("§aDebug bounds enabled! Fly around to check bounds.");
        } else {
            sender.sendMessage("§cDebug bounds disabled.");
        }
        return true;
    }

    private boolean handleLootChance(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can set loot chances.");
            return true;
        }
        if (!mapEditorManager.isEditing(player)) {
            sender.sendMessage("You are not currently in editor mode.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /raidadmin lootchance <percent>");
            return true;
        }
        double percent;
        try {
            percent = Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            sender.sendMessage("Invalid percentage: " + args[1]);
            return true;
        }
        double chance = Math.max(0.0, Math.min(1.0, percent / 100.0));
        double stored = mapEditorManager.setDefaultLootChance(player, chance);
        int storedPercent = (int) Math.round(stored * 100.0);

        Block target = player.getTargetBlockExact(5);
        if (target != null) {
            var updated = mapEditorManager.updateLootContainerChance(player, target, stored);
            if (updated != null) {
                sender.sendMessage("Loot chance set to " + storedPercent + "% for container at "
                        + updated.x() + ", " + updated.y() + ", " + updated.z() + ".");
            } else {
                sender.sendMessage("Target block is not a saved loot container.");
            }
        }
        sender.sendMessage("Default loot chance set to " + storedPercent + "% for new markers.");
        return true;
    }

    private boolean handleLootPreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can toggle loot preview.");
            return true;
        }
        MapEditorManager.LootPreviewOutcome outcome = mapEditorManager.toggleLootPreview(player);
        return switch (outcome.status()) {
            case NOT_EDITING -> {
                sender.sendMessage("You are not currently in editor mode.");
                yield true;
            }
            case NOT_IN_EDITOR_WORLD -> {
                sender.sendMessage("Loot preview can only be used in the editor world.");
                yield true;
            }
            case ENABLED -> {
                sender.sendMessage("Loot preview enabled. Placed " + outcome.touchedContainers()
                        + " chest(s).");
                yield true;
            }
            case DISABLED -> {
                sender.sendMessage("Loot preview disabled. Cleared " + outcome.touchedContainers()
                        + " chest(s).");
                yield true;
            }
        };
    }

    private UUID resolvePlayer(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            // fall through to name lookup
        }
        Player player = plugin.getServer().getPlayerExact(input);
        return player != null ? player.getUniqueId() : null;
    }

    private RaidInstance resolveActiveRaid(String token, CommandSender sender) {
        if (token == null || token.isBlank()) {
            sender.sendMessage("Provide a raid id or use 'active'.");
            return null;
        }
        String normalized = token.toLowerCase();
        if ("active".equals(normalized)) {
            var activeRaids = raidManager.activeRaids();
            if (activeRaids.isEmpty()) {
                sender.sendMessage("No active raids to cancel.");
                return null;
            }
            if (activeRaids.size() == 1) {
                return activeRaids.get(0);
            }
            String ids = activeRaids.stream()
                    .map(raid -> raid.id() + " (" + raid.definition().id() + ")")
                    .toList()
                    .toString();
            sender.sendMessage("Multiple active raids found. Specify one of: " + ids);
            return null;
        }

        RaidInstance byInstance = raidManager.getRaid(token).orElse(null);
        if (byInstance != null) {
            return byInstance;
        }

        var byDefinition = raidManager.activeRaids().stream()
                .filter(raid -> raid.definition().id().equalsIgnoreCase(token))
                .toList();
        if (byDefinition.isEmpty()) {
            sender.sendMessage("No active raid matching '" + token + "'.");
            return null;
        }
        if (byDefinition.size() == 1) {
            return byDefinition.get(0);
        }
        String ids = byDefinition.stream()
                .map(raid -> raid.id() + " (" + raid.definition().id() + ")")
                .toList()
                .toString();
        sender.sendMessage("Multiple active raids match '" + token + "'. Specify one of: " + ids);
        return null;
    }

    private boolean handleSetLobby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can set the lobby spawn.");
            return true;
        }
        configManager.setLobbySpawn(player.getLocation());
        sender.sendMessage("§aLobby spawn set to your current location!");
        return true;
    }

    private boolean handleGiveCredits(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /raidadmin givecredits <player> <amount>");
            return true;
        }

        UUID targetId = resolvePlayer(args[1]);
        if (targetId == null) {
            sender.sendMessage("Could not find player '" + args[1] + "'.");
            return true;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Invalid amount: " + args[2]);
            return true;
        }

        var profile = profileService.addCredits(targetId, amount);
        sender.sendMessage(
                String.format("§aGave %d credits to %s. New balance: %d", amount, args[1], profile.credits()));
        return true;
    }
}
