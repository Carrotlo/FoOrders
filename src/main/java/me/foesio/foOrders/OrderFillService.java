package me.foesio.foOrders;

import me.foesio.foOrders.api.FoOrdersOrderFillApi;
import me.foesio.foOrders.storage.PlayerDataStore;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Serves {@link FoOrdersOrderFillApi} for plugins that want to push items into
 * open orders themselves - a sell shop routing a player's stack into the order
 * that pays best for it, for instance.
 *
 * <p>The bookkeeping deliberately mirrors
 * {@code OrdersMenuDeliverySupport#processDeliveryConfirmation}: pay first,
 * then record the delivery, so a failed payout never eats an order's stock.
 * Items are left in the caller's hands; only the order's counters, history and
 * notifications are FoOrders' business here.
 */
final class OrderFillService implements FoOrdersOrderFillApi {

    private final OrdersMenuManager manager;

    OrderFillService(OrdersMenuManager manager) {
        this.manager = manager;
    }

    @Override
    public int apiVersion() {
        return API_VERSION;
    }

    @Override
    public long openOrderRevision() {
        return manager.playerDataStore.ordersRevision();
    }

    @Override
    public double[] openOrderQuotes(Player seller, ItemStack sample, double minPricePerItem, boolean includeOwnOrders) {
        List<Candidate> candidates = findCandidates(seller, sample, minPricePerItem, includeOwnOrders);
        double[] quotes = new double[1 + candidates.size() * 2];
        quotes[0] = candidates.size();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            quotes[1 + i * 2] = candidate.order.getPricePerItem();
            quotes[2 + i * 2] = candidate.unitsStillNeeded;
        }
        return quotes;
    }

    @Override
    public double[] fillOrders(Player seller, ItemStack sample, int amount, double minPricePerItem, boolean includeOwnOrders) {
        if (amount <= 0) {
            return new double[]{0D, 0D, 0D};
        }

        List<Candidate> candidates = findCandidates(seller, sample, minPricePerItem, includeOwnOrders);
        if (candidates.isEmpty()) {
            return new double[]{0D, 0D, 0D};
        }

        OrdersMenuDeliverySupport delivery = manager.interactionSupport.deliverySupport;
        if (!delivery.hasEconomyProvider()) {
            return new double[]{0D, 0D, 0D};
        }

        int unitsLeft = amount;
        int unitsAccepted = 0;
        double totalPayout = 0D;
        int ordersCompleted = 0;

        for (Candidate candidate : candidates) {
            if (unitsLeft <= 0) {
                break;
            }

            String orderId = candidate.order.getOrderId();
            // Somebody is mid-delivery on this order; leave it to them rather
            // than racing their counters.
            if (!manager.activeOrderOperationLocks.add(orderId)) {
                continue;
            }
            try {
                FillOutcome outcome;
                try {
                    outcome = fillSingleOrder(seller, sample, candidate.ownerId, orderId, unitsLeft, minPricePerItem, includeOwnOrders);
                } catch (RuntimeException exception) {
                    // The caller only takes items away for what this method
                    // returns. Throwing now would hide payouts already made to
                    // earlier orders in this loop, leaving the seller paid for
                    // items they still hold - so stop here and report those.
                    manager.plugin.getLogger().log(Level.SEVERE, "Filling order " + orderId + " failed; stopping this sale.", exception);
                    break;
                }
                if (outcome == null) {
                    continue;
                }
                unitsLeft -= outcome.accepted;
                unitsAccepted += outcome.accepted;
                totalPayout += outcome.payout;
                if (outcome.completed) {
                    ordersCompleted++;
                }
                if (outcome.economyFailed) {
                    break;
                }
            } finally {
                manager.activeOrderOperationLocks.remove(orderId);
            }
        }

        return new double[]{unitsAccepted, totalPayout, ordersCompleted};
    }

    private FillOutcome fillSingleOrder(
        Player seller,
        ItemStack sample,
        UUID ownerId,
        String orderId,
        int unitsLeft,
        double minPricePerItem,
        boolean includeOwnOrders
    ) {
        OrdersMenuDeliverySupport delivery = manager.interactionSupport.deliverySupport;
        PlayerDataStore.PlayerData ownerData = manager.playerDataStore.getOrCreate(ownerId);
        PlayerDataStore.OrderEntry liveOrder = delivery.findOrderById(ownerData, orderId);

        // Re-check everything against the live order: the snapshot the candidate
        // list came from can be a few ticks old.
        if (liveOrder == null || liveOrder.isCancelled()) {
            return null;
        }
        if (!includeOwnOrders && ownerId.equals(seller.getUniqueId())) {
            return null;
        }
        if (liveOrder.getPricePerItem() < minPricePerItem) {
            return null;
        }
        if (!manager.itemSupport.matchesOrderedItem(sample, liveOrder)) {
            return null;
        }

        int remainingNeeded = Math.max(0, liveOrder.getAmountOrdered() - liveOrder.getAmountDelivered());
        int accepted = Math.min(unitsLeft, remainingNeeded);
        if (accepted <= 0) {
            return null;
        }

        double payout = accepted * liveOrder.getPricePerItem();
        if (payout > 0D) {
            EconomyResponse response = delivery.depositEconomy(seller, payout);
            if (response == null || !response.transactionSuccess()) {
                return new FillOutcome(0, 0D, false, true);
            }
        }

        liveOrder.setAmountDelivered(liveOrder.getAmountDelivered() + accepted);
        liveOrder.setAmountClaimable(liveOrder.getAmountClaimable() + accepted);
        liveOrder.setAmountPaid(liveOrder.getAmountPaid() + payout);
        manager.playerDataStore.saveUrgent(ownerId);

        boolean completed = liveOrder.getAmountDelivered() >= liveOrder.getAmountOrdered();
        try {
            announceFill(seller, ownerId, liveOrder, accepted, payout, completed);
        } catch (RuntimeException exception) {
            // The fill itself is done and paid for; a failed history line or
            // message must not make it look as if it never happened.
            manager.plugin.getLogger().log(Level.WARNING, "Could not announce a fill of order " + orderId + ".", exception);
        }
        return new FillOutcome(accepted, payout, completed, false);
    }

    private void announceFill(
        Player seller,
        UUID ownerId,
        PlayerDataStore.OrderEntry order,
        int accepted,
        double payout,
        boolean completed
    ) {
        OrdersMenuDeliverySupport delivery = manager.interactionSupport.deliverySupport;
        String ownerName = manager.itemSupport.resolvePlayerName(ownerId);
        String itemName = manager.itemSupport.formatOrderDisplayName(order);
        String amountText = manager.itemSupport.formatCompactAmount(accepted);
        String payoutText = manager.itemSupport.formatCompactAmount(payout);

        delivery.appendOrderHistory(
            ownerId,
            "Order Delivered",
            seller.getName() + " delivered " + amountText + "x " + itemName
        );
        delivery.appendDeliverHistory(
            seller.getUniqueId(),
            "Delivery Completed",
            "Delivered " + amountText + "x " + itemName
                + " to " + ownerName + " and earned $" + payoutText
        );
        manager.fileLogger.info(
            "Player " + seller.getName() + " sold " + amountText + "x " + itemName
                + " into order " + order.getOrderId()
                + " and earned $" + payoutText + "."
        );
        delivery.sendOrderDeliveredWebhook(seller, ownerName, order, accepted, payout);

        Player owner = Bukkit.getPlayer(ownerId);
        if (owner == null || !owner.isOnline()) {
            return;
        }
        manager.messages().send(owner, "orders.owner-delivered", PluginMessages.placeholders(
            "player", seller.getName(),
            "amount", amountText,
            "item", itemName
        ));
        if (completed) {
            manager.messages().send(owner, "orders.owner-filled", PluginMessages.placeholders("item", itemName));
        }
    }

    /**
     * Open orders {@code sample} satisfies, best paying first so a caller that
     * only offers part of a stack gets the most money for it.
     */
    private List<Candidate> findCandidates(Player seller, ItemStack sample, double minPricePerItem, boolean includeOwnOrders) {
        List<Candidate> candidates = new ArrayList<>();
        if (seller == null || sample == null || sample.getType() == Material.AIR) {
            return candidates;
        }

        UUID sellerId = seller.getUniqueId();
        for (PlayerDataStore.PlayerOrderRecord record : manager.playerDataStore.getAllOrdersSnapshot()) {
            PlayerDataStore.OrderEntry order = record.getOrder();
            if (order.isCancelled()) {
                continue;
            }
            if (!includeOwnOrders && sellerId.equals(record.getPlayerId())) {
                continue;
            }
            if (order.getPricePerItem() < minPricePerItem) {
                continue;
            }
            int stillNeeded = Math.max(0, order.getAmountOrdered() - order.getAmountDelivered());
            if (stillNeeded <= 0) {
                continue;
            }
            if (!manager.itemSupport.matchesOrderedItem(sample, order)) {
                continue;
            }
            candidates.add(new Candidate(record.getPlayerId(), order, stillNeeded));
        }

        candidates.sort(
            Comparator.comparingDouble((Candidate candidate) -> candidate.order.getPricePerItem()).reversed()
                // Oldest first among equally paying orders, so a queue of
                // identical orders drains in the order it formed.
                .thenComparingLong(candidate -> candidate.order.getCreatedAtEpochMillis())
        );
        return candidates;
    }

    private record Candidate(UUID ownerId, PlayerDataStore.OrderEntry order, int unitsStillNeeded) {
    }

    private record FillOutcome(int accepted, double payout, boolean completed, boolean economyFailed) {
    }
}
