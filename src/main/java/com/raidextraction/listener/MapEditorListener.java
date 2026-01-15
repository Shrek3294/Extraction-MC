package com.raidextraction.listener;

import com.raidextraction.editor.LootContainerEntry;
import com.raidextraction.editor.MapEditorManager;
import com.raidextraction.util.TextComponents;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class MapEditorListener implements Listener {
    private final MapEditorManager mapEditorManager;

    public MapEditorListener(MapEditorManager mapEditorManager) {
        this.mapEditorManager = mapEditorManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLootMarkerUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!event.getPlayer().isSneaking()) {
            return;
        }
        ItemStack item = event.getItem();
        if (!mapEditorManager.isLootMarker(item)) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        LootContainerEntry entry = mapEditorManager.markLootContainer(
                event.getPlayer(),
                clicked,
                event.getBlockFace());
        if (entry == null) {
            event.getPlayer().sendMessage(TextComponents.legacy("&cYou are not in editor mode."));
            return;
        }
        event.setCancelled(true);
        int chancePercent = (int) Math.round(entry.chance() * 100.0);
        event.getPlayer().sendMessage(TextComponents.legacy("&aLoot marker saved at "
                + entry.x() + ", " + entry.y() + ", " + entry.z() + " ("
                + entry.facing() + ", " + chancePercent + "%)."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnToolUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.LEFT_CLICK_AIR) {
            return;
        }
        ItemStack item = event.getItem();
        if (!mapEditorManager.isSpawnTool(item)) {
            return;
        }
        if (!mapEditorManager.isEditing(event.getPlayer())) {
            event.getPlayer().sendMessage(TextComponents.legacy("&cYou are not in editor mode."));
            return;
        }
        var entry = mapEditorManager.setSpawnPoint(event.getPlayer());
        if (entry == null) {
            event.getPlayer().sendMessage(TextComponents.legacy("&cSpawn can only be set in the editor world."));
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(TextComponents.legacy("&aSpawn set at "
                + String.format("%.2f", entry.x()) + ", "
                + String.format("%.2f", entry.y()) + ", "
                + String.format("%.2f", entry.z()) + "."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEvacToolUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!mapEditorManager.isEvacTool(item)) {
            return;
        }
        if (!mapEditorManager.isEditing(event.getPlayer())) {
            event.getPlayer().sendMessage(TextComponents.legacy("&cYou are not in editor mode."));
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            boolean set = mapEditorManager.setEvacPos1(event.getPlayer(), clicked);
            if (set) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(TextComponents.legacy("&eEvac corner 1 set at "
                        + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ() + "."));
            } else {
                event.getPlayer().sendMessage(TextComponents.legacy("&cEvac corners must be set in the editor world."));
            }
            return;
        }

        MapEditorManager.EvacSelectionOutcome outcome = mapEditorManager.setEvacPos2(event.getPlayer(), clicked);
        switch (outcome.status()) {
            case NO_POS1 -> event.getPlayer().sendMessage(TextComponents.legacy("&cSet evac corner 1 first."));
            case WORLD_MISMATCH -> event.getPlayer().sendMessage(TextComponents.legacy("&cEvac corners must be in the same world."));
            case NOT_IN_EDITOR_WORLD -> event.getPlayer().sendMessage(TextComponents.legacy("&cEvac corners must be set in the editor world."));
            case NOT_EDITING -> event.getPlayer().sendMessage(TextComponents.legacy("&cYou are not in editor mode."));
            case SAVED -> {
                event.setCancelled(true);
                var entry = outcome.entry();
                event.getPlayer().sendMessage(TextComponents.legacy("&aEvac zone " + entry.name() + " saved ("
                        + entry.minX() + ", " + entry.minY() + ", " + entry.minZ() + ") to ("
                        + entry.maxX() + ", " + entry.maxY() + ", " + entry.maxZ() + ")."));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEditorToolSwap(PlayerItemHeldEvent event) {
        if (!mapEditorManager.isEditing(event.getPlayer())) {
            return;
        }
        ItemStack next = event.getPlayer().getInventory().getItem(event.getNewSlot());
        mapEditorManager.sendToolActionBar(event.getPlayer(), next);
    }

}
