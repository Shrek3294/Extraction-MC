package com.raidextraction.ai;

import com.raidextraction.editor.GuardSpawnEntry;
import com.raidextraction.editor.MapEditorStorage;
import com.raidextraction.raid.RaidInstance;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

public final class SimpleGuardManager {
    private final MapEditorStorage storage;
    private final Logger logger;
    private final Map<String, List<UUID>> raidGuards = new HashMap<>();

    public SimpleGuardManager(JavaPlugin plugin, MapEditorStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.logger = plugin.getLogger();
    }

    public int spawnGuards(RaidInstance raidInstance) {
        String raidId = raidInstance.definition().id();
        List<GuardSpawnEntry> spawns = storage.getGuardSpawns(raidId);
        if (spawns.isEmpty()) {
            return 0;
        }

        World world = Bukkit.getWorld(raidInstance.definition().world());
        if (world == null) {
            return 0;
        }

        List<UUID> guardIds = new ArrayList<>();
        for (GuardSpawnEntry spawn : spawns) {
            Location loc = new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            LivingEntity guard = (LivingEntity) world.spawnEntity(loc, EntityType.ZOMBIE);

            // Basic Guard Polish
            guard.setRemoveWhenFarAway(false);
            
            // Add visible custom names with variety
            String[] guardNames = {
                "Security Guard", "Patrol Officer", "Facility Guard", 
                "Watchman", "Sentinel", "Enforcer"
            };
            String guardName = guardNames[(int)(Math.random() * guardNames.length)];
            guard.customName(net.kyori.adventure.text.Component.text(guardName));
            guard.setCustomNameVisible(true); // Make names visible

            // Item-on-Head trick for custom visuals (V3.1 feature)
            // This item can have CustomModelData set for high-detail models (soldiers,
            // monsters, etc.)
            org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(
                    org.bukkit.Material.CARVED_PUMPKIN);
            org.bukkit.inventory.meta.ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                // Example: We could use CustomModelData 1100 for guards
                // meta.setCustomModelData(1100);
                head.setItemMeta(meta);
            }
            guard.getEquipment().setHelmet(head);
            guard.getEquipment().setHelmetDropChance(0.0f);

            guardIds.add(guard.getUniqueId());
        }

        raidGuards.put(raidInstance.id(), guardIds);
        logger.info("Spawned " + guardIds.size() + " guards for raid " + raidInstance.id());
        return guardIds.size();
    }

    public void cleanupGuards(String raidInstanceId) {
        List<UUID> guardIds = raidGuards.remove(raidInstanceId);
        if (guardIds == null) {
            return;
        }

        int removed = 0;
        for (UUID id : guardIds) {
            var entity = Bukkit.getEntity(id);
            if (entity != null && !entity.isDead()) {
                entity.remove();
                removed++;
            }
        }
        logger.info("Cleaned up " + removed + " guards for raid " + raidInstanceId);
    }
}
