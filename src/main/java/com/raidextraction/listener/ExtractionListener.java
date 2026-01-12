package com.raidextraction.listener;

import com.raidextraction.integration.RaidLifecycleCoordinator;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Objects;

public final class ExtractionListener implements Listener {
    private final RaidLifecycleCoordinator raidLifecycleCoordinator;

    public ExtractionListener(RaidLifecycleCoordinator raidLifecycleCoordinator) {
        this.raidLifecycleCoordinator = Objects.requireNonNull(raidLifecycleCoordinator, "raidLifecycleCoordinator");
    }

    @EventHandler
    public void handlePlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        raidLifecycleCoordinator.handlePlayerMove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void handlePlayerDeath(PlayerDeathEvent event) {
        raidLifecycleCoordinator.handlePlayerDeath(event.getEntity().getUniqueId());
    }
}
