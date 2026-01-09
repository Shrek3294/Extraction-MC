package com.raidextraction.command;

import com.raidextraction.extraction.ExtractionService;
import com.raidextraction.raid.RaidManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

public final class RaidAdminCommand implements CommandExecutor {
    private final RaidManager raidManager;
    private final ExtractionService extractionService;

    public RaidAdminCommand(RaidManager raidManager, ExtractionService extractionService) {
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.extractionService = Objects.requireNonNull(extractionService, "extractionService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /raidadmin <start|stop|force-extract> [raidId]");
            return true;
        }
        String action = args[0].toLowerCase();
        return switch (action) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            case "force-extract" -> handleForceExtract(sender, args);
            default -> {
                sender.sendMessage("Unknown subcommand. Use /raidadmin <start|stop|force-extract>.");
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
            return true;
        }
        boolean created = raidManager.tryCreateFromQueue(raidId).isPresent();
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
        boolean removed = raidManager.endRaid(raidId);
        if (removed) {
            extractionService.clearRaid(raidId);
        }
        sender.sendMessage(removed ? "Ended raid " + raidId + "." : "No active raid with id " + raidId + ".");
        return true;
    }

    private boolean handleForceExtract(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /raidadmin force-extract <activeRaidId> <playerUuid>");
            return true;
        }
        sender.sendMessage("Force extract is a stub; integrate with player lookups and stash handling.");
        return true;
    }
}
