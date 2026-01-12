package com.raidextraction.integration.paper;

import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.integration.TeleportService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class PaperTeleportService implements TeleportService {
    private final JavaPlugin plugin;

    public PaperTeleportService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean sendToRaid(UUID playerId, RaidDefinition raidDefinition) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(raidDefinition, "raidDefinition");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        World world = plugin.getServer().getWorld(raidDefinition.world());
        if (world == null) {
            plugin.getLogger().warning("Raid world not loaded for " + raidDefinition.id() + ": " + raidDefinition.world());
            return false;
        }
        Location spawn = world.getSpawnLocation();
        // Nudge to block center to reduce spawn suffocation risk.
        Location target = new Location(world, spawn.getBlockX() + 0.5, spawn.getY(), spawn.getBlockZ() + 0.5, spawn.getYaw(), spawn.getPitch());
        player.teleport(target);
        return true;
    }

    @Override
    public boolean sendToLobby(UUID playerId, String lobbyWorld) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lobbyWorld, "lobbyWorld");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        World world = plugin.getServer().getWorld(lobbyWorld);
        if (world == null) {
            plugin.getLogger().warning("Lobby world not loaded: " + lobbyWorld);
            return false;
        }
        Location spawn = world.getSpawnLocation();
        Location target = new Location(world, spawn.getBlockX() + 0.5, spawn.getY(), spawn.getBlockZ() + 0.5, spawn.getYaw(), spawn.getPitch());
        player.teleport(target);
        return true;
    }
}
