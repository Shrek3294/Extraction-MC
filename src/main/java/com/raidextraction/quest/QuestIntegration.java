package com.raidextraction.quest;

import com.raidextraction.RaidExtractionPlugin;
import com.raidextraction.profile.PlayerProfileService;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Integration class for quest system with the main plugin.
 */
public class QuestIntegration {
    private final RaidExtractionPlugin plugin;
    private final PlayerProfileService profileService;
    private final Logger logger;
    
    private QuestRepository questRepository;
    private QuestManager questManager;
    private QuestEventListener questEventListener;
    private QuestView questView;
    private QuestCommand questCommand;

    public QuestIntegration(RaidExtractionPlugin plugin, PlayerProfileService profileService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.profileService = Objects.requireNonNull(profileService, "profileService");
        this.logger = plugin.getLogger();
    }

    /**
     * Initialize all quest system components
     */
    public void initialize() {
        logger.info("Initializing Quest System...");
        
        // Create repository
        Path dbPath = plugin.getDataFolder().toPath().resolve("quests.db");
        questRepository = new SQLiteQuestRepository(dbPath, logger);
        
        // Create manager
        questManager = new QuestManager(plugin, questRepository, profileService);
        
        // Create event listener
        questEventListener = new QuestEventListener(plugin, questManager, 
                plugin.getLootPricingService(), plugin.getItemKeys());
        
        // Create view
        questView = new QuestView(plugin, questManager, profileService);
        
        // Create command
        questCommand = new QuestCommand(plugin, questManager, questView);
        
        // Register components
        registerComponents();
        
        logger.info("Quest System initialized successfully!");
    }

    /**
     * Register event listeners and commands
     */
    private void registerComponents() {
        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(questEventListener, plugin);
        
        // Register command
        if (plugin.getCommand("quest") != null) {
            plugin.getCommand("quest").setExecutor(questCommand);
            plugin.getCommand("quest").setTabCompleter(questCommand);
        }
    }

    /**
     * Get the quest manager instance
     */
    public QuestManager getQuestManager() {
        return questManager;
    }

    /**
     * Get the quest view instance
     */
    public QuestView getQuestView() {
        return questView;
    }

    /**
     * Get the quest event listener (for integration with other systems)
     */
    public QuestEventListener getQuestEventListener() {
        return questEventListener;
    }
}