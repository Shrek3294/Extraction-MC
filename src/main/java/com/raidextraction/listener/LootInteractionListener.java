package com.raidextraction.listener;

import com.raidextraction.raid.RaidInstance;
import com.raidextraction.raid.RaidManager;
import com.raidextraction.stash.ItemData;
import com.raidextraction.ux.CrateAnimationService;
import com.raidextraction.ux.CrateAnimationService.CrateHolder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class LootInteractionListener implements Listener {

    private final RaidManager raidManager;
    private final CrateAnimationService animationService;

    private final java.util.logging.Logger logger;

    public LootInteractionListener(RaidManager raidManager, CrateAnimationService animationService,
            java.util.logging.Logger logger) {
        this.raidManager = Objects.requireNonNull(raidManager, "raidManager");
        this.animationService = Objects.requireNonNull(animationService, "animationService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        // Debug Log
        logger.info("Interact at " + block.getX() + "," + block.getY() + "," + block.getZ() + " type=" + block.getType()
                + " useBlock=" + event.useInteractedBlock()
                + " useItem=" + event.useItemInHand());

        Player player = event.getPlayer();
        Optional<RaidInstance> raidOpt = raidManager.getRaidForPlayer(player.getUniqueId());
        if (raidOpt.isEmpty()) {
            logger.info("No raid found for player " + player.getName());
            return;
        }

        RaidInstance raid = raidOpt.get();
        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        if (raid.hasPendingLoot(x, y, z)) {
            logger.info("Found pending loot for " + x + "," + y + "," + z);
            event.setCancelled(true);
            List<ItemData> loot = raid.getPendingLoot(x, y, z);

            // Start animation and define the callback to consume loot upon reveal
            animationService.startAnimation(player, loot, () -> {
                raid.takePendingLoot(x, y, z);
            });
        } else {
            logger.info("No pending loot at " + x + "," + y + "," + z);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CrateHolder holder) {
            if (holder.isAnimating()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CrateHolder holder) {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }

            if (holder.isAnimating()) {
                event.getInventory().clear();
                return;
            }

            // Give all items in the GUI to the player
            for (ItemStack stack : event.getInventory().getContents()) {
                if (stack != null && !stack.getType().isAir()) {
                    var leftovers = player.getInventory().addItem(stack);
                    for (ItemStack leftover : leftovers.values()) {
                        player.getWorld().dropItem(player.getLocation(), leftover);
                    }
                }
            }
            event.getInventory().clear();
        }
    }
}
