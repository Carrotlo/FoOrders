package me.foesio.foOrders;

import me.foesio.core.dialog.DialogService;
import me.foesio.core.inventory.InventoryCloseSuppressor;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foOrders.integration.DiscordWebhookNotifier;
import me.foesio.foOrders.config.GuiConfigManager;
import me.foesio.foOrders.dialog.FoOrdersDialogInputService;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import net.milkbowl.vault.economy.Economy;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class OrdersMenuManager implements Listener {
    static final String ACCENT = ChatColor.of("#03fc88").toString();
    static final String LIGHT_ACCENT = ACCENT;
    static final String WHITE = ChatColor.of("#ffffff").toString();
    static final String MUTED = ChatColor.of("#a7b8b0").toString();
    static final String CANCEL_RED = ChatColor.of("#ff5d73").toString();
    static final String CONFIRM_GREEN = ChatColor.of("#3ecf8e").toString();
    static final String PROGRESS_LEFT = MUTED;
    static final String PROGRESS_RIGHT = CONFIRM_GREEN;
    static final String PROGRESS_LABEL = MUTED;
    static final String ACTIONBAR_ERROR_COLOR = CANCEL_RED;
    static final String LIGHT_GRAY = MUTED;
    static final String NOTIFY_BLUE = ACCENT;
    static final String EARN_GREEN = CONFIRM_GREEN;

    static final String TITLE_MAIN_PREFIX = "ᴏʀᴅᴇʀꜱ (Page ";
    static final String TITLE_YOUR_ORDERS = "ʏᴏᴜʀ ᴏʀᴅᴇʀꜱ";
    static final String TITLE_NEW_ORDER = "ɴᴇᴡ ᴏʀᴅᴇʀ";
    static final String TITLE_SELECT_ITEM = "ꜱᴇʟᴇᴄᴛ ɪᴛᴇᴍ";
    static final String TITLE_MANAGE_ORDER = "ᴍᴀɴᴀɢᴇ ᴏʀᴅᴇʀ";
    static final String TITLE_CLAIM_ORDER = "ᴄʟᴀɪᴍ ᴏʀᴅᴇʀ";
    static final String TITLE_DELIVER = "ᴅᴇʟɪᴠᴇʀ";
    static final String TITLE_DELIVERY_CONFIRM = "ᴄᴏɴꜰɪʀᴍᴀᴛɪᴏɴ";
    static final String TITLE_ENCHANTS = "ᴇɴᴄʜᴀɴᴛꜱ";
    static final String TITLE_ADMIN_ACTIONS = "ᴏʀᴅᴇʀ ᴀᴄᴛɪᴏɴꜱ";
    static final String TITLE_ADMIN_EDITOR = "ɪᴛᴇᴍ ᴇᴅɪᴛᴏʀ";
    static final String TITLE_ADMIN_EDITOR_EDIT = "ᴇᴅɪᴛ ɪᴛᴇᴍ";
    static final String TITLE_HISTORY_PREFIX = "ʜɪꜱᴛᴏʀʏ";

    static final List<Integer> MAIN_ORDER_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    );
    static final int MAIN_ORDERS_CAPACITY = MAIN_ORDER_SLOTS.size();
    static final int CLAIM_PAGE_CAPACITY = 45;
    static final int HISTORY_PAGE_CAPACITY = 45;
    static final int MIN_PLAYER_ORDERS = 1;
    static final int MAX_PLAYER_ORDERS_CAP = 26;
    static final long DEFAULT_ORDER_MENU_REFRESH_COOLDOWN_MILLIS = 50L;
    static final long MAIN_OPTION_REFRESH_DEBOUNCE_TICKS = 4L;
    static final long ITEM_SELECT_OPTION_REFRESH_DEBOUNCE_TICKS = 4L;
    static final double MIN_ORDER_TAX_PERCENTAGE = 0D;
    static final double MAX_ORDER_TAX_PERCENTAGE = 100D;
    static final int ITEM_SELECT_PAGE_SIZE = 45;
    static final int ENCHANT_SELECT_PAGE_SIZE = 45;

    static final int SORT_SLOT = 47;
    static final int FILTER_SLOT = 48;
    static final int REFRESH_SLOT = 49;
    static final int SEARCH_SLOT = 50;
    static final int YOUR_ORDERS_SLOT = 51;
    static final int HISTORY_SLOT = 52;
    static final int MAIN_BACK_SLOT = 45;
    static final int MAIN_NEXT_SLOT = 53;

    static final int CANCEL_SLOT = 10;
    static final int ITEM_SLOT = 12;
    static final int AMOUNT_SLOT = 13;
    static final int PRICE_SLOT = 14;
    static final int ENCHANT_SLOT = 15;
    static final int CONFIRM_SLOT = 16;

    static final int ITEM_SELECT_SORT_SLOT = 48;
    static final int ITEM_SELECT_FILTER_SLOT = 49;
    static final int ITEM_SELECT_SEARCH_SLOT = 50;
    static final int ITEM_SELECT_BACK_SLOT = 45;
    static final int ITEM_SELECT_NEXT_SLOT = 53;

    static final int MANAGE_CANCEL_SLOT = 11;
    static final int MANAGE_CLAIM_SLOT = 14;
    static final int MANAGE_ADMIN_ACTIONS_SLOT = 16;

    static final int CLAIM_BACK_SLOT = 45;
    static final int CLAIM_NEXT_SLOT = 53;
    static final int CLAIM_DROP_PAGE_SLOT = 52;

    static final int DELIVERY_CANCEL_SLOT = 11;
    static final int DELIVERY_CONFIRM_SLOT = 15;

    static final int ENCHANT_SELECT_BACK_SLOT = 45;
    static final int ENCHANT_SELECT_CLEAR_SLOT = 48;
    static final int ENCHANT_SELECT_DONE_SLOT = 49;
    static final int ENCHANT_SELECT_INFO_SLOT = 50;
    static final int ENCHANT_SELECT_NEXT_SLOT = 53;

    static final int ADMIN_PREVIEW_SLOT = 4;
    static final int ADMIN_DELETE_SLOT = 11;
    static final int ADMIN_CANCEL_SLOT = 15;

    static final int ADMIN_EDITOR_BACK_SLOT = 45;
    static final int ADMIN_EDITOR_ADD_SLOT = 49;
    static final int ADMIN_EDITOR_INFO_SLOT = 50;
    static final int ADMIN_EDITOR_NEXT_SLOT = 53;

    static final int ADMIN_EDIT_ID_SLOT = 10;
    static final int ADMIN_EDIT_COPY_HAND_SLOT = 11;
    static final int ADMIN_EDIT_TEMPLATE_SLOT = 13;
    static final int ADMIN_EDIT_ENCHANTABLE_SLOT = 15;
    static final int ADMIN_EDIT_DELETE_SLOT = 29;
    static final int ADMIN_EDIT_SAVE_SLOT = 31;
    static final int ADMIN_EDIT_CANCEL_SLOT = 49;

    static final int HISTORY_INFO_SLOT = 46;
    static final int HISTORY_ORDER_TAB_SLOT = 48;
    static final int HISTORY_BACK_TO_ORDERS_SLOT = 49;
    static final int HISTORY_DELIVER_TAB_SLOT = 50;
    static final int HISTORY_BACK_SLOT = 45;
    static final int HISTORY_NEXT_SLOT = 53;

    static final String ADMIN_PERMISSION = "foorders.admin";

    static final List<String> SORT_OPTIONS = List.of(
        "Most Paid",
        "Most Delivered",
        "Recently Listed",
        "Most Money Per Item"
    );
    static final List<String> FILTER_OPTIONS = List.of(
        "All",
        "Blocks",
        "Tools",
        "Food",
        "Combat",
        "Potions",
        "Books",
        "Ingredients",
        "Utilities"
    );
    static final List<String> ITEM_SORT_OPTIONS = List.of("A-Z", "Z-A");

    static final List<Material> ALL_ORDERABLE_ITEMS = List.of(Material.values()).stream()
        .filter(Material::isItem)
        .filter(material -> !Tag.AIR.isTagged(material))
        .filter(material -> !material.isLegacy())
        .collect(Collectors.toList());
    static final List<Enchantment> ALL_ENCHANTMENTS = List.of(Enchantment.values()).stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(enchantment -> enchantment.getKey().toString()))
        .collect(Collectors.toList());

    final FoOrders plugin;
    final FoScheduler scheduler;
    final PlayerDataStore playerDataStore;
    final CustomItemStore customItemStore;
    final HistoryDataStore historyDataStore;
    final DiscordWebhookNotifier discordWebhookNotifier;
    final OrdersMenuItemSupport itemSupport;
    final PluginMessages messages;
    final GuiConfigManager guiConfigManager;
    final InventoryCloseSuppressor inventoryCloseSuppressor;
    final InventoryDepositService inventoryDepositService;
    final FoFileLogger fileLogger;
    final DialogService dialogService;
    OrdersMenuViewSupport viewSupport;
    OrdersMenuInteractionSupport interactionSupport;
    volatile FoOrdersDialogInputService dialogInputService;
    volatile FoOrdersItemSelectionDialogService itemSelectionDialogService;
    final DateTimeFormatter historyTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    final Map<UUID, MenuViewState> menuStates = new ConcurrentHashMap<>();
    final Map<UUID, Long> orderMenuRefreshNanos = new ConcurrentHashMap<>();
    final Map<UUID, Integer> pendingMainMenuRefreshIds = new ConcurrentHashMap<>();
    final Map<UUID, Integer> pendingItemSelectRefreshIds = new ConcurrentHashMap<>();
    final AtomicInteger mainMenuRefreshSequence = new AtomicInteger();
    final AtomicInteger itemSelectRefreshSequence = new AtomicInteger();
    final AtomicInteger itemSelectContentRevision = new AtomicInteger();
    final Map<UUID, PendingDeliveryState> pendingDeliveries = new ConcurrentHashMap<>();
    final Set<String> activeDeliveryOrderLocks = ConcurrentHashMap.newKeySet();
    final Set<UUID> waitingDeliveryPlayers = ConcurrentHashMap.newKeySet();
    final Object economyLock = new Object();
    volatile Set<Material> blacklistedOrderMaterials = Set.of();
    volatile Set<String> blacklistedOrderNames = Set.of();
    volatile Economy economy;
    volatile int maxOrdersPerPlayer = 3;
    volatile long orderMenuRefreshCooldownMillis = DEFAULT_ORDER_MENU_REFRESH_COOLDOWN_MILLIS;
    volatile boolean announceCreatedOrdersInChat = false;
    volatile double orderTaxPercentage = 0D;
    volatile boolean historyEnabled = true;
    volatile boolean historyPlayersCanViewOwn = true;
    volatile boolean historyAdminsCanViewAny = true;
    volatile int historyMaxEntriesPerType = 100;
    volatile boolean itemSelectionDialogsEnabled = true;

    public OrdersMenuManager(
        FoOrders plugin,
        FoScheduler scheduler,
        PlayerDataStore playerDataStore,
        CustomItemStore customItemStore,
        HistoryDataStore historyDataStore,
        PluginMessages messages,
        GuiConfigManager guiConfigManager,
        FoOrdersDialogInputService dialogInputService,
        DialogService dialogService,
        InventoryCloseSuppressor inventoryCloseSuppressor,
        InventoryDepositService inventoryDepositService,
        FoFileLogger fileLogger
    ) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.playerDataStore = playerDataStore;
        this.customItemStore = customItemStore;
        this.historyDataStore = historyDataStore;
        this.messages = messages;
        this.guiConfigManager = guiConfigManager;
        this.dialogInputService = dialogInputService;
        this.dialogService = dialogService;
        this.inventoryCloseSuppressor = inventoryCloseSuppressor;
        this.inventoryDepositService = inventoryDepositService;
        this.fileLogger = fileLogger;
        this.discordWebhookNotifier = new DiscordWebhookNotifier(plugin);
        this.itemSupport = new OrdersMenuItemSupport(this);
        this.viewSupport = new OrdersMenuViewSupport(this);
        this.interactionSupport = new OrdersMenuInteractionSupport(this);
    }

    public PluginMessages messages() {
        return messages;
    }

    FoOrdersDialogInputService dialogInputService() {
        return dialogInputService;
    }

    DialogService dialogService() {
        return dialogService;
    }

    FoOrdersItemSelectionDialogService itemSelectionDialogService() {
        FoOrdersItemSelectionDialogService currentService = itemSelectionDialogService;
        if (currentService != null) {
            return currentService;
        }
        if (dialogService == null || dialogService.support() == null || !dialogService.support().canUseNativeDialogs()) {
            return null;
        }
        try {
            Class<?> serviceClass = Class.forName("me.foesio.foOrders.FoOrdersPaperItemSelectionDialogService");
            Constructor<?> constructor = serviceClass.getDeclaredConstructor(OrdersMenuManager.class);
            constructor.setAccessible(true);
            currentService = (FoOrdersItemSelectionDialogService) constructor.newInstance(this);
            itemSelectionDialogService = currentService;
            return currentService;
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            dialogService.support().disableForSession("FoOrders item selection dialog unavailable: " + exception.getMessage());
            warn("Native item selection dialog unavailable. Using inventory fallback: " + exception.getMessage());
            return null;
        }
    }

    void setDialogInputService(FoOrdersDialogInputService dialogInputService) {
        this.dialogInputService = dialogInputService;
    }

    GuiConfigManager guis() {
        return guiConfigManager;
    }

    GuiButtonConfig guiButtons() {
        return guiConfigManager.buttons();
    }

    int itemSelectContentRevision() {
        return itemSelectContentRevision.get();
    }

    void invalidateItemSelectCaches() {
        itemSelectContentRevision.incrementAndGet();
        itemSupport.clearItemSelectCaches();
        FoOrdersItemSelectionDialogService currentService = itemSelectionDialogService;
        if (currentService != null) {
            currentService.clearCache();
        }
    }

    FoFileLogger fileLogger() {
        return fileLogger;
    }

    public void setEconomy(Economy economy) {
        synchronized (economyLock) {
            this.economy = economy;
        }
    }


    public void openOrdersMenu(Player player, String searchText) {
        viewSupport.openOrdersMenu(player, searchText);
    }

    public boolean openOrdersMenuFromCommand(Player player, String searchText) {
        if (!tryMarkOrderMenuRefresh(player)) {
            return false;
        }
        openOrdersMenu(player, searchText);
        return true;
    }

    void openNewOrderMenu(Player player) {
        viewSupport.openNewOrderMenu(player);
    }

    void openItemSelectMenu(Player player, boolean resetPage) {
        viewSupport.openItemSelectMenu(player, resetPage);
    }

    public void openAdminItemEditor(Player player) {
        viewSupport.openAdminItemEditor(player);
    }

    public void openHistoryMenu(Player viewer, UUID targetPlayerId, HistoryDataStore.HistoryType historyType, boolean fromAdminCommand) {
        viewSupport.openHistoryMenu(viewer, targetPlayerId, historyType, fromAdminCommand);
    }

    private String normalizeBlacklistedName(String name) {
        return itemSupport.normalizeBlacklistedName(name);
    }

    private void returnPendingItems(Player player, ItemStack[] submittedItems) {
        interactionSupport.returnPendingItems(player, submittedItems);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        interactionSupport.onInventoryClick(event);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        interactionSupport.onInventoryDrag(event);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        interactionSupport.onInventoryClose(event);
    }

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        interactionSupport.onPlayerSwapHand(event);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        interactionSupport.onPlayerJoin(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        orderMenuRefreshNanos.remove(playerId);
        pendingMainMenuRefreshIds.remove(playerId);
        pendingItemSelectRefreshIds.remove(playerId);
        FoOrdersItemSelectionDialogService currentService = itemSelectionDialogService;
        if (currentService != null) {
            currentService.clearPending(playerId);
        }
        interactionSupport.onPlayerQuit(event);
    }

    boolean isOrdersMenu(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        return inventory.getHolder() instanceof OrdersMenuHolder;
    }

    public void initializeOnlinePlayers() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            UUID playerId = onlinePlayer.getUniqueId();
            playerDataStore.getOrCreate(playerId);
            historyDataStore.getOrderHistory(playerId);
        }
    }

    public void reloadFromConfig() {
        fileLogger.info("Runtime config reload started.");
        int configured = plugin.getConfig().getInt("max-order-per-player", 3);
        maxOrdersPerPlayer = Math.max(MIN_PLAYER_ORDERS, Math.min(MAX_PLAYER_ORDERS_CAP, configured));
        if (configured != maxOrdersPerPlayer) {
            warn("max-order-per-player is out of range; using " + maxOrdersPerPlayer + " (allowed " + MIN_PLAYER_ORDERS + "-" + MAX_PLAYER_ORDERS_CAP + ").");
        }
        long configuredRefreshCooldown = plugin.getConfig().getLong(
            "order-menu-refresh-cooldown-ms",
            DEFAULT_ORDER_MENU_REFRESH_COOLDOWN_MILLIS
        );
        orderMenuRefreshCooldownMillis = Math.max(0L, configuredRefreshCooldown);
        if (configuredRefreshCooldown != orderMenuRefreshCooldownMillis) {
            warn("order-menu-refresh-cooldown-ms is out of range; using " + orderMenuRefreshCooldownMillis + ".");
        }
        announceCreatedOrdersInChat = plugin.getConfig().getBoolean("announce-created-orders-in-chat", false);
        double configuredOrderTaxPercentage = plugin.getConfig().getDouble("order-tax.percentage", 0D);
        orderTaxPercentage = Math.max(
            MIN_ORDER_TAX_PERCENTAGE,
            Math.min(MAX_ORDER_TAX_PERCENTAGE, configuredOrderTaxPercentage)
        );
        if (configuredOrderTaxPercentage != orderTaxPercentage) {
            warn("order-tax.percentage is out of range; using " + orderTaxPercentage + " (allowed 0-100).");
        }
        historyEnabled = plugin.getConfig().getBoolean("history.enabled", true);
        historyPlayersCanViewOwn = plugin.getConfig().getBoolean("history.players-can-view-own", true);
        historyAdminsCanViewAny = plugin.getConfig().getBoolean("history.admins-can-view-any", true);
        itemSelectionDialogsEnabled = plugin.getConfig().getBoolean("native-dialogs.item-selection", true);
        int configuredHistoryMaxEntries = plugin.getConfig().getInt("history.max-entries-per-type", 100);
        historyMaxEntriesPerType = Math.max(1, configuredHistoryMaxEntries);
        if (configuredHistoryMaxEntries != historyMaxEntriesPerType) {
            warn("history.max-entries-per-type is out of range; using " + historyMaxEntriesPerType + ".");
        }
        historyDataStore.setMaxEntriesPerType(historyMaxEntriesPerType);

        reloadOrderBlacklistsFromConfig();
        discordWebhookNotifier.reloadFromConfig(plugin.getConfig());
        fileLogger.info("Runtime config reload completed.");
    }

    double calculateOrderTax(double subtotal) {
        if (subtotal <= 0D || !Double.isFinite(subtotal) || orderTaxPercentage <= 0D) {
            return 0D;
        }
        double tax = subtotal * orderTaxPercentage / 100D;
        return Double.isFinite(tax) && tax > 0D ? tax : 0D;
    }

    double calculateOrderCreationCost(double subtotal) {
        double total = subtotal + calculateOrderTax(subtotal);
        return Double.isFinite(total) ? total : Double.NaN;
    }

    boolean tryMarkOrderMenuRefresh(Player player) {
        if (player == null) {
            return false;
        }

        long cooldownMillis = orderMenuRefreshCooldownMillis;
        if (cooldownMillis <= 0L) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        long now = System.nanoTime();
        long cooldownNanos = Math.min(cooldownMillis, Long.MAX_VALUE / 1_000_000L) * 1_000_000L;
        Long previous = orderMenuRefreshNanos.get(playerId);
        if (previous != null && now - previous < cooldownNanos) {
            return false;
        }

        orderMenuRefreshNanos.put(playerId, now);
        return true;
    }

    private void reloadOrderBlacklistsFromConfig() {
        Set<Material> parsedMaterials = new HashSet<>();
        for (String configuredMaterial : plugin.getConfig().getStringList("blacklist-items")) {
            Material material = resolveConfiguredMaterial(configuredMaterial);
            if (material == null) {
                if (configuredMaterial != null && !configuredMaterial.isBlank()) {
                    warn("Ignoring invalid blacklist-items entry: '" + configuredMaterial + "'.");
                }
                continue;
            }
            parsedMaterials.add(material);
        }
        blacklistedOrderMaterials = Set.copyOf(parsedMaterials);

        Set<String> parsedNames = new HashSet<>();
        for (String configuredName : plugin.getConfig().getStringList("blacklist-names")) {
            String normalizedName = normalizeBlacklistedName(configuredName);
            if (normalizedName.isBlank()) {
                continue;
            }
            parsedNames.add(normalizedName);
        }
        blacklistedOrderNames = Set.copyOf(parsedNames);
        invalidateItemSelectCaches();
    }

    private Material resolveConfiguredMaterial(String configuredMaterial) {
        if (configuredMaterial == null || configuredMaterial.isBlank()) {
            return null;
        }
        String raw = configuredMaterial.trim();
        Material resolved = Material.matchMaterial(raw);
        if (resolved == null) {
            resolved = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
        }
        if (resolved == null && !raw.contains(":")) {
            resolved = Material.matchMaterial("minecraft:" + raw.toLowerCase(Locale.ROOT));
        }
        if (resolved == null || !resolved.isItem()) {
            return null;
        }
        return resolved;
    }

    public void saveAllData() {
        fileLogger.info("Saving all FoOrders data.");
        for (Map.Entry<UUID, PendingDeliveryState> entry : new ArrayList<>(pendingDeliveries.entrySet())) {
            PendingDeliveryState pending = pendingDeliveries.remove(entry.getKey());
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && pending != null) {
                returnPendingItems(player, pending.submittedItems());
            }
            waitingDeliveryPlayers.remove(entry.getKey());
        }
        playerDataStore.saveAll();
        historyDataStore.saveAll();
        fileLogger.info("All FoOrders data saved.");
    }

    private void warn(String message) {
        plugin.getLogger().warning(message);
        fileLogger.warn(message);
    }
}
