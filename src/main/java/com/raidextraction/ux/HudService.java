package com.raidextraction.ux;

import com.raidextraction.config.ConfigManager;
import com.raidextraction.profile.PlayerProfile;
import com.raidextraction.profile.PlayerProfileService;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class HudService implements Listener {
    private static final String OBJECTIVE_ID = "rexhud";

    private final JavaPlugin plugin;
    private final PlayerProfileService profileService;
    private final boolean enabled;
    private final String serverName;
    private final long updateTicks;
    private final Logger logger;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private BukkitTask updateTask;

    public HudService(JavaPlugin plugin, ConfigManager configManager, PlayerProfileService profileService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configManager, "configManager");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.enabled = configManager.isHudEnabled();
        this.serverName = sanitizeTitle(configManager.getHudServerName());
        this.updateTicks = Math.max(1L, configManager.getHudUpdateTicks());
        this.logger = plugin.getLogger();
    }

    public void start() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10L, updateTicks);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        boards.clear();
        loading.clear();
    }

    public void toggleFor(Player player) {
        Objects.requireNonNull(player, "player");
        if (!enabled) {
            player.sendMessage("HUD is disabled on this server.");
            clearFor(player);
            return;
        }
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile current;
            try {
                current = profileService.loadOrCreate(playerId);
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to load profile for HUD toggle: " + playerId, error);
                return;
            }
            PlayerProfile updated;
            try {
                updated = profileService.setHudEnabled(playerId, !current.hudEnabled());
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to update profile HUD toggle: " + playerId, error);
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (updated.hudEnabled()) {
                    player.sendMessage("HUD enabled.");
                    render(player, updated);
                } else {
                    player.sendMessage("HUD disabled.");
                    clearFor(player);
                }
            });
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        loading.add(playerId);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            PlayerProfile profile;
            try {
                profile = profileService.loadOrCreate(playerId);
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to load profile for HUD: " + playerId, error);
                return;
            } finally {
                loading.remove(playerId);
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    updateFor(player, profile);
                }
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        boards.remove(playerId);
        loading.remove(playerId);
    }

    private void tick() {
        if (!enabled) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                clearFor(player);
            }
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            PlayerProfile cached = profileService.cached(playerId).orElse(null);
            if (cached == null) {
                maybeLoadAsync(playerId);
                continue;
            }
            updateFor(player, cached);
        }
    }

    private void maybeLoadAsync(UUID playerId) {
        if (!loading.add(playerId)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                profileService.loadOrCreate(playerId);
            } catch (Exception error) {
                logger.log(Level.WARNING, "Failed to load profile for HUD refresh: " + playerId, error);
            } finally {
                loading.remove(playerId);
            }
        });
    }

    private void updateFor(Player player, PlayerProfile profile) {
        if (profile.hudEnabled()) {
            render(player, profile);
        } else {
            clearFor(player);
        }
    }

    private void render(Player player, PlayerProfile profile) {
        Scoreboard scoreboard = boards.computeIfAbsent(player.getUniqueId(), ignored -> createBoard());
        Objective objective = scoreboard.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_ID, "dummy", serverName);
            objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);
        } else if (!serverName.equals(objective.getDisplayName())) {
            objective.setDisplayName(serverName);
        }

        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        String creditsLine = ChatColor.GOLD + "Credits: " + ChatColor.WHITE + profile.credits();
        String levelLine = ChatColor.AQUA + "Level: " + ChatColor.WHITE + profile.level();
        objective.getScore(levelLine).setScore(1);
        objective.getScore(creditsLine).setScore(2);

        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    private void clearFor(Player player) {
        UUID playerId = player.getUniqueId();
        Scoreboard existing = boards.remove(playerId);
        if (existing == null) {
            return;
        }
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager != null && player.isOnline()) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private Scoreboard createBoard() {
        ScoreboardManager manager = plugin.getServer().getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is unavailable");
        }
        return manager.getNewScoreboard();
    }

    private String sanitizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Raid Extraction";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 32) {
            return trimmed.substring(0, 32);
        }
        return trimmed;
    }
}

