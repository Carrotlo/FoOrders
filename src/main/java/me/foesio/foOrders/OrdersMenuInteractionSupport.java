package me.foesio.foOrders;

import me.foesio.core.inventory.InventoryCloseSuppressor;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuInteractionSupport {
    private final OrdersMenuManager manager;
    private final FoScheduler scheduler;
    private final Map<UUID, MenuViewState> menuStates;
    private final InventoryCloseSuppressor inventoryCloseSuppressor;
    private final Map<UUID, PendingDeliveryState> pendingDeliveries;
    private final PlayerDataStore playerDataStore;
    private final HistoryDataStore historyDataStore;
    final OrdersMenuInputSupport inputSupport;
    final OrdersMenuDeliverySupport deliverySupport;
    final OrdersMenuActionSupport actionSupport;

    OrdersMenuInteractionSupport(OrdersMenuManager manager) {
        this.manager = manager;
        this.scheduler = manager.scheduler;
        this.menuStates = manager.menuStates;
        this.inventoryCloseSuppressor = manager.inventoryCloseSuppressor;
        this.pendingDeliveries = manager.pendingDeliveries;
        this.playerDataStore = manager.playerDataStore;
        this.historyDataStore = manager.historyDataStore;
        this.inputSupport = new OrdersMenuInputSupport(manager, this);
        this.deliverySupport = new OrdersMenuDeliverySupport(manager, this);
        this.actionSupport = new OrdersMenuActionSupport(manager, this);
    }

    int getMaxOrdersForPlayer(Player player) {
        int resolved = manager.maxOrdersPerPlayer;
        String prefix = "foorders.maxorders.";
        for (PermissionAttachmentInfo permissionInfo : player.getEffectivePermissions()) {
            if (!permissionInfo.getValue()) {
                continue;
            }
            String permission = permissionInfo.getPermission().toLowerCase(Locale.ROOT);
            if (!permission.startsWith(prefix)) {
                continue;
            }
            String rawAmount = permission.substring(prefix.length());
            try {
                int parsed = Integer.parseInt(rawAmount);
                if (parsed > resolved) {
                    resolved = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(MIN_PLAYER_ORDERS, Math.min(MAX_PLAYER_ORDERS_CAP, resolved));
    }

    void sendInvalidAmountActionbar(Player player) {
        sendErrorActionbar(player, manager.messages().get("actionbar.invalid-amount"));
    }

    void sendErrorActionbar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    void sendDeliveringActionbar(Player player, int animationStep) {
        int dots = (animationStep % 3) + 1;
        StringBuilder message = new StringBuilder(manager.messages().get("actionbar.delivering"));
        for (int i = 0; i < dots; i++) {
            message.append('.');
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message.toString()));
    }

    void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!manager.isOrdersMenu(topInventory)) {
            return;
        }

        InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof OrdersMenuHolder menuHolder)) {
            return;
        }

        if (menuHolder.getMenuType() == MenuType.DELIVER) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean isAdminItemEdit = menuHolder.getMenuType() == MenuType.ADMIN_ITEM_EDIT;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= topInventory.getSize()) {
            if (isAdminItemEdit) {
                ClickType clickType = event.getClick();
                boolean shouldCancel = rawSlot >= 0 && (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT || clickType == ClickType.DOUBLE_CLICK);
                event.setCancelled(shouldCancel);
            } else {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

        switch (menuHolder.getMenuType()) {
            case MAIN -> actionSupport.handleMainClick(player, event.getRawSlot(), event.getClick());
            case YOUR_ORDERS -> actionSupport.handleYourOrdersClick(player, event.getRawSlot());
            case NEW_ORDER -> actionSupport.handleNewOrderClick(player, event.getRawSlot());
            case ITEM_SELECT -> actionSupport.handleItemSelectClick(player, event.getRawSlot());
            case ENCHANT_SELECT -> actionSupport.handleEnchantSelectClick(player, event);
            case MANAGE_ORDER -> actionSupport.handleManageOrderClick(player, event.getRawSlot());
            case CLAIM_ORDER -> deliverySupport.handleClaimOrderClick(player, event.getRawSlot(), event.getCurrentItem());
            case DELIVERY_CONFIRM -> deliverySupport.handleDeliveryConfirmClick(player, event.getRawSlot());
            case HISTORY -> actionSupport.handleHistoryClick(player, event.getRawSlot());
            case ADMIN_ORDER_ACTIONS -> actionSupport.handleAdminOrderActionClick(player, event.getRawSlot());
            case ADMIN_ITEM_EDITOR -> actionSupport.handleAdminItemEditorClick(player, event.getRawSlot(), event.getClick());
            case ADMIN_ITEM_EDIT -> actionSupport.handleAdminItemEditClick(player, event);
            case DELIVER -> {
            }
        }
    }

    void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!manager.isOrdersMenu(topInventory)) {
            return;
        }
        if (topInventory.getHolder() instanceof OrdersMenuHolder holder && holder.getMenuType() == MenuType.DELIVER) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (topInventory.getHolder() instanceof OrdersMenuHolder holder && holder.getMenuType() == MenuType.ADMIN_ITEM_EDIT) {
            actionSupport.handleAdminItemEditDrag(player, event);
        }
    }

    void onInventoryClose(InventoryCloseEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!manager.isOrdersMenu(topInventory)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (inventoryCloseSuppressor.consumeSuppressedClose(player)) {
            return;
        }

        clearSearches(playerId);

        InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof OrdersMenuHolder menuHolder)) {
            return;
        }

        if (menuHolder.getMenuType() == MenuType.MAIN) {
            return;
        }

        if (menuHolder.getMenuType() == MenuType.YOUR_ORDERS) {
            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openOrdersMenu(player, "");
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.ITEM_SELECT) {
            MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
            NewOrderDraft draft = viewState.getOrCreateDraft().withMaterialName(Material.STONE.name());
            viewState.draft = draft;

            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openNewOrderMenu(player);
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.ENCHANT_SELECT) {
            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openNewOrderMenu(player);
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.ADMIN_ORDER_ACTIONS) {
            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openOrdersMenu(player, null);
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.ADMIN_ITEM_EDITOR) {
            return;
        }

        if (menuHolder.getMenuType() == MenuType.ADMIN_ITEM_EDIT) {
            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openAdminItemEditorMenu(player, false);
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.HISTORY) {
            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openOrdersMenu(player, null);
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.CLAIM_ORDER) {
            scheduler.runForPlayer(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
                MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
                deliverySupport.removeOrderIfCompleted(playerData, viewState);
                playerDataStore.save(playerId);
                if (viewState.manageOrderIndex >= 0) {
                    manager.viewSupport.openManageOrderMenu(player, viewState.manageOrderIndex);
                } else {
                    manager.viewSupport.openYourOrdersMenu(player);
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.DELIVER) {
            PendingDeliveryState pending = pendingDeliveries.get(playerId);
            ItemStack[] submitted = deliverySupport.cloneContents(topInventory.getContents());
            topInventory.clear();
            if (pending == null) {
                if (!deliverySupport.isInventoryEmpty(submitted)) {
                    deliverySupport.returnItems(player, submitted);
                }
                scheduler.runForPlayer(player, () -> {
                    if (player.isOnline()) {
                        manager.viewSupport.openOrdersMenu(player, null);
                    }
                });
                return;
            }

            if (deliverySupport.isInventoryEmpty(submitted)) {
                deliverySupport.removePendingDelivery(playerId);
                scheduler.runForPlayer(player, () -> {
                    if (player.isOnline()) {
                        manager.viewSupport.openOrdersMenu(player, null);
                    }
                });
                return;
            }

            PendingDeliveryState updatedPending = pending.withSubmittedItems(submitted);
            if (!pendingDeliveries.replace(playerId, pending, updatedPending)) {
                deliverySupport.returnItems(player, submitted);
                scheduler.runForPlayer(player, () -> {
                    if (player.isOnline()) {
                        manager.viewSupport.openOrdersMenu(player, null);
                    }
                });
                return;
            }

            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openDeliveryConfirmMenu(player);
                }
            });
            return;
        }

        if (menuHolder.getMenuType() == MenuType.DELIVERY_CONFIRM) {
            deliverySupport.returnAndClearPendingDelivery(player, playerId);
            scheduler.runForPlayer(player, () -> {
                if (player.isOnline()) {
                    manager.viewSupport.openOrdersMenu(player, null);
                }
            });
            return;
        }

        scheduler.runForPlayer(player, () -> {
            if (player.isOnline()) {
                manager.viewSupport.openYourOrdersMenu(player);
            }
        });
    }

    private void clearSearches(UUID playerId) {
        MenuViewState viewState = menuStates.get(playerId);
        if (viewState == null) {
            return;
        }
        viewState.search = "";
        viewState.visibleMainOrders = null;
        if (viewState.itemSelectState != null) {
            viewState.itemSelectState.search = "";
            viewState.itemSelectState.page = 1;
        }
    }

    void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        inputSupport.onPlayerSwapHand(event);
    }

    void onPlayerJoin(PlayerJoinEvent event) {
        inputSupport.onPlayerJoin(event);
    }

    void onPlayerQuit(PlayerQuitEvent event) {
        inputSupport.onPlayerQuit(event);
    }

    boolean canOpenOwnHistory(Player player) {
        return deliverySupport.canOpenOwnHistory(player);
    }

    boolean canAccessHistory(Player viewer, UUID targetPlayerId) {
        return deliverySupport.canAccessHistory(viewer, targetPlayerId);
    }

    boolean canModerateOrders(Player player) {
        return deliverySupport.canModerateOrders(player);
    }

    void clearAdminTarget(MenuViewState viewState) {
        deliverySupport.clearAdminTarget(viewState);
    }

    double getRemainingOrderFunds(PlayerDataStore.OrderEntry order) {
        return deliverySupport.getRemainingOrderFunds(order);
    }

    List<Integer> initializeClaimSessionStacks(MenuViewState viewState, PlayerDataStore.OrderEntry order, boolean forceReset) {
        return deliverySupport.initializeClaimSessionStacks(viewState, order, forceReset);
    }

    List<Integer> getClaimSessionPageStackAmounts(List<Integer> claimSessionStacks, int page) {
        return deliverySupport.getClaimSessionPageStackAmounts(claimSessionStacks, page);
    }

    int calculateClaimSessionPageCount(List<Integer> claimSessionStacks) {
        return deliverySupport.calculateClaimSessionPageCount(claimSessionStacks);
    }

    void clearClaimSession(MenuViewState viewState) {
        deliverySupport.clearClaimSession(viewState);
    }

    PlayerDataStore.OrderEntry getSelectedOrder(PlayerDataStore.PlayerData playerData, MenuViewState viewState) {
        return deliverySupport.getSelectedOrder(playerData, viewState);
    }

    boolean removeCompletedOrders(PlayerDataStore.PlayerData playerData) {
        return deliverySupport.removeCompletedOrders(playerData);
    }

    PlayerDataStore.OrderEntry findOrderById(PlayerDataStore.PlayerData playerData, String orderId) {
        return deliverySupport.findOrderById(playerData, orderId);
    }

    void returnPendingItems(Player player, ItemStack[] submittedItems) {
        deliverySupport.returnPendingItems(player, submittedItems);
    }

    List<String> createManageClaimLore(PlayerDataStore.OrderEntry order) {
        return deliverySupport.createManageClaimLore(order);
    }
}
