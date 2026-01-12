package com.raidextraction.command;

import com.raidextraction.integration.paper.StashView;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public final class StashCommand implements CommandExecutor {
    private final StashView stashView;

    public StashCommand(StashView stashView) {
        this.stashView = Objects.requireNonNull(stashView, "stashView");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        stashView.open(player);
        return true;
    }
}
