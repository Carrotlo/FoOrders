package me.foesio.foOrders;

import me.foesio.core.editor.CursorItemEditor;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.foOrders.api.OrderActionResult;
import me.foesio.foOrders.api.OrderQuote;
import me.foesio.foOrders.api.OrderRequest;
import me.foesio.foOrders.api.OrderSnapshot;
import me.foesio.foOrders.api.event.OrderBeforeCreateEvent;
import me.foesio.foOrders.api.event.OrderCreatedEvent;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import me.foesio.foOrders.util.TextFormat;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuActionSupport {
    private static final Pattern NPC_NAME = Pattern.compile("[A-Za-z0-9 _.-]{1,32}");
    private final OrdersMenuManager manager;
    private final OrdersMenuInteractionSupport interaction;
    private final OrdersMenuDeliverySupport deliverySupport;
    private final Map<UUID, MenuViewState> menuStates;
    private final PlayerDataStore playerDataStore;
    private final CustomItemStore customItemStore;
    private final HistoryDataStore historyDataStore;

    OrdersMenuActionSupport(OrdersMenuManager manager, OrdersMenuInteractionSupport interaction) {
        this.manager = manager;
        this.interaction = interaction;
        this.deliverySupport = interaction.deliverySupport;
        this.menuStates = manager.menuStates;
        this.playerDataStore = manager.playerDataStore;
        this.customItemStore = manager.customItemStore;
        this.historyDataStore = manager.historyDataStore;
    }

    private void refreshMainMenu(Player player) {
        manager.viewSupport.refreshMainMenu(player);
    }

    private void refreshMainMenuDebounced(Player player) {
        manager.viewSupport.refreshMainMenuDebounced(player);
    }

    private void openSignInput(Player player, SignInputType inputType) {
        interaction.inputSupport.openSignInput(player, inputType);
    }

    private int calculatePageCount(int totalItems, int pageSize) {
        return manager.viewSupport.calculatePageCount(totalItems, pageSize);
    }

    private List<MainOrderView> getVisibleMainOrders(MenuViewState viewState, PlayerDataStore.PlayerData playerData) {
        return manager.viewSupport.getVisibleMainOrders(viewState, playerData);
    }

    private List<MainOrderView> getCachedVisibleMainOrders(MenuViewState viewState, PlayerDataStore.PlayerData playerData) {
        if (viewState.visibleMainOrders == null) {
            viewState.visibleMainOrders = getVisibleMainOrders(viewState, playerData);
        }
        return viewState.visibleMainOrders;
    }

    private void openYourOrdersMenu(Player player) {
        manager.viewSupport.openYourOrdersMenu(player);
    }

    private void openYourOrdersMenu(Player player, String transitionSound) {
        manager.viewSupport.openYourOrdersMenu(player, transitionSound);
    }

    private void openNewOrderMenu(Player player) {
        manager.viewSupport.openNewOrderMenu(player);
    }

    private void openNewOrderMenu(Player player, String transitionSound) {
        manager.viewSupport.openNewOrderMenu(player, transitionSound);
    }

    private void openItemSelectMenu(Player player, boolean resetPage) {
        manager.viewSupport.openItemSelectMenu(player, resetPage);
    }

    private void openItemSelection(Player player) {
        if (manager.isBedrockPlayer(player)
            || !manager.plugin.getConfig().getBoolean("native-dialogs.enabled", true)
            || !manager.itemSelectionDialogsEnabled
            || manager.dialogService() == null) {
            openItemSelectMenu(player, true);
            return;
        }

        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        viewState.getOrCreateItemSelectState().page = 1;

        NewOrderDraft draft = viewState.getOrCreateDraft();
        FoOrdersItemSelectionDialogService itemDialogService = manager.itemSelectionDialogService();
        if (itemDialogService != null && itemDialogService.open(
            player,
            itemChoiceKey(draft),
            selected -> selectOrderItem(player, viewState, selected),
            () -> openItemSelectMenu(player, true)
        )) {
            return;
        }
        openItemSelectMenu(player, true);
    }

    private void refreshItemSelectMenuDebounced(Player player) {
        manager.viewSupport.refreshItemSelectMenuDebounced(player);
    }

    private void openEnchantSelectMenu(Player player, boolean resetPage) {
        manager.viewSupport.openEnchantSelectMenu(player, resetPage);
    }

    private void openManageOrderMenu(Player player, int orderIndex) {
        manager.viewSupport.openManageOrderMenu(player, orderIndex);
    }

    private void openAdminOrderActionsMenu(Player player, UUID ownerId, String orderId) {
        manager.viewSupport.openAdminOrderActionsMenu(player, ownerId, orderId);
    }

    private void openOrdersMenu(Player player, String searchText) {
        manager.viewSupport.openOrdersMenu(player, searchText);
    }

    private void openOrdersMenu(Player player, String searchText, String transitionSound) {
        manager.viewSupport.openOrdersMenu(player, searchText, transitionSound);
    }

    private void openHistoryMenu(Player player, UUID targetPlayerId, HistoryDataStore.HistoryType historyType, boolean fromAdminCommand) {
        manager.viewSupport.openHistoryMenu(player, targetPlayerId, historyType, fromAdminCommand);
    }

    private void openHistoryMenu(Player player, boolean resetPage) {
        manager.viewSupport.openHistoryMenu(player, resetPage);
    }

    private void openAdminItemEditorMenu(Player player, boolean resetPage) {
        manager.viewSupport.openAdminItemEditorMenu(player, resetPage);
    }

    private void openAdminItemEditorMenu(Player player, boolean resetPage, String transitionSound) {
        manager.viewSupport.openAdminItemEditorMenu(player, resetPage, transitionSound);
    }

    private void openAdminItemEditMenu(Player player) {
        manager.viewSupport.openAdminItemEditMenu(player);
    }

    private void openDeliverMenu(Player player, MainOrderView selectedOrder) {
        manager.viewSupport.openDeliverMenu(player, selectedOrder);
    }

    private void openClaimOrderMenu(Player player, boolean resetPage) {
        manager.viewSupport.openClaimOrderMenu(player, resetPage);
    }

    private boolean canOpenOwnHistory(Player player) {
        return interaction.canOpenOwnHistory(player);
    }

    private boolean isAdminOrderModerationClick(Player player, ClickType clickType) {
        return deliverySupport.isAdminOrderModerationClick(player, clickType);
    }

    private int getMaxOrdersForPlayer(Player player) {
        return interaction.getMaxOrdersForPlayer(player);
    }

    private PlayerDataStore.OrderEntry getSelectedOrder(PlayerDataStore.PlayerData playerData, MenuViewState viewState) {
        return deliverySupport.getSelectedOrder(playerData, viewState);
    }

    private void handleOrderCancellation(
        Player player,
        PlayerDataStore.PlayerData playerData,
        MenuViewState viewState,
        PlayerDataStore.OrderEntry selectedOrder
    ) {
        deliverySupport.handleOrderCancellation(player, playerData, viewState, selectedOrder);
    }

    private boolean canModerateOrders(Player player) {
        return deliverySupport.canModerateOrders(player);
    }

    private AdminOrderTarget resolveAdminOrderTarget(MenuViewState viewState) {
        return deliverySupport.resolveAdminOrderTarget(viewState);
    }

    private void clearAdminTarget(MenuViewState viewState) {
        deliverySupport.clearAdminTarget(viewState);
    }

    private void handleAdminDeleteOrder(Player player, MenuViewState viewState, AdminOrderTarget target) {
        deliverySupport.handleAdminDeleteOrder(player, viewState, target);
    }

    private boolean handleAdminCancelOrder(Player player, AdminOrderTarget target) {
        return deliverySupport.handleAdminCancelOrder(player, target);
    }

    private boolean isCustomItemInUse(String customItemId) {
        return manager.itemSupport.isCustomItemInUse(customItemId);
    }

    private String generateUniqueCustomItemId(ItemStack template) {
        return manager.itemSupport.generateUniqueCustomItemId(template);
    }

    private String generateUniqueCustomItemId(String preferredBase) {
        return manager.itemSupport.generateUniqueCustomItemId(preferredBase);
    }

    private String generateUniqueCustomItemId(String preferredBase, String keepId) {
        return manager.itemSupport.generateUniqueCustomItemId(preferredBase, keepId);
    }

    private List<CustomItemStore.CustomItemDefinition> getSortedCustomItems() {
        return manager.itemSupport.getSortedCustomItems();
    }

    private List<OrderableItemOption> getFilteredSortedItems(ItemSelectState itemSelectState) {
        return manager.itemSupport.getCachedFilteredSortedItems(itemSelectState);
    }

    private boolean isOrderBlacklisted(Material material, Map<String, Integer> enchantments) {
        return manager.itemSupport.isOrderBlacklisted(material, enchantments);
    }

    private void sendErrorActionbar(Player player, String message) {
        interaction.sendErrorActionbar(player, message);
    }

    private Map<String, Integer> sanitizeDraftEnchantments(String customItemId, Material material, Map<String, Integer> rawEnchantments) {
        return manager.itemSupport.sanitizeDraftEnchantments(customItemId, material, rawEnchantments);
    }

    private boolean supportsOrderEnchantments(String customItemId, Material material) {
        return manager.itemSupport.supportsOrderEnchantments(customItemId, material);
    }

    private Material resolveDraftMaterial(NewOrderDraft draft) {
        return manager.itemSupport.resolveDraftMaterial(draft);
    }

    private Material resolveMaterial(String materialName) {
        return manager.itemSupport.resolveMaterial(materialName);
    }

    private List<org.bukkit.enchantments.Enchantment> getVisibleInventoryEnchantments(Material material) {
        return manager.itemSupport.getVisibleInventoryEnchantments(material);
    }

    private String formatCompactAmount(double value) {
        return manager.itemSupport.formatCompactAmount(value);
    }

    private CustomItemStore.CustomItemDefinition resolveCustomItemDefinition(String customItemId) {
        return manager.itemSupport.resolveCustomItemDefinition(customItemId);
    }

    private boolean hasEconomyProvider() {
        return deliverySupport.hasEconomyProvider();
    }

    private boolean hasEconomyBalance(Player player, double amount) {
        return deliverySupport.hasEconomyBalance(player, amount);
    }

    private boolean withdrawEconomy(Player player, double amount) {
        return deliverySupport.withdrawEconomy(player, amount);
    }

    private void appendOrderEvent(UUID playerId, String eventKey, PlayerDataStore.OrderEntry order,
                                  Map<String, String> values) {
        deliverySupport.appendOrderEvent(playerId, eventKey, order, values);
    }

    private void sendOrderCreatedWebhook(Player player, PlayerDataStore.OrderEntry order, double totalCost) {
        deliverySupport.sendOrderCreatedWebhook(player, order, totalCost);
    }

    private void announceCreatedOrderInChat(Player player, PlayerDataStore.OrderEntry order, double totalCost) {
        deliverySupport.announceCreatedOrderInChat(player, order, totalCost);
    }

    private String formatOrderDisplayName(PlayerDataStore.OrderEntry order) {
        return manager.itemSupport.formatOrderDisplayName(order);
    }

    private List<Integer> initializeClaimSessionStacks(MenuViewState viewState, PlayerDataStore.OrderEntry order, boolean forceReset) {
        return deliverySupport.initializeClaimSessionStacks(viewState, order, forceReset);
    }

    private int calculateClaimSessionPageCount(List<Integer> claimSessionStacks) {
        return deliverySupport.calculateClaimSessionPageCount(claimSessionStacks);
    }

    private void dropClaimPage(
        Player player,
        PlayerDataStore.PlayerData playerData,
        MenuViewState viewState,
        PlayerDataStore.OrderEntry selectedOrder,
        List<Integer> claimSessionStacks
    ) {
        deliverySupport.dropClaimPage(player, playerData, viewState, selectedOrder, claimSessionStacks);
    }

    private int transferClaimedItemsToPlayer(Player player, PlayerDataStore.OrderEntry order, int amount) {
        return deliverySupport.transferClaimedItemsToPlayer(player, order, amount);
    }

    private boolean removeOrderIfCompleted(PlayerDataStore.PlayerData playerData, MenuViewState viewState) {
        return deliverySupport.removeOrderIfCompleted(playerData, viewState);
    }

    private void clearClaimSession(MenuViewState viewState) {
        deliverySupport.clearClaimSession(viewState);
    }

    private void sendOrderClaimedWebhook(Player player, PlayerDataStore.OrderEntry order, int claimedAmount, String claimMethod) {
        deliverySupport.sendOrderClaimedWebhook(player, order, claimedAmount, claimMethod);
    }

    private boolean canAccessHistory(Player player, UUID targetPlayerId) {
        return interaction.canAccessHistory(player, targetPlayerId);
    }

    void handleMainClick(Player player, int rawSlot, ClickType clickType) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        int inventorySize = manager.guis().size("main");
        int sortSlot = manager.guis().activeControlSlot("main.sort");
        int filterSlot = manager.guis().activeControlSlot("main.filter");
        int refreshSlot = manager.guis().activeControlSlot("main.refresh");
        int searchSlot = manager.guis().activeControlSlot("main.search");
        int mainBackSlot = manager.guis().activeControlSlot("main.previous-page");
        int mainNextSlot = manager.guis().activeControlSlot("main.next-page");
        int yourOrdersSlot = manager.guis().activeControlSlot("main.your-orders");
        int historySlot = manager.guis().activeControlSlot("main.history");
        List<Integer> orderSlots = manager.guis().contentSlots("main", "order-slots");

        if (rawSlot == sortSlot) {
            viewState.mainSortIndex = (viewState.mainSortIndex + 1) % SORT_OPTIONS.size();
            manager.playSoundWithPitchVariation(player, GUI_SORT_SOUND);
            viewState.page = 1;
            refreshMainMenuDebounced(player);
            return;
        }

        if (rawSlot == filterSlot) {
            viewState.mainFilterIndex = (viewState.mainFilterIndex + 1) % FILTER_OPTIONS.size();
            manager.playSoundWithPitchVariation(player, GUI_FILTER_SOUND);
            viewState.page = 1;
            refreshMainMenuDebounced(player);
            return;
        }

        if (rawSlot == refreshSlot) {
            if (manager.tryMarkOrderMenuRefresh(player)) {
                manager.playSound(player, GUI_REFRESH_SOUND);
                refreshMainMenu(player);
            } else {
                manager.notifyRefreshCooldown(player);
            }
            return;
        }

        if (rawSlot == searchSlot && manager.guis().buttonEnabled("main", "search")) {
            manager.playSound(player, GUI_SEARCH_SOUND);
            openSignInput(player, SignInputType.MAIN_SEARCH);
            return;
        }

        if (rawSlot == mainBackSlot && viewState.page > 1
            && manager.guis().buttonEnabled("main", "previous-page")) {
            viewState.page--;
            manager.playSound(player, GUI_PAGE_PREVIOUS_SOUND);
            refreshMainMenu(player);
            return;
        }

        if (rawSlot == mainNextSlot && manager.guis().buttonEnabled("main", "next-page")) {
            int pageCount = calculatePageCount(getCachedVisibleMainOrders(viewState, playerData).size(), orderSlots.size());
            if (viewState.page < pageCount) {
                viewState.page++;
                manager.playSound(player, GUI_PAGE_NEXT_SOUND);
                refreshMainMenu(player);
            }
            return;
        }

        if (rawSlot == yourOrdersSlot) {
            openYourOrdersMenu(player);
            return;
        }

        if (rawSlot == historySlot) {
            if (!canOpenOwnHistory(player)) {
                return;
            }
            openHistoryMenu(player, player.getUniqueId(), HistoryDataStore.HistoryType.ORDER, false);
            return;
        }

        if (!manager.guis().itemEnabled("main.order-entry")) {
            return;
        }
        int orderSlotIndex = orderSlots.indexOf(rawSlot);
        if (orderSlotIndex >= 0) {
            int orderIndex = (viewState.page - 1) * orderSlots.size() + orderSlotIndex;
            List<MainOrderView> visibleOrders = getCachedVisibleMainOrders(viewState, playerData);
            if (orderIndex < 0 || orderIndex >= visibleOrders.size()) {
                return;
            }

            MainOrderView selected = visibleOrders.get(orderIndex);
            if (isAdminOrderModerationClick(player, clickType)) {
                openAdminOrderActionsMenu(player, selected.ownerId(), selected.order().getOrderId());
                return;
            }

            if (selected.ownerId().equals(playerId)) {
                int ownOrderIndex = findOwnOrderIndex(playerData, selected.order().getOrderId());
                if (ownOrderIndex >= 0) {
                    viewState.manageOrderIndex = ownOrderIndex;
                    viewState.claimPage = 1;
                    openManageOrderMenu(player, ownOrderIndex);
                } else {
                    manager.playSound(player, GUI_ERROR_SOUND);
                    sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                    openYourOrdersMenu(player);
                }
                return;
            }

            openDeliverMenu(player, selected);
        }
    }

    void handleYourOrdersClick(Player player, int rawSlot) {
        UUID playerId = player.getUniqueId();
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        int playerMaxOrders = getMaxOrdersForPlayer(player);
        int newOrderSlot = manager.guis().activeControlSlot("your-orders.new-order");
        List<Integer> orderSlots = manager.viewSupport.yourOrderSlots(newOrderSlot);
        int displayCount = Math.min(playerData.getOrders().size(), Math.min(playerMaxOrders, orderSlots.size()));

        if (rawSlot == newOrderSlot) {
            if (playerData.getOrders().size() >= playerMaxOrders) {
                manager.playSound(player, GUI_ERROR_SOUND);
                manager.messages().send(player, "orders.max-orders", PluginMessages.placeholders("amount", formatCompactAmount(playerMaxOrders)));
                return;
            }
            openNewOrderMenu(player);
            return;
        }

        if (!manager.guis().itemEnabled("your-orders.order-entry")) {
            return;
        }
        int orderSlotIndex = orderSlots.indexOf(rawSlot);
        if (orderSlotIndex >= 0 && orderSlotIndex < displayCount) {
            viewState.manageOrderIndex = orderSlotIndex;
            viewState.claimPage = 1;
            openManageOrderMenu(player, orderSlotIndex);
        }
    }

    private int findOwnOrderIndex(PlayerDataStore.PlayerData playerData, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return -1;
        }
        List<PlayerDataStore.OrderEntry> orders = playerData.getOrders();
        for (int index = 0; index < orders.size(); index++) {
            if (orderId.equals(orders.get(index).getOrderId())) {
                return index;
            }
        }
        return -1;
    }

    void handleNewOrderClick(Player player, int rawSlot) {
        int cancelSlot = manager.guis().activeControlSlot("new-order.cancel");
        int itemSlot = manager.guis().activeControlSlot("new-order.item");
        int amountSlot = manager.guis().activeControlSlot("new-order.amount");
        int priceSlot = manager.guis().activeControlSlot("new-order.price");
        int enchantSlot = manager.guis().activeControlSlot("new-order.enchants");
        int confirmSlot = manager.guis().activeControlSlot("new-order.confirm");

        if (rawSlot == cancelSlot) {
            openYourOrdersMenu(player, GUI_BACK_SOUND);
            return;
        }

        if (rawSlot == itemSlot) {
            openItemSelection(player);
            return;
        }

        if (rawSlot == amountSlot) {
            manager.playSound(player, GUI_CLICK_SOUND);
            openSignInput(player, SignInputType.AMOUNT);
            return;
        }

        if (rawSlot == priceSlot) {
            manager.playSound(player, GUI_CLICK_SOUND);
            openSignInput(player, SignInputType.PRICE);
            return;
        }

        if (rawSlot == enchantSlot) {
            NewOrderDraft draft = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState()).getOrCreateDraft();
            if (!supportsOrderEnchantments(draft.customItemId(), resolveDraftMaterial(draft))) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openNewOrderMenu(player);
                return;
            }
            manager.playSound(player, GUI_CLICK_SOUND);
            openEnchantSelectMenu(player, false);
            return;
        }

        if (rawSlot == confirmSlot) {
            confirmNewOrder(player);
        }
    }

    void handleManageOrderClick(Player player, int rawSlot) {
        UUID playerId = player.getUniqueId();
        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        PlayerDataStore.OrderEntry selectedOrder = getSelectedOrder(playerData, viewState);
        if (selectedOrder == null) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
            openYourOrdersMenu(player);
            return;
        }

        int cancelSlot = manager.guis().activeControlSlot("manage-order.cancel");
        int claimSlot = manager.guis().activeControlSlot("manage-order.claim");

        if (rawSlot == cancelSlot && !selectedOrder.isCancelled()) {
            handleOrderCancellation(player, playerData, viewState, selectedOrder);
            return;
        }

        if (rawSlot == claimSlot) {
            openClaimOrderMenu(player, true);
            return;
        }

        if (rawSlot == manager.guis().activeControlSlot("manage-order.admin-actions") && canModerateOrders(player)) {
            manager.playSound(player, GUI_CLICK_SOUND);
            openAdminOrderActionsMenu(player, playerId, selectedOrder.getOrderId());
        }
    }

    void handleAdminOrderActionClick(Player player, int rawSlot) {
        if (!canModerateOrders(player)) {
            manager.playSound(player, GUI_ERROR_SOUND);
            openOrdersMenu(player, null);
            return;
        }

        UUID adminId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(adminId, ignored -> new MenuViewState());
        AdminOrderTarget target = resolveAdminOrderTarget(viewState);
        if (target == null) {
            manager.playSound(player, GUI_ERROR_SOUND);
            clearAdminTarget(viewState);
            openOrdersMenu(player, null);
            return;
        }

        if (rawSlot == ADMIN_DELETE_SLOT) {
            handleAdminDeleteOrder(player, viewState, target);
            return;
        }

        if (rawSlot == ADMIN_CANCEL_SLOT) {
            if (handleAdminCancelOrder(player, target)) {
                openAdminOrderActionsMenu(player, target.ownerId(), target.order().getOrderId());
            }
            return;
        }
    }

    void handleAdminItemEditorBrowserClick(Player player, int rawSlot, ClickType clickType, EntryBrowserHolder holder) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        EntryBrowserClick click = EntryBrowserMenus.handleClick(rawSlot, holder, clickType);
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        switch (click.action()) {
            case ENTRY -> {
                CustomItemStore.CustomItemDefinition selected = customItemStore.get(click.entryId());
                if (selected == null) {
                    manager.editorSounds().error(player);
                    openAdminItemEditorMenu(player, false);
                    return;
                }
                if (clickType == ClickType.SHIFT_RIGHT) {
                    if (isCustomItemInUse(selected.id())) {
                        manager.editorSounds().error(player);
                        manager.messages().send(player, "custom-items.in-use");
                        openAdminItemEditorMenu(player, false);
                        return;
                    }
                    if (customItemStore.remove(selected.id())) {
                        manager.editorSounds().delete(player);
                        manager.invalidateItemSelectCaches();
                        manager.messages().send(player, "custom-items.removed", PluginMessages.placeholders("id", selected.id()));
                        manager.fileLogger().info("Admin " + player.getName() + " removed custom item " + selected.id() + ".");
                    } else {
                        manager.editorSounds().error(player);
                    }
                    openAdminItemEditorMenu(player, false);
                    return;
                }

                viewState.adminItemDraft = AdminItemDraft.fromDefinition(selected);
                openAdminItemEditMenu(player);
            }
            case ADD -> {
                viewState.adminItemDraft = AdminItemDraft.newDraft(generateUniqueCustomItemId("custom_item"));
                manager.editorSounds().add(player);
                openAdminItemEditMenu(player);
            }
            case SEARCH -> {
                manager.editorSounds().search(player);
                interaction.inputSupport.openAdminItemSearch(player);
            }
            case CLEAR_SEARCH -> {
                viewState.adminEditorSearch = "";
                viewState.adminEditorPage = 0;
                manager.editorSounds().clearSearch(player);
                openAdminItemEditorMenu(player, false);
            }
            case PREVIOUS_PAGE -> {
                viewState.adminEditorPage = Math.max(0, holder.request().page() - 1);
                manager.editorSounds().previousPage(player);
                openAdminItemEditorMenu(player, false);
            }
            case NEXT_PAGE -> {
                viewState.adminEditorPage = Math.min(holder.maxPage(), holder.request().page() + 1);
                manager.editorSounds().nextPage(player);
                openAdminItemEditorMenu(player, false);
            }
            case BACK, NONE -> {
            }
        }
    }

    void handleAdminItemEditClick(Player player, InventoryClickEvent event) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        int rawSlot = event.getRawSlot();
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        AdminItemDraft draft = viewState.getOrCreateAdminItemDraft();

        if (rawSlot == ADMIN_EDIT_CANCEL_SLOT) {
            openAdminItemEditorMenu(player, false, FoEditorSounds.BACK);
            return;
        }

        if (rawSlot == ADMIN_EDIT_ENCHANTABLE_SLOT) {
            boolean enabled = !draft.allowOrderEnchants();
            viewState.adminItemDraft = draft.withAllowOrderEnchants(enabled);
            manager.editorSounds().toggle(player, enabled);
            openAdminItemEditMenu(player);
            return;
        }

        if (rawSlot == ADMIN_EDIT_TEMPLATE_SLOT) {
            ItemStack templateSource = resolveTemplateSourceFromClick(player, event);
            if (templateSource != null) {
                updateAdminTemplateFromSource(player, viewState, draft, templateSource);
                return;
            }
        }

        if (rawSlot == ADMIN_EDIT_COPY_HAND_SLOT || rawSlot == ADMIN_EDIT_TEMPLATE_SLOT) {
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand == null || inHand.getType() == Material.AIR) {
                manager.editorSounds().addItemError(player);
                manager.messages().send(player, "custom-items.hold-template");
                return;
            }

            ItemStack template = cloneSingleTemplate(inHand);
            if (template == null) {
                manager.editorSounds().addItemError(player);
                manager.messages().send(player, "custom-items.hold-template");
                return;
            }
            String resolvedId = draft.itemId();
            if (draft.existingId() == null) {
                resolvedId = generateUniqueCustomItemId(template);
            }
            viewState.adminItemDraft = draft.withTemplate(template).withItemId(resolvedId);
            manager.editorSounds().addItem(player);
            openAdminItemEditMenu(player);
            return;
        }

        if (rawSlot == ADMIN_EDIT_DELETE_SLOT && draft.existingId() != null) {
            if (isCustomItemInUse(draft.existingId())) {
                manager.editorSounds().error(player);
                manager.messages().send(player, "custom-items.in-use");
                return;
            }
            if (customItemStore.remove(draft.existingId())) {
                manager.editorSounds().delete(player);
                manager.invalidateItemSelectCaches();
                manager.messages().send(player, "custom-items.removed", PluginMessages.placeholders("id", draft.existingId()));
                manager.fileLogger().info("Admin " + player.getName() + " removed custom item " + draft.existingId() + ".");
            } else {
                manager.editorSounds().error(player);
            }
            viewState.adminItemDraft = null;
            openAdminItemEditorMenu(player, false);
            return;
        }

        if (rawSlot == ADMIN_EDIT_SAVE_SLOT) {
            ItemStack template = draft.template();
            if (template == null || template.getType() == Material.AIR || !template.getType().isItem()) {
                manager.editorSounds().error(player);
                manager.messages().send(player, "custom-items.valid-template-required");
                return;
            }

            String saveId = draft.existingId();
            if (saveId == null) {
                if (draft.itemId() != null && !draft.itemId().isBlank()) {
                    saveId = generateUniqueCustomItemId(draft.itemId(), null);
                } else {
                    saveId = generateUniqueCustomItemId(template);
                }
            }
            try {
                customItemStore.save(saveId, template, draft.allowOrderEnchants());
                manager.invalidateItemSelectCaches();
            } catch (IllegalArgumentException | IllegalStateException exception) {
                manager.editorSounds().error(player);
                manager.messages().send(player, "custom-items.save-failed", PluginMessages.placeholders("error", exception.getMessage()));
                return;
            }

            manager.editorSounds().save(player);
            manager.messages().send(player, "custom-items.saved", PluginMessages.placeholders("id", saveId));
            manager.fileLogger().info("Admin " + player.getName() + " saved custom item " + saveId + ".");
            viewState.adminItemDraft = null;
            openAdminItemEditorMenu(player, true);
        }
    }

    void handleAdminItemEditDrag(Player player, InventoryDragEvent event) {
        if (!event.getRawSlots().contains(ADMIN_EDIT_TEMPLATE_SLOT)) {
            return;
        }

        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        AdminItemDraft draft = viewState.getOrCreateAdminItemDraft();
        ItemStack templateSource = event.getNewItems().get(ADMIN_EDIT_TEMPLATE_SLOT);
        if (templateSource == null || templateSource.getType() == Material.AIR || !templateSource.getType().isItem()) {
            manager.editorSounds().addItemError(player);
            return;
        }

        updateAdminTemplateFromSource(player, viewState, draft, templateSource);
    }

    private ItemStack resolveTemplateSourceFromClick(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack cursorItem = CursorItemEditor.cloneItem(cursor).orElse(null);
        if (cursorItem != null) {
            return cursorItem;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
                ItemStack hotbarTemplate = CursorItemEditor.cloneItem(hotbarItem).orElse(null);
                if (hotbarTemplate != null) {
                    return hotbarTemplate;
                }
            }
        }

        return null;
    }

    private void updateAdminTemplateFromSource(Player player, MenuViewState viewState, AdminItemDraft draft, ItemStack templateSource) {
        ItemStack template = cloneSingleTemplate(templateSource);
        if (template == null) {
            return;
        }
        String resolvedId = draft.itemId();
        if (draft.existingId() == null) {
            resolvedId = generateUniqueCustomItemId(template);
        }
        viewState.adminItemDraft = draft.withTemplate(template).withItemId(resolvedId);
        manager.editorSounds().addItem(player);
        openAdminItemEditMenu(player);
    }

    private ItemStack cloneSingleTemplate(ItemStack source) {
        ItemStack template = CursorItemEditor.cloneItem(source).orElse(null);
        if (template == null || !template.getType().isItem()) {
            return null;
        }
        template.setAmount(1);
        return template;
    }

    void handleHistoryClick(Player player, int rawSlot) {
        UUID viewerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(viewerId, ignored -> new MenuViewState());
        UUID targetId = viewState.historyTargetId == null ? viewerId : viewState.historyTargetId;
        if (!canAccessHistory(player, targetId)) {
            manager.viewSupport.notifyHistoryUnavailable(player, targetId, false);
            openOrdersMenu(player, null);
            return;
        }

        int orderTabSlot = manager.guis().activeControlSlot("history.order-tab");
        int deliverTabSlot = manager.guis().activeControlSlot("history.deliver-tab");
        int backToOrdersSlot = manager.guis().activeControlSlot("history.back-to-orders");
        int backSlot = manager.guis().activeControlSlot("history.previous-page");
        int nextSlot = manager.guis().activeControlSlot("history.next-page");

        if (rawSlot == backToOrdersSlot && manager.guis().buttonEnabled("history", "back")) {
            openOrdersMenu(player, null, GUI_BACK_SOUND);
            return;
        }

        if (rawSlot == orderTabSlot && viewState.historyType != HistoryDataStore.HistoryType.ORDER
            && manager.guis().stateMaterial("history.order-tab", false) != null) {
            viewState.historyType = HistoryDataStore.HistoryType.ORDER;
            viewState.historyPage = 1;
            manager.playSound(player, GUI_SELECT_SOUND);
            openHistoryMenu(player, false);
            return;
        }

        if (rawSlot == deliverTabSlot && viewState.historyType != HistoryDataStore.HistoryType.DELIVER
            && manager.guis().stateMaterial("history.deliver-tab", false) != null) {
            viewState.historyType = HistoryDataStore.HistoryType.DELIVER;
            viewState.historyPage = 1;
            manager.playSound(player, GUI_SELECT_SOUND);
            openHistoryMenu(player, false);
            return;
        }

        if (rawSlot == backSlot && viewState.historyPage > 1
            && manager.guis().buttonEnabled("history", "previous-page")) {
            viewState.historyPage--;
            manager.playSound(player, GUI_PAGE_PREVIOUS_SOUND);
            openHistoryMenu(player, false);
            return;
        }

        if (rawSlot == nextSlot && manager.guis().buttonEnabled("history", "next-page")) {
            List<HistoryDataStore.HistoryEntry> entries = viewState.historyType == HistoryDataStore.HistoryType.DELIVER
                ? historyDataStore.getDeliverHistory(targetId)
                : historyDataStore.getOrderHistory(targetId);
            String entryState = viewState.historyType == HistoryDataStore.HistoryType.DELIVER ? "deliver" : "order";
            int pageCount = !manager.guis().sourceEntryStateEnabled("history.entry", entryState) ? 1
                : calculatePageCount(entries.size(), manager.guis().contentSlots("history", "content-slots").size());
            if (viewState.historyPage < pageCount) {
                viewState.historyPage++;
                manager.playSound(player, GUI_PAGE_NEXT_SOUND);
                openHistoryMenu(player, false);
            }
        }
    }

    void handleItemSelectClick(Player player, int rawSlot) {
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        ItemSelectState itemSelectState = viewState.getOrCreateItemSelectState();

        List<Integer> contentSlots = manager.guis().contentSlots("item-select", "content-slots");
        int sortSlot = manager.guis().activeControlSlot("item-select.sort");
        int filterSlot = manager.guis().activeControlSlot("item-select.filter");
        int searchSlot = manager.guis().activeControlSlot("item-select.search");
        int backSlot = manager.guis().activeControlSlot("item-select.previous-page");
        int returnSlot = manager.guis().activeControlSlot("item-select.back");
        int nextSlot = manager.guis().activeControlSlot("item-select.next-page");

        if (rawSlot == sortSlot) {
            itemSelectState.sortIndex = (itemSelectState.sortIndex + 1) % ITEM_SORT_OPTIONS.size();
            itemSelectState.page = 1;
            manager.playSoundWithPitchVariation(player, GUI_SORT_SOUND);
            refreshItemSelectMenuDebounced(player);
            return;
        }

        if (rawSlot == filterSlot) {
            itemSelectState.filterIndex = (itemSelectState.filterIndex + 1) % FILTER_OPTIONS.size();
            itemSelectState.page = 1;
            manager.playSoundWithPitchVariation(player, GUI_FILTER_SOUND);
            refreshItemSelectMenuDebounced(player);
            return;
        }

        if (rawSlot == searchSlot && manager.guis().buttonEnabled("item-select", "search")) {
            manager.playSound(player, GUI_SEARCH_SOUND);
            openSignInput(player, SignInputType.ITEM_SEARCH);
            return;
        }

        if (rawSlot == returnSlot && manager.guis().buttonEnabled("item-select", "back")) {
            openNewOrderMenu(player, GUI_BACK_SOUND);
            return;
        }

        if (rawSlot == backSlot && itemSelectState.page > 1
            && manager.guis().buttonEnabled("item-select", "previous-page")) {
            itemSelectState.page--;
            manager.playSound(player, GUI_PAGE_PREVIOUS_SOUND);
            openItemSelectMenu(player, false);
            return;
        }

        if (rawSlot == nextSlot && manager.guis().buttonEnabled("item-select", "next-page")) {
            int pageCount = calculatePageCount(getFilteredSortedItems(itemSelectState).size(), contentSlots.size());
            if (itemSelectState.page < pageCount) {
                itemSelectState.page++;
                manager.playSound(player, GUI_PAGE_NEXT_SOUND);
                openItemSelectMenu(player, false);
            }
            return;
        }

        int contentIndex = contentSlots.indexOf(rawSlot);
        if (contentIndex >= 0) {
            List<OrderableItemOption> items = getFilteredSortedItems(itemSelectState);
            int itemIndex = (itemSelectState.page - 1) * contentSlots.size() + contentIndex;
            if (itemIndex >= 0 && itemIndex < items.size()) {
                selectOrderItem(player, viewState, items.get(itemIndex));
            }
        }
    }

    private void selectOrderItem(Player player, MenuViewState viewState, OrderableItemOption selected) {
        NewOrderDraft currentDraft = viewState.getOrCreateDraft();
        if (!selected.isCustom() && isOrderBlacklisted(selected.material(), Map.of())) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.order-blacklisted"));
            openItemSelectMenu(player, false);
            return;
        }

        manager.playSound(player, GUI_SELECT_SOUND);

        Map<String, Integer> selectedEnchantments =
            sanitizeDraftEnchantments(selected.customItemId(), selected.material(), currentDraft.enchantLevels());
        viewState.draft = currentDraft
            .withSelection(selected.customItemId(), selected.material().name())
            .withEnchantLevels(selectedEnchantments);

        if (supportsOrderEnchantments(selected.customItemId(), selected.material())) {
            openEnchantSelectMenu(player, true);
        } else {
            openNewOrderMenu(player);
        }
    }

    private String itemChoiceKey(NewOrderDraft draft) {
        if (draft.customItemId() != null) {
            return OrderableItemOption.customChoiceKey(draft.customItemId());
        }
        return OrderableItemOption.materialChoiceKey(resolveMaterial(draft.materialName()));
    }

    void handleEnchantSelectClick(Player player, InventoryClickEvent event) {
        handleEnchantSelectClick(player, event.getRawSlot(), event.getClick());
    }

    void handleEnchantSelectClick(Player player, int rawSlot, ClickType click) {
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        NewOrderDraft draft = viewState.getOrCreateDraft();
        Material material = resolveDraftMaterial(draft);
        if (!supportsOrderEnchantments(draft.customItemId(), material)) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
            openNewOrderMenu(player);
            return;
        }

        List<Integer> contentSlots = manager.guis().contentSlots("enchant-select", "content-slots");
        int doneSlot = manager.guis().activeControlSlot("enchant-select.done");
        int clearSlot = manager.guis().activeControlSlot("enchant-select.clear");
        int backSlot = manager.guis().activeControlSlot("enchant-select.previous-page");
        int returnSlot = manager.guis().activeControlSlot("enchant-select.back");
        int nextSlot = manager.guis().activeControlSlot("enchant-select.next-page");
        int controlsSlot = manager.guis().activeControlSlot("enchant-select.controls");

        if (rawSlot == doneSlot) {
            openNewOrderMenu(player, GUI_BACK_SOUND);
            return;
        }

        if (rawSlot == clearSlot) {
            viewState.draft = draft.withEnchantLevels(Map.of());
            manager.playSound(player, GUI_CLEAR_SEARCH_SOUND);
            openEnchantSelectMenu(player, false);
            return;
        }

        if (rawSlot == returnSlot && manager.guis().buttonEnabled("enchant-select", "back")) {
            manager.playSound(player, GUI_BACK_SOUND);
            openItemSelectMenu(player, false);
            return;
        }

        if (rawSlot == backSlot && viewState.enchantPage > 1
            && manager.guis().buttonEnabled("enchant-select", "previous-page")) {
            viewState.enchantPage--;
            manager.playSound(player, GUI_PAGE_PREVIOUS_SOUND);
            openEnchantSelectMenu(player, false);
            return;
        }

        if (rawSlot == nextSlot && manager.guis().buttonEnabled("enchant-select", "next-page")) {
            int pageCount = calculatePageCount(getVisibleInventoryEnchantments(material).size(), contentSlots.size());
            if (viewState.enchantPage < pageCount) {
                viewState.enchantPage++;
                manager.playSound(player, GUI_PAGE_NEXT_SOUND);
                openEnchantSelectMenu(player, false);
            }
            return;
        }

        if (rawSlot == controlsSlot) {
            return;
        }

        int contentIndex = contentSlots.indexOf(rawSlot);
        if (contentIndex < 0) {
            return;
        }

        int step = click.isShiftClick() ? 5 : 1;
        int direction;
        if (click.isLeftClick()) {
            direction = 1;
        } else if (click.isRightClick()) {
            direction = -1;
        } else {
            return;
        }

        List<Enchantment> selectable = getVisibleInventoryEnchantments(material);
        int enchantIndex = (viewState.enchantPage - 1) * contentSlots.size() + contentIndex;
        if (enchantIndex < 0 || enchantIndex >= selectable.size()) {
            return;
        }

        Enchantment selected = selectable.get(enchantIndex);
        int visibleLevel = sanitizeDraftEnchantments(draft.customItemId(), material, draft.enchantLevels())
            .getOrDefault(selected.getKey().toString(), 0);
        if (!manager.guis().sourceEntryStateEnabled("enchant-select.entry",
            visibleLevel > 0 ? "selected" : "unselected")) {
            return;
        }
        int maxLevel = Math.max(1, selected.getMaxLevel());
        Map<String, Integer> updatedEnchantments =
            new LinkedHashMap<>(sanitizeDraftEnchantments(draft.customItemId(), material, draft.enchantLevels()));
        String key = selected.getKey().toString();
        int currentLevel = updatedEnchantments.getOrDefault(key, 0);
        int newLevel = Math.max(0, Math.min(maxLevel, currentLevel + (direction * step)));
        if (newLevel <= 0) {
            updatedEnchantments.remove(key);
        } else {
            updatedEnchantments.put(key, newLevel);
        }

        viewState.draft = draft.withEnchantLevels(updatedEnchantments);
        manager.playSoundWithPitchVariation(player, GUI_CYCLE_SOUND);
        openEnchantSelectMenu(player, false);
    }

    OrderQuote quote(Player owner, OrderRequest request) {
        if (owner == null || !owner.isOnline()) {
            return new OrderQuote(OrderQuote.Status.PLAYER_OFFLINE, null, 0, 0, 0, 0, 0);
        }
        int current = playerDataStore.getPlayerOrdersSnapshot(owner.getUniqueId()).size();
        int maximum = getMaxOrdersForPlayer(owner);
        if (!owner.hasPermission("foorders.use")) {
            return new OrderQuote(OrderQuote.Status.NO_PERMISSION, null, current, maximum, 0, 0, 0);
        }
        return quoteRequest(request, current, maximum, owner);
    }

    private OrderQuote quoteRequest(OrderRequest request, int current, int maximum, Player owner) {
        if (request == null || request.amount() <= 0 || request.pricePerItem() <= 0
            || !Double.isFinite(request.pricePerItem())) {
            return new OrderQuote(OrderQuote.Status.INVALID_REQUEST, null, current, maximum, 0, 0, 0);
        }
        CustomItemStore.CustomItemDefinition custom = request.customItemId() == null
            ? null : customItemStore.get(request.customItemId());
        if (request.customItemId() != null && custom == null) {
            return new OrderQuote(OrderQuote.Status.ITEM_UNAVAILABLE, null, current, maximum, 0, 0, 0);
        }
        Material material = custom == null ? request.material() : custom.template().getType();
        if (material == null || material == Material.AIR || !material.isItem()) {
            return new OrderQuote(OrderQuote.Status.INVALID_REQUEST, null, current, maximum, 0, 0, 0);
        }
        Map<String, Integer> enchantments = sanitizeDraftEnchantments(
            custom == null ? null : custom.id(), material, request.enchantments());
        OrderRequest effective = new OrderRequest(custom == null ? null : custom.id(), material,
            enchantments, request.amount(), request.pricePerItem());
        if (custom == null && material == Material.ENCHANTED_BOOK && enchantments.isEmpty()) {
            return new OrderQuote(OrderQuote.Status.INVALID_REQUEST, effective, current, maximum, 0, 0, 0);
        }
        if (custom == null && isOrderBlacklisted(material, enchantments)) {
            return new OrderQuote(OrderQuote.Status.BLACKLISTED, effective, current, maximum, 0, 0, 0);
        }
        if (owner != null && current >= maximum) {
            return new OrderQuote(OrderQuote.Status.ORDER_LIMIT, effective, current, maximum, 0, 0, 0);
        }
        double subtotal = request.amount() * request.pricePerItem();
        double tax = manager.calculateOrderTax(subtotal);
        double total = manager.calculateOrderCreationCost(subtotal);
        if (subtotal <= 0 || total <= 0 || !Double.isFinite(subtotal) || !Double.isFinite(total)) {
            return new OrderQuote(OrderQuote.Status.INVALID_REQUEST, effective, current, maximum, 0, 0, 0);
        }
        if (!hasEconomyProvider()) {
            return new OrderQuote(OrderQuote.Status.ECONOMY_UNAVAILABLE, effective, current, maximum,
                subtotal, tax, total);
        }
        if (owner != null && !hasEconomyBalance(owner, total)) {
            return new OrderQuote(OrderQuote.Status.INSUFFICIENT_FUNDS, effective, current, maximum,
                subtotal, tax, total);
        }
        return new OrderQuote(OrderQuote.Status.ELIGIBLE, effective, current, maximum, subtotal, tax, total);
    }

    OrderActionResult createNpcOrder(String npcListingName, OrderRequest request) {
        if (npcListingName == null || !NPC_NAME.matcher(npcListingName.trim()).matches()) {
            return new OrderActionResult(OrderActionResult.Status.INVALID_NPC_NAME, null);
        }
        String name = npcListingName.trim();
        UUID ownerId = UUID.nameUUIDFromBytes(("foorders:npc:" + name.toLowerCase(Locale.ROOT))
            .getBytes(StandardCharsets.UTF_8));
        if (!manager.activeCreateOwners.add(ownerId)) {
            return new OrderActionResult(OrderActionResult.Status.BUSY, null);
        }
        try {
            OrderQuote quote = quoteRequest(request, 0, 0, null);
            if (!quote.eligible()) {
                return new OrderActionResult(OrderActionResult.Status.valueOf(quote.status().name()), null);
            }
            // NPC orders use the normal item rules; their creation has no tax or Vault withdrawal.
            OrderQuote freeQuote = new OrderQuote(OrderQuote.Status.ELIGIBLE, quote.effectiveRequest(),
                0, 0, quote.subtotal(), 0, 0);
            OrderBeforeCreateEvent before = new OrderBeforeCreateEvent(ownerId, freeQuote.effectiveRequest(), freeQuote, name);
            manager.plugin.getServer().getPluginManager().callEvent(before);
            if (before.isCancelled()) {
                return new OrderActionResult(OrderActionResult.Status.CANCELLED_BY_EVENT, null);
            }
            OrderQuote checked = quoteRequest(quote.effectiveRequest(), 0, 0, null);
            if (!checked.eligible()) {
                return new OrderActionResult(OrderActionResult.Status.valueOf(checked.status().name()), null);
            }
            OrderRequest effective = checked.effectiveRequest();
            PlayerDataStore.OrderEntry order = new PlayerDataStore.OrderEntry(
                UUID.randomUUID().toString(), effective.customItemId(), effective.material().name(),
                effective.enchantments(), effective.amount(), 0, 0, effective.pricePerItem(),
                0, false, System.currentTimeMillis());
            order.setNpcName(name);
            playerDataStore.getOrCreate(ownerId).getOrders().add(order);
            playerDataStore.saveUrgent(ownerId);
            appendOrderEvent(ownerId, "order-created", order, TextFormat.placeholders(
                "amount", order.getAmountOrdered(), "price", order.getPricePerItem(),
                "subtotal", checked.subtotal(), "tax", 0, "total", 0));
            OrderSnapshot snapshot = OrderSnapshots.of(ownerId, order);
            manager.plugin.getServer().getPluginManager().callEvent(new OrderCreatedEvent(snapshot, null, 0, 0));
            manager.fileLogger().info("NPC " + name + " created order " + order.getOrderId()
                + " for " + order.getAmountOrdered() + "x " + formatOrderDisplayName(order)
                + " at $" + order.getPricePerItem() + " each; no account was charged.");
            return new OrderActionResult(OrderActionResult.Status.SUCCESS, snapshot);
        } finally {
            manager.activeCreateOwners.remove(ownerId);
        }
    }

    OrderActionResult createOrder(Player owner, OrderRequest request) {
        if (owner == null || !owner.isOnline()) {
            return new OrderActionResult(OrderActionResult.Status.PLAYER_OFFLINE, null);
        }
        UUID ownerId = owner.getUniqueId();
        if (!manager.activeCreateOwners.add(ownerId)) {
            return new OrderActionResult(OrderActionResult.Status.BUSY, null);
        }
        try {
            OrderQuote quote = quote(owner, request);
            if (!quote.eligible()) {
                return new OrderActionResult(OrderActionResult.Status.valueOf(quote.status().name()), null);
            }
            OrderBeforeCreateEvent before = new OrderBeforeCreateEvent(ownerId, quote.effectiveRequest(), quote);
            manager.plugin.getServer().getPluginManager().callEvent(before);
            if (before.isCancelled()) {
                return new OrderActionResult(OrderActionResult.Status.CANCELLED_BY_EVENT, null);
            }
            OrderQuote checked = quote(owner, quote.effectiveRequest());
            if (!checked.eligible()) {
                return new OrderActionResult(OrderActionResult.Status.valueOf(checked.status().name()), null);
            }
            if (!withdrawEconomy(owner, checked.total())) {
                return new OrderActionResult(OrderActionResult.Status.ECONOMY_ERROR, null);
            }
            OrderRequest effective = checked.effectiveRequest();
            PlayerDataStore.OrderEntry order = new PlayerDataStore.OrderEntry(
                UUID.randomUUID().toString(), effective.customItemId(), effective.material().name(),
                effective.enchantments(), effective.amount(), 0, 0, effective.pricePerItem(),
                0, false, System.currentTimeMillis());
            PlayerDataStore.PlayerData data = playerDataStore.getOrCreate(ownerId);
            data.getOrders().add(order);
            playerDataStore.saveUrgent(ownerId);
            appendOrderEvent(ownerId, "order-created", order, TextFormat.placeholders(
                "amount", order.getAmountOrdered(), "price", order.getPricePerItem(),
                "subtotal", checked.subtotal(), "tax", checked.tax(), "total", checked.total()));
            OrderSnapshot snapshot = OrderSnapshots.of(ownerId, order);
            manager.plugin.getServer().getPluginManager().callEvent(
                new OrderCreatedEvent(snapshot, ownerId, checked.tax(), checked.total()));
            manager.fileLogger().info("Player " + owner.getName() + " created order " + order.getOrderId()
                + " for " + formatCompactAmount(order.getAmountOrdered()) + "x " + formatOrderDisplayName(order)
                + " at $" + formatCompactAmount(order.getPricePerItem()) + " each with $"
                + formatCompactAmount(checked.tax()) + " tax and $" + formatCompactAmount(checked.total()) + " charged.");
            sendOrderCreatedWebhook(owner, order, checked.subtotal());
            announceCreatedOrderInChat(owner, order, checked.subtotal());
            return new OrderActionResult(OrderActionResult.Status.SUCCESS, snapshot);
        } finally {
            manager.activeCreateOwners.remove(ownerId);
        }
    }

    void confirmNewOrder(Player player) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        if (viewState.newOrderConfirmLocked) return;
        viewState.newOrderConfirmLocked = true;
        try {
            NewOrderDraft draft = viewState.getOrCreateDraft();
            CustomItemStore.CustomItemDefinition custom = resolveCustomItemDefinition(draft.customItemId());
            Material material = custom == null ? resolveMaterial(draft.materialName()) : custom.template().getType();
            Map<String, Integer> sanitized = sanitizeDraftEnchantments(draft.customItemId(), material, draft.enchantLevels());
            if (!sanitized.equals(draft.enchantLevels())) {
                draft = draft.withEnchantLevels(sanitized);
                viewState.draft = draft;
            }
            OrderActionResult result = createOrder(player, new OrderRequest(
                draft.customItemId(), material, draft.enchantLevels(), draft.amount(), draft.pricePerItem()));
            if (result.successful()) {
                manager.playSound(player, ORDER_CREATED_SOUND);
                viewState.draft = NewOrderDraft.defaults();
                openYourOrdersMenu(player);
                return;
            }
            manager.playSound(player, GUI_ERROR_SOUND);
            switch (result.status()) {
                case ORDER_LIMIT -> {
                    manager.messages().send(player, "orders.max-orders", PluginMessages.placeholders(
                        "amount", formatCompactAmount(getMaxOrdersForPlayer(player))));
                    openYourOrdersMenu(player);
                }
                case ITEM_UNAVAILABLE -> {
                    sendErrorActionbar(player, manager.messages().get("actionbar.custom-item-missing"));
                    viewState.draft = draft.withSelection(null, Material.STONE.name()).withEnchantLevels(Map.of());
                    openNewOrderMenu(player);
                }
                case BLACKLISTED -> {
                    sendErrorActionbar(player, manager.messages().get("actionbar.order-blacklisted"));
                    openNewOrderMenu(player);
                }
                case INSUFFICIENT_FUNDS -> {
                    sendErrorActionbar(player, manager.messages().get("actionbar.not-enough-money"));
                    openNewOrderMenu(player);
                }
                case ECONOMY_UNAVAILABLE, ECONOMY_ERROR -> {
                    sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
                    openNewOrderMenu(player);
                }
                case INVALID_REQUEST -> {
                    sendErrorActionbar(player, manager.messages().get(
                        material == Material.ENCHANTED_BOOK ? "actionbar.enchant-required" : "actionbar.invalid-price"));
                    openNewOrderMenu(player);
                }
                default -> {
                    sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                    openNewOrderMenu(player);
                }
            }
        } finally {
            viewState.newOrderConfirmLocked = false;
        }
    }
}
