package com.raidextraction.integration.paper;

import com.raidextraction.config.model.LobbySpawnConfig;
import com.raidextraction.config.model.RaidDefinition;
import com.raidextraction.editor.MapEditorStorage;
import com.raidextraction.editor.SpawnPointEntry;
import com.raidextraction.integration.TeleportService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;

public final class PaperTeleportService implements TeleportService {
    private final JavaPlugin plugin;
    private final MapEditorStorage mapEditorStorage;

    public PaperTeleportService(JavaPlugin plugin, MapEditorStorage mapEditorStorage) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.mapEditorStorage = Objects.requireNonNull(mapEditorStorage, "mapEditorStorage");
    }

    @Override
    public boolean sendToRaid(UUID playerId, RaidDefinition raidDefinition) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(raidDefinition, "raidDefinition");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }

        SpawnPointEntry editorSpawn = mapEditorStorage.getSpawnPoint(raidDefinition.id());
        if (editorSpawn != null) {
            World editorWorld = ensureWorldLoaded(editorSpawn.world());
            if (editorWorld == null) {
                plugin.getLogger().warning(
                        "Editor spawn world not loaded for " + raidDefinition.id() + ": " + editorSpawn.world());
                return false;
            }
            Location target = new Location(editorWorld, editorSpawn.x(), editorSpawn.y(), editorSpawn.z(),
                    editorSpawn.yaw(), editorSpawn.pitch());
            player.teleport(target);
            return true;
        }

        World world = ensureWorldLoaded(raidDefinition.world());
        if (world == null) {
            plugin.getLogger()
                    .warning("Raid world not loaded for " + raidDefinition.id() + ": " + raidDefinition.world());
            return false;
        }
        if (raidDefinition.spawn() != null) {
            // Use configured raid spawn
            var s = raidDefinition.spawn();
            Location target = new Location(world, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
            player.teleport(target);
            return true;
        }

        Location spawn = world.getSpawnLocation();
        // Nudge to block center to reduce spawn suffocation risk.
        Location target = new Location(world, spawn.getBlockX() + 0.5, spawn.getY(), spawn.getBlockZ() + 0.5,
                spawn.getYaw(), spawn.getPitch());
        player.teleport(target);
        return true;
    }

    @Override
    public boolean sendToLobby(UUID playerId, LobbySpawnConfig lobbySpawnConfig) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lobbySpawnConfig, "lobbySpawnConfig");
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        World world = ensureWorldLoaded(lobbySpawnConfig.world());
        if (world == null) {
            plugin.getLogger().warning("Lobby world not loaded: " + lobbySpawnConfig.world());
            return false;
        }
        // Use configured lobby spawn coordinates
        Location target = new Location(
                world,
                lobbySpawnConfig.x(),
                lobbySpawnConfig.y(),
                lobbySpawnConfig.z(),
                lobbySpawnConfig.yaw(),
                lobbySpawnConfig.pitch());
        player.teleport(target);
        return true;
    }

    private World ensureWorldLoaded(String worldName) {
        World world = plugin.getServer().getWorld(worldName);
        if (world != null) {
            return world;
        }
        plugin.getLogger().info("Attempting to load world: " + worldName);
        return plugin.getServer().createWorld(new org.bukkit.WorldCreator(worldName));
    }
}
