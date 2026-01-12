package com.raidextraction.listener;

import com.raidextraction.integration.RaidLifecycleCoordinator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class RaidListener implements Listener {
    private final RaidLifecycleCoordinator raidLifecycleCoordinator;

    public RaidListener(RaidLifecycleCoordinator raidLifecycleCoordinator) {
        this.raidLifecycleCoordinator = Objects.requireNonNull(raidLifecycleCoordinator, "raidLifecycleCoordinator");
    }

    @EventHandler
    public void handlePlayerQuit(PlayerQuitEvent event) {
        raidLifecycleCoordinator.handlePlayerQuit(event.getPlayer().getUniqueId());
    }
}
