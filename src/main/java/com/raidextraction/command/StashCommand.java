package com.raidextraction.command;

import com.raidextraction.stash.ItemData;
import com.raidextraction.stash.StashService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StashCommand implements CommandExecutor {
    private final StashService stashService;

    public StashCommand(StashService stashService) {
        this.stashService = Objects.requireNonNull(stashService, "stashService");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        UUID ownerId = player.getUniqueId();
        List<ItemData> items = stashService.load(ownerId);
        sender.sendMessage("Your stash contains " + items.size() + " item stack(s).");
        return true;
    }
}
