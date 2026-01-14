package com.raidextraction.command;

import com.raidextraction.integration.paper.StashView;
import com.raidextraction.raid.RaidManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class StashCommand implements CommandExecutor {
    private final StashView stashView;
    private final RaidManager raidManager;

    public StashCommand(StashView stashView, RaidManager raidManager) {
        this.stashView = Objects.requireNonNull(stashView, "stashView");
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (raidManager.getRaidForPlayer(player.getUniqueId()).isPresent()) {
            player.sendMessage("You cannot access your stash while in a raid.");
            return true;
        }
        stashView.open(player);
        return true;
    }
}
