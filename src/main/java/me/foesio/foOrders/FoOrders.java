package me.foesio.foOrders;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.discord.DiscordWebhookConfigDefaults;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foOrders.command.OrderAdminCommand;
import me.foesio.foOrders.command.OrderCommand;
import me.foesio.foOrders.config.GuiConfigManager;
import me.foesio.foOrders.dialog.FoOrdersDialogInputService;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class FoOrders extends JavaPlugin {
    private static final String DISPLAY_NAME = "FoOrders";
    private static final String LEGACY_PLUGIN_TITLE_PATH = "plugin-title";
    private static final String MODRINTH_PROJECT_ID = "foorders";
    private static final int BSTATS_PLUGIN_ID = 32420;

    private OrdersMenuManager ordersMenuManager;
    private PluginMessages messages;
    private GuiConfigManager guiConfigManager;
    private FoOrdersDialogInputService dialogInputService;
    private FoFileLogger fileLogger;
    private FoCoreContext core;
    private UpdateNoticeService updateNotices;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigDefaults();
        fileLogger = FoFileLogger.create(this);
        fileLogger.configureFromConfig("file-logging", true);
        fileLogger.info("FoOrders enable started.");
        messages = new PluginMessages(this);
        messages.reload();
        guiConfigManager = new GuiConfigManager(this);
        guiConfigManager.reload();
        core = FoPluginCore.create(this);
        FoScheduler schedulerAdapter = core.scheduler();
        getLogger().info("FoOrders scheduler mode: " + (schedulerAdapter.isFolia() ? "Folia-compatible bridge" : "Bukkit scheduler"));
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
        DialogService dialogService = core.dialogService();
        dialogInputService = new FoOrdersDialogInputService(this, schedulerAdapter, fileLogger);

        PlayerDataStore playerDataStore = new PlayerDataStore(this, schedulerAdapter);
        playerDataStore.initialize();
        CustomItemStore customItemStore = new CustomItemStore(this);
        customItemStore.initialize();
        HistoryDataStore historyDataStore = new HistoryDataStore(this, schedulerAdapter);
        historyDataStore.initialize();

        ordersMenuManager = new OrdersMenuManager(
            this,
            schedulerAdapter,
            playerDataStore,
            customItemStore,
            historyDataStore,
            messages,
            guiConfigManager,
            dialogInputService,
            dialogService,
            core.inventoryCloseSuppressor(),
            core.inventoryDeposits(),
            fileLogger
        );
        OrderCommand orderCommand = new OrderCommand(ordersMenuManager);
        ordersMenuManager.reloadFromConfig();
        if (!reloadEconomyHook()) {
            fileLogger.error("No economy provider found through Vault. Disabling FoOrders.", null);
            getLogger().severe("No economy provider found through Vault. Disabling FoOrders.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        updateNotices = createUpdateNotices();
        OrderAdminCommand orderAdminCommand = new OrderAdminCommand(this, ordersMenuManager, updateNotices);

        getServer().getPluginManager().registerEvents(ordersMenuManager, this);

        registerCommand("order", orderCommand);
        registerCommand("orderadmin", orderAdminCommand);

        ordersMenuManager.initializeOnlinePlayers();
        fileLogger.info("FoOrders enable completed.");
    }

    private UpdateNoticeService createUpdateNotices() {
        return core.createUpdateNotices(new UpdateNoticeService.UpdateMessenger() {
            @Override
            public void send(CommandSender sender, String template, Map<String, String> placeholders) {
                messages.sendTemplate(sender, template, fixedPluginPlaceholders(placeholders));
            }

            @Override
            public void sendClickable(CommandSender sender, String template, String url, Map<String, String> placeholders) {
                messages.sendClickableTemplate(sender, template, url, fixedPluginPlaceholders(placeholders));
            }
        }, MODRINTH_PROJECT_ID).start();
    }

    private Map<String, String> fixedPluginPlaceholders(Map<String, String> placeholders) {
        Map<String, String> fixedPlaceholders = new LinkedHashMap<>(placeholders == null ? Map.of() : placeholders);
        fixedPlaceholders.put("plugin", DISPLAY_NAME);
        return fixedPlaceholders;
    }

    @Override
    public void onDisable() {
        if (fileLogger != null) {
            fileLogger.info("FoOrders disable started.");
        }
        if (ordersMenuManager != null) {
            ordersMenuManager.saveAllData();
        }
        if (dialogInputService != null) {
            dialogInputService.shutdown();
        }
        if (core != null) {
            core.close();
        }
        if (fileLogger != null) {
            fileLogger.info("FoOrders disable completed.");
            fileLogger.close();
        }
    }

    public boolean reloadEconomyHook() {
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(Economy.class);
        Economy economy = economyProvider == null ? null : economyProvider.getProvider();
        if (ordersMenuManager != null) {
            ordersMenuManager.setEconomy(economy);
        }
        return economy != null;
    }

    public void ensureConfigDefaults() {
        FileConfiguration config = getConfig();
        boolean changed = backfillConfigDefaults(config);
        changed |= removeLegacyPluginTitle(config);
        changed |= setDefaultIfMissing(config, "max-order-per-player", 3);
        changed |= setDefaultIfMissing(config, "order-menu-refresh-cooldown-ms", OrdersMenuManager.DEFAULT_ORDER_MENU_REFRESH_COOLDOWN_MILLIS);
        changed |= setDefaultIfMissing(config, "order-tax.percentage", 0);
        changed |= setDefaultIfMissing(config, "file-logging", false);
        changed |= setDefaultIfMissing(config, NativeDialogConfigDefaults.ENABLED_PATH, true);
        changed |= setDefaultIfMissing(config, "native-dialogs.item-selection", true);
        changed |= setDefaultIfMissing(config, NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH, true);
        changed |= setDefaultIfMissing(config, DiscordWebhookConfigDefaults.WEBHOOK_URL_PATH, "");
        changed |= setDefaultIfMissing(config, DiscordWebhookConfigDefaults.USERNAME_PATH, "");
        changed |= setDefaultIfMissing(config, DiscordWebhookConfigDefaults.AVATAR_URL_PATH, "");
        changed |= setDefaultIfMissing(config, DiscordWebhookConfigDefaults.LOG_FAILURES_PATH, true);
        if (changed) {
            saveConfig();
        }
    }

    private boolean removeLegacyPluginTitle(FileConfiguration config) {
        if (!config.isSet(LEGACY_PLUGIN_TITLE_PATH) && config.getComments(LEGACY_PLUGIN_TITLE_PATH).isEmpty()) {
            return false;
        }
        config.set(LEGACY_PLUGIN_TITLE_PATH, null);
        config.setComments(LEGACY_PLUGIN_TITLE_PATH, java.util.List.of());
        return true;
    }

    private boolean backfillConfigDefaults(FileConfiguration config) {
        Configuration defaults = config.getDefaults();
        if (defaults == null) {
            return false;
        }

        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || config.isSet(key)) {
                continue;
            }
            config.set(key, defaults.get(key));
            changed = true;
        }
        return changed;
    }

    private boolean setDefaultIfMissing(FileConfiguration config, String path, Object value) {
        if (config.isSet(path)) {
            return false;
        }
        config.set(path, value);
        return true;
    }

    public void reloadMessages() {
        if (messages != null) {
            messages.reload();
        }
    }

    public void reloadFileLogging() {
        if (fileLogger != null) {
            fileLogger.configureFromConfig("file-logging", false);
        }
    }

    public void reloadGuiFiles() {
        if (guiConfigManager != null) {
            guiConfigManager.reload();
        }
    }

    public void reloadDialogFiles() {
        if (dialogInputService != null) {
            dialogInputService.reloadDialogs();
        }
    }

    public void reloadDialogInputService() {
        if (dialogInputService != null) {
            dialogInputService.reloadSupport();
        }
        if (ordersMenuManager != null) {
            ordersMenuManager.setDialogInputService(dialogInputService);
        }
    }

    public FoReloadResult reloadPlugin() {
        FoReloadRegistry registry = FoReloadRegistry.create()
            .addConfig(this)
            .add("config-defaults", this::ensureConfigDefaults)
            .add("file-logging", this::reloadFileLogging)
            .add("messages", this::reloadMessages)
            .add("dialogs", this::reloadDialogFiles)
            .add("guis", this::reloadGuiFiles)
            .add("dialog-support", this::reloadDialogInputService)
            .add("runtime-settings", () -> {
                if (ordersMenuManager != null) {
                    ordersMenuManager.reloadFromConfig();
                }
            })
            .add("economy", () -> {
                if (!reloadEconomyHook()) {
                    throw new IllegalStateException("Vault economy provider not found");
                }
            });
        return registry.reload();
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Command '" + name + "' not defined in plugin.yml");
        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    public PluginMessages messages() {
        return messages;
    }

    public FoFileLogger fileLogger() {
        return fileLogger;
    }

    GuiConfigManager guiConfigManager() {
        return guiConfigManager;
    }
}
