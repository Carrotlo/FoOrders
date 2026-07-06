package me.foesio.foOrders.command;

import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foOrders.FoOrders;
import me.foesio.foOrders.OrdersMenuManager;
import me.foesio.foOrders.PluginMessages;
import me.foesio.foOrders.storage.HistoryDataStore;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class OrderAdminCommand implements CommandExecutor, TabCompleter {
    private final FoOrders plugin;
    private final OrdersMenuManager ordersMenuManager;
    private final UpdateNoticeService updateNotices;

    public OrderAdminCommand(FoOrders plugin, OrdersMenuManager ordersMenuManager, UpdateNoticeService updateNotices) {
        this.plugin = plugin;
        this.ordersMenuManager = ordersMenuManager;
        this.updateNotices = updateNotices;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            plugin.fileLogger().warn("Admin command missing subcommand from " + sender.getName() + ".");
            ordersMenuManager.messages().send(sender, "admin.usage", PluginMessages.placeholders("label", label));
            return true;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("version")) {
            if (args.length != 1) {
                plugin.fileLogger().warn("Invalid version command usage from " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.version-usage", PluginMessages.placeholders("label", label));
                return true;
            }
            plugin.fileLogger().info("Admin " + sender.getName() + " used version command.");
            String pluginVersion = plugin.getDescription() == null ? "unknown" : plugin.getDescription().getVersion();
            ordersMenuManager.messages().send(sender, "admin.version-author", PluginMessages.placeholders("author", "Carrotio"));
            ordersMenuManager.messages().send(sender, "admin.version-number", PluginMessages.placeholders("version", pluginVersion == null ? "unknown" : pluginVersion));
            updateNotices.sendVersion(sender);
            return true;
        }

        if (subCommand.equals("reload")) {
            if (args.length != 1) {
                plugin.fileLogger().warn("Invalid reload command usage from " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.reload-usage", PluginMessages.placeholders("label", label));
                return true;
            }
            plugin.fileLogger().info("Admin " + sender.getName() + " started reload.");
            FoReloadResult result = plugin.reloadPlugin();
            if (!result.successful()) {
                if ("economy".equals(result.failedStep())) {
                    ordersMenuManager.messages().send(sender, "admin.economy-missing");
                    plugin.fileLogger().error("Reload completed but Vault economy provider is missing.", result.error());
                    return true;
                }
                plugin.fileLogger().error("Reload failed at " + result.failedStep() + ": " + result.errorMessage(), result.error());
                ordersMenuManager.messages().send(sender, "admin.reload-failed", PluginMessages.placeholders(
                    "step", result.failedStep(),
                    "error", result.errorMessage()
                ));
                return true;
            }
            ordersMenuManager.messages().send(sender, "admin.reload-success");
            plugin.fileLogger().info("Admin " + sender.getName() + " completed reload.");
            return true;
        }

        if (subCommand.equals("editor")) {
            if (args.length != 1) {
                plugin.fileLogger().warn("Invalid editor command usage from " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.editor-usage", PluginMessages.placeholders("label", label));
                return true;
            }
            if (!(sender instanceof Player player)) {
                plugin.fileLogger().warn("Non-player tried to open editor: " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.editor-player-only");
                return true;
            }
            plugin.fileLogger().info("Admin " + player.getName() + " opened item editor.");
            ordersMenuManager.openAdminItemEditor(player);
            return true;
        }

        if (subCommand.equals("history")) {
            if (!(sender instanceof Player player)) {
                plugin.fileLogger().warn("Non-player tried to open history menu: " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.history-player-only");
                return true;
            }
            if (args.length < 2) {
                plugin.fileLogger().warn("History command missing player from " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.history-usage", PluginMessages.placeholders("label", label));
                return true;
            }

            String targetInput = String.join(" ", List.of(args).subList(1, args.length)).trim();
            if (targetInput.isEmpty()) {
                plugin.fileLogger().warn("History command empty player from " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.history-usage", PluginMessages.placeholders("label", label));
                return true;
            }

            OfflinePlayer target = findOfflinePlayer(targetInput);
            if (target == null || target.getUniqueId() == null) {
                plugin.fileLogger().warn("History target not found: " + targetInput + " by " + sender.getName() + ".");
                ordersMenuManager.messages().send(sender, "admin.player-not-found", PluginMessages.placeholders("player", targetInput));
                return true;
            }

            plugin.fileLogger().info("Admin " + player.getName() + " opened history for " + targetInput + ".");
            ordersMenuManager.openHistoryMenu(player, target.getUniqueId(), HistoryDataStore.HistoryType.ORDER, true);
            return true;
        }

        plugin.fileLogger().warn("Unknown admin subcommand '" + subCommand + "' from " + sender.getName() + ".");
        ordersMenuManager.messages().send(sender, "admin.usage", PluginMessages.placeholders("label", label));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            if ("reload".startsWith(input)) {
                completions.add("reload");
            }
            if ("version".startsWith(input)) {
                completions.add("version");
            }
            if ("editor".startsWith(input)) {
                completions.add("editor");
            }
            if ("history".startsWith(input)) {
                completions.add("history");
            }
            return completions;
        }

        if (args.length == 2 && "history".equalsIgnoreCase(args[0])) {
            String input = args[1].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String name = onlinePlayer.getName();
                if (name.toLowerCase(Locale.ROOT).startsWith(input)) {
                    completions.add(name);
                }
            }
            return completions;
        }

        return Collections.emptyList();
    }

    private OfflinePlayer findOfflinePlayer(String input) {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getName().equalsIgnoreCase(input)) {
                return onlinePlayer;
            }
        }

        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && name.equalsIgnoreCase(input)) {
                return offlinePlayer;
            }
        }

        return null;
    }
}
