package me.foesio.foOrders;

import me.foesio.foOrders.config.GuiConfigManager;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.gui.FoButtonStyle;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiInventoryHolder;
import me.foesio.core.gui.publicgui.PublicGuiRenderer;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foOrders.dialog.EnchantDialogAction;
import me.foesio.foOrders.dialog.EnchantDialogRequest;
import me.foesio.foOrders.dialog.EnchantDialogRow;
import me.foesio.foOrders.dialog.FoOrdersDialogInputService;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import me.foesio.foOrders.util.TextFormat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuViewSupport {
    private static final long NATIVE_DIALOG_CLOSE_SUPPRESSION_TICKS = 5L;
    private static final Set<String> HISTORY_EVENT_KEYS = Set.of(
        "order-created", "order-claimed-inventory", "order-claimed-drop", "order-delivered",
        "delivery-completed", "order-cancelled", "order-deleted-admin", "order-cancelled-admin");

    private final OrdersMenuManager manager;
    private final FoScheduler scheduler;
    private final Map<UUID, MenuViewState> menuStates;
    private final Map<UUID, PendingDeliveryState> pendingDeliveries;
    private final Set<UUID> waitingDeliveryPlayers;
    private final PlayerDataStore playerDataStore;
    private final CustomItemStore customItemStore;
    private final HistoryDataStore historyDataStore;
    private final Map<StaticGuiItemKey, ItemStack> staticGuiItemTemplates = new ConcurrentHashMap<>();
    private final Map<CyclingGuiItemKey, ItemStack> cyclingGuiItemTemplates = new ConcurrentHashMap<>();
    private volatile int staticGuiItemRevision = -1;
    private volatile int messageGuiItemRevision = -1;

    OrdersMenuViewSupport(OrdersMenuManager manager) {
        this.manager = manager;
        this.scheduler = manager.scheduler;
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

    private List<PlayerDataStore.OrderEntry> removeCompletedOrders(PlayerDataStore.PlayerData playerData) {
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

    /** A disabled control must not clear another control moved into its old slot. */
    private void putItemIfPresent(Inventory menu, int slot, ItemStack item) {
        if (item != null) {
            menu.setItem(slot, item);
        }
    }

    private void openMenu(Player player, Inventory menu) {
        openMenu(player, menu, null);
    }

    private void openMenu(Player player, Inventory menu, String transitionSound) {
        renderMenuContentsForViewer(player, menu);
        if (hasScreenChanged(player, menu)) {
            if (transitionSound != null && !transitionSound.isBlank()) {
                manager.playSound(player, transitionSound);
            } else if (isEditorScreen(menu)) {
                manager.editorSounds().open(player);
            } else {
                manager.plugin.getGuiSounds().open(player);
            }
        }
        manager.itemSupport.openMenu(player, menu);
    }

    private Map<String, String> publicValues(Map<String, String> values) {
        Map<String, String> merged = new HashMap<>(manager.messages().ownerTokens());
        merged.putAll(values);
        return merged;
    }

    private boolean publicGuiReady(Player player, String key) {
        if (manager.publicGuiAvailable(key)) return true;
        manager.interactionSupport.sendErrorActionbar(player,
            manager.messages().get("actionbar.action-unavailable"));
        return false;
    }

    private void openPublicMenu(Player player, String menuId, Map<String, String> values,
                                Map<String, String> states,
                                Map<String, PublicGuiText.CycleSelection> cycles,
                                Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries,
                                String transitionSound) {
        Inventory current = player.getOpenInventory().getTopInventory();
        boolean changedScreen = !(current.getHolder() instanceof PublicGuiInventoryHolder holder
            && holder.frame().definition().menuId().equals("orders." + menuId));
        boolean carriesItems = current.getHolder() instanceof OrdersMenuHolder holder
            && holder.getMenuType() == MenuType.DELIVER;
        if (manager.isOrdersMenu(current) && !carriesItems) {
            manager.inventoryCloseSuppressor.suppressNextClose(player);
        }
        try {
            manager.publicGuis().open(player, menuId, new PublicGuiRenderInput(
                publicValues(values), states, cycles, entries));
            if (changedScreen) {
                if (transitionSound != null && !transitionSound.isBlank()) {
                    manager.playSound(player, transitionSound);
                } else {
                    manager.plugin.getGuiSounds().open(player);
                }
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            manager.inventoryCloseSuppressor.clear(player);
            manager.plugin.getLogger().severe("Could not open public GUI " + menuId + ": "
                + exception.getMessage());
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
        }
    }

    private static Map<Integer, PublicGuiRenderInput.EntryValue> entryAt(int slot, String id,
                                                                         Map<String, String> values) {
        return slot < 0 ? Map.of() : Map.of(slot, new PublicGuiRenderInput.EntryValue(id, null, values));
    }

    /**
     * Re-render fresh menu templates immediately before a player sees them.
     * In-place refreshes need the same pass as newly opened menus: templates
     * intentionally retain sprite tokens until the actual viewer is known.
     */
    private void renderMenuContentsForViewer(Player player, Inventory menu) {
        for (int slot = 0; slot < menu.getSize(); slot++) {
            ItemStack item = menu.getItem(slot);
            if (item != null) {
                putItemIfPresent(menu, slot, manager.itemSupport.renderForViewer(player, item));
            }
        }
    }

    private boolean hasScreenChanged(Player player, Inventory menu) {
        Inventory current = player == null ? null : player.getOpenInventory().getTopInventory();
        if (current == null || menu == null) {
            return true;
        }

        InventoryHolder currentHolder = current.getHolder();
        InventoryHolder nextHolder = menu.getHolder();
        if (currentHolder instanceof OrdersMenuHolder currentOrders
            && nextHolder instanceof OrdersMenuHolder nextOrders) {
            return currentOrders.getMenuType() != nextOrders.getMenuType();
        }
        if (currentHolder instanceof EntryBrowserHolder currentBrowser
            && nextHolder instanceof EntryBrowserHolder nextBrowser) {
            return !(currentBrowser.request().context() instanceof AdminCustomItemBrowserContext)
                || !(nextBrowser.request().context() instanceof AdminCustomItemBrowserContext);
        }
        return true;
    }

    private boolean isEditorScreen(Inventory menu) {
        if (menu == null) {
            return false;
        }
        InventoryHolder holder = menu.getHolder();
        if (holder instanceof OrdersMenuHolder ordersHolder) {
            return ordersHolder.getMenuType() == MenuType.ADMIN_ITEM_EDIT;
        }
        return holder instanceof EntryBrowserHolder browserHolder
            && browserHolder.request().context() instanceof AdminCustomItemBrowserContext;
    }

    private ItemStack createSimpleItem(Material material, String displayName, List<String> loreLines) {
        return manager.itemSupport.createSimpleItem(material, displayName, loreLines);
    }

    private ItemStack createSimpleItem(Material material, String displayName, List<String> loreLines, Integer customModelData) {
        ItemStack item = createSimpleItem(material, displayName, loreLines);
        if (customModelData == null) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSimpleItem(GuiConfigManager.GuiItem configured) {
        ItemStack item = createSimpleItem(configured.material(), configured.name(), configured.lore(),
            configured.customModelData());
        item.setAmount(configured.amount());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            OrdersMenuItemSupport.applyConfiguredFlags(meta, configured);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGuiItem(String path, Material material, String displayName, List<String> loreLines) {
        if (!manager.guis().itemEnabled(path)) {
            return null;
        }
        int revision = refreshGuiItemCachesIfNeeded();
        StaticGuiItemKey key = new StaticGuiItemKey(revision, path, material, displayName, loreLines);
        ItemStack template = staticGuiItemTemplates.computeIfAbsent(key, ignored -> {
            GuiConfigManager.GuiItem item = manager.guis().item(path, material, displayName, loreLines);
            return createSimpleItem(item);
        });
        return template.clone();
    }

    private ItemStack createGuiItem(String path, Material material, String displayName, List<String> loreLines, Map<String, String> placeholders) {
        if (!manager.guis().itemEnabled(path)) {
            return null;
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return createGuiItem(path, material, displayName, loreLines);
        }
        GuiConfigManager.GuiItem item = manager.guis().item(path, material, displayName, loreLines, placeholders);
        return createSimpleItem(item);
    }

    private ItemStack createGuiItem(
        String path,
        Material material,
        String displayName,
        List<String> loreLines,
        Map<String, String> placeholders,
        List<String> hiddenLorePlaceholders
    ) {
        if (!manager.guis().itemEnabled(path)) {
            return null;
        }
        GuiConfigManager.GuiItem item = manager.guis().item(
            path,
            material,
            displayName,
            loreLines,
            placeholders,
            hiddenLorePlaceholders
        );
        return createSimpleItem(item);
    }

    private ItemStack createGuiItem(String path, Material material, String displayName, List<String> fallbackLore,
                                    Map<String, String> placeholders, Map<String, List<String>> loreExpansions) {
        if (!manager.guis().itemEnabled(path)) {
            return null;
        }
        GuiConfigManager.GuiItem item = manager.guis().item(
            path, material, displayName, fallbackLore, placeholders, List.of(), loreExpansions
        );
        return createSimpleItem(item);
    }

    private ItemStack createGuiCyclingItem(String path, Material material, String displayName, List<String> options, int selectedIndex, String accentColor, String defaultColor) {
        if (!manager.guis().itemEnabled(path)) {
            return null;
        }
        int revision = refreshGuiItemCachesIfNeeded();
        String configuredSelectedColor = manager.messages().themeColor();
        String selectedColor = configuredSelectedColor == null || configuredSelectedColor.isBlank() ? accentColor : configuredSelectedColor;
        CyclingGuiItemKey key = new CyclingGuiItemKey(revision, path, material, displayName, options, selectedIndex, selectedColor, defaultColor);
        ItemStack template = cyclingGuiItemTemplates.computeIfAbsent(key, ignored -> {
            List<String> information = cycleOptionLines(options, selectedIndex, (selected, option) ->
                manager.guis().text(
                    "items." + path + (selected ? ".selected-option" : ".other-option"),
                    (selected ? selectedColor + "» " : defaultColor + "• ") + "{option}",
                    TextFormat.placeholders("option", option)
                ));
            GuiConfigManager.GuiItem item = manager.guis().item(
                path, material, displayName, FoButtonStyle.buttonLore(information, "cycle"),
                Map.of(), List.of(), Map.of("{options}", information)
            );
            return createSimpleItem(item);
        });
        return template.clone();
    }

    static List<String> cycleOptionLines(List<String> options, int selectedIndex,
                                         BiFunction<Boolean, String, String> renderLine) {
        List<String> lines = new ArrayList<>();
        for (int index = 0; index < options.size(); index++) {
            String line = renderLine.apply(index == selectedIndex, options.get(index));
            if (line != null && !line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
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

    private ItemStack createSearchGuiItem(String guiPath, Player player, String searchText) {
        String current = searchText == null || searchText.isBlank()
            ? manager.guis().configuredText("labels." + guiPath + ".search-empty", Map.of())
            : manager.guis().configuredText("labels." + guiPath + ".search-current",
                TextFormat.placeholders("search", searchText));
        return createConfiguredButton(guiPath, "search", TextFormat.placeholders("current", current));
    }

    private ItemStack createConfiguredButton(String guiPath, String key, Map<String, String> placeholders) {
        GuiConfigManager.GuiItem item = manager.guis().button(guiPath, key, placeholders);
        return item == null ? null : createSimpleItem(item);
    }

    private ItemStack createPageButton(String guiPath, String key, int page, int maxPage) {
        return createConfiguredButton(guiPath, key,
            TextFormat.placeholders("page", page + 1, "max_page", maxPage + 1));
    }

    private ItemStack createBackButton(String guiPath) {
        return createConfiguredButton(guiPath, "back", Map.of());
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

    private List<String> buildFullEnchantmentSummaryLore(Map<String, Integer> enchantments) {
        return manager.itemSupport.buildFullEnchantmentSummaryLore(enchantments);
    }

    private List<OrderableItemOption> getFilteredSortedItems(ItemSelectState itemSelectState) {
        return manager.itemSupport.getCachedFilteredSortedItems(itemSelectState);
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

    private String formatEnchantmentName(Enchantment enchantment) {
        return manager.itemSupport.formatEnchantmentName(enchantment);
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
        openOrdersMenu(player, searchText, null);
    }

    void openOrdersMenu(Player player, String searchText, String transitionSound) {
        if (!publicGuiReady(player, "main")) return;
        UUID playerId = player.getUniqueId();
        manager.pendingMainMenuRefreshIds.remove(playerId);
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        prepareMainMenuState(viewState);

        if (searchText != null) {
            viewState.search = searchText.trim();
            viewState.page = 1;
        }

        List<Integer> orderSlots = manager.guis().contentSlots("main", "order-slots");
        List<MainOrderView> visibleOrders = getVisibleMainOrders(viewState, playerData);
        viewState.visibleMainOrders = visibleOrders;
        int pageCount = calculatePageCount(visibleOrders.size(), orderSlots.size());
        if (viewState.page < 1) {
            viewState.page = 1;
        }
        if (viewState.page > pageCount) {
            viewState.page = pageCount;
        }

        Map<String, String> values = TextFormat.placeholders(
            "page", formatCompactAmount(viewState.page),
            "max_page", formatCompactAmount(pageCount),
            "current", viewState.search == null || viewState.search.isBlank()
                ? manager.guis().configuredText("labels.main.search-empty", Map.of())
                : manager.guis().configuredText("labels.main.search-current",
                    TextFormat.placeholders("search", viewState.search)));
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new HashMap<>();
        Map<Integer, PublicGuiRenderInput.EntryValue> orderEntries = new HashMap<>();
        int start = (viewState.page - 1) * orderSlots.size();
        for (int offset = 0; offset < orderSlots.size() && start + offset < visibleOrders.size(); offset++) {
            MainOrderView visible = visibleOrders.get(start + offset);
            PlayerDataStore.OrderEntry order = visible.order();
            ItemStack stack = createOrderStack(order, 1);
            String itemName = order.getCustomItemId() == null
                ? formatMaterialName(stack.getType()) : resolveTemplateDisplayName(stack);
            orderEntries.put(orderSlots.get(offset), new PublicGuiRenderInput.EntryValue(
                visible.ownerId() + ":" + order.getOrderId(),
                stack,
                TextFormat.placeholders("owner", visible.ownerName(), "item", itemName,
                    "amount", formatCompactAmount(order.getAmountOrdered()),
                    "delivered", formatCompactAmount(order.getAmountDelivered()),
                    "price", formatCompactAmount(order.getPricePerItem()),
                    "total", formatCompactAmount(order.getTotalCost()),
                    "paid", formatCompactAmount(order.getAmountPaid())),
                canModerateOrders(player) ? "admin" : "normal"));
        }
        entries.put("order-entry", orderEntries);
        if (canOpenOwnHistory(player)) {
            entries.put("history", entryAt(manager.guis().activeControlSlot("main.history"),
                "history", Map.of()));
        }
        if (viewState.page > 1) {
            entries.put("previous-page", entryAt(manager.guis().activeControlSlot("main.previous-page"),
                "previous", values));
        }
        if (viewState.page < pageCount) {
            entries.put("next-page", entryAt(manager.guis().activeControlSlot("main.next-page"),
                "next", values));
        }
        Map<String, PublicGuiText.CycleSelection> cycles = new HashMap<>();
        if (manager.guis().buttonEnabled("main", "sort")) {
            List<String> options = guiLabels("main.main-sort-options", SORT_OPTIONS);
            cycles.put("sort", new PublicGuiText.CycleSelection(options,
                options.isEmpty() ? -1 : Math.floorMod(viewState.mainSortIndex, options.size())));
        }
        if (manager.guis().buttonEnabled("main", "filter")) {
            List<String> options = guiLabels("main.filter-options", FILTER_OPTIONS);
            cycles.put("filter", new PublicGuiText.CycleSelection(options,
                options.isEmpty() ? -1 : Math.floorMod(viewState.mainFilterIndex, options.size())));
        }
        openPublicMenu(player, "main", values, Map.of(), cycles, entries, transitionSound);
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
        populateMainOrderFrame(menu);
        populateMainOrders(player, menu, visibleOrders, viewState.page, orderSlots);

        if (viewState.page > 1) {
            putItemIfPresent(menu, mainBackSlot, createPageButton("main", "previous-page", viewState.page - 1, pageCount - 1));
        }
        if (viewState.page < pageCount) {
            putItemIfPresent(menu, mainNextSlot, createPageButton("main", "next-page", viewState.page - 1, pageCount - 1));
        }

        putItemIfPresent(menu, sortSlot, createGuiCyclingItem("main.sort", Material.CAULDRON, ACCENT + "ꜱᴏʀᴛ", guiLabels("main.main-sort-options", SORT_OPTIONS), viewState.mainSortIndex, ACCENT, WHITE));
        putItemIfPresent(menu, filterSlot, createGuiCyclingItem("main.filter", Material.HOPPER, ACCENT + "ꜰɪʟᴛᴇʀ", guiLabels("main.filter-options", FILTER_OPTIONS), viewState.mainFilterIndex, ACCENT, WHITE));
        putItemIfPresent(menu, refreshSlot, createGuiItem("main.refresh", Material.MAP, ACCENT + "ᴏʀᴅᴇʀꜱ", List.of(WHITE + "Click to refresh")));
        putItemIfPresent(menu, searchSlot, createSearchGuiItem("main", player, viewState.search));
        putItemIfPresent(menu, yourOrdersSlot, createGuiItem("main.your-orders", Material.BOOK, ACCENT + "ʏᴏᴜʀ ᴏʀᴅᴇʀꜱ", List.of(WHITE + "Click to view your Orders")));
        if (canOpenOwnHistory(player)) {
            putItemIfPresent(menu, historySlot, createGuiItem("main.history", Material.WRITABLE_BOOK, ACCENT + "ʜɪꜱᴛᴏʀʏ", List.of(
                WHITE + "Click to view order history",
                WHITE + "Switch between order and deliver tabs"
            )));
        }
    }

    void openYourOrdersMenu(Player player) {
        openYourOrdersMenu(player, null);
    }

    void openYourOrdersMenu(Player player, String transitionSound) {
        if (!publicGuiReady(player, "your-orders")) return;
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        clearAdminTarget(viewState);
        viewState.manageOrderIndex = -1;
        viewState.manageOrderId = null;
        viewState.claimPage = 1;
        clearClaimSession(viewState);
        int playerMaxOrders = getMaxOrdersForPlayer(player);
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        List<PlayerDataStore.OrderEntry> closedOrders = removeCompletedOrders(playerData);
        if (!closedOrders.isEmpty()) {
            playerDataStore.save(playerId);
            for (PlayerDataStore.OrderEntry closed : closedOrders) {
                manager.interactionSupport.deliverySupport.publishClosed(OrderSnapshots.of(playerId, closed), null);
            }
        }

        List<PlayerDataStore.OrderEntry> playerOrders = playerData.getOrders();
        int newOrderSlot = manager.guis().activeControlSlot("your-orders.new-order");
        List<Integer> orderSlots = yourOrderSlots(newOrderSlot);
        int displayCount = Math.min(playerOrders.size(), Math.min(playerMaxOrders, orderSlots.size()));
        Map<Integer, PublicGuiRenderInput.EntryValue> orders = new HashMap<>();
        for (int i = 0; i < displayCount; i++) {
            PlayerDataStore.OrderEntry order = playerOrders.get(i);
            ItemStack stack = createOrderStack(order, 1);
            String itemName = order.getCustomItemId() == null
                ? formatMaterialName(stack.getType()) : resolveTemplateDisplayName(stack);
            orders.put(orderSlots.get(i), new PublicGuiRenderInput.EntryValue(order.getOrderId(),
                stack, TextFormat.placeholders(
                    "owner", player.getName(), "item", itemName,
                    "amount", formatCompactAmount(order.getAmountOrdered()),
                    "delivered", formatCompactAmount(order.getAmountDelivered()),
                    "price", formatCompactAmount(order.getPricePerItem()),
                    "total", formatCompactAmount(order.getTotalCost()),
                    "paid", formatCompactAmount(order.getAmountPaid())), "normal"));
        }
        openPublicMenu(player, "your-orders", Map.of(), Map.of(), Map.of(),
            Map.of("order-entry", orders), transitionSound);
    }

    List<Integer> yourOrderSlots(int newOrderSlot) {
        boolean newOrderEnabled = manager.guis().itemEnabled("your-orders.new-order");
        return manager.guis().contentSlots("your-orders", "order-slots").stream()
            .filter(slot -> !newOrderEnabled || slot != newOrderSlot).toList();
    }

    void openNewOrderMenu(Player player) {
        openNewOrderMenu(player, null);
    }

    void openNewOrderMenu(Player player, String transitionSound) {
        if (!publicGuiReady(player, "new-order")) return;
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        viewState.newOrderConfirmLocked = false;
        NewOrderDraft draft = viewState.getOrCreateDraft();

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
        double subtotal = draft.amount() * draft.pricePerItem();
        double tax = manager.calculateOrderTax(subtotal);
        double total = manager.calculateOrderCreationCost(subtotal);
        String taxPercent = formatTaxPercentage(manager.orderTaxPercentage);
        Map<String, String> values = TextFormat.placeholders(
            "item", selectedItemName,
            "amount", formatCompactAmount(draft.amount()),
            "price", formatCompactAmount(draft.pricePerItem()),
            "subtotal", formatCompactAmount(subtotal),
            "tax_percent", taxPercent,
            "tax", formatCompactAmount(tax),
            "total", formatCompactAmount(total));
        ItemStack source = customSelection == null ? new ItemStack(selectedMaterial)
            : customSelection.template().clone();
        Map<Integer, PublicGuiRenderInput.EntryValue> itemEntry = entryAt(
            manager.guis().activeControlSlot("new-order.item"), "draft-item", Map.of());
        if (!itemEntry.isEmpty()) {
            int slot = itemEntry.keySet().iterator().next();
            itemEntry = Map.of(slot, new PublicGuiRenderInput.EntryValue("draft-item",
                source, Map.of()));
        }
        Map<String, String> states = new HashMap<>();
        states.put("confirm", tax > 0D ? "taxed" : "untaxed");
        Map<String, PublicGuiText.CycleSelection> cycles = new HashMap<>();
        if (supportsOrderEnchantments(draft.customItemId(), selectedMaterial)) {
            if (manager.guis().buttonEnabled("new-order", "enchants")) {
                cycles.put("enchants", new PublicGuiText.CycleSelection(
                    buildFullEnchantmentSummaryLore(sanitizedEnchantments), -1));
            }
        } else if (manager.guis().buttonEnabled("new-order", "enchants")) {
            states.put("enchants", "hidden");
        }
        openPublicMenu(player, "new-order", values, states, cycles,
            Map.of("item", itemEntry), transitionSound);
    }

    void openItemSelectMenu(Player player, boolean resetPage) {
        if (!publicGuiReady(player, "item-select")) return;
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        ItemSelectState itemSelectState = viewState.getOrCreateItemSelectState();
        itemSelectState.clearUnavailableControls(
            manager.guis().itemEnabled("item-select.sort"),
            manager.guis().itemEnabled("item-select.filter"),
            manager.guis().buttonEnabled("item-select", "search"));
        if (resetPage) {
            itemSelectState.page = 1;
        }

        List<OrderableItemOption> items = getFilteredSortedItems(itemSelectState);
        List<Integer> contentSlots = manager.guis().contentSlots("item-select", "content-slots");
        int pageCount = calculatePageCount(items.size(), contentSlots.size());
        if (itemSelectState.page > pageCount) {
            itemSelectState.page = pageCount;
        }
        if (itemSelectState.page < 1) {
            itemSelectState.page = 1;
        }

        Map<String, String> values = TextFormat.placeholders(
            "page", formatCompactAmount(itemSelectState.page),
            "max_page", formatCompactAmount(pageCount),
            "current", itemSelectState.search == null || itemSelectState.search.isBlank()
                ? manager.guis().configuredText("labels.item-select.search-empty", Map.of())
                : manager.guis().configuredText("labels.item-select.search-current",
                    TextFormat.placeholders("search", itemSelectState.search)));
        Map<Integer, PublicGuiRenderInput.EntryValue> choices = new HashMap<>();
        int startIndex = (itemSelectState.page - 1) * contentSlots.size();
        for (int i = 0; i < contentSlots.size(); i++) {
            int itemIndex = startIndex + i;
            if (itemIndex >= items.size()) {
                break;
            }
            OrderableItemOption option = items.get(itemIndex);
            choices.put(contentSlots.get(i), manager.itemSupport.itemSelectEntryValue(option));
        }
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new HashMap<>();
        entries.put("entry", choices);
        if (itemSelectState.page > 1) {
            entries.put("previous-page", entryAt(
                manager.guis().activeControlSlot("item-select.previous-page"), "previous", values));
        }
        if (itemSelectState.page < pageCount) {
            entries.put("next-page", entryAt(
                manager.guis().activeControlSlot("item-select.next-page"), "next", values));
        }
        Map<String, PublicGuiText.CycleSelection> cycles = new HashMap<>();
        if (manager.guis().buttonEnabled("item-select", "sort")) {
            List<String> options = guiLabels("item-select.item-sort-options", ITEM_SORT_OPTIONS);
            cycles.put("sort", new PublicGuiText.CycleSelection(options,
                options.isEmpty() ? -1 : Math.floorMod(itemSelectState.sortIndex, options.size())));
        }
        if (manager.guis().buttonEnabled("item-select", "filter")) {
            List<String> options = guiLabels("item-select.filter-options", FILTER_OPTIONS);
            cycles.put("filter", new PublicGuiText.CycleSelection(options,
                options.isEmpty() ? -1 : Math.floorMod(itemSelectState.filterIndex, options.size())));
        }
        openPublicMenu(player, "item-select", values, Map.of(), cycles, entries, null);
    }

    void openEnchantSelectMenu(Player player, boolean resetPage) {
        if (!publicGuiReady(player, "enchant-select")) return;
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        NewOrderDraft draft = viewState.getOrCreateDraft();
        Material material = resolveDraftMaterial(draft);
        if (!supportsOrderEnchantments(draft.customItemId(), material)) {
            manager.playSound(player, GUI_ERROR_SOUND);
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
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
        if (tryOpenEnchantDialog(player, viewState, draft, material, sanitizedEnchantments, enchantments)) {
            return;
        }
        enchantments = manager.itemSupport.getVisibleInventoryEnchantments(material);

        List<Integer> contentSlots = manager.guis().contentSlots("enchant-select", "content-slots");
        int pageCount = calculatePageCount(enchantments.size(), contentSlots.size());
        if (viewState.enchantPage < 1) {
            viewState.enchantPage = 1;
        }
        if (viewState.enchantPage > pageCount) {
            viewState.enchantPage = pageCount;
        }

        Map<String, String> values = TextFormat.placeholders(
            "page", formatCompactAmount(viewState.enchantPage),
            "max_page", formatCompactAmount(pageCount),
            "selected", formatCompactAmount(sanitizedEnchantments.size()));
        Map<Integer, PublicGuiRenderInput.EntryValue> choices = new HashMap<>();
        int startIndex = (viewState.enchantPage - 1) * contentSlots.size();
        for (int i = 0; i < contentSlots.size(); i++) {
            int enchantIndex = startIndex + i;
            if (enchantIndex >= enchantments.size()) {
                break;
            }
            Enchantment enchantment = enchantments.get(enchantIndex);
            int level = sanitizedEnchantments.getOrDefault(enchantment.getKey().toString(), 0);
            int max = Math.max(1, enchantment.getMaxLevel());
            String current = level > 0 ? manager.guis().configuredText(
                "labels.enchant-select.level-format", TextFormat.placeholders(
                    "roman", manager.itemSupport.toRomanNumeral(level), "level", level))
                : manager.guis().configuredText("labels.enchant-select.none", Map.of());
            String maximum = manager.guis().configuredText("labels.enchant-select.level-format",
                TextFormat.placeholders("roman", manager.itemSupport.toRomanNumeral(max), "level", max));
            choices.put(contentSlots.get(i), new PublicGuiRenderInput.EntryValue(
                enchantment.getKey().toString(), new ItemStack(Material.BOOK),
                TextFormat.placeholders("enchant", formatEnchantmentName(enchantment),
                    "current", current, "max", maximum, "item", formatMaterialName(material)),
                level > 0 ? "selected" : "unselected"));
        }
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new HashMap<>();
        entries.put("entry", choices);
        if (viewState.enchantPage > 1) {
            entries.put("previous-page", entryAt(
                manager.guis().activeControlSlot("enchant-select.previous-page"), "previous", values));
        }
        if (viewState.enchantPage < pageCount) {
            entries.put("next-page", entryAt(
                manager.guis().activeControlSlot("enchant-select.next-page"), "next", values));
        }
        openPublicMenu(player, "enchant-select", values, Map.of(), Map.of(), entries, null);
    }

    private boolean tryOpenEnchantDialog(
        Player player,
        MenuViewState viewState,
        NewOrderDraft draft,
        Material material,
        Map<String, Integer> selectedEnchantments,
        List<Enchantment> enchantments
    ) {
        FoOrdersDialogInputService dialogInputService = manager.dialogInputService();
        if (manager.isBedrockPlayer(player) || dialogInputService == null || !dialogInputService.nativeEnabled()
            || !manager.itemSupport.enchantSelectionEntriesAvailable()) {
            return false;
        }

        EnchantDialogRequest request = createEnchantDialogRequest(
            draft,
            material,
            selectedEnchantments,
            enchantments
        );
        boolean suppressInventoryClose = manager.isOrdersMenu(player.getOpenInventory().getTopInventory());
        if (suppressInventoryClose) {
            manager.inventoryCloseSuppressor.suppressNextClose(player);
        }

        boolean opened = dialogInputService.openEnchantSelection(
            player,
            request,
            action -> handleEnchantDialogAction(player, action)
        );
        if (opened) {
            if (suppressInventoryClose) {
                scheduler.runLaterForPlayer(
                    player,
                    () -> manager.inventoryCloseSuppressor.clear(player),
                    NATIVE_DIALOG_CLOSE_SUPPRESSION_TICKS
                );
            }
            return true;
        }

        if (suppressInventoryClose) {
            manager.inventoryCloseSuppressor.clear(player);
        }
        return false;
    }

    private EnchantDialogRequest createEnchantDialogRequest(
        NewOrderDraft draft,
        Material material,
        Map<String, Integer> selectedEnchantments,
        List<Enchantment> enchantments
    ) {
        CustomItemStore.CustomItemDefinition customSelection = resolveCustomItemDefinition(draft.customItemId());
        ItemStack displayItem = customSelection == null ? new ItemStack(material) : customSelection.template().clone();
        if (displayItem.getType() == Material.AIR) {
            displayItem = new ItemStack(material);
        }
        displayItem.setAmount(1);

        String itemName = customSelection == null
            ? formatMaterialName(material)
            : resolveTemplateDisplayName(customSelection.template());
        List<EnchantDialogRow> rows = new ArrayList<>();
        for (Enchantment enchantment : enchantments) {
            String key = enchantment.getKey().toString();
            rows.add(new EnchantDialogRow(
                key,
                formatEnchantmentName(enchantment),
                Math.max(1, enchantment.getMaxLevel()),
                selectedEnchantments.getOrDefault(key, 0),
                enchantment.isCursed()
            ));
        }

        return new EnchantDialogRequest(
            material,
            displayItem,
            itemName,
            rows,
            selectedEnchantments.size()
        );
    }

    private void handleEnchantDialogAction(Player player, EnchantDialogAction action) {
        if (action == null) {
            return;
        }

        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        NewOrderDraft draft = viewState.getOrCreateDraft();
        Material material = resolveDraftMaterial(draft);
        if (!supportsOrderEnchantments(draft.customItemId(), material)) {
            manager.playSound(player, GUI_ERROR_SOUND);
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
            openNewOrderMenu(player);
            return;
        }

        switch (action.type()) {
            case BACK_TO_ITEMS -> openItemSelectMenu(player, false);
            case CONTINUE_ORDER -> openNewOrderMenu(player);
            case SET_LEVEL -> updateDialogEnchantLevel(player, viewState, draft, material, action.enchantmentKey(), action.level());
        }
    }

    private void updateDialogEnchantLevel(
        Player player,
        MenuViewState viewState,
        NewOrderDraft draft,
        Material material,
        String enchantmentKey,
        int requestedLevel
    ) {
        Enchantment selected = manager.itemSupport.resolveEnchantment(enchantmentKey);
        if (selected == null || !getSelectableEnchantments(material).contains(selected)) {
            openEnchantSelectMenu(player, false);
            return;
        }

        int maxLevel = Math.max(1, selected.getMaxLevel());
        int newLevel = Math.max(0, Math.min(maxLevel, requestedLevel));
        Map<String, Integer> updatedEnchantments =
            new LinkedHashMap<>(sanitizeDraftEnchantments(draft.customItemId(), material, draft.enchantLevels()));
        String key = selected.getKey().toString();
        int currentLevel = updatedEnchantments.getOrDefault(key, 0);
        if (newLevel <= 0 || currentLevel == newLevel) {
            updatedEnchantments.remove(key);
        } else {
            updatedEnchantments.put(key, newLevel);
        }

        viewState.draft = draft.withEnchantLevels(updatedEnchantments);
        openEnchantSelectMenu(player, false);
    }

    void openManageOrderMenu(Player player, int orderIndex) {
        if (!publicGuiReady(player, "manage-order")) return;
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        if (orderIndex < 0 || orderIndex >= playerData.getOrders().size()) {
            viewState.manageOrderIndex = -1;
            viewState.manageOrderId = null;
            manager.playSound(player, GUI_ERROR_SOUND);
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
            openYourOrdersMenu(player);
            return;
        }

        viewState.manageOrderIndex = orderIndex;
        viewState.claimPage = 1;
        PlayerDataStore.OrderEntry order = playerData.getOrders().get(orderIndex);
        viewState.manageOrderId = order.getOrderId();

        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new HashMap<>();
        if (!order.isCancelled()) {
            entries.put("cancel", entryAt(manager.guis().activeControlSlot("manage-order.cancel"),
                order.getOrderId(), Map.of()));
        }
        List<String> claimStacks = createManageClaimLore(order);
        Map<String, String> values = new HashMap<>();
        if (canModerateOrders(player)) {
            double remainingFunds = getRemainingOrderFunds(order);
            String refundLine = remainingFunds > 0D
                ? manager.guis().configuredText("items.manage-order.admin-actions.refund-line",
                    TextFormat.placeholders("refund", formatCompactAmount(remainingFunds)))
                : manager.guis().configuredText("items.manage-order.admin-actions.no-refund-line", Map.of());
            values.put("refund_line", refundLine);
            entries.put("admin-actions", entryAt(
                manager.guis().activeControlSlot("manage-order.admin-actions"),
                order.getOrderId(), values));
        }
        Map<String, PublicGuiText.CycleSelection> cycles = manager.guis().buttonEnabled("manage-order", "claim")
            ? Map.of("claim", new PublicGuiText.CycleSelection(claimStacks, -1)) : Map.of();
        openPublicMenu(player, "manage-order", values, Map.of(), cycles, entries, null);
    }

    void openClaimOrderMenu(Player player, boolean resetPage) {
        if (!publicGuiReady(player, "claim-order")) return;
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        PlayerDataStore.OrderEntry order = getSelectedOrder(playerData, viewState);
        if (order == null) {
            manager.playSound(player, GUI_ERROR_SOUND);
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
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

        List<Integer> contentSlots = manager.guis().contentSlots("claim-order", "content-slots");
        Map<String, String> values = TextFormat.placeholders(
            "page", formatCompactAmount(viewState.claimPage),
            "max_page", formatCompactAmount(pageCount));
        Map<Integer, PublicGuiRenderInput.EntryValue> stacks = new HashMap<>();
        for (int index = 0; index < pageStacks.size() && index < contentSlots.size(); index++) {
            int stackAmount = pageStacks.get(index);
            if (stackAmount <= 0) {
                continue;
            }
            ItemStack source = createOrderStack(order, stackAmount);
            String itemName = source.getItemMeta() != null && source.getItemMeta().hasDisplayName()
                ? source.getItemMeta().getDisplayName() : formatMaterialName(source.getType());
            stacks.put(contentSlots.get(index), new PublicGuiRenderInput.EntryValue(
                order.getOrderId() + ":" + ((viewState.claimPage - 1) * contentSlots.size() + index),
                source, TextFormat.placeholders(
                    "item", itemName, "amount", formatCompactAmount(stackAmount))));
        }
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new HashMap<>();
        entries.put("entry", stacks);
        if (viewState.claimPage > 1) {
            entries.put("previous-page", entryAt(
                manager.guis().activeControlSlot("claim-order.previous-page"), "previous", values));
        }
        if (viewState.claimPage < pageCount) {
            entries.put("next-page", entryAt(
                manager.guis().activeControlSlot("claim-order.next-page"), "next", values));
        }
        openPublicMenu(player, "claim-order", values, Map.of(), Map.of(), entries, null);
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
        String ownerName = order.getNpcName() == null ? resolvePlayerName(ownerId) : order.getNpcName();
        Inventory menu = createMenu(MenuType.ADMIN_ORDER_ACTIONS, 27, TITLE_ADMIN_ACTIONS);
        putItemIfPresent(menu, ADMIN_PREVIEW_SLOT, createOrderItem(ownerName, order, false));
        putItemIfPresent(menu,
            ADMIN_DELETE_SLOT,
            createSimpleItem(Material.LAVA_BUCKET, CANCEL_RED + "ᴅᴇʟᴇᴛᴇ ᴏʀᴅᴇʀ", List.of(
                WHITE + "Delete order and all claimable items",
                remainingFunds > 0D
                    ? WHITE + "Refund owner: " + ACCENT + "$" + formatCompactAmount(remainingFunds)
                    : MUTED + "No refund remaining"
            ))
        );
        putItemIfPresent(menu,
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
        viewState.adminEditorPage = 0;
        viewState.adminEditorSearch = "";
        viewState.adminItemDraft = null;
        openAdminItemEditorMenu(player, true);
    }

    void openAdminItemEditorMenu(Player player, boolean resetPage) {
        openAdminItemEditorMenu(player, resetPage, null);
    }

    void openAdminItemEditorMenu(Player player, boolean resetPage, String transitionSound) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        if (resetPage) {
            viewState.adminEditorPage = 1;
        }

        List<CustomItemStore.CustomItemDefinition> customItems = getFilteredAdminCustomItems(viewState.adminEditorSearch);
        List<EntryBrowserRequest.Entry> entries = new ArrayList<>(customItems.size());
        for (CustomItemStore.CustomItemDefinition customItem : customItems) {
            entries.add(EntryBrowserRequest.Entry.of(customItem.id(), createAdminCustomItemListEntry(customItem)));
        }

        EntryBrowserRequest request = EntryBrowserRequest.builder()
            .title(TITLE_ADMIN_EDITOR)
            .entries(entries)
            .page(viewState.adminEditorPage)
            .filter(viewState.adminEditorSearch)
            .buttons(manager.guiButtons())
            .showBack(false)
            .addButton(EditorItemFactory.button(
                player,
                Material.ANVIL,
                CONFIRM_GREEN,
                "Add Item",
                List.of(
                    WHITE + "Create a new custom order item",
                    MUTED + "Includes name, lore, model data, enchants, etc."
                ),
                "add an item"
            ))
            .emptyItem(EditorItemFactory.item(player, Material.PAPER, MUTED + "No Custom Items", List.of(
                WHITE + "Create one with the Anvil button"
            )))
            .context(new AdminCustomItemBrowserContext())
            .build();

        int maxPage = EntryBrowserMenus.maxPage(request);
        viewState.adminEditorPage = Math.max(0, Math.min(viewState.adminEditorPage, maxPage));
        openMenu(player, EntryBrowserMenus.createInventory(player, request.withPage(viewState.adminEditorPage)), transitionSound);
    }

    private List<CustomItemStore.CustomItemDefinition> getFilteredAdminCustomItems(String rawFilter) {
        String filter = rawFilter == null ? "" : rawFilter.trim().toLowerCase(Locale.ROOT);
        List<CustomItemStore.CustomItemDefinition> customItems = getSortedCustomItems();
        if (filter.isBlank()) {
            return customItems;
        }

        List<CustomItemStore.CustomItemDefinition> filtered = new ArrayList<>();
        for (CustomItemStore.CustomItemDefinition customItem : customItems) {
            String searchable = customItem.id() + " "
                + resolveTemplateDisplayName(customItem.template()) + " "
                + customItem.template().getType().name();
            if (searchable.toLowerCase(Locale.ROOT).contains(filter)) {
                filtered.add(customItem);
            }
        }
        return filtered;
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
        putItemIfPresent(menu,
            ADMIN_EDIT_ID_SLOT,
            EditorItemFactory.item(
                player,
                Material.PAPER,
                ACCENT + "&lID",
                List.of(
                    WHITE + resolvedId,
                    MUTED + "Set automatically from your template item"
                )
            )
        );
        putItemIfPresent(menu,
            ADMIN_EDIT_COPY_HAND_SLOT,
            EditorItemFactory.button(
                player,
                Material.ANVIL,
                ACCENT,
                "Set From Hand",
                List.of(
                    WHITE + "Hold the item in your main hand",
                    WHITE + "Click here to copy it as template",
                    MUTED + "Or drag/click an item onto the template slot"
                ),
                "set the template item"
            )
        );

        ItemStack template = draft.template();
        if (template == null || template.getType() == Material.AIR) {
            putItemIfPresent(menu,
                ADMIN_EDIT_TEMPLATE_SLOT,
                EditorItemFactory.button(
                    player,
                    Material.GRAY_STAINED_GLASS_PANE,
                    MUTED,
                    "No Template",
                    List.of(
                        WHITE + "Drag or click an item onto this slot",
                        WHITE + "Or use 'Set from hand'",
                        MUTED + "This stores name/lore/model data/enchants/etc."
                    ),
                    "set the template item"
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
            lore.add(FoButtonStyle.INFO_HEADER);
            lore.add(FoButtonStyle.informationLine("This item is used as the order template"));
            lore.add(FoButtonStyle.informationLine("Drag/click another item here to replace it"));
            lore.add(FoButtonStyle.informationLine("Or use 'Set from hand' again"));
            lore.add("");
            lore.add(FoButtonStyle.clickHint("replace the template item"));
            if (templateMeta != null) {
                templateMeta.setLore(lore);
                templatePreview.setItemMeta(templateMeta);
            }
            putItemIfPresent(menu, ADMIN_EDIT_TEMPLATE_SLOT, templatePreview);
        }

        putItemIfPresent(menu,
            ADMIN_EDIT_ENCHANTABLE_SLOT,
            EditorItemFactory.button(
                player,
                draft.allowOrderEnchants() ? Material.ENCHANTED_BOOK : Material.BOOK,
                ACCENT,
                "Order Enchants",
                List.of(
                    FoButtonStyle.stateLine(draft.allowOrderEnchants()),
                    WHITE + "If enabled, orderers can choose enchantments",
                    WHITE + "like normal tool orders."
                ),
                "toggle order enchants"
            )
        );
        putItemIfPresent(menu,
            ADMIN_EDIT_SAVE_SLOT,
            EditorItemFactory.button(
                player,
                Material.LIME_STAINED_GLASS_PANE,
                CONFIRM_GREEN,
                "Save",
                List.of(WHITE + "Save this custom item"),
                "save the custom item"
            )
        );
        putItemIfPresent(menu,
            ADMIN_EDIT_CANCEL_SLOT,
            manager.guiButtons().back(player)
        );

        if (draft.existingId() != null) {
            putItemIfPresent(menu,
                ADMIN_EDIT_DELETE_SLOT,
                EditorItemFactory.button(
                    player,
                    Material.LAVA_BUCKET,
                    CANCEL_RED,
                    "Remove",
                    List.of(
                        WHITE + "Remove this custom item",
                        MUTED + "Blocked while active orders still use it"
                    ),
                    "remove the custom item"
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
        lore.add(FoButtonStyle.INFO_HEADER);
        lore.add(FoButtonStyle.informationLine("ID: " + ACCENT + definition.id()));
        lore.add(FoButtonStyle.informationLine("Order enchants: " + FoButtonStyle.stateText(definition.allowOrderEnchants())));
        lore.add("");
        lore.add(FoButtonStyle.clickHint("edit the item"));
        lore.add(FoButtonStyle.informationLine("Shift + Right Click to remove"));
        meta.setLore(lore);
        preview.setItemMeta(meta);
        return preview;
    }

    void openDeliverMenu(Player player, MainOrderView selectedOrder) {
        UUID delivererId = player.getUniqueId();
        manager.handledDeliveryClosePlayers.remove(delivererId);
        PendingDeliveryState previous = pendingDeliveries.remove(delivererId);
        waitingDeliveryPlayers.remove(delivererId);
        if (previous != null) {
            returnPendingItems(player, previous.submittedItems());
        }

        PlayerDataStore.PlayerData ownerData = playerDataStore.getOrCreate(selectedOrder.ownerId());
        PlayerDataStore.OrderEntry liveOrder = findOrderById(ownerData, selectedOrder.order().getOrderId());
        if (liveOrder == null || liveOrder.isCancelled()) {
            manager.playSound(player, GUI_ERROR_SOUND);
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
            openOrdersMenu(player, null);
            return;
        }

        int remainingNeeded = Math.max(0, liveOrder.getAmountOrdered() - liveOrder.getAmountDelivered());
        if (remainingNeeded <= 0) {
            manager.playSound(player, GUI_ERROR_SOUND);
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
            openOrdersMenu(player, null);
            return;
        }

        Inventory menu;
        try {
            PublicGuiFrame frame = manager.publicGuis().service("deliver").plan("orders.deliver",
                new PublicGuiRenderInput(publicValues(Map.of()), Map.of(), Map.of(), Map.of()));
            menu = createMenu(MenuType.DELIVER, frame.definition().size(), frame.title());
            PublicGuiRenderer.renderInto(player, menu, frame);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            manager.plugin.getLogger().severe("Could not open delivery GUI: " + exception.getMessage());
            manager.interactionSupport.sendErrorActionbar(player,
                manager.messages().get("actionbar.action-unavailable"));
            return;
        }
        pendingDeliveries.put(delivererId,
            new PendingDeliveryState(selectedOrder.ownerId(), liveOrder.getOrderId(), null));
        manager.playSound(player, ORDER_DELIVERY_OPEN_SOUND);
        manager.itemSupport.openMenu(player, menu);
    }

    void openDeliveryConfirmMenu(Player player) {
        if (!publicGuiReady(player, "delivery-confirm")) {
            manager.interactionSupport.deliverySupport.returnAndClearPendingDelivery(
                player, player.getUniqueId());
            return;
        }
        openPublicMenu(player, "delivery-confirm", Map.of(), Map.of(), Map.of(), Map.of(),
            ORDER_DELIVERY_CONFIRM_SOUND);
    }

    void openHistoryMenu(Player viewer, UUID targetPlayerId, HistoryDataStore.HistoryType historyType, boolean fromAdminCommand) {
        if (!manager.historyEnabled) {
            notifyHistoryUnavailable(viewer, targetPlayerId, fromAdminCommand);
            return;
        }

        UUID resolvedTargetId = targetPlayerId == null ? viewer.getUniqueId() : targetPlayerId;
        if (!canAccessHistory(viewer, resolvedTargetId)) {
            notifyHistoryUnavailable(viewer, resolvedTargetId, fromAdminCommand);
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
        if (!publicGuiReady(player, "history")) return;
        UUID viewerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(viewerId, ignored -> new MenuViewState());
        HistoryDataStore.HistoryType activeType = viewState.historyType == null ? HistoryDataStore.HistoryType.ORDER : viewState.historyType;
        UUID targetPlayerId = viewState.historyTargetId == null ? viewerId : viewState.historyTargetId;
        if (!canAccessHistory(player, targetPlayerId)) {
            notifyHistoryUnavailable(player, targetPlayerId, false);
            openOrdersMenu(player, null);
            return;
        }

        if (resetPage) {
            viewState.historyPage = 1;
        }

        String entryState = activeType == HistoryDataStore.HistoryType.ORDER ? "order" : "deliver";
        List<HistoryDataStore.HistoryEntry> entries = !manager.guis().sourceEntryStateEnabled("history.entry", entryState)
            ? List.of()
            : activeType == HistoryDataStore.HistoryType.ORDER
                ? historyDataStore.getOrderHistory(targetPlayerId)
                : historyDataStore.getDeliverHistory(targetPlayerId);

        List<Integer> contentSlots = manager.guis().contentSlots("history", "content-slots");
        int pageCount = calculatePageCount(entries.size(), contentSlots.size());
        if (viewState.historyPage < 1) {
            viewState.historyPage = 1;
        }
        if (viewState.historyPage > pageCount) {
            viewState.historyPage = pageCount;
        }

        String targetName = resolvePlayerName(targetPlayerId);
        String tabName = activeType == HistoryDataStore.HistoryType.ORDER
            ? manager.guis().text("labels.history.order-tab-title", "ᴏʀᴅᴇʀ", Map.of())
            : manager.guis().text("labels.history.deliver-tab-title", "ᴅᴇʟɪᴠᴇʀ", Map.of());
        Map<String, String> values = TextFormat.placeholders(
            "tab", tabName, "target", targetName,
            "page", formatCompactAmount(viewState.historyPage),
            "max_page", formatCompactAmount(pageCount),
            "pages", formatCompactAmount(pageCount),
            "entries", formatCompactAmount(entries.size()));
        Map<Integer, PublicGuiRenderInput.EntryValue> rows = new HashMap<>();
        int startIndex = (viewState.historyPage - 1) * contentSlots.size();
        for (int index = 0; index < contentSlots.size(); index++) {
            int historyIndex = startIndex + index;
            if (historyIndex >= entries.size()) {
                break;
            }
            HistoryDataStore.HistoryEntry historyEntry = entries.get(historyIndex);
            rows.put(contentSlots.get(index), new PublicGuiRenderInput.EntryValue(
                activeType + ":" + historyIndex + ":" + historyEntry.timestamp(),
                new ItemStack(Material.BOOK), historyEntryValues(historyEntry, targetName), entryState));
        }
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> shown = new HashMap<>();
        shown.put("entry", rows);
        if (viewState.historyPage > 1) {
            shown.put("previous-page", entryAt(
                manager.guis().activeControlSlot("history.previous-page"), "previous", values));
        }
        if (viewState.historyPage < pageCount) {
            shown.put("next-page", entryAt(
                manager.guis().activeControlSlot("history.next-page"), "next", values));
        }
        openPublicMenu(player, "history", values,
            Map.of("order-tab", activeType == HistoryDataStore.HistoryType.ORDER ? "selected" : "unselected",
                "deliver-tab", activeType == HistoryDataStore.HistoryType.DELIVER ? "selected" : "unselected"),
            Map.of(), shown, null);
    }

    void notifyHistoryUnavailable(Player viewer, UUID targetPlayerId, boolean fromAdminCommand) {
        boolean viewingAnotherPlayer = targetPlayerId != null && !viewer.getUniqueId().equals(targetPlayerId);
        manager.messages().send(viewer, historyAccessFeedbackPath(
            manager.historyEnabled, fromAdminCommand || viewingAnotherPlayer));
    }

    static String historyAccessFeedbackPath(boolean historyEnabled, boolean viewingAnotherPlayer) {
        if (!historyEnabled) {
            return "history.disabled-config";
        }
        return viewingAnotherPlayer ? "history.access-denied-player" : "history.access-denied";
    }

    private ItemStack createHistoryTab(String key, boolean selected) {
        String path = "history." + key;
        Material icon = manager.guis().stateMaterial(path, selected);
        if (icon == null) {
            return null;
        }
        String state = selected ? "selected" : "unselected";
        String color = manager.guis().stateColor(path, selected);
        String name = manager.guis().configuredText("items." + path + "." + state + "-name",
            TextFormat.placeholders("state_color", color));
        List<String> stateLore = manager.guis().lines("items." + path + "." + state + "-lore",
            Map.of(), Map.of());
        return createGuiItem(path, icon, "", List.of(),
            TextFormat.placeholders("state_color", color, "state_name", name),
            Map.of("{state_lore}", stateLore));
    }

    ItemStack createHistoryEntryItem(HistoryDataStore.HistoryType historyType, HistoryDataStore.HistoryEntry entry, String targetName) {
        Material icon = manager.guis().stateMaterial("history.entry",
            historyType == HistoryDataStore.HistoryType.ORDER ? "order" : "deliver");
        if (icon == null) {
            return null;
        }
        Map<String, String> values = historyEntryValues(entry, targetName);
        return createGuiItem("history.entry", icon, ACCENT + values.get("action"),
            List.of(WHITE + values.get("details"), MUTED + values.get("target"),
                MUTED + values.get("time")), values);
    }

    private Map<String, String> historyEntryValues(HistoryDataStore.HistoryEntry entry, String targetName) {
        String timestamp = entry.timestamp() > 0L
            ? formatHistoryTimestamp(entry.timestamp())
            : manager.guis().configuredText("labels.history.unknown-time", Map.of());
        String action;
        String details;
        if (entry.eventKey().isBlank()) {
            action = sanitizeHistoryLabel(entry.action(), "labels.history.unknown-event");
            details = sanitizeHistoryLabel(entry.details(), "labels.history.unknown-details");
        } else {
            Map<String, String> values = new HashMap<>(entry.values());
            values.put("item", manager.itemSupport.formatHistoryItem(entry.values()));
            for (String numberKey : List.of("amount", "price", "subtotal", "tax", "total", "payout", "refund")) {
                if (values.containsKey(numberKey)) {
                    try {
                        values.put(numberKey, formatCompactAmount(Double.parseDouble(values.get(numberKey))));
                    } catch (NumberFormatException exception) {
                        values.put(numberKey, "");
                    }
                }
            }
            String root = "events.history." + entry.eventKey();
            action = manager.guis().configuredText(root + ".action", values);
            details = manager.guis().configuredText(root + ".details", values);
            if (!HISTORY_EVENT_KEYS.contains(entry.eventKey())) {
                if (!manager.guis().has(root + ".action")) {
                    action = manager.guis().configuredText("labels.history.unknown-event", Map.of());
                }
                if (!manager.guis().has(root + ".details")) {
                    details = manager.guis().configuredText("labels.history.unknown-details", Map.of());
                }
            }
        }
        return TextFormat.placeholders(
                "action", action,
                "details", details,
                "target", targetName,
                "time", timestamp
            );
    }

    private String formatHistoryTimestamp(long timestamp) {
        String pattern = manager.guis().rawString("labels.history.timestamp-pattern");
        String language = manager.guis().rawString("labels.history.timestamp-locale");
        String zoneText = manager.guis().rawString("labels.history.timestamp-zone");
        if (pattern.isBlank() || language.isBlank() || zoneText.isBlank()) {
            return manager.guis().configuredText("labels.history.unknown-time", Map.of());
        }
        try {
            ZoneId zone = zoneText.equalsIgnoreCase("system") ? ZoneId.systemDefault() : ZoneId.of(zoneText);
            return DateTimeFormatter.ofPattern(pattern, Locale.forLanguageTag(language))
                .withZone(zone).format(Instant.ofEpochMilli(timestamp));
        } catch (IllegalArgumentException exception) {
            return manager.guis().configuredText("labels.history.unknown-time", Map.of());
        }
    }

    String sanitizeHistoryLabel(String value, String emptyLabelPath) {
        if (value == null || value.isBlank()) {
            return manager.guis().configuredText(emptyLabelPath, Map.of());
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
            if (!isPublicMenu(topInventory, "main")) {
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
            if (!isPublicMenu(topInventory, "item-select")) {
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
        if (!isPublicMenu(topInventory, "main")) {
            return false;
        }
        openOrdersMenu(player, null);
        return true;
    }

    private static boolean isPublicMenu(Inventory inventory, String key) {
        return inventory.getHolder() instanceof PublicGuiInventoryHolder holder
            && holder.frame().definition().menuId().equals("orders." + key);
    }

    List<MainOrderView> getVisibleMainOrders(MenuViewState viewState, PlayerDataStore.PlayerData playerData) {
        if (!manager.guis().itemEnabled("main.order-entry")) {
            return List.of();
        }
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

            if (!matchesItemFilter(orderMaterial, viewState.mainFilterIndex)) {
                continue;
            }

            UUID ownerId = record.getPlayerId();
            String ownerName = order.getNpcName() == null
                ? ownerNameCache.computeIfAbsent(ownerId, this::resolvePlayerName) : order.getNpcName();
            if (!searchLower.isBlank()) {
                String itemSearchable = buildOrderSearchText(order);
                String ownerSearchable = ownerName.toLowerCase(Locale.ROOT);
                if (!itemSearchable.contains(searchLower) && !ownerSearchable.contains(searchLower)) {
                    continue;
                }
            }

            visibleOrders.add(new MainOrderView(ownerId, ownerName, order));
        }

        visibleOrders.sort((left, right) -> compareByMainSort(left.order(), right.order(), viewState.mainSortIndex));
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
            putItemIfPresent(menu, orderSlots.get(slotIndex), createOrderItem(orderView.ownerName(), orderView.order(), canModerate));
        }
    }

    void populateMainOrderFrame(Inventory menu) {
        ItemStack grayPane = createGuiItem("main.gray-pane", Material.GRAY_STAINED_GLASS_PANE, "", List.of());
        ItemStack emptyOrderPane = createGuiItem("main.empty-order-slot", Material.LIGHT_GRAY_STAINED_GLASS_PANE, "", List.of());
        for (int slot : manager.guis().contentSlots("main", "frame-slots")) {
            setFrameItem(menu, slot, grayPane);
        }
        for (int slot : manager.guis().contentSlots("main", "empty-slots")) {
            setFrameItem(menu, slot, emptyOrderPane);
        }
    }

    private void setFrameItem(Inventory menu, int slot, ItemStack item) {
        if (item != null) {
            putItemIfPresent(menu, slot, item.clone());
        }
    }

    private int refreshGuiItemCachesIfNeeded() {
        int revision = manager.guis().revision();
        int messageRevision = manager.messages().revision();
        if (staticGuiItemRevision != revision || messageGuiItemRevision != messageRevision) {
            staticGuiItemTemplates.clear();
            cyclingGuiItemTemplates.clear();
            staticGuiItemRevision = revision;
            messageGuiItemRevision = messageRevision;
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

    int calculatePageCount(int totalItems, int pageSize) {
        return pageSize <= 0 ? 1 : Math.max(1, (int) Math.ceil(totalItems / (double) pageSize));
    }
}
