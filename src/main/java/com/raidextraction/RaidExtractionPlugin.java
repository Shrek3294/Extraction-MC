package com.raidextraction;

import com.raidextraction.config.ConfigManager;
import com.raidextraction.command.RaidAdminCommand;
import com.raidextraction.command.RaidCommand;
import com.raidextraction.command.StashCommand;
import com.raidextraction.command.WeaponCommand;
import com.raidextraction.editor.MapEditorManager;
import com.raidextraction.editor.MapEditorStorage;
import com.raidextraction.extraction.EvacTracker;
import com.raidextraction.extraction.ExtractionService;
import com.raidextraction.loot.LootService;
import com.raidextraction.loot.LootTableRegistry;
import com.raidextraction.loot.LootPricingService;
import com.raidextraction.integration.ItemDataMapper;
import com.raidextraction.integration.PaperLootItemFactory;
import com.raidextraction.integration.InventorySnapshotService;
import com.raidextraction.integration.RaidLifecycleCoordinator;
import com.raidextraction.integration.RegionProvider;
import com.raidextraction.integration.TeleportService;
import com.raidextraction.integration.paper.PaperInventorySnapshotService;
import com.raidextraction.integration.paper.PaperRegionProvider;
import com.raidextraction.integration.paper.StashView;
import com.raidextraction.integration.paper.PaperTeleportService;
import com.raidextraction.integration.paper.PaperWorldManager;
import com.raidextraction.integration.WorldManager;
import com.raidextraction.raid.QueueManager;
import com.raidextraction.raid.RaidManager;
import com.raidextraction.stash.SQLiteStashRepository;
import com.raidextraction.stash.FixedStashCapacityProvider;
import com.raidextraction.stash.StashService;
import com.raidextraction.listener.ExtractionListener;
import com.raidextraction.listener.LootInteractionListener;
import com.raidextraction.listener.MapEditorListener;
import com.raidextraction.listener.RaidListener;
import com.raidextraction.ux.CrateAnimationService;
import com.raidextraction.ux.WeaponBenchView;
import com.raidextraction.ux.WeaponGuideBookFactory;
import com.raidextraction.item.CustomItemFactory;
import com.raidextraction.item.CustomEnchantRegistry;
import com.raidextraction.item.CustomItemRegistry;
import com.raidextraction.item.ItemKeys;
import com.raidextraction.listener.CustomEnchantListener;
import com.raidextraction.magic.ManaHudService;
import com.raidextraction.magic.PlayerManaService;
import com.raidextraction.magic.SpellCastingService;
import com.raidextraction.profile.PlayerProfileService;
import com.raidextraction.profile.SQLitePlayerProfileRepository;
import com.raidextraction.trader.TraderService;
import com.raidextraction.trader.TraderView;
import com.raidextraction.ux.HudService;
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
    private LootPricingService lootPricingService;
    private StashService stashService;
    private PlayerProfileService profileService;
    private HudService hudService;
    private ItemDataMapper itemDataMapper;
    private ExtractionService extractionService;
    private InventorySnapshotService inventorySnapshotService;
    private TeleportService teleportService;
    private RegionProvider regionProvider;
    private WorldManager worldManager;
    private StashView stashView;
    private RaidLifecycleCoordinator raidLifecycleCoordinator;
    private MapEditorStorage mapEditorStorage;
    private MapEditorManager mapEditorManager;
    private CrateAnimationService crateAnimationService;
    private ItemKeys itemKeys;
    private CustomItemRegistry customItemRegistry;
    private CustomItemFactory customItemFactory;
    private CustomEnchantRegistry customEnchantRegistry;
    private com.raidextraction.item.WeaponUpgradeService weaponUpgradeService;
    private WeaponBenchView weaponBenchView;
    private WeaponGuideBookFactory weaponGuideBookFactory;
    private PlayerManaService manaService;
    private ManaHudService manaHudService;
    private SpellCastingService spellCastingService;
    private CustomEnchantListener customEnchantListener;
    private TraderService traderService;
    private TraderView traderView;

    @Override
    public void onLoad() {
        getLogger().info("RaidExtraction plugin loading (preparing data folder and config scaffolding).");
        prepareDataFolder();
    }

    @Override
    public void onEnable() {
        try {
            getLogger().info("RaidExtraction plugin enabling (commands and listeners will register in later phases).");
            prepareDataFolder();

            getLogger().info("Step 1: Scaffolding");
            initializeConfigurationScaffolding();

            getLogger().info("Step 2: Services");
            initializeServices();

            getLogger().info("Step 3: Commands");
            registerCommands();

            getLogger().info("Step 4: Listeners");
            registerListeners();

            getLogger().info("Step 5: Rehydration");
            raidLifecycleCoordinator.rehydrateState();

            getLogger().info("RaidExtraction enable complete.");
        } catch (Throwable t) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to enable RaidExtraction", t);
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("RaidExtraction plugin disabling. Cleaning up resources.");
        if (manaService != null) {
            manaService.stop();
        }
        if (manaHudService != null) {
            manaHudService.stop();
        }
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
        stashService = new StashService(
                new SQLiteStashRepository(getDataFolder().toPath().resolve("stash.db")),
                new FixedStashCapacityProvider(configManager.getStashMaxStacks()));
        profileService = new PlayerProfileService(
                new SQLitePlayerProfileRepository(getDataFolder().toPath().resolve("profiles.db")));
        itemDataMapper = new ItemDataMapper(getLogger());
        itemKeys = new ItemKeys(this);
        customItemRegistry = new CustomItemRegistry(configManager.getItemsDefinition());
        customItemFactory = new CustomItemFactory(itemKeys);
        customEnchantRegistry = new CustomEnchantRegistry(configManager.getItemsDefinition());
        weaponUpgradeService = new com.raidextraction.item.WeaponUpgradeService(itemKeys, customItemRegistry,
                customEnchantRegistry);
        weaponBenchView = new WeaponBenchView(this, itemKeys, weaponUpgradeService);
        weaponGuideBookFactory = new WeaponGuideBookFactory(configManager.getItemsDefinition());
        lootService = new LootService(lootTableRegistry, new Random(),
                new PaperLootItemFactory(customItemRegistry, customItemFactory, itemDataMapper));
        lootPricingService = new LootPricingService(lootTableRegistry.definitions());
        manaService = new PlayerManaService(this, configManager.getItemsDefinition().manaMax(),
                configManager.getItemsDefinition().manaRegenPerSecond());
        manaService.start();
        manaHudService = new ManaHudService(this, itemKeys, manaService);
        manaHudService.start();
        spellCastingService = new SpellCastingService(this, itemKeys, configManager.getItemsDefinition(), manaService);
        hudService = new HudService(this, configManager, profileService);
        hudService.start();
        customEnchantListener = new CustomEnchantListener(this, itemKeys, customEnchantRegistry);
        traderService = new TraderService(this, configManager, lootPricingService, itemKeys, customItemRegistry,
                customItemFactory);
        traderService.ensureNpcPresent();
        traderView = new TraderView(this, traderService, profileService);
        extractionService = new ExtractionService(new EvacTracker(clock), clock);
        inventorySnapshotService = new PaperInventorySnapshotService(this, itemDataMapper);
        regionProvider = new PaperRegionProvider(this);
        worldManager = new PaperWorldManager(this);
        stashView = new StashView(this, stashService, itemDataMapper);
        mapEditorStorage = new MapEditorStorage(this);
        mapEditorManager = new MapEditorManager(this, worldManager, mapEditorStorage);
        teleportService = new PaperTeleportService(this, mapEditorStorage);
        crateAnimationService = new CrateAnimationService(this, itemDataMapper);
        raidLifecycleCoordinator = new RaidLifecycleCoordinator(
                this,
                configManager,
                raidManager,
                queueManager,
                extractionService,
                lootService,
                stashService,
                profileService,
                inventorySnapshotService,
                teleportService,
                regionProvider,
                itemDataMapper,
                mapEditorStorage);
    }

    private void registerCommands() {
        if (getCommand("raid") != null) {
            getCommand("raid").setExecutor(
                    new RaidCommand(raidManager, queueManager, raidLifecycleCoordinator, extractionService,
                            hudService));
        }
        if (getCommand("stash") != null) {
            getCommand("stash").setExecutor(new StashCommand(stashView, raidManager));
        }
        if (getCommand("weapon") != null) {
            getCommand("weapon").setExecutor(
                    new WeaponCommand(this, customItemRegistry, customItemFactory, weaponUpgradeService,
                            weaponBenchView,
                            weaponGuideBookFactory));
        }
        if (getCommand("raidadmin") != null) {
            getCommand("raidadmin").setExecutor(
                    new RaidAdminCommand(
                            this,
                            raidManager,
                            raidLifecycleCoordinator,
                            configManager,
                            worldManager,
                            mapEditorManager,
                            profileService));
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new RaidListener(raidLifecycleCoordinator), this);
        getServer().getPluginManager().registerEvents(new ExtractionListener(raidLifecycleCoordinator), this);
        getServer().getPluginManager().registerEvents(stashView, this);
        getServer().getPluginManager().registerEvents(new MapEditorListener(mapEditorManager), this);
        getServer().getPluginManager().registerEvents(manaService, this);
        getServer().getPluginManager().registerEvents(spellCastingService, this);
        getServer().getPluginManager().registerEvents(customEnchantListener, this);
        getServer().getPluginManager().registerEvents(weaponBenchView, this);
        getServer().getPluginManager().registerEvents(hudService, this);
        getServer().getPluginManager().registerEvents(traderView, this);
        getServer().getPluginManager().registerEvents(
                new LootInteractionListener(raidManager, crateAnimationService, getLogger()),
                this);
    }
}
