package me.foesio.foOrders;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.economy.VaultEconomyBridge;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foOrders.command.OrderAdminCommand;
import me.foesio.foOrders.command.OrderCommand;
import me.foesio.foOrders.config.GuiConfigManager;
import me.foesio.foOrders.config.PublicSoundFiles;
import me.foesio.foOrders.dialog.FoOrdersDialogInputService;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import me.foesio.foOrders.util.OwnerUpgradeBackups;
import me.foesio.foOrders.util.DeferredPublicMigrations;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class FoOrders extends JavaPlugin {
    private static final String DISPLAY_NAME = "FoOrders";
    private static final String LEGACY_PLUGIN_TITLE_PATH = "plugin-title";
    private static final String MODRINTH_PROJECT_ID = "foorders";
    private static final int BSTATS_PLUGIN_ID = 32420;

    private OrdersMenuManager ordersMenuManager;
    private OrdersPublicGuis publicGuis;
    private PluginMessages messages;
    private GuiConfigManager guiConfigManager;
    private FoOrdersDialogInputService dialogInputService;
    private FoFileLogger fileLogger;
    private FoCoreContext core;
    private FoSoundService sounds;
    private FoEditorSounds editorSounds;
    private FoAdminSounds adminSounds;
    private FoGuiSounds guiSounds;
    private UpdateNoticeService updateNotices;

    @Override
    public void onEnable() {
        File[] existingData = getDataFolder().listFiles();
        boolean freshInstall = existingData == null || existingData.length == 0;
        saveDefaultConfig();
        ensureConfigDefaults();
        fileLogger = FoFileLogger.create(this);
        fileLogger.configureFromConfig("file-logging", true);
        fileLogger.info("FoOrders enable started.");
        core = FoPluginCore.create(this);
        int originalSchemaVersion = core.migrations().version();
        OwnerUpgradeBackups.begin(getDataFolder(), originalSchemaVersion);
        originalSchemaVersion = OwnerUpgradeBackups.originalVersion(getDataFolder(), originalSchemaVersion);
        if (!freshInstall && core.migrations().version() < GuiConfigManager.CORE_PUBLIC_GUI_VERSION) {
            try {
                OwnerUpgradeBackups.retainPublicGuiOrigin(getDataFolder(), originalSchemaVersion);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not retain original public GUI upgrade state", failure);
            }
        }
        messages = new PluginMessages(this, freshInstall);
        messages.reload();
        messages.migrateSprites(core.migrations(), 1);
        if (core.migrations().version() < 1) {
            throw new IllegalStateException("Could not migrate FoOrders public message sprites.");
        }
        messages.migratePrefix(core.migrations(), 2);
        if (core.migrations().version() < 2) {
            throw new IllegalStateException("Could not migrate FoOrders public message prefix.");
        }
        guiConfigManager = new GuiConfigManager(this, core.migrations(), freshInstall);
        guiConfigManager.deferLegacyPresentationLoad();
        guiConfigManager.reload();
        if (freshInstall && !new File(getDataFolder(), "sounds.yml").isFile()) {
            saveResource("sounds.yml", false);
        }
        PublicSoundFiles.migrateVariations(this, core.migrations());
        PublicSoundFiles.Snapshot soundSnapshot = PublicSoundFiles.capture(this);
        sounds = core.createSounds();
        soundSnapshot.restoreAfterCoreLoad(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        adminSounds = FoAdminSounds.create(sounds);
        guiSounds = FoGuiSounds.create(sounds);
        FoScheduler schedulerAdapter = core.scheduler();
        getLogger().info("FoOrders scheduler mode: " + (schedulerAdapter.isFolia() ? "Folia-compatible bridge" : "Bukkit scheduler"));
        core.warnIfNativeDialogsUnavailable();
        if (!core.supportsDialogSprites()) {
            getLogger().info("FoOrders dialog sprite icons disabled: " + core.dialogSpriteSupport().unavailableReason());
        }
        core.metrics(BSTATS_PLUGIN_ID);
        DialogService dialogService = core.dialogService();
        dialogInputService = new FoOrdersDialogInputService(
            this, schedulerAdapter, fileLogger, core.migrations(), freshInstall);
        guiConfigManager.migrateNewItemFlags();
        guiConfigManager.reload();
        if (!messages.migrateNumberFormats(core.migrations())) {
            throw new IllegalStateException("Could not migrate public number formats.");
        }
        messages.reload();
        guiConfigManager.migrateSearchAndHistoryPresentation();
        guiConfigManager.reload();
        if (!messages.migrateStyleTokens(core.migrations())) {
            throw new IllegalStateException("Could not migrate owner style tokens.");
        }
        messages.reload();
        guiConfigManager.migrateHistoryTabNames();
        guiConfigManager.reload();
        dialogInputService.migrateEnchantActionOrder();
        dialogInputService.reloadDialogs();
        dialogInputService.reloadSupport();
        if (!messages.migrateUnavailableInputFeedback(core.migrations())) {
            throw new IllegalStateException("Could not migrate public input failure feedback.");
        }
        if (!messages.migrateRefreshCooldownFeedback(core.migrations())) {
            throw new IllegalStateException("Could not migrate public refresh cooldown feedback.");
        }
        if (!messages.migrateActionFailureFeedback(core.migrations())) {
            throw new IllegalStateException("Could not migrate public action failure feedback.");
        }
        messages.reload();
        guiConfigManager.migrateClaimEntryPresentation();
        guiConfigManager.migrateHistoryUnknownDetails();
        guiConfigManager.migrateCustomItemPickerLore();
        guiConfigManager.reload();
        dialogInputService.migrateSearchClearHint();
        dialogInputService.reloadDialogs();
        guiConfigManager.migratePreviewLimits();
        guiConfigManager.reload();
        if (!messages.migrateInvalidPriceFeedback(core.migrations())) {
            throw new IllegalStateException("Could not migrate public price input feedback.");
        }
        messages.reload();
        dialogInputService.migrateLegacyTargetLabels();
        dialogInputService.reloadDialogs();
        guiConfigManager.restoreRemovedCustomItemPickerLore();
        guiConfigManager.reload();
        dialogInputService.migrateTextDialogColumns();
        dialogInputService.reloadDialogs();
        dialogInputService.migrateTargetTemplateUsage();
        dialogInputService.reloadDialogs();

        boolean hadDeferredPublicMigration = DeferredPublicMigrations.hasPending(getDataFolder());
        if (hadDeferredPublicMigration) {
            PublicSoundFiles.retryDeferredVariation(this);
            if (dialogInputService.retryDeferredSteps()) {
                dialogInputService.reloadSupport();
            }
        }

        java.util.Set<String> availablePublicGuis = OrdersPublicGuiUpgrade.migrate(this, core.migrations(),
            originalSchemaVersion, guiConfigManager.wasFreshCoreInstall());
        if (hadDeferredPublicMigration && !DeferredPublicMigrations.hasPending(getDataFolder())) {
            try {
                reloadSoundFiles();
            } catch (IllegalStateException invalidOwnerSounds) {
                getLogger().warning("Owner sounds.yml remains unavailable after legacy retry: "
                    + invalidOwnerSounds.getMessage());
            }
        }
        dialogInputService.finishStartup();
        messages.finishStartup();
        if (!availablePublicGuis.isEmpty()) {
            guiConfigManager.reload();
        } else {
            getLogger().severe("FoOrders public inventory migration is pending; public menus remain unavailable.");
        }
        if (guiConfigManager.publicTextUnavailable()) {
            availablePublicGuis = java.util.Set.of();
            getLogger().severe("FoOrders public text is unavailable; public menus remain unavailable until repair and restart.");
        }

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
            fileLogger,
            sounds,
            editorSounds
        );
        OrderCommand orderCommand = new OrderCommand(ordersMenuManager);
        ordersMenuManager.reloadFromConfig();
        if (!availablePublicGuis.isEmpty()) {
            publicGuis = new OrdersPublicGuis(this, ordersMenuManager);
            ordersMenuManager.setPublicGuis(publicGuis);
            publicGuis.start(availablePublicGuis);
            guiConfigManager.usePublicDefinitions(publicGuis::definition);
        }
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
        }, MODRINTH_PROJECT_ID, adminSounds).start();
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
            ordersMenuManager.clearItemSelectionDialogState();
            ordersMenuManager.saveAllData();
        }
        if (dialogInputService != null) {
            dialogInputService.shutdown();
        }
        if (publicGuis != null) {
            publicGuis.close();
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
        VaultEconomyBridge economy = core.createVaultEconomy();
        if (ordersMenuManager != null) {
            ordersMenuManager.setEconomy(economy.isAvailable() ? economy : null);
        }
        return economy.isAvailable();
    }

    public void ensureConfigDefaults() {
        FileConfiguration config = getConfig();
        if (migrateLegacyConfigFields(config)) {
            saveConfig();
        }
    }

    static boolean migrateLegacyConfigFields(FileConfiguration config) {
        int schemaVersion = config.getInt("config-version", 0);
        boolean changed = schemaVersion < 13 && removeLegacyPluginTitle(config);
        if (schemaVersion < 18 && config.isSet("native-dialogs")
            && config.isConfigurationSection("native-dialogs")
            && !config.isSet("native-dialogs.item-selection")) {
            // This setting was genuinely introduced with the native picker.
            // Other missing owner keys are older defaults or ambiguous removals.
            config.set("native-dialogs.item-selection", true);
            changed = true;
        }
        return changed;
    }

    private static boolean removeLegacyPluginTitle(FileConfiguration config) {
        if (!config.isSet(LEGACY_PLUGIN_TITLE_PATH) && config.getComments(LEGACY_PLUGIN_TITLE_PATH).isEmpty()) {
            return false;
        }
        config.set(LEGACY_PLUGIN_TITLE_PATH, null);
        config.setComments(LEGACY_PLUGIN_TITLE_PATH, java.util.List.of());
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
            if (guiConfigManager.legacyAggregateUnavailable()) {
                throw new IllegalStateException("Legacy public GUI migration remains pending; "
                    + "repair guis/orders.yml and restart before reloading menus.");
            }
            if (core != null
                && core.migrations().version() < GuiConfigManager.CORE_PUBLIC_GUI_VERSION) {
                throw new IllegalStateException("Public GUI migration remains pending; "
                    + "repair the logged owner GUI file and restart before reloading menus.");
            }
            if (publicGuis == null) {
                throw new IllegalStateException("Public menus were unavailable at startup; "
                    + "repair the logged owner file and restart before reloading menus.");
            }
            guiConfigManager.validatePublicText();
            if (!publicGuis.reloadAll()) {
                throw new IllegalStateException("Public GUI reload rejected; prior working menus remain active.");
            }
            guiConfigManager.reload();
            if (ordersMenuManager != null) {
                ordersMenuManager.invalidateItemSelectCaches();
            }
        }
    }

    public FoGuiSounds getGuiSounds() {
        return guiSounds;
    }

    public FoAdminSounds getAdminSounds() {
        return adminSounds;
    }

    public void reloadDialogFiles() {
        if (dialogInputService != null) {
            dialogInputService.reloadDialogs();
            dialogInputService.reloadSupport();
        }
        if (ordersMenuManager != null) {
            ordersMenuManager.reloadItemSelectionDialogConfig();
            ordersMenuManager.invalidateItemSelectCaches();
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

    public void reloadSoundFiles() {
        File ownerSounds = new File(getDataFolder(), "sounds.yml");
        PublicSoundFiles.validateReload(this);
        if (ownerSounds.isFile() && !sounds.validateInstalled()) {
            throw new IllegalStateException("Owner sounds.yml reload rejected; prior working sounds remain active.");
        }
        PublicSoundFiles.Snapshot snapshot = PublicSoundFiles.capture(this);
        if (!sounds.reloadChecked()) {
            throw new IllegalStateException("Owner sounds.yml reload rejected; prior working sounds remain active.");
        }
        snapshot.restoreAfterCoreLoad(sounds);
    }

    public FoReloadResult reloadPlugin() {
        FoReloadRegistry registry = FoReloadRegistry.create()
            .addConfig(this)
            .add("config-defaults", this::ensureConfigDefaults)
            .add("file-logging", this::reloadFileLogging)
            .add("sounds", this::reloadSoundFiles)
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
