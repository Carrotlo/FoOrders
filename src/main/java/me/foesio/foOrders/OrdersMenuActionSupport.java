package me.foesio.foOrders;

import me.foesio.core.editor.CursorItemEditor;
import me.foesio.foOrders.storage.CustomItemStore;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import net.milkbowl.vault.economy.EconomyResponse;
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

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuActionSupport {
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

    private int guiItemSlot(String path, int fallback, int inventorySize) {
        return manager.guis().itemSlot(path, fallback, inventorySize);
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

    private void openNewOrderMenu(Player player) {
        manager.viewSupport.openNewOrderMenu(player);
    }

    private void openItemSelectMenu(Player player, boolean resetPage) {
        manager.viewSupport.openItemSelectMenu(player, resetPage);
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

    private void openHistoryMenu(Player player, UUID targetPlayerId, HistoryDataStore.HistoryType historyType, boolean fromAdminCommand) {
        manager.viewSupport.openHistoryMenu(player, targetPlayerId, historyType, fromAdminCommand);
    }

    private void openHistoryMenu(Player player, boolean resetPage) {
        manager.viewSupport.openHistoryMenu(player, resetPage);
    }

    private void openAdminItemEditorMenu(Player player, boolean resetPage) {
        manager.viewSupport.openAdminItemEditorMenu(player, resetPage);
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

    private List<org.bukkit.enchantments.Enchantment> getSelectableEnchantments(Material material) {
        return manager.itemSupport.getSelectableEnchantments(material);
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

    private EconomyResponse withdrawEconomy(Player player, double amount) {
        return deliverySupport.withdrawEconomy(player, amount);
    }

    private void appendOrderHistory(UUID playerId, String action, String details) {
        deliverySupport.appendOrderHistory(playerId, action, details);
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
        int inventorySize = 54;
        int sortSlot = manager.guis().itemSlot("main.sort", SORT_SLOT, inventorySize);
        int filterSlot = manager.guis().itemSlot("main.filter", FILTER_SLOT, inventorySize);
        int refreshSlot = manager.guis().itemSlot("main.refresh", REFRESH_SLOT, inventorySize);
        int searchSlot = manager.guis().itemSlot("main.search", SEARCH_SLOT, inventorySize);
        int mainBackSlot = manager.guis().itemSlot("main.previous-page", MAIN_BACK_SLOT, inventorySize);
        int mainNextSlot = manager.guis().itemSlot("main.next-page", MAIN_NEXT_SLOT, inventorySize);
        int yourOrdersSlot = manager.guis().itemSlot("main.your-orders", YOUR_ORDERS_SLOT, inventorySize);
        int historySlot = manager.guis().itemSlot("main.history", HISTORY_SLOT, inventorySize);
        List<Integer> orderSlots = manager.guis().slots("layout.main.order-slots", MAIN_ORDER_SLOTS, inventorySize);

        if (rawSlot == sortSlot) {
            playerData.setSortIndex((playerData.getSortIndex() + 1) % SORT_OPTIONS.size());
            playerDataStore.save(playerId);
            viewState.page = 1;
            refreshMainMenuDebounced(player);
            return;
        }

        if (rawSlot == filterSlot) {
            playerData.setFilterIndex((playerData.getFilterIndex() + 1) % FILTER_OPTIONS.size());
            playerDataStore.save(playerId);
            viewState.page = 1;
            refreshMainMenuDebounced(player);
            return;
        }

        if (rawSlot == refreshSlot) {
            if (manager.tryMarkOrderMenuRefresh(player)) {
                refreshMainMenu(player);
            }
            return;
        }

        if (rawSlot == searchSlot) {
            openSignInput(player, SignInputType.MAIN_SEARCH);
            return;
        }

        if (rawSlot == mainBackSlot && viewState.page > 1) {
            viewState.page--;
            refreshMainMenu(player);
            return;
        }

        if (rawSlot == mainNextSlot) {
            int pageCount = calculatePageCount(getCachedVisibleMainOrders(viewState, playerData).size(), orderSlots.size());
            if (viewState.page < pageCount) {
                viewState.page++;
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
                manager.messages().send(player, "history.disabled");
                return;
            }
            openHistoryMenu(player, player.getUniqueId(), HistoryDataStore.HistoryType.ORDER, false);
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
        int newOrderSlot = guiItemSlot("your-orders.new-order", Math.min(playerData.getOrders().size(), playerMaxOrders), 27);
        List<Integer> orderSlots = manager.viewSupport.yourOrderSlots(newOrderSlot);
        int displayCount = Math.min(playerData.getOrders().size(), Math.min(playerMaxOrders, orderSlots.size()));

        if (rawSlot == newOrderSlot) {
            if (playerData.getOrders().size() >= playerMaxOrders) {
                manager.messages().send(player, "orders.max-orders", PluginMessages.placeholders("amount", formatCompactAmount(playerMaxOrders)));
                return;
            }
            openNewOrderMenu(player);
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
        int inventorySize = 27;
        int cancelSlot = guiItemSlot("new-order.cancel", CANCEL_SLOT, inventorySize);
        int itemSlot = guiItemSlot("new-order.item", ITEM_SLOT, inventorySize);
        int amountSlot = guiItemSlot("new-order.amount", AMOUNT_SLOT, inventorySize);
        int priceSlot = guiItemSlot("new-order.price", PRICE_SLOT, inventorySize);
        int enchantSlot = guiItemSlot("new-order.enchants", ENCHANT_SLOT, inventorySize);
        int confirmSlot = guiItemSlot("new-order.confirm", CONFIRM_SLOT, inventorySize);

        if (rawSlot == cancelSlot) {
            openYourOrdersMenu(player);
            return;
        }

        if (rawSlot == itemSlot) {
            openItemSelectMenu(player, true);
            return;
        }

        if (rawSlot == amountSlot) {
            openSignInput(player, SignInputType.AMOUNT);
            return;
        }

        if (rawSlot == priceSlot) {
            openSignInput(player, SignInputType.PRICE);
            return;
        }

        if (rawSlot == enchantSlot) {
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
            openYourOrdersMenu(player);
            return;
        }

        int inventorySize = 27;
        int cancelSlot = guiItemSlot("manage-order.cancel", MANAGE_CANCEL_SLOT, inventorySize);
        int claimSlot = guiItemSlot("manage-order.claim", MANAGE_CLAIM_SLOT, inventorySize);

        if (rawSlot == cancelSlot) {
            handleOrderCancellation(player, playerData, viewState, selectedOrder);
            return;
        }

        if (rawSlot == claimSlot) {
            openClaimOrderMenu(player, true);
            return;
        }

        if (rawSlot == MANAGE_ADMIN_ACTIONS_SLOT && canModerateOrders(player)) {
            openAdminOrderActionsMenu(player, playerId, selectedOrder.getOrderId());
        }
    }

    void handleAdminOrderActionClick(Player player, int rawSlot) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        UUID adminId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(adminId, ignored -> new MenuViewState());
        AdminOrderTarget target = resolveAdminOrderTarget(viewState);
        if (target == null) {
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

    void handleAdminItemEditorClick(Player player, int rawSlot, ClickType clickType) {
        if (!canModerateOrders(player)) {
            openOrdersMenu(player, null);
            return;
        }

        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        List<CustomItemStore.CustomItemDefinition> customItems = getSortedCustomItems();
        int pageCount = Math.max(1, (int) Math.ceil(customItems.size() / 45D));
        if (viewState.adminEditorPage < 1) {
            viewState.adminEditorPage = 1;
        }
        if (viewState.adminEditorPage > pageCount) {
            viewState.adminEditorPage = pageCount;
        }

        if (rawSlot >= 0 && rawSlot < 45) {
            int itemIndex = (viewState.adminEditorPage - 1) * 45 + rawSlot;
            if (itemIndex < 0 || itemIndex >= customItems.size()) {
                return;
            }

            CustomItemStore.CustomItemDefinition selected = customItems.get(itemIndex);
            if (clickType == ClickType.SHIFT_RIGHT) {
                if (isCustomItemInUse(selected.id())) {
                    manager.messages().send(player, "custom-items.in-use");
                    openAdminItemEditorMenu(player, false);
                    return;
                }
                if (customItemStore.remove(selected.id())) {
                    manager.invalidateItemSelectCaches();
                    manager.messages().send(player, "custom-items.removed", PluginMessages.placeholders("id", selected.id()));
                    manager.fileLogger().info("Admin " + player.getName() + " removed custom item " + selected.id() + ".");
                }
                openAdminItemEditorMenu(player, false);
                return;
            }

            viewState.adminItemDraft = AdminItemDraft.fromDefinition(selected);
            openAdminItemEditMenu(player);
            return;
        }

        if (rawSlot == ADMIN_EDITOR_ADD_SLOT) {
            viewState.adminItemDraft = AdminItemDraft.newDraft(generateUniqueCustomItemId("custom_item"));
            openAdminItemEditMenu(player);
            return;
        }

        if (rawSlot == ADMIN_EDITOR_BACK_SLOT && viewState.adminEditorPage > 1) {
            viewState.adminEditorPage--;
            openAdminItemEditorMenu(player, false);
            return;
        }

        if (rawSlot == ADMIN_EDITOR_NEXT_SLOT && viewState.adminEditorPage < pageCount) {
            viewState.adminEditorPage++;
            openAdminItemEditorMenu(player, false);
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
            openAdminItemEditorMenu(player, false);
            return;
        }

        if (rawSlot == ADMIN_EDIT_ENCHANTABLE_SLOT) {
            viewState.adminItemDraft = draft.withAllowOrderEnchants(!draft.allowOrderEnchants());
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
                manager.messages().send(player, "custom-items.hold-template");
                return;
            }

            ItemStack template = cloneSingleTemplate(inHand);
            if (template == null) {
                manager.messages().send(player, "custom-items.hold-template");
                return;
            }
            String resolvedId = draft.itemId();
            if (draft.existingId() == null) {
                resolvedId = generateUniqueCustomItemId(template);
            }
            viewState.adminItemDraft = draft.withTemplate(template).withItemId(resolvedId);
            openAdminItemEditMenu(player);
            return;
        }

        if (rawSlot == ADMIN_EDIT_DELETE_SLOT && draft.existingId() != null) {
            if (isCustomItemInUse(draft.existingId())) {
                manager.messages().send(player, "custom-items.in-use");
                return;
            }
            if (customItemStore.remove(draft.existingId())) {
                manager.invalidateItemSelectCaches();
                manager.messages().send(player, "custom-items.removed", PluginMessages.placeholders("id", draft.existingId()));
                manager.fileLogger().info("Admin " + player.getName() + " removed custom item " + draft.existingId() + ".");
            }
            viewState.adminItemDraft = null;
            openAdminItemEditorMenu(player, false);
            return;
        }

        if (rawSlot == ADMIN_EDIT_SAVE_SLOT) {
            ItemStack template = draft.template();
            if (template == null || template.getType() == Material.AIR || !template.getType().isItem()) {
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
            } catch (IllegalArgumentException exception) {
                manager.messages().send(player, "custom-items.save-failed", PluginMessages.placeholders("error", exception.getMessage()));
                return;
            }

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
            openOrdersMenu(player, null);
            return;
        }

        int inventorySize = 54;
        int orderTabSlot = guiItemSlot("history.order-tab", HISTORY_ORDER_TAB_SLOT, inventorySize);
        int deliverTabSlot = guiItemSlot("history.deliver-tab", HISTORY_DELIVER_TAB_SLOT, inventorySize);
        int backToOrdersSlot = guiItemSlot("history.back-to-orders", HISTORY_BACK_TO_ORDERS_SLOT, inventorySize);
        int backSlot = guiItemSlot("history.previous-page", HISTORY_BACK_SLOT, inventorySize);
        int nextSlot = guiItemSlot("history.next-page", HISTORY_NEXT_SLOT, inventorySize);

        if (rawSlot == backToOrdersSlot) {
            openOrdersMenu(player, null);
            return;
        }

        if (rawSlot == orderTabSlot && viewState.historyType != HistoryDataStore.HistoryType.ORDER) {
            viewState.historyType = HistoryDataStore.HistoryType.ORDER;
            viewState.historyPage = 1;
            openHistoryMenu(player, false);
            return;
        }

        if (rawSlot == deliverTabSlot && viewState.historyType != HistoryDataStore.HistoryType.DELIVER) {
            viewState.historyType = HistoryDataStore.HistoryType.DELIVER;
            viewState.historyPage = 1;
            openHistoryMenu(player, false);
            return;
        }

        if (rawSlot == backSlot && viewState.historyPage > 1) {
            viewState.historyPage--;
            openHistoryMenu(player, false);
            return;
        }

        if (rawSlot == nextSlot) {
            List<HistoryDataStore.HistoryEntry> entries = viewState.historyType == HistoryDataStore.HistoryType.DELIVER
                ? historyDataStore.getDeliverHistory(targetId)
                : historyDataStore.getOrderHistory(targetId);
            int pageCount = calculatePageCount(entries.size(), HISTORY_PAGE_CAPACITY);
            if (viewState.historyPage < pageCount) {
                viewState.historyPage++;
                openHistoryMenu(player, false);
            }
        }
    }

    void handleItemSelectClick(Player player, int rawSlot) {
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        ItemSelectState itemSelectState = viewState.getOrCreateItemSelectState();

        int inventorySize = 54;
        int sortSlot = guiItemSlot("item-select.sort", ITEM_SELECT_SORT_SLOT, inventorySize);
        int filterSlot = guiItemSlot("item-select.filter", ITEM_SELECT_FILTER_SLOT, inventorySize);
        int searchSlot = guiItemSlot("item-select.search", ITEM_SELECT_SEARCH_SLOT, inventorySize);
        int backSlot = guiItemSlot("item-select.previous-page", ITEM_SELECT_BACK_SLOT, inventorySize);
        int nextSlot = guiItemSlot("item-select.next-page", ITEM_SELECT_NEXT_SLOT, inventorySize);

        if (rawSlot == sortSlot) {
            itemSelectState.sortIndex = (itemSelectState.sortIndex + 1) % ITEM_SORT_OPTIONS.size();
            itemSelectState.page = 1;
            refreshItemSelectMenuDebounced(player);
            return;
        }

        if (rawSlot == filterSlot) {
            itemSelectState.filterIndex = (itemSelectState.filterIndex + 1) % FILTER_OPTIONS.size();
            itemSelectState.page = 1;
            refreshItemSelectMenuDebounced(player);
            return;
        }

        if (rawSlot == searchSlot) {
            openSignInput(player, SignInputType.ITEM_SEARCH);
            return;
        }

        if (rawSlot == backSlot && itemSelectState.page > 1) {
            itemSelectState.page--;
            openItemSelectMenu(player, false);
            return;
        }

        if (rawSlot == nextSlot) {
            int pageCount = Math.max(1, (int) Math.ceil(getFilteredSortedItems(itemSelectState).size() / (double) ITEM_SELECT_PAGE_SIZE));
            if (itemSelectState.page < pageCount) {
                itemSelectState.page++;
                openItemSelectMenu(player, false);
            }
            return;
        }

        if (rawSlot >= 0 && rawSlot < ITEM_SELECT_PAGE_SIZE) {
            List<OrderableItemOption> items = getFilteredSortedItems(itemSelectState);
            int itemIndex = (itemSelectState.page - 1) * ITEM_SELECT_PAGE_SIZE + rawSlot;
            if (itemIndex >= 0 && itemIndex < items.size()) {
                OrderableItemOption selected = items.get(itemIndex);
                NewOrderDraft currentDraft = viewState.getOrCreateDraft();
                if (!selected.isCustom() && isOrderBlacklisted(selected.material(), Map.of())) {
                    sendErrorActionbar(player, manager.messages().get("actionbar.order-blacklisted"));
                    openItemSelectMenu(player, false);
                    return;
                }

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
        }
    }

    void handleEnchantSelectClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        MenuViewState viewState = menuStates.computeIfAbsent(player.getUniqueId(), ignored -> new MenuViewState());
        NewOrderDraft draft = viewState.getOrCreateDraft();
        Material material = resolveDraftMaterial(draft);
        if (!supportsOrderEnchantments(draft.customItemId(), material)) {
            openNewOrderMenu(player);
            return;
        }

        int inventorySize = 54;
        int doneSlot = guiItemSlot("enchant-select.done", ENCHANT_SELECT_DONE_SLOT, inventorySize);
        int clearSlot = guiItemSlot("enchant-select.clear", ENCHANT_SELECT_CLEAR_SLOT, inventorySize);
        int backSlot = guiItemSlot("enchant-select.previous-page", ENCHANT_SELECT_BACK_SLOT, inventorySize);
        int nextSlot = guiItemSlot("enchant-select.next-page", ENCHANT_SELECT_NEXT_SLOT, inventorySize);
        int controlsSlot = guiItemSlot("enchant-select.controls", ENCHANT_SELECT_INFO_SLOT, inventorySize);

        if (rawSlot == doneSlot) {
            openNewOrderMenu(player);
            return;
        }

        if (rawSlot == clearSlot) {
            viewState.draft = draft.withEnchantLevels(Map.of());
            openEnchantSelectMenu(player, false);
            return;
        }

        if (rawSlot == backSlot && viewState.enchantPage > 1) {
            viewState.enchantPage--;
            openEnchantSelectMenu(player, false);
            return;
        }

        if (rawSlot == nextSlot) {
            int pageCount = Math.max(1, (int) Math.ceil(getSelectableEnchantments(material).size() / (double) ENCHANT_SELECT_PAGE_SIZE));
            if (viewState.enchantPage < pageCount) {
                viewState.enchantPage++;
                openEnchantSelectMenu(player, false);
            }
            return;
        }

        if (rawSlot == controlsSlot) {
            return;
        }

        if (rawSlot < 0 || rawSlot >= ENCHANT_SELECT_PAGE_SIZE) {
            return;
        }

        ClickType click = event.getClick();
        int step = click.isShiftClick() ? 5 : 1;
        int direction;
        if (click.isLeftClick()) {
            direction = 1;
        } else if (click.isRightClick()) {
            direction = -1;
        } else {
            return;
        }

        List<Enchantment> selectable = getSelectableEnchantments(material);
        int enchantIndex = (viewState.enchantPage - 1) * ENCHANT_SELECT_PAGE_SIZE + rawSlot;
        if (enchantIndex < 0 || enchantIndex >= selectable.size()) {
            return;
        }

        Enchantment selected = selectable.get(enchantIndex);
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
        openEnchantSelectMenu(player, false);
    }

    void confirmNewOrder(Player player) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        if (viewState.newOrderConfirmLocked) {
            return;
        }
        viewState.newOrderConfirmLocked = true;
        NewOrderDraft draft = viewState.getOrCreateDraft();
        CustomItemStore.CustomItemDefinition customSelection = resolveCustomItemDefinition(draft.customItemId());
        if (draft.customItemId() != null && customSelection == null) {
            sendErrorActionbar(player, manager.messages().get("actionbar.custom-item-missing"));
            viewState.draft = draft.withSelection(null, Material.STONE.name()).withEnchantLevels(Map.of());
            openNewOrderMenu(player);
            return;
        }

        Material orderMaterial = customSelection == null
            ? resolveMaterial(draft.materialName())
            : customSelection.template().getType();
        Map<String, Integer> sanitizedEnchantments =
            sanitizeDraftEnchantments(draft.customItemId(), orderMaterial, draft.enchantLevels());
        if (!sanitizedEnchantments.equals(draft.enchantLevels())) {
            draft = draft.withEnchantLevels(sanitizedEnchantments);
            viewState.draft = draft;
        }
        int playerMaxOrders = getMaxOrdersForPlayer(player);

        PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
        if (playerData.getOrders().size() >= playerMaxOrders) {
            manager.messages().send(player, "orders.max-orders", PluginMessages.placeholders("amount", formatCompactAmount(playerMaxOrders)));
            openYourOrdersMenu(player);
            return;
        }

        if (customSelection == null && orderMaterial == Material.ENCHANTED_BOOK && sanitizedEnchantments.isEmpty()) {
            sendErrorActionbar(player, manager.messages().get("actionbar.enchant-required"));
            openNewOrderMenu(player);
            return;
        }

        if (customSelection == null && isOrderBlacklisted(orderMaterial, sanitizedEnchantments)) {
            sendErrorActionbar(player, manager.messages().get("actionbar.order-blacklisted"));
            openNewOrderMenu(player);
            return;
        }

        if (!hasEconomyProvider()) {
            sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
            openNewOrderMenu(player);
            return;
        }

        double subtotal = draft.amount() * draft.pricePerItem();
        if (subtotal <= 0 || !Double.isFinite(subtotal)) {
            sendErrorActionbar(player, manager.messages().get("actionbar.invalid-amount"));
            openNewOrderMenu(player);
            return;
        }
        double taxAmount = manager.calculateOrderTax(subtotal);
        double totalCost = manager.calculateOrderCreationCost(subtotal);
        if (totalCost <= 0 || !Double.isFinite(totalCost)) {
            sendErrorActionbar(player, manager.messages().get("actionbar.invalid-amount"));
            openNewOrderMenu(player);
            return;
        }

        if (!hasEconomyBalance(player, totalCost)) {
            sendErrorActionbar(player, manager.messages().get("actionbar.not-enough-money"));
            openNewOrderMenu(player);
            return;
        }

        EconomyResponse withdrawResponse = withdrawEconomy(player, totalCost);
        if (withdrawResponse == null || !withdrawResponse.transactionSuccess()) {
            sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
            openNewOrderMenu(player);
            return;
        }

        PlayerDataStore.OrderEntry orderEntry = new PlayerDataStore.OrderEntry(
            UUID.randomUUID().toString(),
            customSelection == null ? null : customSelection.id(),
            orderMaterial.name(),
            sanitizedEnchantments,
            draft.amount(),
            0,
            0,
            draft.pricePerItem(),
            0,
            false,
            System.currentTimeMillis()
        );
        playerData.getOrders().add(orderEntry);
        playerDataStore.saveUrgent(playerId);
        appendOrderHistory(
            playerId,
            "Order Created",
            formatCompactAmount(orderEntry.getAmountOrdered())
                + "x " + formatOrderDisplayName(orderEntry)
                + " at $" + formatCompactAmount(orderEntry.getPricePerItem())
                + " each (Subtotal $" + formatCompactAmount(subtotal)
                + ", Tax $" + formatCompactAmount(taxAmount)
                + ", Total $" + formatCompactAmount(totalCost) + ")"
        );
        manager.fileLogger().info(
            "Player " + player.getName() + " created order " + orderEntry.getOrderId()
                + " for " + formatCompactAmount(orderEntry.getAmountOrdered())
                + "x " + formatOrderDisplayName(orderEntry)
                + " at $" + formatCompactAmount(orderEntry.getPricePerItem())
                + " each with $" + formatCompactAmount(taxAmount)
                + " tax and $" + formatCompactAmount(totalCost) + " charged."
        );
        sendOrderCreatedWebhook(player, orderEntry, subtotal);
        announceCreatedOrderInChat(player, orderEntry, subtotal);

        viewState.draft = NewOrderDraft.defaults();
        openYourOrdersMenu(player);
    }
}
