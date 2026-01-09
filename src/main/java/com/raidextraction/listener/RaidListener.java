package com.raidextraction.listener;

import com.raidextraction.raid.RaidManager;
import org.bukkit.event.Listener;

import java.util.Objects;

public final class RaidListener implements Listener {
    private final RaidManager raidManager;

    public RaidListener(RaidManager raidManager) {
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
    }
}
