package com.raidextraction.quest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.raidextraction.quest.TaskType.DEPOSIT_CREDITS;

/**
 * Command executor for quest-related commands.
 */
public class QuestCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final QuestManager questManager;
    private final QuestView questView;

    public QuestCommand(JavaPlugin plugin, QuestManager questManager, QuestView questView) {
        this.plugin = plugin;
        this.questManager = questManager;
        this.questView = questView;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use quest commands.");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "list":
                listQuests(player);
                break;
                
            case "accept":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /quest accept <questId>");
                    return true;
                }
                questView.acceptQuest(player, args[1]);
                break;
                
            case "claim":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /quest claim <questId>");
                    return true;
                }
                questView.claimQuest(player, args[1]);
                break;
                
            case "info":
                if (args.length < 2) {
                    sender.sendMessage("Usage: /quest info <questId>");
                    return true;
                }
                showQuestInfo(player, args[1]);
                break;
                
            case "turnin":
                if (args.length < 3 || !"credits".equals(args[1])) {
                    sender.sendMessage("Usage: /quest turnin credits <amount>");
                    return true;
                }
                try {
                    long credits = Long.parseLong(args[2]);
                    if (credits <= 0) {
                        sender.sendMessage("Amount must be positive.");
                        return true;
                    }
                    turnInCredits(player, credits);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid amount. Please enter a valid number.");
                }
                break;
                
            default:
                showHelp(sender);
                break;
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("list", "accept", "claim", "info", "turnin").stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if ("accept".equals(subCommand) || "claim".equals(subCommand) || "info".equals(subCommand)) {
                return questManager.getAvailableQuests().stream()
                        .map(Quest::getQuestId)
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            } else if ("turnin".equals(subCommand)) {
                return Arrays.asList("credits");
            }
        }

        return Collections.emptyList();
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("=== Quest Commands ===");
        sender.sendMessage("/quest list - Show available quests");
        sender.sendMessage("/quest accept <questId> - Accept a quest");
        sender.sendMessage("/quest claim <questId> - Claim completed quest rewards");
        sender.sendMessage("/quest info <questId> - Show quest details");
        sender.sendMessage("/quest turnin credits <amount> - Turn in credits for quests");
        sender.sendMessage("=====================");
    }

    private void listQuests(Player player) {
        player.sendMessage("=== Available Quests ===");
        
        questManager.getAvailableQuests().forEach(quest -> {
            String status = "Available";
            if (questManager.hasActiveQuest(player.getUniqueId(), quest.getQuestId())) {
                status = "Active";
            }
            player.sendMessage(quest.getQuestId() + " - " + quest.getName() + " [" + status + "]");
            player.sendMessage("  " + quest.getDescription());
            player.sendMessage("  Reward: " + quest.getRewardCredits() + " credits, " + quest.getRewardXp() + " XP");
        });
        
        player.sendMessage("======================");
    }

    private void showQuestInfo(Player player, String questId) {
        questManager.getQuestTemplate(questId).ifPresentOrElse(quest -> {
            player.sendMessage("=== Quest: " + quest.getName() + " ===");
            player.sendMessage(quest.getDescription());
            player.sendMessage("Tasks:");
            quest.getTasks().forEach(task -> {
                player.sendMessage("- " + task.getType() + " " + task.getTarget() + ": " + 
                                 task.getProgress() + "/" + task.getRequiredAmount());
            });
            player.sendMessage("Reward: " + quest.getRewardCredits() + " credits, " + quest.getRewardXp() + " XP");
            player.sendMessage("============================");
        }, () -> {
            player.sendMessage("Quest not found: " + questId);
        });
    }
    
    private void turnInCredits(Player player, long credits) {
        UUID playerId = player.getUniqueId();
        Collection<Quest> activeQuests = questManager.getPlayerActiveQuests(playerId);
        
        // Check if player has any active deposit credit quests
        boolean hasDepositQuest = activeQuests.stream()
                .flatMap(quest -> quest.getIncompleteTasks().stream())
                .anyMatch(task -> task.getType() == TaskType.DEPOSIT_CREDITS);
        
        if (!hasDepositQuest) {
            player.sendMessage("You don't have any active quests that require depositing credits.");
            player.sendMessage("Check your active quests with /quest list");
            return;
        }
        
        // Update quest progress for credit deposit tasks
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Check if any quests will be completed by this submission
            Collection<Quest> questsBefore = questManager.getPlayerActiveQuests(playerId);
            
            questManager.updateQuestProgress(playerId, TaskType.DEPOSIT_CREDITS, "credits", (int) credits);
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("Turned in " + credits + " credits for quest progress!");
                    
                    // Check if any quests were completed
                    Collection<Quest> questsAfter = questManager.getPlayerActiveQuests(playerId);
                    Collection<Quest> completedQuests = questsBefore.stream()
                            .filter(quest -> !questsAfter.contains(quest) && quest.isCompleted())
                            .toList();
                    
                    if (!completedQuests.isEmpty()) {
                        player.sendMessage("=== Quests Completed! ===");
                        completedQuests.forEach(quest -> {
                            player.sendMessage(quest.getName() + " - Ready to claim!");
                            player.sendMessage("Use: /quest claim " + quest.getQuestId());
                        });
                        player.sendMessage("=========================");
                    }
                    
                    player.sendMessage("Check your quest progress with /quest list");
                }
            });
        });
    }
}