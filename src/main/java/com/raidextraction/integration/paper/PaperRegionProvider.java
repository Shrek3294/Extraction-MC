package com.raidextraction.integration.paper;

import com.raidextraction.config.model.EvacZoneDefinition;
import com.raidextraction.config.model.RaidBoundsDefinition;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.integration.RegionProvider;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class PaperRegionProvider implements RegionProvider {
    private final JavaPlugin plugin;

    public PaperRegionProvider(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean isInRaidWorld(UUID playerId, RaidDefinition raidDefinition) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(raidDefinition, "raidDefinition");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        return player.getWorld().getName().equalsIgnoreCase(raidDefinition.world());
    }

    @Override
    public boolean isInEvacZone(UUID playerId, EvacZoneDefinition evacZoneDefinition) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(evacZoneDefinition, "evacZoneDefinition");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(evacZoneDefinition.world())) {
            return false;
        }
        Location location = player.getLocation();
        double radius = evacZoneDefinition.radius();
        double dx = Math.abs(location.getX() - evacZoneDefinition.x());
        double dy = Math.abs(location.getY() - evacZoneDefinition.y());
        double dz = Math.abs(location.getZ() - evacZoneDefinition.z());
        if (dx > radius || dy > radius || dz > radius) {
            return false;
        }
        int surfaceY = player.getWorld().getHighestBlockYAt(location.getBlockX(), location.getBlockZ());
        return location.getY() >= surfaceY - 1;
    }

    @Override
    public boolean isInRaidBounds(UUID playerId, RaidBoundsDefinition raidBoundsDefinition) {
        if (raidBoundsDefinition == null) {
            return true; // No bounds configured, always in bounds
        }
        Objects.requireNonNull(playerId, "playerId");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(raidBoundsDefinition.world())) {
            return false;
        }
        Location location = player.getLocation();
        return raidBoundsDefinition.contains(location.getX(), location.getY(), location.getZ());
    }
}
