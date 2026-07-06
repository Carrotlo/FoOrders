package me.foesio.foOrders;

import me.foesio.foOrders.config.GuiConfigManager;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import me.foesio.foOrders.util.TextFormat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuViewSupport {
    private final OrdersMenuManager manager;
    private final FoScheduler scheduler;
    private final DateTimeFormatter historyTimestampFormatter;
    private final Map<UUID, MenuViewState> menuStates;
    private final Map<UUID, PendingDeliveryState> pendingDeliveries;
    private final Set<UUID> waitingDeliveryPlayers;
    private final PlayerDataStore playerDataStore;
    private final CustomItemStore customItemStore;
    private final HistoryDataStore historyDataStore;
    private final Map<StaticGuiItemKey, ItemStack> staticGuiItemTemplates = new ConcurrentHashMap<>();
    private final Map<CyclingGuiItemKey, ItemStack> cyclingGuiItemTemplates = new ConcurrentHashMap<>();
    private volatile int staticGuiItemRevision = -1;

    OrdersMenuViewSupport(OrdersMenuManager manager) {
        this.manager = manager;
        this.scheduler = manager.scheduler;
        this.historyTimestampFormatter = manager.historyTimestampFormatter;
        this.menuStates = manager.menuStates;
        this.pendingDeliveries = manager.pendingDeliveries;
        this.waitingDeliveryPlayers = manager.waitingDeliveryPlayers;
        this.playerDataStore = manager.playerDataStore;
        this.customItemStore = manager.customItemStore;
        this.historyDataStore = manager.historyDataStore;
    }

    private void clearAdminTarget(MenuViewState viewState) {
        manager.interactionSupport.clearAdminTarget(viewState);
    }

    private void clearClaimSession(MenuViewState viewState) {
        manager.interactionSupport.clearClaimSession(viewState);
    }

    private int getMaxOrdersForPlayer(Player player) {
        return manager.interactionSupport.getMaxOrdersForPlayer(player);
    }

    private boolean removeCompletedOrders(PlayerDataStore.PlayerData playerData) {
        return manager.interactionSupport.removeCompletedOrders(playerData);
    }

    private boolean canOpenOwnHistory(Player player) {
        return manager.interactionSupport.canOpenOwnHistory(player);
    }

    private boolean canAccessHistory(Player viewer, UUID targetPlayerId) {
        return manager.interactionSupport.canAccessHistory(viewer, targetPlayerId);
    }

    private boolean canModerateOrders(Player player) {
        return manager.interactionSupport.canModerateOrders(player);
    }

    private double getRemainingOrderFunds(PlayerDataStore.OrderEntry order) {
        return manager.interactionSupport.getRemainingOrderFunds(order);
    }

    private PlayerDataStore.OrderEntry getSelectedOrder(PlayerDataStore.PlayerData playerData, MenuViewState viewState) {
        return manager.interactionSupport.getSelectedOrder(playerData, viewState);
    }

    private List<Integer> initializeClaimSessionStacks(MenuViewState viewState, PlayerDataStore.OrderEntry order, boolean forceReset) {
        return manager.interactionSupport.initializeClaimSessionStacks(viewState, order, forceReset);
    }

    private List<Integer> getClaimSessionPageStackAmounts(List<Integer> claimSessionStacks, int page) {
        return manager.interactionSupport.getClaimSessionPageStackAmounts(claimSessionStacks, page);
    }

    private int calculateClaimSessionPageCount(List<Integer> claimSessionStacks) {
        return manager.interactionSupport.calculateClaimSessionPageCount(claimSessionStacks);
    }

    private List<String> createManageClaimLore(PlayerDataStore.OrderEntry order) {
        return manager.interactionSupport.createManageClaimLore(order);
    }

    private PlayerDataStore.OrderEntry findOrderById(PlayerDataStore.PlayerData playerData, String orderId) {
        return manager.interactionSupport.findOrderById(playerData, orderId);
    }

    private void returnPendingItems(Player player, ItemStack[] submittedItems) {
        manager.interactionSupport.returnPendingItems(player, submittedItems);
    }

    private Inventory createMenu(MenuType menuType, int size, String title) {
        return manager.itemSupport.createMenu(menuType, size, title);
    }

    private void openMenu(Player player, Inventory menu) {
        manager.itemSupport.openMenu(player, menu);
    }

    private ItemStack createSimpleItem(Material material, String displayName, List<String> loreLines) {
        return manager.itemSupport.createSimpleItem(material, displayName, loreLines);
    }

    private ItemStack createGuiItem(String path, Material material, String displayName, List<String> loreLines) {
        int revision = refreshGuiItemCachesIfNeeded();
        StaticGuiItemKey key = new StaticGuiItemKey(revision, path, material, displayName, loreLines);
        ItemStack template = staticGuiItemTemplates.computeIfAbsent(key, ignored -> {
            GuiConfigManager.GuiItem item = manager.guis().item(path, material, displayName, loreLines);
            return createSimpleItem(item.material(), item.name(), item.lore());
        });
        return template.clone();
    }

    private ItemStack createGuiItem(String path, Material material, String displayName, List<String> loreLines, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return createGuiItem(path, material, displayName, loreLines);
        }
        GuiConfigManager.GuiItem item = manager.guis().item(path, material, displayName, loreLines, placeholders);
        return createSimpleItem(item.material(), item.name(), item.lore());
    }

    private ItemStack createGuiItem(
        String path,
        Material material,
        String displayName,
        List<String> loreLines,
        Map<String, String> placeholders,
        List<String> hiddenLorePlaceholders
    ) {
        GuiConfigManager.GuiItem item = manager.guis().item(
            path,
            material,
            displayName,
            loreLines,
            placeholders,
            hiddenLorePlaceholders
        );
        return createSimpleItem(item.material(), item.name(), item.lore());
    }

    private ItemStack createGuiCyclingItem(String path, Material material, String displayName, List<String> options, int selectedIndex, String accentColor, String defaultColor) {
        int revision = refreshGuiItemCachesIfNeeded();
        CyclingGuiItemKey key = new CyclingGuiItemKey(revision, path, material, displayName, options, selectedIndex, accentColor, defaultColor);
        ItemStack template = cyclingGuiItemTemplates.computeIfAbsent(key, ignored -> {
            GuiConfigManager.GuiItem item = manager.guis().item(path, material, displayName, List.of());
            List<String> lore = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                lore.add((i == selectedIndex ? accentColor : defaultColor) + "• " + options.get(i));
            }
            return createSimpleItem(item.material(), item.name(), lore);
        });
        return template.clone();
    }

    private String guiTitle(String path, String fallback) {
        return manager.guis().title(path, fallback);
    }

    private String guiTitle(String path, String fallback, Map<String, String> placeholders) {
        return manager.guis().title(path, fallback, placeholders);
    }

    private List<String> guiLabels(String path, List<String> fallback) {
        return manager.guis().labels(path, fallback);
    }

    private int guiItemSlot(String path, int fallback, int inventorySize) {
        return manager.guis().itemSlot(path, fallback, inventorySize);
    }

    private List<Integer> guiSlots(String path, List<Integer> fallback, int inventorySize) {
        return manager.guis().slots(path, fallback, inventorySize);
    }

    private ItemStack createSearchGuiItem(String searchText) {
        return manager.guiButtons().search(searchText);
    }

    private ItemStack createCyclingItem(Material material, String name, List<String> options, int selectedIndex) {
        return manager.itemSupport.createCyclingItem(material, name, options, selectedIndex);
    }

    private ItemStack createCyclingItem(
        Material material,
        String name,
        List<String> options,
        int selectedIndex,
        String accentColor,
        String defaultColor
    ) {
        return manager.itemSupport.createCyclingItem(material, name, options, selectedIndex, accentColor, defaultColor);
    }

    private ItemStack createOrderItem(String ownerName, PlayerDataStore.OrderEntry order, boolean showAdminModerationLore) {
        return manager.itemSupport.createOrderItem(ownerName, order, showAdminModerationLore);
    }

    private ItemStack createOrderStack(PlayerDataStore.OrderEntry order, int amount) {
        return manager.itemSupport.createOrderStack(order, amount);
    }

    private CustomItemStore.CustomItemDefinition resolveCustomItemDefinition(String customItemId) {
        return manager.itemSupport.resolveCustomItemDefinition(customItemId);
    }

    private Material resolveMaterial(String materialName) {
        return manager.itemSupport.resolveMaterial(materialName);
    }

    private Map<String, Integer> sanitizeDraftEnchantments(String customItemId, Material material, Map<String, Integer> rawEnchantments) {
        return manager.itemSupport.sanitizeDraftEnchantments(customItemId, material, rawEnchantments);
    }

    private String formatMaterialName(Material material) {
        return manager.itemSupport.formatMaterialName(material);
    }

    private String resolveTemplateDisplayName(ItemStack template) {
        return manager.itemSupport.resolveTemplateDisplayName(template);
    }

    private String formatCompactAmount(double value) {
        return manager.itemSupport.formatCompactAmount(value);
    }

    private String formatTaxPercentage(double value) {
        return manager.itemSupport.formatMoney(value);
    }

    private boolean supportsOrderEnchantments(String customItemId, Material material) {
        return manager.itemSupport.supportsOrderEnchantments(customItemId, material);
    }

    private List<String> buildEnchantmentSummaryLore(Map<String, Integer> enchantments, int maxLines) {
        return manager.itemSupport.buildEnchantmentSummaryLore(enchantments, maxLines);
    }

    private List<OrderableItemOption> getFilteredSortedItems(ItemSelectState itemSelectState) {
        return manager.itemSupport.getCachedFilteredSortedItems(itemSelectState);
    }

    private ItemStack createOrderableSelectItem(OrderableItemOption option) {
        return manager.itemSupport.createOrderableSelectItem(option);
    }

    private Material resolveDraftMaterial(NewOrderDraft draft) {
        return manager.itemSupport.resolveDraftMaterial(draft);
    }

    private List<Enchantment> getSelectableEnchantments(Material material) {
        return manager.itemSupport.getSelectableEnchantments(material);
    }

    private ItemStack createEnchantSelectionItem(Material material, Enchantment enchantment, int selectedLevel) {
        return manager.itemSupport.createEnchantSelectionItem(material, enchantment, selectedLevel);
    }

    private String resolvePlayerName(UUID playerId) {
        return manager.itemSupport.resolvePlayerName(playerId);
    }

    private boolean matchesItemFilter(Material material, int filterIndex) {
        return manager.itemSupport.matchesItemFilter(material, filterIndex);
    }

    private String buildOrderSearchText(PlayerDataStore.OrderEntry order) {
        return manager.itemSupport.buildOrderSearchText(order);
    }

    private List<CustomItemStore.CustomItemDefinition> getSortedCustomItems() {
        return manager.itemSupport.getSortedCustomItems();
    }

    void openOrdersMenu(Player player, String searchText) {
        UUID playerId = player.getUniqueId();
        manager.pendingMainMenuRefreshIds.remove(playerId);
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        prepareMainMenuState(viewState);

        if (searchText != null) {
            viewState.search = searchText.trim();
            viewState.page = 1;
        }

        List<Integer> orderSlots = guiSlots("layout.main.order-slots", MAIN_ORDER_SLOTS, 54);
        List<MainOrderView> visibleOrders = getVisibleMainOrders(viewState, playerData);
        viewState.visibleMainOrders = visibleOrders;
        int pageCount = calculatePageCount(visibleOrders.size(), orderSlots.size());
        if (viewState.page < 1) {
            viewState.page = 1;
        }
        if (viewState.page > pageCount) {
            viewState.page = pageCount;
        }

        Inventory menu = createMenu(MenuType.MAIN, 54, mainMenuTitle(viewState));
        populateMainMenuContents(player, menu, viewState, playerData, orderSlots, visibleOrders, pageCount);

        openMenu(player, menu);
    }

    private void prepareMainMenuState(MenuViewState viewState) {
        clearAdminTarget(viewState);
        viewState.historyTargetId = null;
        viewState.historyType = HistoryDataStore.HistoryType.ORDER;
        viewState.historyPage = 1;
    }

    private String mainMenuTitle(MenuViewState viewState) {
        return guiTitle(
            "main",
            TITLE_MAIN_PREFIX + viewState.page + ")",
            TextFormat.placeholders("page", viewState.page)
        );
    }

    private void populateMainMenuContents(
        Player player,
        Inventory menu,
        MenuViewState viewState,
        PlayerDataStore.PlayerData playerData,
        List<Integer> orderSlots,
        List<MainOrderView> visibleOrders,
        int pageCount
    ) {
        menu.clear();
        int inventorySize = menu.getSize();
        int mainBackSlot = guiItemSlot("main.previous-page", MAIN_BACK_SLOT, inventorySize);
        int mainNextSlot = guiItemSlot("main.next-page", MAIN_NEXT_SLOT, inventorySize);
        int sortSlot = guiItemSlot("main.sort", SORT_SLOT, inventorySize);
        int filterSlot = guiItemSlot("main.filter", FILTER_SLOT, inventorySize);
        int refreshSlot = guiItemSlot("main.refresh", REFRESH_SLOT, inventorySize);
        int searchSlot = guiItemSlot("main.search", SEARCH_SLOT, inventorySize);
        int yourOrdersSlot = guiItemSlot("main.your-orders", YOUR_ORDERS_SLOT, inventorySize);
        int historySlot = guiItemSlot("main.history", HISTORY_SLOT, inventorySize);
        warnMainSlotOverlaps(
            orderSlots,
            new String[]{
                "main.previous-page", "main.next-page", "main.sort", "main.filter",
                "main.refresh", "main.search", "main.your-orders", "main.history"
            },
            new int[]{
                mainBackSlot, mainNextSlot, sortSlot, filterSlot,
                refreshSlot, searchSlot, yourOrdersSlot, historySlot
            }
        );

        populateMainOrderFrame(menu, orderSlots);
        populateMainOrders(player, menu, visibleOrders, viewState.page, orderSlots);

        if (viewState.page > 1) {
            menu.setItem(mainBackSlot, manager.guiButtons().previousPage(viewState.page - 1, pageCount - 1));
        }
        if (viewState.page < pageCount) {
            menu.setItem(mainNextSlot, manager.guiButtons().nextPage(viewState.page - 1, pageCount - 1));
        }

        menu.setItem(sortSlot, createGuiCyclingItem("main.sort", Material.CAULDRON, ACCENT + "ꜱᴏʀᴛ", guiLabels("main-sort-options", SORT_OPTIONS), playerData.getSortIndex(), ACCENT, WHITE));
        menu.setItem(filterSlot, createGuiCyclingItem("main.filter", Material.HOPPER, ACCENT + "ꜰɪʟᴛᴇʀ", guiLabels("filter-options", FILTER_OPTIONS), playerData.getFilterIndex(), ACCENT, WHITE));
        menu.setItem(refreshSlot, createGuiItem("main.refresh", Material.MAP, ACCENT + "ᴏʀᴅᴇʀꜱ", List.of(WHITE + "Click to refresh")));
        menu.setItem(searchSlot, createSearchGuiItem(viewState.search));
        menu.setItem(yourOrdersSlot, createGuiItem("main.your-orders", Material.BOOK, ACCENT + "ʏᴏᴜʀ ᴏʀᴅᴇʀꜱ", List.of(WHITE + "Click to view your Orders")));
        if (canOpenOwnHistory(player)) {
            menu.setItem(historySlot, createGuiItem("main.history", Material.WRITABLE_BOOK, ACCENT + "ʜɪꜱᴛᴏʀʏ", List.of(
                WHITE + "Click to view order history",
                WHITE + "Switch between order and deliver tabs"
            )));
        }
    }

    void openYourOrdersMenu(Player player) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        clearAdminTarget(viewState);
        viewState.manageOrderIndex = -1;
        viewState.claimPage = 1;
        clearClaimSession(viewState);
        int playerMaxOrders = getMaxOrdersForPlayer(player);
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        if (removeCompletedOrders(playerData)) {
            playerDataStore.save(playerId);
        }

        Inventory menu = createMenu(MenuType.YOUR_ORDERS, 27, guiTitle("your-orders", TITLE_YOUR_ORDERS));

        List<PlayerDataStore.OrderEntry> playerOrders = playerData.getOrders();
        int newOrderSlot = guiItemSlot("your-orders.new-order", Math.min(playerOrders.size(), playerMaxOrders), 27);
        List<Integer> orderSlots = yourOrderSlots(newOrderSlot);
        int displayCount = Math.min(playerOrders.size(), Math.min(playerMaxOrders, orderSlots.size()));
        for (int i = 0; i < displayCount; i++) {
            menu.setItem(orderSlots.get(i), createOrderItem(player.getName(), playerOrders.get(i), false));
        }

        menu.setItem(newOrderSlot, createGuiItem("your-orders.new-order", Material.MAP, ACCENT + "New Order", List.of(WHITE + "Click to create new order")));

        openMenu(player, menu);
    }

    List<Integer> yourOrderSlots(int newOrderSlot) {
        List<Integer> slots = new ArrayList<>(26);
        for (int slot = 0; slot < 27; slot++) {
            if (slot != newOrderSlot) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    void openNewOrderMenu(Player player) {
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        viewState.newOrderConfirmLocked = false;
        NewOrderDraft draft = viewState.getOrCreateDraft();

        int inventorySize = 27;
        int cancelSlot = guiItemSlot("new-order.cancel", CANCEL_SLOT, inventorySize);
        int itemSlot = guiItemSlot("new-order.item", ITEM_SLOT, inventorySize);
        int amountSlot = guiItemSlot("new-order.amount", AMOUNT_SLOT, inventorySize);
        int priceSlot = guiItemSlot("new-order.price", PRICE_SLOT, inventorySize);
        int enchantSlot = guiItemSlot("new-order.enchants", ENCHANT_SLOT, inventorySize);
        int confirmSlot = guiItemSlot("new-order.confirm", CONFIRM_SLOT, inventorySize);

        Inventory menu = createMenu(MenuType.NEW_ORDER, inventorySize, guiTitle("new-order", TITLE_NEW_ORDER));
        menu.setItem(cancelSlot, createGuiItem("new-order.cancel", Material.RED_STAINED_GLASS_PANE, CANCEL_RED + "ᴄᴀɴᴄᴇʟ", List.of(WHITE + "Click to return")));

        CustomItemStore.CustomItemDefinition customSelection = resolveCustomItemDefinition(draft.customItemId());
        if (draft.customItemId() != null && customSelection == null) {
            draft = draft.withSelection(null, draft.materialName());
            viewState.draft = draft;
        }

        Material selectedMaterial = customSelection == null
            ? resolveMaterial(draft.materialName())
            : customSelection.template().getType();
        Map<String, Integer> sanitizedEnchantments = sanitizeDraftEnchantments(draft.customItemId(), selectedMaterial, draft.enchantLevels());
        if (!sanitizedEnchantments.equals(draft.enchantLevels())) {
            draft = draft.withEnchantLevels(sanitizedEnchantments);
            viewState.draft = draft;
        }

        String selectedItemName = customSelection == null
            ? formatMaterialName(selectedMaterial)
            : resolveTemplateDisplayName(customSelection.template());
        List<String> itemLore = new ArrayList<>();
        itemLore.add(WHITE + "Click to choose item");
        itemLore.add(MUTED + "(" + selectedItemName + ")");
        if (customSelection != null) {
            itemLore.add(MUTED + "(Custom ID: " + customSelection.id() + ")");
        }
        menu.setItem(itemSlot, createGuiItem("new-order.item", selectedMaterial, LIGHT_ACCENT + "ɪᴛᴇᴍ", itemLore, TextFormat.placeholders(
            "item", selectedItemName,
            "custom_id", customSelection == null ? "" : customSelection.id()
        )));
        menu.setItem(amountSlot, createGuiItem("new-order.amount", Material.CHEST, LIGHT_ACCENT + "ᴀᴍᴏᴜɴᴛ", List.of(
            WHITE + "Click to type number of items",
            MUTED + "(" + formatCompactAmount(draft.amount()) + ")"
        ), TextFormat.placeholders("amount", formatCompactAmount(draft.amount()))));
        menu.setItem(priceSlot, createGuiItem("new-order.price", Material.EMERALD, LIGHT_ACCENT + "ᴘʀɪᴄᴇ", List.of(
            WHITE + "Click to type the price per item",
            MUTED + "($" + formatCompactAmount(draft.pricePerItem()) + ")"
        ), TextFormat.placeholders("price", formatCompactAmount(draft.pricePerItem()))));

        if (supportsOrderEnchantments(draft.customItemId(), selectedMaterial)) {
            List<String> enchantLore = new ArrayList<>();
            enchantLore.add(WHITE + "Click to edit enchantments");
            if (sanitizedEnchantments.isEmpty()) {
                enchantLore.add(MUTED + "(None selected)");
            } else {
                enchantLore.add(MUTED + "(" + formatCompactAmount(sanitizedEnchantments.size()) + " selected)");
                enchantLore.addAll(buildEnchantmentSummaryLore(sanitizedEnchantments, 2));
            }
            menu.setItem(enchantSlot, createGuiItem("new-order.enchants", Material.ENCHANTED_BOOK, LIGHT_ACCENT + "ᴇɴᴄʜᴀɴᴛꜱ", enchantLore, TextFormat.placeholders(
                "selected", formatCompactAmount(sanitizedEnchantments.size())
            )));
        }

        double subtotal = draft.amount() * draft.pricePerItem();
        double tax = manager.calculateOrderTax(subtotal);
        double total = manager.calculateOrderCreationCost(subtotal);
        String taxPercent = formatTaxPercentage(manager.orderTaxPercentage);
        List<String> hiddenTaxLore = tax <= 0D ? List.of("tax", "tax_percent") : List.of();
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add(WHITE + "Click to confirm order");
        confirmLore.add(MUTED + "Subtotal: " + ACCENT + "$" + formatCompactAmount(subtotal));
        if (tax > 0D) {
            confirmLore.add(MUTED + "Tax (" + taxPercent + "%): " + ACCENT + "$" + formatCompactAmount(tax));
        }
        confirmLore.add(MUTED + "Total: " + CONFIRM_GREEN + "$" + formatCompactAmount(total));
        menu.setItem(confirmSlot, createGuiItem("new-order.confirm", Material.LIME_STAINED_GLASS_PANE, CONFIRM_GREEN + "ᴄᴏɴꜰɪʀᴍ", confirmLore, TextFormat.placeholders(
            "subtotal", formatCompactAmount(subtotal),
            "tax_percent", taxPercent,
            "tax", formatCompactAmount(tax),
            "total", formatCompactAmount(total)
        ), hiddenTaxLore));

        openMenu(player, menu);
    }

    void openItemSelectMenu(Player player, boolean resetPage) {
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        ItemSelectState itemSelectState = viewState.getOrCreateItemSelectState();
        if (resetPage) {
            itemSelectState.page = 1;
        }

        List<OrderableItemOption> items = getFilteredSortedItems(itemSelectState);
        int pageCount = Math.max(1, (int) Math.ceil(items.size() / (double) ITEM_SELECT_PAGE_SIZE));
        if (itemSelectState.page > pageCount) {
            itemSelectState.page = pageCount;
        }
        if (itemSelectState.page < 1) {
            itemSelectState.page = 1;
        }

        int inventorySize = 54;
        int sortSlot = guiItemSlot("item-select.sort", ITEM_SELECT_SORT_SLOT, inventorySize);
        int filterSlot = guiItemSlot("item-select.filter", ITEM_SELECT_FILTER_SLOT, inventorySize);
        int searchSlot = guiItemSlot("item-select.search", ITEM_SELECT_SEARCH_SLOT, inventorySize);
        int backSlot = guiItemSlot("item-select.previous-page", ITEM_SELECT_BACK_SLOT, inventorySize);
        int nextSlot = guiItemSlot("item-select.next-page", ITEM_SELECT_NEXT_SLOT, inventorySize);

        Inventory menu = createMenu(MenuType.ITEM_SELECT, inventorySize, guiTitle("item-select", TITLE_SELECT_ITEM));
        int startIndex = (itemSelectState.page - 1) * ITEM_SELECT_PAGE_SIZE;
        for (int i = 0; i < ITEM_SELECT_PAGE_SIZE; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= items.size()) {
                break;
            }
            menu.setItem(i, createOrderableSelectItem(items.get(itemIndex)));
        }

        menu.setItem(
            sortSlot,
            createGuiCyclingItem("item-select.sort", Material.CAULDRON, LIGHT_ACCENT + "ꜱᴏʀᴛ", guiLabels("item-sort-options", ITEM_SORT_OPTIONS), itemSelectState.sortIndex, LIGHT_ACCENT, WHITE)
        );
        menu.setItem(
            filterSlot,
            createGuiCyclingItem("item-select.filter", Material.HOPPER, ACCENT + "ꜰɪʟᴛᴇʀ", guiLabels("filter-options", FILTER_OPTIONS), itemSelectState.filterIndex, ACCENT, WHITE)
        );
        menu.setItem(searchSlot, createSearchGuiItem(itemSelectState.search));

        if (itemSelectState.page > 1) {
            menu.setItem(backSlot, manager.guiButtons().previousPage(itemSelectState.page - 1, pageCount - 1));
        }
        if (itemSelectState.page < pageCount) {
            menu.setItem(nextSlot, manager.guiButtons().nextPage(itemSelectState.page - 1, pageCount - 1));
        }

        openMenu(player, menu);
    }

    void openEnchantSelectMenu(Player player, boolean resetPage) {
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        NewOrderDraft draft = viewState.getOrCreateDraft();
        Material material = resolveDraftMaterial(draft);
        if (!supportsOrderEnchantments(draft.customItemId(), material)) {
            openNewOrderMenu(player);
            return;
        }

        if (resetPage) {
            viewState.enchantPage = 1;
        }

        Map<String, Integer> sanitizedEnchantments = sanitizeDraftEnchantments(draft.customItemId(), material, draft.enchantLevels());
        if (!sanitizedEnchantments.equals(draft.enchantLevels())) {
            draft = draft.withEnchantLevels(sanitizedEnchantments);
            viewState.draft = draft;
        }

        List<Enchantment> enchantments = getSelectableEnchantments(material);
        int pageCount = Math.max(1, (int) Math.ceil(enchantments.size() / (double) ENCHANT_SELECT_PAGE_SIZE));
        if (viewState.enchantPage < 1) {
            viewState.enchantPage = 1;
        }
        if (viewState.enchantPage > pageCount) {
            viewState.enchantPage = pageCount;
        }

        int inventorySize = 54;
        int backSlot = guiItemSlot("enchant-select.previous-page", ENCHANT_SELECT_BACK_SLOT, inventorySize);
        int nextSlot = guiItemSlot("enchant-select.next-page", ENCHANT_SELECT_NEXT_SLOT, inventorySize);
        int clearSlot = guiItemSlot("enchant-select.clear", ENCHANT_SELECT_CLEAR_SLOT, inventorySize);
        int doneSlot = guiItemSlot("enchant-select.done", ENCHANT_SELECT_DONE_SLOT, inventorySize);
        int controlsSlot = guiItemSlot("enchant-select.controls", ENCHANT_SELECT_INFO_SLOT, inventorySize);

        Inventory menu = createMenu(MenuType.ENCHANT_SELECT, inventorySize, guiTitle("enchant-select", TITLE_ENCHANTS));
        int startIndex = (viewState.enchantPage - 1) * ENCHANT_SELECT_PAGE_SIZE;
        for (int i = 0; i < ENCHANT_SELECT_PAGE_SIZE; i++) {
            int enchantIndex = startIndex + i;
            if (enchantIndex >= enchantments.size()) {
                break;
            }
            Enchantment enchantment = enchantments.get(enchantIndex);
            int level = sanitizedEnchantments.getOrDefault(enchantment.getKey().toString(), 0);
            menu.setItem(i, createEnchantSelectionItem(material, enchantment, level));
        }

        if (viewState.enchantPage > 1) {
            menu.setItem(backSlot, manager.guiButtons().previousPage(viewState.enchantPage - 1, pageCount - 1));
        }
        if (viewState.enchantPage < pageCount) {
            menu.setItem(nextSlot, manager.guiButtons().nextPage(viewState.enchantPage - 1, pageCount - 1));
        }

        menu.setItem(
            clearSlot,
            createGuiItem("enchant-select.clear", Material.RED_TERRACOTTA, CANCEL_RED + "ᴄʟᴇᴀʀ", List.of(WHITE + "Click to remove all selected enchantments"))
        );
        menu.setItem(
            doneSlot,
            createGuiItem("enchant-select.done", Material.LIME_STAINED_GLASS_PANE, CONFIRM_GREEN + "ᴅᴏɴᴇ", List.of(
                WHITE + "Click to return",
                MUTED + "(" + formatCompactAmount(sanitizedEnchantments.size()) + " selected)"
            ), TextFormat.placeholders("selected", formatCompactAmount(sanitizedEnchantments.size())))
        );
        menu.setItem(
            controlsSlot,
            createGuiItem("enchant-select.controls", Material.OAK_SIGN, ACCENT + "ᴄᴏɴᴛʀᴏʟꜱ", List.of(
                WHITE + "Left click: +1 level",
                WHITE + "Right click: -1 level",
                WHITE + "Shift + Left: +5 levels",
                WHITE + "Shift + Right: -5 levels"
            ))
        );
        openMenu(player, menu);
    }

    void openManageOrderMenu(Player player, int orderIndex) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        if (orderIndex < 0 || orderIndex >= playerData.getOrders().size()) {
            viewState.manageOrderIndex = -1;
            openYourOrdersMenu(player);
            return;
        }

        viewState.manageOrderIndex = orderIndex;
        viewState.claimPage = 1;
        PlayerDataStore.OrderEntry order = playerData.getOrders().get(orderIndex);

        int inventorySize = 27;
        int cancelSlot = guiItemSlot("manage-order.cancel", MANAGE_CANCEL_SLOT, inventorySize);
        int claimSlot = guiItemSlot("manage-order.claim", MANAGE_CLAIM_SLOT, inventorySize);

        Inventory menu = createMenu(MenuType.MANAGE_ORDER, inventorySize, guiTitle("manage-order", TITLE_MANAGE_ORDER));
        if (!order.isCancelled()) {
            menu.setItem(
                cancelSlot,
                createGuiItem("manage-order.cancel", Material.RED_TERRACOTTA, CANCEL_RED + "ᴄᴀɴᴄᴇʟ", List.of(WHITE + "Click to cancel your order"))
            );
        }
        menu.setItem(
            claimSlot,
            createGuiItem("manage-order.claim", Material.CHEST, CONFIRM_GREEN + "ᴄʟᴀɪᴍ ᴏʀᴅᴇʀ", createManageClaimLore(order))
        );
        if (canModerateOrders(player)) {
            double remainingFunds = getRemainingOrderFunds(order);
            menu.setItem(
                MANAGE_ADMIN_ACTIONS_SLOT,
                createSimpleItem(
                    Material.BARRIER,
                    CANCEL_RED + "ᴀᴅᴍɪɴ ᴀᴄᴛɪᴏɴꜱ",
                    List.of(
                        WHITE + "Open admin moderation for this order",
                        remainingFunds > 0D
                            ? WHITE + "Refund owner: " + ACCENT + "$" + formatCompactAmount(remainingFunds)
                            : MUTED + "No refund remaining"
                    )
                )
            );
        }
        openMenu(player, menu);
    }

    void openClaimOrderMenu(Player player, boolean resetPage) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        PlayerDataStore.OrderEntry order = getSelectedOrder(playerData, viewState);
        if (order == null) {
            openYourOrdersMenu(player);
            return;
        }

        if (resetPage) {
            viewState.claimPage = 1;
        }

        List<Integer> claimSessionStacks = initializeClaimSessionStacks(viewState, order, resetPage);
        List<Integer> pageStacks = getClaimSessionPageStackAmounts(claimSessionStacks, viewState.claimPage);
        int pageCount = calculateClaimSessionPageCount(claimSessionStacks);
        if (viewState.claimPage > pageCount) {
            viewState.claimPage = pageCount;
            pageStacks = getClaimSessionPageStackAmounts(claimSessionStacks, viewState.claimPage);
        }
        if (viewState.claimPage < 1) {
            viewState.claimPage = 1;
            pageStacks = getClaimSessionPageStackAmounts(claimSessionStacks, viewState.claimPage);
        }

        int inventorySize = 54;
        int backSlot = guiItemSlot("claim-order.previous-page", CLAIM_BACK_SLOT, inventorySize);
        int nextSlot = guiItemSlot("claim-order.next-page", CLAIM_NEXT_SLOT, inventorySize);
        int dropPageSlot = guiItemSlot("claim-order.drop-page", CLAIM_DROP_PAGE_SLOT, inventorySize);

        Inventory menu = createMenu(MenuType.CLAIM_ORDER, inventorySize, guiTitle("claim-order", TITLE_CLAIM_ORDER));
        for (int slot = 0; slot < pageStacks.size(); slot++) {
            int stackAmount = pageStacks.get(slot);
            if (stackAmount <= 0) {
                continue;
            }
            menu.setItem(slot, createOrderStack(order, stackAmount));
        }

        if (viewState.claimPage > 1) {
            menu.setItem(backSlot, manager.guiButtons().previousPage(viewState.claimPage - 1, pageCount - 1));
        }
        if (viewState.claimPage < pageCount) {
            menu.setItem(nextSlot, manager.guiButtons().nextPage(viewState.claimPage - 1, pageCount - 1));
        }
        menu.setItem(
            dropPageSlot,
            createGuiItem("claim-order.drop-page", Material.DISPENSER, MUTED + "ᴅʀᴏᴘ ᴘᴀɢᴇ", List.of(WHITE + "Drop all items on the page"))
        );
        openMenu(player, menu);
    }

    void openAdminOrderActionsMenu(Player player, UUID ownerId, String orderId) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        UUID viewerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(viewerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData ownerData = playerDataStore.getOrCreate(ownerId);
        PlayerDataStore.OrderEntry order = findOrderById(ownerData, orderId);
        if (order == null) {
            clearAdminTarget(viewState);
            openOrdersMenu(player, null);
            return;
        }

        viewState.adminTargetOwnerId = ownerId;
        viewState.adminTargetOrderId = orderId;

        double remainingFunds = getRemainingOrderFunds(order);
        String ownerName = resolvePlayerName(ownerId);
        Inventory menu = createMenu(MenuType.ADMIN_ORDER_ACTIONS, 27, TITLE_ADMIN_ACTIONS);
        menu.setItem(ADMIN_PREVIEW_SLOT, createOrderItem(ownerName, order, false));
        menu.setItem(
            ADMIN_DELETE_SLOT,
            createSimpleItem(Material.BARRIER, CANCEL_RED + "ᴅᴇʟᴇᴛᴇ ᴏʀᴅᴇʀ", List.of(
                WHITE + "Delete order and all claimable items",
                remainingFunds > 0D
                    ? WHITE + "Refund owner: " + ACCENT + "$" + formatCompactAmount(remainingFunds)
                    : MUTED + "No refund remaining"
            ))
        );
        menu.setItem(
            ADMIN_CANCEL_SLOT,
            createSimpleItem(
                order.isCancelled() ? Material.GRAY_STAINED_GLASS_PANE : Material.RED_TERRACOTTA,
                order.isCancelled() ? MUTED + "ᴊᴜꜱᴛ ᴄᴀɴᴄᴇʟ" : CANCEL_RED + "ᴊᴜꜱᴛ ᴄᴀɴᴄᴇʟ",
                order.isCancelled()
                    ? List.of(MUTED + "Order is already cancelled")
                    : List.of(
                        WHITE + "Cancel order and keep claimable items",
                        remainingFunds > 0D
                            ? WHITE + "Refund owner: " + ACCENT + "$" + formatCompactAmount(remainingFunds)
                            : MUTED + "No refund remaining"
                    )
            )
        );
        openMenu(player, menu);
    }

    void openAdminItemEditor(Player player) {
        if (!canModerateOrders(player)) {
            manager.messages().send(player, "general.no-permission");
            return;
        }
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        viewState.adminEditorPage = 1;
        viewState.adminItemDraft = null;
        openAdminItemEditorMenu(player, true);
    }

    void openAdminItemEditorMenu(Player player, boolean resetPage) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        if (resetPage) {
            viewState.adminEditorPage = 1;
        }

        List<CustomItemStore.CustomItemDefinition> customItems = getSortedCustomItems();
        int pageCount = Math.max(1, (int) Math.ceil(customItems.size() / 45D));
        if (viewState.adminEditorPage < 1) {
            viewState.adminEditorPage = 1;
        }
        if (viewState.adminEditorPage > pageCount) {
            viewState.adminEditorPage = pageCount;
        }

        Inventory menu = createMenu(MenuType.ADMIN_ITEM_EDITOR, 54, TITLE_ADMIN_EDITOR + " (" + viewState.adminEditorPage + ")");
        int startIndex = (viewState.adminEditorPage - 1) * 45;
        for (int slot = 0; slot < 45; slot++) {
            int itemIndex = startIndex + slot;
            if (itemIndex >= customItems.size()) {
                break;
            }
            menu.setItem(slot, createAdminCustomItemListEntry(customItems.get(itemIndex)));
        }

        if (viewState.adminEditorPage > 1) {
            menu.setItem(ADMIN_EDITOR_BACK_SLOT, createSimpleItem(Material.ARROW, ACCENT + "ʙᴀᴄᴋ", List.of(WHITE + "Click to go to the previous page")));
        }
        if (viewState.adminEditorPage < pageCount) {
            menu.setItem(ADMIN_EDITOR_NEXT_SLOT, createSimpleItem(Material.ARROW, ACCENT + "ɴᴇxᴛ", List.of(WHITE + "Click to go to the next page")));
        }

        menu.setItem(
            ADMIN_EDITOR_ADD_SLOT,
            createSimpleItem(
                Material.LIME_STAINED_GLASS_PANE,
                CONFIRM_GREEN + "ᴀᴅᴅ ɪᴛᴇᴍ",
                List.of(
                    WHITE + "Create a new custom order item",
                    MUTED + "Includes name, lore, model data, enchants, etc."
                )
            )
        );
        menu.setItem(
            ADMIN_EDITOR_INFO_SLOT,
            createSimpleItem(
                Material.OAK_SIGN,
                ACCENT + "ᴄᴏɴᴛʀᴏʟꜱ",
                List.of(
                    WHITE + "Left click: Edit item",
                    WHITE + "Shift + Right click: Remove item"
                )
            )
        );

        openMenu(player, menu);
    }

    void openAdminItemEditMenu(Player player) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        AdminItemDraft draft = viewState.getOrCreateAdminItemDraft();
        Inventory menu = createMenu(MenuType.ADMIN_ITEM_EDIT, 54, TITLE_ADMIN_EDITOR_EDIT);

        String resolvedId = draft.itemId() == null ? "custom_item" : draft.itemId();
        menu.setItem(
            ADMIN_EDIT_ID_SLOT,
            createSimpleItem(
                Material.PAPER,
                ACCENT + "ɪᴅ",
                List.of(
                    WHITE + resolvedId,
                    MUTED + "Set automatically from your template item"
                )
            )
        );
        menu.setItem(
            ADMIN_EDIT_COPY_HAND_SLOT,
            createSimpleItem(
                Material.ANVIL,
                ACCENT + "ꜱᴇᴛ ꜰʀᴏᴍ ʜᴀɴᴅ",
                List.of(
                    WHITE + "Hold the item in your main hand",
                    WHITE + "Click here to copy it as template",
                    MUTED + "Or drag/click an item onto the template slot"
                )
            )
        );

        ItemStack template = draft.template();
        if (template == null || template.getType() == Material.AIR) {
            menu.setItem(
                ADMIN_EDIT_TEMPLATE_SLOT,
                createSimpleItem(
                    Material.GRAY_STAINED_GLASS_PANE,
                    MUTED + "ɴᴏ ᴛᴇᴍᴘʟᴀᴛᴇ",
                    List.of(
                        WHITE + "Drag or click an item onto this slot",
                        WHITE + "Or use 'Set from hand'",
                        MUTED + "This stores name/lore/model data/enchants/etc."
                    )
                )
            );
        } else {
            ItemStack templatePreview = template.clone();
            templatePreview.setAmount(1);
            ItemMeta templateMeta = templatePreview.getItemMeta();
            List<String> lore = templateMeta != null && templateMeta.hasLore()
                ? new ArrayList<>(templateMeta.getLore())
                : new ArrayList<>();
            lore.add("");
            lore.add(WHITE + "This item is used as the order template");
            lore.add(MUTED + "Drag/click another item here to replace it");
            lore.add(MUTED + "Or use 'Set from hand' again");
            if (templateMeta != null) {
                templateMeta.setLore(lore);
                templatePreview.setItemMeta(templateMeta);
            }
            menu.setItem(ADMIN_EDIT_TEMPLATE_SLOT, templatePreview);
        }

        menu.setItem(
            ADMIN_EDIT_ENCHANTABLE_SLOT,
            createSimpleItem(
                draft.allowOrderEnchants() ? Material.ENCHANTED_BOOK : Material.BOOK,
                ACCENT + "ᴏʀᴅᴇʀ ᴇɴᴄʜᴀɴᴛꜱ",
                List.of(
                    draft.allowOrderEnchants()
                        ? CONFIRM_GREEN + "Enabled"
                        : CANCEL_RED + "Disabled",
                    WHITE + "If enabled, orderers can choose enchantments",
                    WHITE + "like normal tool orders."
                )
            )
        );
        menu.setItem(
            ADMIN_EDIT_SAVE_SLOT,
            createSimpleItem(Material.LIME_STAINED_GLASS_PANE, CONFIRM_GREEN + "ꜱᴀᴠᴇ", List.of(WHITE + "Save this custom item"))
        );
        menu.setItem(
            ADMIN_EDIT_CANCEL_SLOT,
            createSimpleItem(Material.RED_STAINED_GLASS_PANE, CANCEL_RED + "ʙᴀᴄᴋ", List.of(WHITE + "Return to editor list"))
        );

        if (draft.existingId() != null) {
            menu.setItem(
                ADMIN_EDIT_DELETE_SLOT,
                createSimpleItem(
                    Material.BARRIER,
                    CANCEL_RED + "ʀᴇᴍᴏᴠᴇ",
                    List.of(
                        WHITE + "Remove this custom item",
                        MUTED + "Blocked while active orders still use it"
                    )
                )
            );
        }

        openMenu(player, menu);
    }

    ItemStack createAdminCustomItemListEntry(CustomItemStore.CustomItemDefinition definition) {
        ItemStack preview = definition.template().clone();
        preview.setAmount(1);
        ItemMeta meta = preview.getItemMeta();
        if (meta == null) {
            return preview;
        }

        String displayName = resolveTemplateDisplayName(definition.template());
        meta.setDisplayName(ACCENT + displayName);
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(WHITE + "ID: " + ACCENT + definition.id());
        lore.add(WHITE + "Order enchants: " + (definition.allowOrderEnchants() ? CONFIRM_GREEN + "Enabled" : CANCEL_RED + "Disabled"));
        lore.add("");
        lore.add(WHITE + "Left click to edit");
        lore.add(WHITE + "Shift + Right click to remove");
        meta.setLore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    void openDeliverMenu(Player player, MainOrderView selectedOrder) {
        UUID delivererId = player.getUniqueId();
        PendingDeliveryState previous = pendingDeliveries.remove(delivererId);
        waitingDeliveryPlayers.remove(delivererId);
        if (previous != null) {
            returnPendingItems(player, previous.submittedItems());
        }

        PlayerDataStore.PlayerData ownerData = playerDataStore.getOrCreate(selectedOrder.ownerId());
        PlayerDataStore.OrderEntry liveOrder = findOrderById(ownerData, selectedOrder.order().getOrderId());
        if (liveOrder == null || liveOrder.isCancelled()) {
            openOrdersMenu(player, null);
            return;
        }

        int remainingNeeded = Math.max(0, liveOrder.getAmountOrdered() - liveOrder.getAmountDelivered());
        if (remainingNeeded <= 0) {
            openOrdersMenu(player, null);
            return;
        }

        PendingDeliveryState pending = new PendingDeliveryState(selectedOrder.ownerId(), liveOrder.getOrderId(), null);
        pendingDeliveries.put(delivererId, pending);
        Inventory menu = createMenu(MenuType.DELIVER, 36, guiTitle("deliver", TITLE_DELIVER));
        openMenu(player, menu);
    }

    void openDeliveryConfirmMenu(Player player) {
        int inventorySize = 27;
        int cancelSlot = guiItemSlot("delivery-confirm.cancel", DELIVERY_CANCEL_SLOT, inventorySize);
        int confirmSlot = guiItemSlot("delivery-confirm.confirm", DELIVERY_CONFIRM_SLOT, inventorySize);
        Inventory menu = createMenu(MenuType.DELIVERY_CONFIRM, inventorySize, guiTitle("delivery-confirm", TITLE_DELIVERY_CONFIRM));
        menu.setItem(cancelSlot, createGuiItem("delivery-confirm.cancel", Material.RED_STAINED_GLASS_PANE, CANCEL_RED + "ᴄᴀɴᴄᴇʟ", List.of(WHITE + "Click to return")));
        menu.setItem(confirmSlot, createGuiItem("delivery-confirm.confirm", Material.LIME_STAINED_GLASS_PANE, CONFIRM_GREEN + "ᴄᴏɴꜰɪʀᴍ", List.of(WHITE + "Click to confirm order")));
        openMenu(player, menu);
    }

    void openHistoryMenu(Player viewer, UUID targetPlayerId, HistoryDataStore.HistoryType historyType, boolean fromAdminCommand) {
        if (!manager.historyEnabled) {
            manager.messages().send(viewer, "history.disabled-config");
            return;
        }

        UUID resolvedTargetId = targetPlayerId == null ? viewer.getUniqueId() : targetPlayerId;
        if (!canAccessHistory(viewer, resolvedTargetId)) {
            if (fromAdminCommand) {
                manager.messages().send(viewer, "history.access-denied-player");
            } else {
                manager.messages().send(viewer, "history.access-denied");
            }
            return;
        }

        UUID viewerId = viewer.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(viewerId, ignored -> new MenuViewState());
        viewState.historyTargetId = resolvedTargetId;
        viewState.historyType = historyType == null ? HistoryDataStore.HistoryType.ORDER : historyType;
        viewState.historyPage = 1;
        openHistoryMenu(viewer, true);
    }

    void openHistoryMenu(Player player, boolean resetPage) {
        UUID viewerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(viewerId, ignored -> new MenuViewState());
        HistoryDataStore.HistoryType activeType = viewState.historyType == null ? HistoryDataStore.HistoryType.ORDER : viewState.historyType;
        UUID targetPlayerId = viewState.historyTargetId == null ? viewerId : viewState.historyTargetId;
        if (!canAccessHistory(player, targetPlayerId)) {
            openOrdersMenu(player, null);
            return;
        }

        if (resetPage) {
            viewState.historyPage = 1;
        }

        List<HistoryDataStore.HistoryEntry> entries = activeType == HistoryDataStore.HistoryType.ORDER
            ? historyDataStore.getOrderHistory(targetPlayerId)
            : historyDataStore.getDeliverHistory(targetPlayerId);

        int pageCount = calculatePageCount(entries.size(), HISTORY_PAGE_CAPACITY);
        if (viewState.historyPage < 1) {
            viewState.historyPage = 1;
        }
        if (viewState.historyPage > pageCount) {
            viewState.historyPage = pageCount;
        }

        String targetName = resolvePlayerName(targetPlayerId);
        String tabName = activeType == HistoryDataStore.HistoryType.ORDER ? "ᴏʀᴅᴇʀ" : "ᴅᴇʟɪᴠᴇʀ";
        int inventorySize = 54;
        int infoSlot = guiItemSlot("history.info", HISTORY_INFO_SLOT, inventorySize);
        int orderTabSlot = guiItemSlot("history.order-tab", HISTORY_ORDER_TAB_SLOT, inventorySize);
        int deliverTabSlot = guiItemSlot("history.deliver-tab", HISTORY_DELIVER_TAB_SLOT, inventorySize);
        int backToOrdersSlot = guiItemSlot("history.back-to-orders", HISTORY_BACK_TO_ORDERS_SLOT, inventorySize);
        int backSlot = guiItemSlot("history.previous-page", HISTORY_BACK_SLOT, inventorySize);
        int nextSlot = guiItemSlot("history.next-page", HISTORY_NEXT_SLOT, inventorySize);

        Inventory menu = createMenu(MenuType.HISTORY, inventorySize, guiTitle(
            "history",
            TITLE_HISTORY_PREFIX + " • " + tabName,
            TextFormat.placeholders("tab", tabName)
        ));
        int startIndex = (viewState.historyPage - 1) * HISTORY_PAGE_CAPACITY;
        for (int slot = 0; slot < HISTORY_PAGE_CAPACITY; slot++) {
            int historyIndex = startIndex + slot;
            if (historyIndex >= entries.size()) {
                break;
            }
            menu.setItem(slot, createHistoryEntryItem(activeType, entries.get(historyIndex), targetName));
        }

        menu.setItem(
            infoSlot,
            createGuiItem(
                "history.info",
                Material.OAK_SIGN,
                ACCENT + "ᴠɪᴇᴡɪɴɢ",
                List.of(
                    WHITE + targetName,
                    MUTED + "Page " + formatCompactAmount(viewState.historyPage) + "/" + formatCompactAmount(pageCount),
                    MUTED + "Entries: " + formatCompactAmount(entries.size())
                ),
                TextFormat.placeholders(
                    "target", targetName,
                    "page", formatCompactAmount(viewState.historyPage),
                    "pages", formatCompactAmount(pageCount),
                    "entries", formatCompactAmount(entries.size())
                )
            )
        );
        menu.setItem(
            orderTabSlot,
            createGuiItem(
                "history.order-tab",
                activeType == HistoryDataStore.HistoryType.ORDER ? Material.WRITABLE_BOOK : Material.BOOK,
                (activeType == HistoryDataStore.HistoryType.ORDER ? CONFIRM_GREEN : ACCENT) + "ᴏʀᴅᴇʀ ʜɪꜱᴛᴏʀʏ",
                List.of(
                    activeType == HistoryDataStore.HistoryType.ORDER
                        ? MUTED + "Current tab"
                        : WHITE + "Click to switch"
                )
            )
        );
        menu.setItem(
            deliverTabSlot,
            createGuiItem(
                "history.deliver-tab",
                activeType == HistoryDataStore.HistoryType.DELIVER ? Material.MINECART : Material.CHEST_MINECART,
                (activeType == HistoryDataStore.HistoryType.DELIVER ? CONFIRM_GREEN : ACCENT) + "ᴅᴇʟɪᴠᴇʀ ʜɪꜱᴛᴏʀʏ",
                List.of(
                    activeType == HistoryDataStore.HistoryType.DELIVER
                        ? MUTED + "Current tab"
                        : WHITE + "Click to switch"
                )
            )
        );
        menu.setItem(
            backToOrdersSlot,
            manager.guiButtons().back()
        );

        if (viewState.historyPage > 1) {
            menu.setItem(backSlot, manager.guiButtons().previousPage(viewState.historyPage - 1, pageCount - 1));
        }
        if (viewState.historyPage < pageCount) {
            menu.setItem(nextSlot, manager.guiButtons().nextPage(viewState.historyPage - 1, pageCount - 1));
        }
        openMenu(player, menu);
    }

    ItemStack createHistoryEntryItem(HistoryDataStore.HistoryType historyType, HistoryDataStore.HistoryEntry entry, String targetName) {
        Material icon = historyType == HistoryDataStore.HistoryType.ORDER ? Material.WRITABLE_BOOK : Material.CHEST_MINECART;
        String timestamp = entry.timestamp() > 0L
            ? historyTimestampFormatter.format(Instant.ofEpochMilli(entry.timestamp()))
            : "Unknown";
        return createGuiItem(
            "history.entry",
            icon,
            ACCENT + sanitizeHistoryLabel(entry.action()),
            List.of(
                WHITE + sanitizeHistoryLabel(entry.details()),
                MUTED + "Player: " + targetName,
                MUTED + "Time: " + timestamp
            ),
            TextFormat.placeholders(
                "action", sanitizeHistoryLabel(entry.action()),
                "details", sanitizeHistoryLabel(entry.details()),
                "target", targetName,
                "time", timestamp
            )
        );
    }

    String sanitizeHistoryLabel(String value) {
        if (value == null || value.isBlank()) {
            return "Event";
        }
        return value;
    }
    void refreshMainMenu(Player player) {
        scheduler.runForPlayer(player, () -> {
            if (player.isOnline()) {
                if (!refreshOpenMainMenuInPlace(player)) {
                    openOrdersMenu(player, null);
                }
            }
        });
    }

    void refreshMainMenuDebounced(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        int refreshId = manager.mainMenuRefreshSequence.incrementAndGet();
        manager.pendingMainMenuRefreshIds.put(playerId, refreshId);
        scheduler.runLaterForPlayer(player, () -> {
            if (!player.isOnline()) {
                manager.pendingMainMenuRefreshIds.remove(playerId, refreshId);
                return;
            }
            if (!manager.pendingMainMenuRefreshIds.remove(playerId, refreshId)) {
                return;
            }
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (!(topInventory.getHolder() instanceof OrdersMenuHolder holder) || holder.getMenuType() != MenuType.MAIN) {
                return;
            }
            if (!refreshOpenMainMenuInPlace(player)) {
                openOrdersMenu(player, null);
            }
        }, MAIN_OPTION_REFRESH_DEBOUNCE_TICKS);
    }

    void refreshItemSelectMenuDebounced(Player player) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        int refreshId = manager.itemSelectRefreshSequence.incrementAndGet();
        manager.pendingItemSelectRefreshIds.put(playerId, refreshId);
        scheduler.runLaterForPlayer(player, () -> {
            if (!player.isOnline()) {
                manager.pendingItemSelectRefreshIds.remove(playerId, refreshId);
                return;
            }
            if (!manager.pendingItemSelectRefreshIds.remove(playerId, refreshId)) {
                return;
            }
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (!(topInventory.getHolder() instanceof OrdersMenuHolder holder) || holder.getMenuType() != MenuType.ITEM_SELECT) {
                return;
            }
            openItemSelectMenu(player, false);
        }, ITEM_SELECT_OPTION_REFRESH_DEBOUNCE_TICKS);
    }

    private boolean refreshOpenMainMenuInPlace(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (!(topInventory.getHolder() instanceof OrdersMenuHolder holder) || holder.getMenuType() != MenuType.MAIN) {
            return false;
        }
        if (holder.getGuiRevision() != manager.guis().revision() || topInventory.getSize() != 54) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        prepareMainMenuState(viewState);

        List<Integer> orderSlots = guiSlots("layout.main.order-slots", MAIN_ORDER_SLOTS, topInventory.getSize());
        List<MainOrderView> visibleOrders = getVisibleMainOrders(viewState, playerData);
        viewState.visibleMainOrders = visibleOrders;
        int pageCount = calculatePageCount(visibleOrders.size(), orderSlots.size());
        if (viewState.page < 1) {
            viewState.page = 1;
        }
        if (viewState.page > pageCount) {
            viewState.page = pageCount;
        }

        if (!mainMenuTitle(viewState).equals(holder.getTitle())) {
            return false;
        }

        populateMainMenuContents(player, topInventory, viewState, playerData, orderSlots, visibleOrders, pageCount);
        player.updateInventory();
        return true;
    }

    List<MainOrderView> getVisibleMainOrders(MenuViewState viewState, PlayerDataStore.PlayerData playerData) {
        List<PlayerDataStore.PlayerOrderRecord> allOrders = playerDataStore.getAllOrdersSnapshot();
        String searchLower = viewState.search.toLowerCase(Locale.ROOT);

        List<MainOrderView> visibleOrders = new ArrayList<>();
        Map<UUID, String> ownerNameCache = new HashMap<>();
        for (PlayerDataStore.PlayerOrderRecord record : allOrders) {
            PlayerDataStore.OrderEntry order = record.getOrder();
            if (order.isCancelled()) {
                continue;
            }
            if (order.getAmountDelivered() >= order.getAmountOrdered()) {
                continue;
            }
            Material orderMaterial = resolveMaterial(order.getMaterial());

            if (!matchesItemFilter(orderMaterial, playerData.getFilterIndex())) {
                continue;
            }

            if (!searchLower.isBlank()) {
                String searchable = buildOrderSearchText(order);
                if (!searchable.contains(searchLower)) {
                    continue;
                }
            }

            UUID ownerId = record.getPlayerId();
            String ownerName = ownerNameCache.computeIfAbsent(ownerId, this::resolvePlayerName);
            visibleOrders.add(new MainOrderView(ownerId, ownerName, order));
        }

        visibleOrders.sort((left, right) -> compareByMainSort(left.order(), right.order(), playerData.getSortIndex()));
        return visibleOrders;
    }

    int compareByMainSort(PlayerDataStore.OrderEntry left, PlayerDataStore.OrderEntry right, int sortIndex) {
        int compare = switch (sortIndex) {
            case 0 -> Double.compare(right.getTotalCost(), left.getTotalCost());
            case 1 -> Integer.compare(right.getAmountDelivered(), left.getAmountDelivered());
            case 2 -> Long.compare(right.getCreatedAtEpochMillis(), left.getCreatedAtEpochMillis());
            case 3 -> Double.compare(right.getPricePerItem(), left.getPricePerItem());
            default -> 0;
        };
        if (compare != 0) {
            return compare;
        }
        return right.getMaterial().compareToIgnoreCase(left.getMaterial());
    }

    void populateMainOrders(Player viewer, Inventory menu, List<MainOrderView> visibleOrders, int page, List<Integer> orderSlots) {
        boolean canModerate = canModerateOrders(viewer);
        int startIndex = (page - 1) * orderSlots.size();
        for (int slotIndex = 0; slotIndex < orderSlots.size(); slotIndex++) {
            int index = startIndex + slotIndex;
            if (index >= visibleOrders.size()) {
                break;
            }
            MainOrderView orderView = visibleOrders.get(index);
            menu.setItem(orderSlots.get(slotIndex), createOrderItem(orderView.ownerName(), orderView.order(), canModerate));
        }
    }

    void populateMainOrderFrame(Inventory menu, List<Integer> orderSlots) {
        ItemStack grayPane = createGuiItem("main.gray-pane", Material.GRAY_STAINED_GLASS_PANE, "", List.of());
        ItemStack emptyOrderPane = createGuiItem("main.empty-order-slot", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "", List.of());

        for (int column = 0; column < 9; column++) {
            setFrameItem(menu, column, grayPane);
            setFrameItem(menu, 36 + column, grayPane);
            setFrameItem(menu, 45 + column, grayPane);
        }

        for (int row = 1; row <= 3; row++) {
            int rowStart = row * 9;
            setFrameItem(menu, rowStart, grayPane);
            setFrameItem(menu, rowStart + 8, grayPane);
            for (int column = 1; column <= 7; column++) {
                setFrameItem(menu, rowStart + column, emptyOrderPane);
            }
        }

        for (int slot : orderSlots) {
            setFrameItem(menu, slot, emptyOrderPane);
        }
    }

    private void setFrameItem(Inventory menu, int slot, ItemStack item) {
        menu.setItem(slot, item.clone());
    }

    private int refreshGuiItemCachesIfNeeded() {
        int revision = manager.guis().revision();
        if (staticGuiItemRevision != revision) {
            staticGuiItemTemplates.clear();
            cyclingGuiItemTemplates.clear();
            staticGuiItemRevision = revision;
        }
        return revision;
    }

    private static List<String> copyStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private record StaticGuiItemKey(int revision, String path, Material material, String displayName, List<String> loreLines) {
        private StaticGuiItemKey {
            loreLines = copyStringList(loreLines);
        }
    }

    private record CyclingGuiItemKey(
        int revision,
        String path,
        Material material,
        String displayName,
        List<String> options,
        int selectedIndex,
        String accentColor,
        String defaultColor
    ) {
        private CyclingGuiItemKey {
            options = copyStringList(options);
        }
    }

    private void warnMainSlotOverlaps(List<Integer> orderSlots, String[] controlNames, int[] controlSlots) {
        for (int left = 0; left < controlSlots.length; left++) {
            for (int right = left + 1; right < controlSlots.length; right++) {
                if (controlSlots[left] != controlSlots[right]) {
                    continue;
                }
                manager.guis().warnConfig(
                    "Main GUI slot conflict: items." + controlNames[left] + ".slot and items."
                        + controlNames[right] + ".slot both use slot " + controlSlots[left] + "."
                );
            }
        }

        for (int orderSlot : orderSlots) {
            for (int index = 0; index < controlSlots.length; index++) {
                if (orderSlot != controlSlots[index]) {
                    continue;
                }
                manager.guis().warnConfig(
                    "Main GUI slot conflict: layout.main.order-slots contains slot " + orderSlot
                        + ", which is also used by items." + controlNames[index] + ".slot."
                );
            }
        }
    }

    int calculatePageCount(int totalItems, int pageSize) {
        return Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
    }
}
