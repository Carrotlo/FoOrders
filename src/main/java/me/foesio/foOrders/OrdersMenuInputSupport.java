package me.foesio.foOrders;

import me.foesio.core.inventory.InventoryCloseSuppressor;
import me.foesio.foOrders.dialog.FoOrdersDialogInputService;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuInputSupport {
    private static final long NATIVE_DIALOG_CLOSE_SUPPRESSION_TICKS = 5L;

    private final OrdersMenuManager manager;
    private final OrdersMenuInteractionSupport interaction;
    private final FoScheduler scheduler;
    private final Map<UUID, MenuViewState> menuStates;
    private final InventoryCloseSuppressor inventoryCloseSuppressor;
    private final Map<UUID, PendingDeliveryState> pendingDeliveries;
    private final Set<UUID> waitingDeliveryPlayers;
    private final Set<UUID> nativeDialogFallbackWarnings = ConcurrentHashMap.newKeySet();
    private final PlayerDataStore playerDataStore;
    private final HistoryDataStore historyDataStore;

    OrdersMenuInputSupport(OrdersMenuManager manager, OrdersMenuInteractionSupport interaction) {
        this.manager = manager;
        this.interaction = interaction;
        this.scheduler = manager.scheduler;
        this.menuStates = manager.menuStates;
        this.inventoryCloseSuppressor = manager.inventoryCloseSuppressor;
        this.pendingDeliveries = manager.pendingDeliveries;
        this.waitingDeliveryPlayers = manager.waitingDeliveryPlayers;
        this.playerDataStore = manager.playerDataStore;
        this.historyDataStore = manager.historyDataStore;
    }

    void handleInputFromChat(Player player, SignInputType inputType, String rawValue) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());

        switch (inputType) {
            case AMOUNT -> {
                NewOrderDraft draft = viewState.getOrCreateDraft();
                int parsedAmount = manager.itemSupport.parsePositiveInt(rawValue);
                if (parsedAmount <= 0) {
                    draft = draft.withAmount(1);
                    interaction.sendInvalidAmountActionbar(player);
                } else {
                    draft = draft.withAmount(parsedAmount);
                }
                viewState.draft = draft;
            }
            case PRICE -> {
                NewOrderDraft draft = viewState.getOrCreateDraft();
                double parsedPrice = manager.itemSupport.parsePositiveDouble(rawValue);
                if (parsedPrice <= 0) {
                    draft = draft.withPricePerItem(1);
                    interaction.sendInvalidAmountActionbar(player);
                } else {
                    draft = draft.withPricePerItem(parsedPrice);
                }
                viewState.draft = draft;
            }
            case MAIN_SEARCH -> {
                viewState.search = rawValue;
                viewState.page = 1;
            }
            case ITEM_SEARCH -> {
                ItemSelectState itemSelectState = viewState.getOrCreateItemSelectState();
                itemSelectState.search = rawValue;
                itemSelectState.page = 1;
            }
        }

        if (player.isOnline()) {
            manager.itemSupport.reopenAfterSignInput(player, inputType);
        }
    }

    private boolean isSearchInputType(SignInputType inputType) {
        return inputType == SignInputType.MAIN_SEARCH || inputType == SignInputType.ITEM_SEARCH;
    }

    void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        Inventory topInventory = event.getPlayer().getOpenInventory().getTopInventory();
        if (!manager.isOrdersMenu(topInventory)) {
            return;
        }
        if (topInventory.getHolder() instanceof OrdersMenuHolder holder && holder.getMenuType() == MenuType.DELIVER) {
            return;
        }
        event.setCancelled(true);
    }

    void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerDataStore.getOrCreate(playerId);
        historyDataStore.getOrderHistory(playerId);
    }

    void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingDeliveryState pendingDeliveryState = pendingDeliveries.remove(playerId);
        if (pendingDeliveryState != null) {
            interaction.deliverySupport.returnPendingItems(event.getPlayer(), pendingDeliveryState.submittedItems());
        }
        waitingDeliveryPlayers.remove(playerId);
        menuStates.remove(playerId);
        inventoryCloseSuppressor.clear(event.getPlayer());
        if (manager.dialogInputService() != null) {
            manager.dialogInputService().clear(event.getPlayer());
        }
        playerDataStore.saveAndUnload(playerId);
        historyDataStore.saveAndUnload(playerId);
    }

    void openSignInput(Player player, SignInputType inputType) {
        UUID playerId = player.getUniqueId();
        MenuViewState viewState = menuStates.computeIfAbsent(playerId, ignored -> new MenuViewState());
        NewOrderDraft draft = viewState.getOrCreateDraft();
        ItemSelectState itemSelectState = viewState.getOrCreateItemSelectState();
        String row1Value = manager.itemSupport.getSignInputFirstLine(inputType, viewState.search, itemSelectState.search, draft);
        String targetLabel = isSearchInputType(inputType) ? "Search" : manager.itemSupport.getSignInputTargetLabel(inputType);

        FoOrdersDialogInputService dialogInputService = manager.dialogInputService();
        if (dialogInputService != null) {
            boolean suppressInventoryClose = manager.isOrdersMenu(player.getOpenInventory().getTopInventory());
            if (suppressInventoryClose) {
                inventoryCloseSuppressor.suppressNextClose(player);
            }
            if (dialogInputService.willUseFallback()) {
                warnNativeDialogFallback(player);
            }

            boolean opened = dialogInputService.openInput(
                player,
                inputType,
                row1Value,
                targetLabel,
                value -> {
                    if (isSearchInputType(inputType) && value.equalsIgnoreCase("clear")) {
                        handleInputFromChat(player, inputType, "");
                        return;
                    }
                    handleInputFromChat(player, inputType, value);
                },
                () -> manager.itemSupport.reopenAfterSignInput(player, inputType)
            );
            if (opened) {
                if (suppressInventoryClose) {
                    scheduler.runLaterForPlayer(player, () -> inventoryCloseSuppressor.clear(player), NATIVE_DIALOG_CLOSE_SUPPRESSION_TICKS);
                }
                return;
            }
        }
        if (manager.isOrdersMenu(player.getOpenInventory().getTopInventory())) {
            inventoryCloseSuppressor.clear(player);
        }
    }

    private void warnNativeDialogFallback(Player player) {
        FoOrdersDialogInputService dialogInputService = manager.dialogInputService();
        if (dialogInputService == null
            || !dialogInputService.nativeEnabled()
            || !dialogInputService.warnOnFallback()
            || !player.hasPermission(ADMIN_PERMISSION)
            || !nativeDialogFallbackWarnings.add(player.getUniqueId())) {
            return;
        }
        manager.messages().send(player, "native-dialogs.fallback");
    }
}
