package com.raidextraction;

import com.raidextraction.config.ConfigManager;
import com.raidextraction.command.RaidAdminCommand;
import com.raidextraction.command.RaidCommand;
import com.raidextraction.command.StashCommand;
import com.raidextraction.extraction.EvacTracker;
import com.raidextraction.extraction.ExtractionService;
import com.raidextraction.loot.LootService;
import com.raidextraction.loot.LootTableRegistry;
import com.raidextraction.integration.ItemDataMapper;
import com.raidextraction.integration.InventorySnapshotService;
import com.raidextraction.integration.RaidLifecycleCoordinator;
import com.raidextraction.integration.RegionProvider;
import com.raidextraction.integration.TeleportService;
import com.raidextraction.integration.paper.PaperInventorySnapshotService;
import com.raidextraction.integration.paper.PaperRegionProvider;
import com.raidextraction.integration.paper.StashView;
import com.raidextraction.integration.paper.PaperTeleportService;
import com.raidextraction.raid.QueueManager;
import com.raidextraction.raid.RaidManager;
import com.raidextraction.stash.SQLiteStashRepository;
import com.raidextraction.stash.StashService;
import com.raidextraction.listener.ExtractionListener;
import com.raidextraction.listener.RaidListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Clock;
import java.util.Random;

/**
 * Base plugin entrypoint for the Raid Extraction Paper plugin.
 */
public final class RaidExtractionPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private QueueManager queueManager;
    private RaidManager raidManager;
    private LootTableRegistry lootTableRegistry;
    private LootService lootService;
    private StashService stashService;
    private ItemDataMapper itemDataMapper;
    private ExtractionService extractionService;
    private InventorySnapshotService inventorySnapshotService;
    private TeleportService teleportService;
    private RegionProvider regionProvider;
    private StashView stashView;
    private RaidLifecycleCoordinator raidLifecycleCoordinator;

    @Override
    public void onLoad() {
        getLogger().info("RaidExtraction plugin loading (preparing data folder and config scaffolding).");
        prepareDataFolder();
    }

    @Override
    public void onEnable() {
        getLogger().info("RaidExtraction plugin enabling (commands and listeners will register in later phases).");
        prepareDataFolder();
        initializeConfigurationScaffolding();
        initializeServices();
        registerCommands();
        registerListeners();
        raidLifecycleCoordinator.rehydrateState();
    }

    @Override
    public void onDisable() {
        getLogger().info("RaidExtraction plugin disabling. Cleaning up resources.");
        if (raidLifecycleCoordinator != null) {
            raidLifecycleCoordinator.persistState();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RaidManager getRaidManager() {
        return raidManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public LootService getLootService() {
        return lootService;
    }

    public StashService getStashService() {
        return stashService;
    }

    public ExtractionService getExtractionService() {
        return extractionService;
    }

    public InventorySnapshotService getInventorySnapshotService() {
        return inventorySnapshotService;
    }

    public TeleportService getTeleportService() {
        return teleportService;
    }

    public RegionProvider getRegionProvider() {
        return regionProvider;
    }

    public RaidLifecycleCoordinator getRaidLifecycleCoordinator() {
        return raidLifecycleCoordinator;
    }

    private void prepareDataFolder() {
        if (!getDataFolder().exists() && getDataFolder().mkdirs()) {
            getLogger().info("Created plugin data folder at " + getDataFolder().getAbsolutePath());
        }
    }

    private void initializeConfigurationScaffolding() {
        configManager = new ConfigManager(this);
        configManager.load();

        if (configManager.getRaidDefinitions().isEmpty()) {
            getLogger().warning("No raid definitions found; add entries to raids.yml to enable raid creation.");
        }

        if (configManager.getLootTableDefinitions().isEmpty()) {
            getLogger().warning("No loot tables found; add entries to loot_tables.yml to enable loot rolls.");
        }

        getLogger().info("Configuration scaffolding ready; raid and loot definitions loaded for future phases.");
    }

    private void initializeServices() {
        Clock clock = Clock.systemUTC();
        queueManager = new QueueManager();
        raidManager = new RaidManager(configManager.getRaidDefinitions(), queueManager, clock);
        lootTableRegistry = new LootTableRegistry(configManager.getLootTableDefinitions());
        lootService = new LootService(lootTableRegistry, new Random());
        stashService = new StashService(new SQLiteStashRepository(getDataFolder().toPath().resolve("stash.db")));
        itemDataMapper = new ItemDataMapper(getLogger());
        extractionService = new ExtractionService(new EvacTracker(clock));
        inventorySnapshotService = new PaperInventorySnapshotService(this, itemDataMapper);
        teleportService = new PaperTeleportService(this);
        regionProvider = new PaperRegionProvider(this);
        stashView = new StashView(this, stashService, itemDataMapper);
        raidLifecycleCoordinator = new RaidLifecycleCoordinator(
                this,
                configManager,
                raidManager,
                queueManager,
                extractionService,
                lootService,
                stashService,
                inventorySnapshotService,
                teleportService,
                regionProvider,
                itemDataMapper);
    }

    private void registerCommands() {
        if (getCommand("raid") != null) {
            getCommand("raid").setExecutor(new RaidCommand(raidManager, queueManager, raidLifecycleCoordinator));
        }
        if (getCommand("stash") != null) {
            getCommand("stash").setExecutor(new StashCommand(stashView));
        }
        if (getCommand("raidadmin") != null) {
            getCommand("raidadmin").setExecutor(new RaidAdminCommand(this, raidManager, raidLifecycleCoordinator));
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new RaidListener(raidLifecycleCoordinator), this);
        getServer().getPluginManager().registerEvents(new ExtractionListener(raidLifecycleCoordinator), this);
        getServer().getPluginManager().registerEvents(stashView, this);
    }
}
