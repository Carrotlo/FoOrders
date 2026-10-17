package me.foesio.foOrders;

import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.foOrders.integration.DiscordWebhookNotifier;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foOrders.storage.HistoryDataStore;
import me.foesio.foOrders.storage.PlayerDataStore;
import me.foesio.foOrders.util.TextFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static me.foesio.foOrders.OrdersMenuManager.*;

final class OrdersMenuDeliverySupport {
    private final OrdersMenuManager manager;
    private final OrdersMenuInteractionSupport interaction;
    private final FoScheduler scheduler;
    private final Map<UUID, MenuViewState> menuStates;
    private final Map<UUID, PendingDeliveryState> pendingDeliveries;
    private final Set<String> activeOrderOperationLocks;
    private final Set<UUID> waitingDeliveryPlayers;
    private final Object economyLock;
    private final PlayerDataStore playerDataStore;
    private final HistoryDataStore historyDataStore;
    private final DiscordWebhookNotifier discordWebhookNotifier;
    private final InventoryDepositService inventoryDepositService;

    OrdersMenuDeliverySupport(OrdersMenuManager manager, OrdersMenuInteractionSupport interaction) {
        this.manager = manager;
        this.interaction = interaction;
        this.scheduler = manager.scheduler;
        this.menuStates = manager.menuStates;
        this.pendingDeliveries = manager.pendingDeliveries;
        this.activeOrderOperationLocks = manager.activeOrderOperationLocks;
        this.waitingDeliveryPlayers = manager.waitingDeliveryPlayers;
        this.economyLock = manager.economyLock;
        this.playerDataStore = manager.playerDataStore;
        this.historyDataStore = manager.historyDataStore;
        this.discordWebhookNotifier = manager.discordWebhookNotifier;
        this.inventoryDepositService = manager.inventoryDepositService;
    }

    private void openYourOrdersMenu(Player player) {
        manager.viewSupport.openYourOrdersMenu(player);
    }

    private void openClaimOrderMenu(Player player, boolean resetPage) {
        manager.viewSupport.openClaimOrderMenu(player, resetPage);
    }

    private void openOrdersMenu(Player player, String searchText) {
        manager.viewSupport.openOrdersMenu(player, searchText);
    }

    private void openOrdersMenu(Player player, String searchText, String transitionSound) {
        manager.viewSupport.openOrdersMenu(player, searchText, transitionSound);
    }

    private boolean tryAcquireOrderOperation(String orderId) {
        return orderId != null && !orderId.isBlank() && activeOrderOperationLocks.add(orderId);
    }

    private void releaseOrderOperation(String orderId) {
        if (orderId != null && !orderId.isBlank()) {
            activeOrderOperationLocks.remove(orderId);
        }
    }

    private PlayerDataStore.OrderEntry resolveSelectedOrder(
        PlayerDataStore.PlayerData playerData,
        MenuViewState viewState,
        String orderId
    ) {
        int orderIndex = findOrderIndexById(playerData, orderId);
        if (orderIndex < 0) {
            viewState.manageOrderIndex = -1;
            viewState.manageOrderId = null;
            clearClaimSession(viewState);
            return null;
        }
        viewState.manageOrderIndex = orderIndex;
        viewState.manageOrderId = orderId;
        return playerData.getOrders().get(orderIndex);
    }

    PendingDeliveryState removePendingDelivery(UUID playerId) {
        PendingDeliveryState removed = pendingDeliveries.remove(playerId);
        waitingDeliveryPlayers.remove(playerId);
        return removed;
    }

    PendingDeliveryState removePendingDelivery(UUID playerId, String orderId) {
        if (playerId == null || orderId == null || orderId.isBlank()) {
            return null;
        }

        PendingDeliveryState pending = pendingDeliveries.get(playerId);
        if (pending == null || !orderId.equals(pending.orderId())) {
            return null;
        }
        if (!pendingDeliveries.remove(playerId, pending)) {
            return null;
        }
        waitingDeliveryPlayers.remove(playerId);
        return pending;
    }

    void returnAndClearPendingDelivery(Player player, UUID playerId) {
        PendingDeliveryState removed = removePendingDelivery(playerId);
        if (removed != null) {
            returnPendingItems(player, removed.submittedItems());
        }
    }

    private void openManageOrderMenu(Player player, int orderIndex) {
        manager.viewSupport.openManageOrderMenu(player, orderIndex);
    }

    private void sendErrorActionbar(Player player, String message) {
        interaction.sendErrorActionbar(player, message);
    }

    private void sendDeliveringActionbar(Player player, int animationStep) {
        interaction.sendDeliveringActionbar(player, animationStep);
    }

    private boolean matchesOrderedItem(ItemStack item, PlayerDataStore.OrderEntry order) {
        return manager.itemSupport.matchesOrderedItem(item, order);
    }

    private String resolvePlayerName(UUID playerId) {
        return manager.itemSupport.resolvePlayerName(playerId);
    }

    private String formatOrderDisplayName(PlayerDataStore.OrderEntry order) {
        return manager.itemSupport.formatOrderDisplayName(order);
    }

    private String formatCompactAmount(double value) {
        return manager.itemSupport.formatCompactAmount(value);
    }

    private ItemStack createOrderStack(PlayerDataStore.OrderEntry order, int amount) {
        return manager.itemSupport.createOrderStack(order, amount);
    }

    void handleClaimOrderClick(Player player, int rawSlot, ItemStack clickedItem) {
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
        List<Integer> claimSessionStacks = initializeClaimSessionStacks(viewState, selectedOrder, false);

        List<Integer> contentSlots = manager.guis().contentSlots("claim-order", "content-slots");
        int backSlot = manager.guis().activeControlSlot("claim-order.previous-page");
        int nextSlot = manager.guis().activeControlSlot("claim-order.next-page");
        int dropPageSlot = manager.guis().activeControlSlot("claim-order.drop-page");

        if (rawSlot == backSlot && viewState.claimPage > 1
            && manager.guis().buttonEnabled("claim-order", "previous-page")) {
            viewState.claimPage--;
            manager.playSound(player, GUI_PAGE_PREVIOUS_SOUND);
            openClaimOrderMenu(player, false);
            return;
        }

        if (rawSlot == nextSlot && manager.guis().buttonEnabled("claim-order", "next-page")) {
            int pageCount = calculateClaimSessionPageCount(claimSessionStacks);
            if (viewState.claimPage < pageCount) {
                viewState.claimPage++;
                manager.playSound(player, GUI_PAGE_NEXT_SOUND);
                openClaimOrderMenu(player, false);
            }
            return;
        }

        if (rawSlot == dropPageSlot) {
            dropClaimPage(player, playerData, viewState, selectedOrder, claimSessionStacks);
            return;
        }

        int contentIndex = contentSlots.indexOf(rawSlot);
        if (contentIndex < 0 || clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        String orderId = selectedOrder.getOrderId();
        if (!tryAcquireOrderOperation(orderId)) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.order-busy"));
            openClaimOrderMenu(player, false);
            return;
        }

        try {
            playerData = playerDataStore.getOrCreate(playerId);
            selectedOrder = resolveSelectedOrder(playerData, viewState, orderId);
            if (selectedOrder == null) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openYourOrdersMenu(player);
                return;
            }

            claimSessionStacks = initializeClaimSessionStacks(viewState, selectedOrder, false);
            int absoluteStackIndex = (viewState.claimPage - 1) * contentSlots.size() + contentIndex;
            if (absoluteStackIndex < 0 || absoluteStackIndex >= claimSessionStacks.size()) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openClaimOrderMenu(player, false);
                return;
            }

            int availableInSession = Math.max(0, claimSessionStacks.get(absoluteStackIndex));
            int requestedAmount = claimAmountForClick(availableInSession,
                selectedOrder.getAmountClaimable());
            if (requestedAmount <= 0) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openClaimOrderMenu(player, false);
                return;
            }

            int transferred = transferClaimedItemsToPlayer(player, selectedOrder, requestedAmount);
            if (transferred <= 0) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.inventory-full"));
                return;
            }

            selectedOrder.setAmountClaimable(Math.max(0, selectedOrder.getAmountClaimable() - transferred));
            claimSessionStacks.set(absoluteStackIndex, Math.max(0, availableInSession - transferred));
            boolean removed = removeOrderIfCompleted(playerData, viewState);
            if (removed) {
                clearClaimSession(viewState);
            }
            playerDataStore.saveUrgent(playerId);
            appendOrderEvent(playerId, "order-claimed-inventory", selectedOrder,
                TextFormat.placeholders("amount", transferred));
            manager.fileLogger().info(
                "Player " + player.getName() + " claimed "
                    + formatCompactAmount(transferred) + "x " + formatOrderDisplayName(selectedOrder)
                    + " from order " + selectedOrder.getOrderId() + "."
            );
            sendOrderClaimedWebhook(player, selectedOrder, transferred, "Inventory");
            manager.playSoundWithPitchVariation(player, ORDER_CLAIMED_SOUND);

            if (!removed && viewState.manageOrderIndex >= 0) {
                openClaimOrderMenu(player, false);
            } else {
                openYourOrdersMenu(player);
            }
        } finally {
            releaseOrderOperation(orderId);
        }
    }

    /** The GUI stack count is presentation; the claim session owns the transfer count. */
    static int claimAmountForClick(int availableInSession, int claimable) {
        return Math.max(0, Math.min(availableInSession, claimable));
    }

    void handleDeliveryConfirmClick(Player player, int rawSlot) {
        UUID playerId = player.getUniqueId();
        PendingDeliveryState pending = pendingDeliveries.get(playerId);
        if (pending == null) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
            openOrdersMenu(player, null);
            return;
        }

        int cancelSlot = manager.guis().activeControlSlot("delivery-confirm.cancel");
        int confirmSlot = manager.guis().activeControlSlot("delivery-confirm.confirm");

        if (rawSlot == cancelSlot) {
            PendingDeliveryState removed = removePendingDelivery(playerId, pending.orderId());
            if (removed != null) {
                returnPendingItems(player, removed.submittedItems());
            }
            openOrdersMenu(player, null, GUI_BACK_SOUND);
            return;
        }

        if (rawSlot == confirmSlot) {
            waitAndProcessDeliveryConfirmation(player, pending, 0);
        }
    }

    void waitAndProcessDeliveryConfirmation(Player player, PendingDeliveryState pending, int animationStep) {
        UUID playerId = player.getUniqueId();
        PendingDeliveryState current = pendingDeliveries.get(playerId);
        if (current == null || !current.orderId().equals(pending.orderId())) {
            waitingDeliveryPlayers.remove(playerId);
            return;
        }
        if (!player.isOnline()) {
            waitingDeliveryPlayers.remove(playerId);
            return;
        }

        if (!tryAcquireOrderOperation(pending.orderId())) {
            waitingDeliveryPlayers.add(playerId);
            sendDeliveringActionbar(player, animationStep);
            scheduler.runLaterForPlayer(player, () -> waitAndProcessDeliveryConfirmation(player, pending, (animationStep + 1) % 3), 6L);
            return;
        }

        waitingDeliveryPlayers.remove(playerId);
        try {
            processDeliveryConfirmation(player, current);
        } finally {
            releaseOrderOperation(pending.orderId());
        }
    }

    void processDeliveryConfirmation(Player player, PendingDeliveryState pending) {
        UUID delivererId = player.getUniqueId();
        PendingDeliveryState activePending = removePendingDelivery(delivererId, pending.orderId());
        if (activePending == null) {
            return;
        }

        PlayerDataStore.PlayerData ownerData = playerDataStore.getOrCreate(activePending.ownerId());
        PlayerDataStore.OrderEntry liveOrder = findOrderById(ownerData, activePending.orderId());
        ItemStack[] submittedItems = cloneContents(activePending.submittedItems());

        if (liveOrder == null || liveOrder.isCancelled() || submittedItems.length == 0) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get(submittedItems.length == 0
                ? "actionbar.no-items-accepted" : "actionbar.action-unavailable"));
            returnPendingItems(player, submittedItems);
            openOrdersMenu(player, null);
            return;
        }

        int submittedWantedAmount = 0;
        for (ItemStack item : submittedItems) {
            submittedWantedAmount += countDeliverableWantedAmount(item, liveOrder);
        }

        int remainingNeeded = Math.max(0, liveOrder.getAmountOrdered() - liveOrder.getAmountDelivered());
        int acceptedAmount = Math.min(submittedWantedAmount, remainingNeeded);
        if (acceptedAmount <= 0) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.no-items-accepted"));
        }

        double payout = acceptedAmount * liveOrder.getPricePerItem();
        if (payout > 0D) {
            if (!hasEconomyProvider()) {
                manager.playSound(player, GUI_ERROR_SOUND);
                returnPendingItems(player, submittedItems);
                sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
                openOrdersMenu(player, null);
                return;
            }

            if (!depositEconomy(player, payout)) {
                manager.playSound(player, GUI_ERROR_SOUND);
                returnPendingItems(player, submittedItems);
                sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
                openOrdersMenu(player, null);
                return;
            }
        }

        List<ItemStack> toReturn = new ArrayList<>();
        int acceptedLeft = acceptedAmount;
        for (ItemStack item : submittedItems) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (matchesOrderedItem(item, liveOrder)) {
                ItemStack stackCopy = item.clone();
                int consume = Math.min(acceptedLeft, stackCopy.getAmount());
                acceptedLeft -= consume;
                int overflow = stackCopy.getAmount() - consume;
                if (overflow > 0) {
                    stackCopy.setAmount(overflow);
                    toReturn.add(stackCopy);
                }
                continue;
            }

            if (isShulkerBoxItem(item)) {
                int boxCount = Math.max(1, item.getAmount());
                for (int boxIndex = 0; boxIndex < boxCount; boxIndex++) {
                    ItemStack singleShulker = item.clone();
                    singleShulker.setAmount(1);
                    acceptedLeft = extractFromShulker(singleShulker, liveOrder, acceptedLeft);
                    toReturn.add(singleShulker);
                }
                continue;
            }

            toReturn.add(item.clone());
        }

        if (!toReturn.isEmpty()) {
            returnItems(player, toReturn.toArray(ItemStack[]::new));
        }

        if (acceptedAmount > 0) {
            liveOrder.setAmountDelivered(liveOrder.getAmountDelivered() + acceptedAmount);
            liveOrder.setAmountClaimable(liveOrder.getAmountClaimable() + acceptedAmount);
            liveOrder.setAmountPaid(liveOrder.getAmountPaid() + payout);
            playerDataStore.saveUrgent(activePending.ownerId());

            String ownerName = resolvePlayerName(activePending.ownerId());
            String itemName = formatOrderDisplayName(liveOrder);
            appendOrderEvent(activePending.ownerId(), "order-delivered", liveOrder,
                TextFormat.placeholders("actor", player.getName(), "amount", acceptedAmount));
            appendDeliverEvent(delivererId, "delivery-completed", liveOrder,
                TextFormat.placeholders("amount", acceptedAmount, "owner", ownerName, "payout", payout));
            manager.fileLogger().info(
                "Player " + player.getName() + " delivered "
                    + formatCompactAmount(acceptedAmount) + "x " + itemName
                    + " to order " + liveOrder.getOrderId()
                    + " and earned $" + formatCompactAmount(payout) + "."
            );
            sendOrderDeliveredWebhook(player, ownerName, liveOrder, acceptedAmount, payout);
            manager.playSound(player, ORDER_DELIVERED_SOUND);
            manager.messages().send(player, "orders.delivered", PluginMessages.placeholders("payout", formatCompactAmount(payout)));
            Player orderOwner = Bukkit.getPlayer(activePending.ownerId());
            if (orderOwner != null && orderOwner.isOnline()) {
                manager.playSound(orderOwner, ORDER_DELIVERED_SOUND);
                manager.messages().send(orderOwner, "orders.owner-delivered", PluginMessages.placeholders(
                    "player", player.getName(),
                    "amount", formatCompactAmount(acceptedAmount),
                    "item", itemName
                ));
                if (liveOrder.getAmountDelivered() >= liveOrder.getAmountOrdered()) {
                    manager.playSound(orderOwner, ORDER_FILLED_SOUND);
                    manager.messages().send(orderOwner, "orders.owner-filled", PluginMessages.placeholders("item", itemName));
                }
            }
        }

        openOrdersMenu(player, null);
    }

    int countDeliverableWantedAmount(ItemStack item, PlayerDataStore.OrderEntry order) {
        if (item == null || item.getType() == Material.AIR) {
            return 0;
        }

        if (matchesOrderedItem(item, order)) {
            return item.getAmount();
        }

        if (!isShulkerBoxItem(item)) {
            return 0;
        }

        int perBox = countWantedInShulker(item, order);
        if (perBox <= 0) {
            return 0;
        }

        long total = (long) perBox * Math.max(1, item.getAmount());
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    boolean isShulkerBoxItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof BlockStateMeta blockStateMeta)) {
            return false;
        }
        return blockStateMeta.getBlockState() instanceof ShulkerBox;
    }

    int countWantedInShulker(ItemStack shulkerItem, PlayerDataStore.OrderEntry order) {
        ItemMeta itemMeta = shulkerItem.getItemMeta();
        if (!(itemMeta instanceof BlockStateMeta blockStateMeta)) {
            return 0;
        }
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return 0;
        }

        int amount = 0;
        for (ItemStack content : shulkerBox.getInventory().getContents()) {
            if (!matchesOrderedItem(content, order)) {
                continue;
            }
            amount += content.getAmount();
        }
        return amount;
    }

    int extractFromShulker(ItemStack shulkerItem, PlayerDataStore.OrderEntry order, int acceptedLeft) {
        if (acceptedLeft <= 0) {
            return acceptedLeft;
        }

        ItemMeta itemMeta = shulkerItem.getItemMeta();
        if (!(itemMeta instanceof BlockStateMeta blockStateMeta)) {
            return acceptedLeft;
        }
        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return acceptedLeft;
        }

        ItemStack[] contents = shulkerBox.getInventory().getContents();
        boolean changed = false;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack content = contents[slot];
            if (!matchesOrderedItem(content, order)) {
                continue;
            }

            int consume = Math.min(acceptedLeft, content.getAmount());
            if (consume <= 0) {
                continue;
            }

            acceptedLeft -= consume;
            changed = true;
            int leftInStack = content.getAmount() - consume;
            if (leftInStack <= 0) {
                contents[slot] = null;
            } else {
                ItemStack remaining = content.clone();
                remaining.setAmount(leftInStack);
                contents[slot] = remaining;
            }

            if (acceptedLeft <= 0) {
                break;
            }
        }

        if (changed) {
            shulkerBox.getInventory().setContents(contents);
            blockStateMeta.setBlockState(shulkerBox);
            shulkerItem.setItemMeta(blockStateMeta);
        }
        return acceptedLeft;
    }

    void handleOrderCancellation(
        Player player,
        PlayerDataStore.PlayerData playerData,
        MenuViewState viewState,
        PlayerDataStore.OrderEntry selectedOrder
    ) {
        String orderId = selectedOrder.getOrderId();
        if (!tryAcquireOrderOperation(orderId)) {
            sendErrorActionbar(player, manager.messages().get("actionbar.order-busy"));
            openManageOrderMenu(player, viewState.manageOrderIndex);
            return;
        }

        try {
            playerData = playerDataStore.getOrCreate(player.getUniqueId());
            selectedOrder = resolveSelectedOrder(playerData, viewState, orderId);
            if (selectedOrder == null) {
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openYourOrdersMenu(player);
                return;
            }
            if (selectedOrder.isCancelled()) {
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openManageOrderMenu(player, viewState.manageOrderIndex);
                return;
            }

            double refundedAmount = getRemainingOrderFunds(selectedOrder);
            if (!refundRemainingOrderFunds(player.getUniqueId(), selectedOrder)) {
                sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
                return;
            }

            selectedOrder.setCancelled(true);
            sendErrorActionbar(player, manager.messages().get("actionbar.order-cancelled"));
            manager.messages().send(player, "orders.cancelled");
            appendOrderEvent(player.getUniqueId(), "order-cancelled", selectedOrder,
                TextFormat.placeholders("refund", refundedAmount));
            manager.fileLogger().info(
                "Player " + player.getName() + " cancelled order " + selectedOrder.getOrderId()
                    + " with refund $" + formatCompactAmount(refundedAmount) + "."
            );
            boolean removed = removeOrderIfCompleted(playerData, viewState);
            playerDataStore.saveUrgent(player.getUniqueId());
            sendOrderCancelledWebhook(player, selectedOrder, refundedAmount);
            manager.playSound(player, ORDER_CANCELLED_SOUND);
            if (removed) {
                openYourOrdersMenu(player);
                return;
            }
            openManageOrderMenu(player, viewState.manageOrderIndex);
        } finally {
            releaseOrderOperation(orderId);
        }
    }

    void handleAdminDeleteOrder(Player player, MenuViewState viewState, AdminOrderTarget target) {
        String orderId = target.order().getOrderId();
        if (!tryAcquireOrderOperation(orderId)) {
            return;
        }

        try {
            PlayerDataStore.PlayerData ownerData = playerDataStore.getOrCreate(target.ownerId());
            PlayerDataStore.OrderEntry liveOrder = findOrderById(ownerData, orderId);
            if (liveOrder == null) {
                clearAdminTarget(viewState);
                openOrdersMenu(player, null);
                return;
            }
            target = new AdminOrderTarget(target.ownerId(), ownerData, liveOrder);

            boolean selfModeration = target.ownerId().equals(player.getUniqueId());
            double remainingFunds = target.order().isCancelled() ? 0D : getRemainingOrderFunds(target.order());
            if (remainingFunds > 0D && !refundRemainingOrderFunds(target.ownerId(), target.order())) {
                sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
                return;
            }

            boolean removed = target.ownerData().getOrders().remove(target.order());
            if (!removed) {
                clearAdminTarget(viewState);
                openOrdersMenu(player, null);
                return;
            }

            String ownerName = resolvePlayerName(target.ownerId());
            String orderName = formatOrderDisplayName(target.order());
            playerDataStore.saveUrgent(target.ownerId());
            appendOrderEvent(target.ownerId(), "order-deleted-admin", target.order(),
                TextFormat.placeholders("actor", player.getName(), "refund", remainingFunds));
            manager.fileLogger().info(
                "Admin " + player.getName() + " deleted order " + target.order().getOrderId()
                    + " owned by " + ownerName
                    + " with refund $" + formatCompactAmount(remainingFunds) + "."
            );
            sendAdminOrderDeletedWebhook(player, ownerName, target.order(), remainingFunds);
            manager.playSound(player, ORDER_ADMIN_DELETED_SOUND);
            clearAdminTarget(viewState);
            if (selfModeration) {
                manager.messages().send(player, "orders.deleted-self", PluginMessages.placeholders("order", orderName));
            } else {
                manager.messages().send(player, "orders.deleted-other", PluginMessages.placeholders(
                    "owner", ownerName,
                    "order", orderName
                ));
            }

            Player owner = Bukkit.getPlayer(target.ownerId());
            if (!selfModeration && owner != null && owner.isOnline()) {
                manager.messages().send(owner, "orders.owner-deleted", PluginMessages.placeholders(
                    "order", orderName,
                    "admin", player.getName()
                ));
                if (remainingFunds > 0D) {
                    manager.messages().send(owner, "orders.refunded", PluginMessages.placeholders("amount", formatCompactAmount(remainingFunds)));
                }
            }
            openOrdersMenu(player, null);
        } finally {
            releaseOrderOperation(orderId);
        }
    }

    boolean handleAdminCancelOrder(Player player, AdminOrderTarget target) {
        String orderId = target.order().getOrderId();
        if (!tryAcquireOrderOperation(orderId)) {
            return false;
        }

        try {
            PlayerDataStore.PlayerData ownerData = playerDataStore.getOrCreate(target.ownerId());
            PlayerDataStore.OrderEntry liveOrder = findOrderById(ownerData, orderId);
            if (liveOrder == null) {
                return false;
            }
            target = new AdminOrderTarget(target.ownerId(), ownerData, liveOrder);
            if (target.order().isCancelled()) {
                return true;
            }

            boolean selfModeration = target.ownerId().equals(player.getUniqueId());
            double remainingFunds = getRemainingOrderFunds(target.order());
            if (!refundRemainingOrderFunds(target.ownerId(), target.order())) {
                sendErrorActionbar(player, manager.messages().get("actionbar.economy-error"));
                return false;
            }

            target.order().setCancelled(true);
            playerDataStore.saveUrgent(target.ownerId());

            String ownerName = resolvePlayerName(target.ownerId());
            String orderName = formatOrderDisplayName(target.order());
            appendOrderEvent(target.ownerId(), "order-cancelled-admin", target.order(),
                TextFormat.placeholders("actor", player.getName(), "refund", remainingFunds));
            manager.fileLogger().info(
                "Admin " + player.getName() + " cancelled order " + target.order().getOrderId()
                    + " owned by " + ownerName
                    + " with refund $" + formatCompactAmount(remainingFunds) + "."
            );
            sendAdminOrderCancelledWebhook(player, ownerName, target.order(), remainingFunds);
            manager.playSound(player, ORDER_ADMIN_CANCELLED_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.order-cancelled"));
            if (selfModeration) {
                manager.messages().send(player, "orders.cancelled-self-admin", PluginMessages.placeholders("order", orderName));
            } else {
                manager.messages().send(player, "orders.cancelled-other-admin", PluginMessages.placeholders(
                    "owner", ownerName,
                    "order", orderName
                ));
            }

            Player owner = Bukkit.getPlayer(target.ownerId());
            if (!selfModeration && owner != null && owner.isOnline()) {
                manager.messages().send(owner, "orders.owner-cancelled-admin", PluginMessages.placeholders(
                    "order", orderName,
                    "admin", player.getName()
                ));
                if (remainingFunds > 0D) {
                    manager.messages().send(owner, "orders.refunded", PluginMessages.placeholders("amount", formatCompactAmount(remainingFunds)));
                }
            }
            return true;
        } finally {
            releaseOrderOperation(orderId);
        }
    }

    void sendOrderCreatedWebhook(Player player, PlayerDataStore.OrderEntry order, double totalCost) {
        discordWebhookNotifier.sendEmbed(
            DiscordWebhookNotifier.WebhookCategory.CREATING,
            "New Order Created",
            player.getName() + " created a new order.",
            0x57F287,
            List.of(
                webhookField("Player", player.getName(), true),
                webhookField("Item", formatOrderDisplayName(order), true),
                webhookField("Amount", formatCompactAmount(order.getAmountOrdered()), true),
                webhookField("Price Each", "$" + formatCompactAmount(order.getPricePerItem()), true),
                webhookField("Total", "$" + formatCompactAmount(totalCost), true),
                webhookField("Order ID", order.getOrderId(), false)
            )
        );
    }

    void announceCreatedOrderInChat(Player player, PlayerDataStore.OrderEntry order, double totalCost) {
        if (!manager.announceCreatedOrdersInChat) {
            return;
        }

        manager.messages().broadcast("orders.created-broadcast", Map.of(
            "player", player.getName(),
            "amount", formatCompactAmount(order.getAmountOrdered()),
            "item", formatOrderDisplayName(order),
            "price", formatCompactAmount(order.getPricePerItem()),
            "total", formatCompactAmount(totalCost)
        ), CREATED_ORDER_ANNOUNCEMENTS_PERMISSION);
    }

    void sendOrderCancelledWebhook(Player player, PlayerDataStore.OrderEntry order, double refundedAmount) {
        discordWebhookNotifier.sendEmbed(
            DiscordWebhookNotifier.WebhookCategory.CANCELLING,
            "Order Cancelled",
            player.getName() + " cancelled their order.",
            0xED4245,
            List.of(
                webhookField("Player", player.getName(), true),
                webhookField("Item", formatOrderDisplayName(order), true),
                webhookField("Refunded", "$" + formatCompactAmount(refundedAmount), true),
                webhookField("Progress", buildOrderProgress(order), true),
                webhookField("Claimable", formatCompactAmount(order.getAmountClaimable()), true),
                webhookField("Order ID", order.getOrderId(), false)
            )
        );
    }

    void sendOrderDeliveredWebhook(
        Player deliverer,
        String ownerName,
        PlayerDataStore.OrderEntry order,
        int deliveredAmount,
        double payout
    ) {
        discordWebhookNotifier.sendEmbed(
            DiscordWebhookNotifier.WebhookCategory.DELIVERING,
            "Order Delivery Confirmed",
            deliverer.getName() + " delivered items to " + ownerName + "'s order.",
            0x5865F2,
            List.of(
                webhookField("Deliverer", deliverer.getName(), true),
                webhookField("Order Owner", ownerName, true),
                webhookField("Item", formatOrderDisplayName(order), true),
                webhookField("Delivered Now", formatCompactAmount(deliveredAmount), true),
                webhookField("Payout", "$" + formatCompactAmount(payout), true),
                webhookField("Progress", buildOrderProgress(order), true),
                webhookField("Order ID", order.getOrderId(), false)
            )
        );
    }

    void sendOrderClaimedWebhook(Player player, PlayerDataStore.OrderEntry order, int claimedAmount, String claimMethod) {
        discordWebhookNotifier.sendEmbed(
            DiscordWebhookNotifier.WebhookCategory.CLAIMING,
            "Order Claimed",
            player.getName() + " claimed items from their order.",
            0xFEE75C,
            List.of(
                webhookField("Player", player.getName(), true),
                webhookField("Item", formatOrderDisplayName(order), true),
                webhookField("Claimed", formatCompactAmount(claimedAmount), true),
                webhookField("Method", claimMethod, true),
                webhookField("Remaining Claimable", formatCompactAmount(order.getAmountClaimable()), true),
                webhookField("Order ID", order.getOrderId(), false)
            )
        );
    }

    void sendAdminOrderCancelledWebhook(
        Player admin,
        String ownerName,
        PlayerDataStore.OrderEntry order,
        double refundedAmount
    ) {
        discordWebhookNotifier.sendEmbed(
            DiscordWebhookNotifier.WebhookCategory.ADMIN_MODERATION,
            "Admin Cancelled Order",
            admin.getName() + " cancelled an order owned by " + ownerName + ".",
            0xFAA61A,
            List.of(
                webhookField("Admin", admin.getName(), true),
                webhookField("Order Owner", ownerName, true),
                webhookField("Item", formatOrderDisplayName(order), true),
                webhookField("Refunded", "$" + formatCompactAmount(refundedAmount), true),
                webhookField("Progress", buildOrderProgress(order), true),
                webhookField("Order ID", order.getOrderId(), false)
            )
        );
    }

    void sendAdminOrderDeletedWebhook(
        Player admin,
        String ownerName,
        PlayerDataStore.OrderEntry order,
        double refundedAmount
    ) {
        discordWebhookNotifier.sendEmbed(
            DiscordWebhookNotifier.WebhookCategory.ADMIN_MODERATION,
            "Admin Deleted Order",
            admin.getName() + " deleted an order owned by " + ownerName + ".",
            0xFAA61A,
            List.of(
                webhookField("Admin", admin.getName(), true),
                webhookField("Order Owner", ownerName, true),
                webhookField("Item", formatOrderDisplayName(order), true),
                webhookField("Refunded", "$" + formatCompactAmount(refundedAmount), true),
                webhookField("Progress", buildOrderProgress(order), true),
                webhookField("Order ID", order.getOrderId(), false)
            )
        );
    }

    DiscordWebhookNotifier.EmbedField webhookField(String name, String value, boolean inline) {
        return new DiscordWebhookNotifier.EmbedField(name, value, inline);
    }

    String buildOrderProgress(PlayerDataStore.OrderEntry order) {
        return formatCompactAmount(order.getAmountDelivered()) + "/" + formatCompactAmount(order.getAmountOrdered());
    }

    AdminOrderTarget resolveAdminOrderTarget(MenuViewState viewState) {
        if (viewState.adminTargetOwnerId == null || viewState.adminTargetOrderId == null) {
            return null;
        }

        PlayerDataStore.PlayerData ownerData = playerDataStore.getOrCreate(viewState.adminTargetOwnerId);
        PlayerDataStore.OrderEntry order = findOrderById(ownerData, viewState.adminTargetOrderId);
        if (order == null) {
            return null;
        }
        return new AdminOrderTarget(viewState.adminTargetOwnerId, ownerData, order);
    }

    double getRemainingOrderFunds(PlayerDataStore.OrderEntry order) {
        return Math.max(0D, order.getTotalCost() - order.getAmountPaid());
    }

    boolean refundRemainingOrderFunds(UUID ownerId, PlayerDataStore.OrderEntry order) {
        return transferRemainingOrderFunds(ownerId, order, true);
    }

    boolean reserveRemainingOrderFunds(UUID ownerId, PlayerDataStore.OrderEntry order) {
        return transferRemainingOrderFunds(ownerId, order, false);
    }

    boolean hasEconomyProvider() {
        synchronized (economyLock) {
            return manager.economy != null && manager.economy.isAvailable();
        }
    }

    boolean hasEconomyBalance(OfflinePlayer player, double amount) {
        synchronized (economyLock) {
            return manager.economy != null && manager.economy.isAvailable() && manager.economy.has(player, amount);
        }
    }

    boolean withdrawEconomy(OfflinePlayer player, double amount) {
        synchronized (economyLock) {
            if (manager.economy == null || !manager.economy.isAvailable()) {
                return false;
            }
            return manager.economy.withdraw(player, amount);
        }
    }

    boolean depositEconomy(OfflinePlayer player, double amount) {
        synchronized (economyLock) {
            if (manager.economy == null || !manager.economy.isAvailable()) {
                return false;
            }
            return manager.economy.deposit(player, amount);
        }
    }

    boolean transferRemainingOrderFunds(UUID ownerId, PlayerDataStore.OrderEntry order, boolean refund) {
        double remainingFunds = getRemainingOrderFunds(order);
        if (remainingFunds <= 0D) {
            return true;
        }
        if (!hasEconomyProvider()) {
            return false;
        }

        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerId);
        boolean successful = refund
            ? depositEconomy(owner, remainingFunds)
            : withdrawEconomy(owner, remainingFunds);
        return successful;
    }

    boolean isAdminOrderModerationClick(Player player, ClickType clickType) {
        return clickType == ClickType.SHIFT_LEFT && canModerateOrders(player);
    }

    void appendOrderHistory(UUID playerId, String action, String details) {
        if (!manager.historyEnabled) {
            return;
        }
        historyDataStore.appendOrderHistory(playerId, action, details);
    }

    void appendDeliverHistory(UUID playerId, String action, String details) {
        if (!manager.historyEnabled) {
            return;
        }
        historyDataStore.appendDeliverHistory(playerId, action, details);
    }

    void appendOrderEvent(UUID playerId, String eventKey, PlayerDataStore.OrderEntry order,
                          Map<String, String> extraValues) {
        if (!manager.historyEnabled) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>(manager.itemSupport.historyItemValues(order));
        values.putAll(extraValues);
        historyDataStore.appendOrderEvent(playerId, eventKey, values);
    }

    void appendDeliverEvent(UUID playerId, String eventKey, PlayerDataStore.OrderEntry order,
                            Map<String, String> extraValues) {
        if (!manager.historyEnabled) {
            return;
        }
        Map<String, String> values = new LinkedHashMap<>(manager.itemSupport.historyItemValues(order));
        values.putAll(extraValues);
        historyDataStore.appendDeliverEvent(playerId, eventKey, values);
    }

    boolean canOpenOwnHistory(Player player) {
        return manager.historyEnabled && (manager.historyPlayersCanViewOwn || canOpenAnyHistory(player));
    }

    boolean canOpenAnyHistory(Player player) {
        return manager.historyEnabled && manager.historyAdminsCanViewAny && canModerateOrders(player);
    }

    boolean canAccessHistory(Player viewer, UUID targetPlayerId) {
        if (!manager.historyEnabled || targetPlayerId == null) {
            return false;
        }
        if (viewer.getUniqueId().equals(targetPlayerId)) {
            return canOpenOwnHistory(viewer);
        }
        return canOpenAnyHistory(viewer);
    }

    boolean canModerateOrders(Player player) {
        return player.hasPermission(ADMIN_PERMISSION);
    }

    void clearAdminTarget(MenuViewState viewState) {
        viewState.adminTargetOwnerId = null;
        viewState.adminTargetOrderId = null;
    }

    void dropClaimPage(
        Player player,
        PlayerDataStore.PlayerData playerData,
        MenuViewState viewState,
        PlayerDataStore.OrderEntry selectedOrder,
        List<Integer> claimSessionStacks
    ) {
        String orderId = selectedOrder.getOrderId();
        if (!tryAcquireOrderOperation(orderId)) {
            manager.playSound(player, GUI_ERROR_SOUND);
            sendErrorActionbar(player, manager.messages().get("actionbar.order-busy"));
            openClaimOrderMenu(player, false);
            return;
        }

        try {
            playerData = playerDataStore.getOrCreate(player.getUniqueId());
            selectedOrder = resolveSelectedOrder(playerData, viewState, orderId);
            if (selectedOrder == null) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openYourOrdersMenu(player);
                return;
            }

            claimSessionStacks = initializeClaimSessionStacks(viewState, selectedOrder, false);
            int pageCapacity = claimPageCapacity();
            int startIndex = (viewState.claimPage - 1) * pageCapacity;
            int endIndex = Math.min(claimSessionStacks.size(), startIndex + pageCapacity);
            if (startIndex < 0 || startIndex >= endIndex) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openClaimOrderMenu(player, false);
                return;
            }

            List<Integer> pageStacks = new ArrayList<>(endIndex - startIndex);
            for (int index = startIndex; index < endIndex; index++) {
                pageStacks.add(Math.max(0, claimSessionStacks.get(index)));
            }
            if (pageStacks.isEmpty()) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openClaimOrderMenu(player, false);
                return;
            }

            List<ItemStack> stacksToDrop = new ArrayList<>();
            int droppedAmount = 0;
            for (int stackAmount : pageStacks) {
                if (stackAmount <= 0) {
                    continue;
                }
                droppedAmount += stackAmount;
                stacksToDrop.add(createOrderStack(selectedOrder, stackAmount));
            }

            if (droppedAmount <= 0) {
                manager.playSound(player, GUI_ERROR_SOUND);
                sendErrorActionbar(player, manager.messages().get("actionbar.action-unavailable"));
                openClaimOrderMenu(player, false);
                return;
            }

            for (int index = startIndex; index < endIndex; index++) {
                claimSessionStacks.set(index, 0);
            }
            selectedOrder.setAmountClaimable(Math.max(0, selectedOrder.getAmountClaimable() - droppedAmount));
            boolean removed = removeOrderIfCompleted(playerData, viewState);
            if (removed) {
                clearClaimSession(viewState);
            }
            playerDataStore.saveUrgent(player.getUniqueId());
            appendOrderEvent(player.getUniqueId(), "order-claimed-drop", selectedOrder,
                TextFormat.placeholders("amount", droppedAmount));
            sendOrderClaimedWebhook(player, selectedOrder, droppedAmount, "Drop Page");
            manager.playSound(player, ORDER_CLAIMED_DROP_SOUND);
            dropClaimStacksWithDelay(player, stacksToDrop);
            if (!removed && viewState.manageOrderIndex >= 0) {
                openClaimOrderMenu(player, false);
            } else {
                openYourOrdersMenu(player);
            }
        } finally {
            releaseOrderOperation(orderId);
        }
    }

    int transferClaimedItemsToPlayer(Player player, PlayerDataStore.OrderEntry order, int amount) {
        if (amount <= 0) {
            return 0;
        }
        ItemStack singleItem = createOrderStack(order, 1);
        int maxStackSize = Math.max(1, singleItem.getMaxStackSize());
        int remaining = amount;
        int movedTotal = 0;
        while (remaining > 0) {
            int chunkSize = Math.min(maxStackSize, remaining);
            ItemStack toGive = createOrderStack(order, chunkSize);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(toGive);

            int notMoved = 0;
            for (ItemStack leftover : leftovers.values()) {
                if (leftover != null) {
                    notMoved += leftover.getAmount();
                }
            }

            int moved = Math.max(0, chunkSize - notMoved);
            if (moved <= 0) {
                break;
            }

            movedTotal += moved;
            remaining -= moved;
        }
        return movedTotal;
    }

    int calculateClaimSessionPageCount(List<Integer> claimSessionStacks) {
        int pageCapacity = claimPageCapacity();
        if (claimSessionStacks.isEmpty() || pageCapacity <= 0) {
            return 1;
        }
        return Math.max(1, (int) Math.ceil(claimSessionStacks.size() / (double) pageCapacity));
    }

    private int claimPageCapacity() {
        return manager.guis().contentSlots("claim-order", "content-slots").size();
    }

    List<Integer> getClaimSessionPageStackAmounts(List<Integer> claimSessionStacks, int page) {
        if (claimSessionStacks.isEmpty()) {
            return List.of();
        }

        int safePage = Math.max(1, page);
        int pageCapacity = claimPageCapacity();
        if (pageCapacity <= 0) {
            return List.of();
        }
        int startIndex = (safePage - 1) * pageCapacity;
        if (startIndex >= claimSessionStacks.size()) {
            return List.of();
        }

        int endIndex = Math.min(claimSessionStacks.size(), startIndex + pageCapacity);
        return new ArrayList<>(claimSessionStacks.subList(startIndex, endIndex));
    }

    List<Integer> initializeClaimSessionStacks(MenuViewState viewState, PlayerDataStore.OrderEntry order, boolean forceReset) {
        String orderId = order.getOrderId();
        if (forceReset
            || viewState.claimSessionStacks == null
            || viewState.claimSessionOrderId == null
            || !viewState.claimSessionOrderId.equals(orderId)) {
            viewState.claimSessionOrderId = orderId;
            int maxStackSize = Math.max(1, createOrderStack(order, 1).getMaxStackSize());
            viewState.claimSessionStacks = buildClaimSessionStacks(order.getAmountClaimable(), maxStackSize);
        }
        return viewState.claimSessionStacks;
    }

    List<Integer> buildClaimSessionStacks(int claimableAmount, int maxStackSize) {
        if (claimableAmount <= 0) {
            return new ArrayList<>();
        }
        List<Integer> stacks = new ArrayList<>();
        int remaining = claimableAmount;
        int normalizedMaxStack = Math.max(1, maxStackSize);
        while (remaining > 0) {
            int stackAmount = Math.min(normalizedMaxStack, remaining);
            stacks.add(stackAmount);
            remaining -= stackAmount;
        }
        return stacks;
    }

    void clearClaimSession(MenuViewState viewState) {
        viewState.claimSessionOrderId = null;
        viewState.claimSessionStacks = null;
    }

    boolean finishClaimSession(UUID playerId, MenuViewState viewState) {
        String orderId = viewState.claimSessionOrderId;
        if (!tryAcquireOrderOperation(orderId)) {
            viewState.manageOrderIndex = -1;
            viewState.manageOrderId = null;
            clearClaimSession(viewState);
            return false;
        }

        try {
            PlayerDataStore.PlayerData playerData = playerDataStore.getOrCreate(playerId);
            if (resolveSelectedOrder(playerData, viewState, orderId) == null) {
                return false;
            }
            boolean removed = removeOrderIfCompleted(playerData, viewState);
            playerDataStore.save(playerId);
            return removed;
        } finally {
            releaseOrderOperation(orderId);
        }
    }

    PlayerDataStore.OrderEntry getSelectedOrder(PlayerDataStore.PlayerData playerData, MenuViewState viewState) {
        if (viewState.manageOrderId != null) {
            return resolveSelectedOrder(playerData, viewState, viewState.manageOrderId);
        }

        int selectedIndex = viewState.manageOrderIndex;
        if (selectedIndex < 0 || selectedIndex >= playerData.getOrders().size()) {
            viewState.manageOrderIndex = -1;
            viewState.manageOrderId = null;
            clearClaimSession(viewState);
            return null;
        }
        PlayerDataStore.OrderEntry selectedOrder = playerData.getOrders().get(selectedIndex);
        viewState.manageOrderId = selectedOrder.getOrderId();
        return selectedOrder;
    }

    boolean removeOrderIfCompleted(PlayerDataStore.PlayerData playerData, MenuViewState viewState) {
        int selectedIndex = viewState.manageOrderIndex;
        if (selectedIndex < 0 || selectedIndex >= playerData.getOrders().size()) {
            viewState.manageOrderIndex = -1;
            viewState.manageOrderId = null;
            clearClaimSession(viewState);
            return false;
        }

        PlayerDataStore.OrderEntry order = playerData.getOrders().get(selectedIndex);
        boolean hasNoClaimable = order.getAmountClaimable() <= 0;
        boolean completedNormally = order.getAmountDelivered() >= order.getAmountOrdered() && hasNoClaimable;
        boolean completedAfterCancel = order.isCancelled() && hasNoClaimable;
        if (!completedNormally && !completedAfterCancel) {
            return false;
        }

        playerData.getOrders().remove(selectedIndex);
        viewState.manageOrderIndex = -1;
        viewState.manageOrderId = null;
        viewState.claimPage = 1;
        clearClaimSession(viewState);
        return true;
    }

    boolean removeCompletedOrders(PlayerDataStore.PlayerData playerData) {
        int originalSize = playerData.getOrders().size();
        playerData.getOrders().removeIf(order -> {
            boolean noClaimable = order.getAmountClaimable() <= 0;
            boolean completedNormally = order.getAmountDelivered() >= order.getAmountOrdered() && noClaimable;
            boolean completedAfterCancel = order.isCancelled() && noClaimable;
            return completedNormally || completedAfterCancel;
        });
        return playerData.getOrders().size() != originalSize;
    }

    PlayerDataStore.OrderEntry findOrderById(PlayerDataStore.PlayerData playerData, String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        for (PlayerDataStore.OrderEntry order : playerData.getOrders()) {
            if (orderId.equals(order.getOrderId())) {
                return order;
            }
        }
        return null;
    }

    int findOrderIndexById(PlayerDataStore.PlayerData playerData, String orderId) {
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

    ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    boolean isInventoryEmpty(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    void returnPendingItems(Player player, ItemStack[] submittedItems) {
        if (submittedItems == null || submittedItems.length == 0) {
            return;
        }
        returnItems(player, submittedItems);
    }

    void returnItems(Player player, ItemStack[] items) {
        if (items == null || items.length == 0) {
            return;
        }
        inventoryDepositService.deposit(player, Arrays.asList(items), OverflowPolicy.DROP_OVERFLOW, false);
    }

    void dropClaimStacksWithDelay(Player player, List<ItemStack> stacksToDrop) {
        if (stacksToDrop.isEmpty()) {
            return;
        }

        for (int i = 0; i < stacksToDrop.size(); i++) {
            ItemStack stack = stacksToDrop.get(i).clone();
            scheduler.runLaterForPlayer(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                org.bukkit.util.Vector direction = player.getEyeLocation().getDirection().normalize();
                Location spawnLocation = player.getEyeLocation().clone().add(direction.clone().multiply(0.4));
                var dropped = player.getWorld().dropItem(spawnLocation, stack);
                dropped.setVelocity(direction.multiply(0.35).add(new org.bukkit.util.Vector(0, 0.1, 0)));
            }, i);
        }
    }

    List<String> createManageClaimLore(PlayerDataStore.OrderEntry order) {
        int claimableAmount = Math.max(0, order.getAmountClaimable());
        int maxLoreLines = manager.guis().previewLimit("items.manage-order.claim.max-stack-lines");
        if (claimableAmount <= 0 || maxLoreLines <= 0) {
            return List.of();
        }

        int maxStackSize = Math.max(1, createOrderStack(order, 1).getMaxStackSize());
        List<Integer> stacks = buildClaimSessionStacks(claimableAmount, maxStackSize);
        String itemName = formatOrderDisplayName(order);
        return claimPreviewLines(stacks, maxLoreLines,
            amount -> manager.guis().configuredText("items.manage-order.claim.stack-line",
                TextFormat.placeholders("amount", formatCompactAmount(amount), "item", itemName)),
            count -> manager.guis().configuredText("items.manage-order.claim.more-line",
                TextFormat.placeholders("count", formatCompactAmount(count))));
    }

    static List<String> claimPreviewLines(List<Integer> stacks, int maxLines,
                                          java.util.function.IntFunction<String> stackLine,
                                          java.util.function.IntFunction<String> moreLine) {
        if (stacks == null || stacks.isEmpty() || maxLines <= 0) {
            return List.of();
        }
        List<String> details = new ArrayList<>();
        for (int stack : stacks) {
            String line = stackLine.apply(stack);
            if (line != null && !line.isBlank()) {
                details.add(line);
            }
        }
        int shown = Math.min(details.size(), maxLines);
        int hidden = stacks.size() - shown;
        String summary = hidden > 0 ? moreLine.apply(hidden) : "";
        if (summary != null && !summary.isBlank() && shown == maxLines) {
            shown--;
            hidden++;
            summary = moreLine.apply(hidden);
        }
        List<String> lore = new ArrayList<>(details.subList(0, shown));
        if (summary != null && !summary.isBlank()) {
            lore.add(summary);
        }
        return lore;
    }
}
