package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after matching items are accepted, payout succeeds, and order progress changes. */
public final class OrderDeliveryAcceptedEvent extends OrderLifecycleEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final int acceptedAmount;
    private final double payout;

    public OrderDeliveryAcceptedEvent(OrderSnapshot order, UUID delivererId, int acceptedAmount, double payout) {
        super(order, delivererId);
        this.acceptedAmount = acceptedAmount;
        this.payout = payout;
    }

    public int getAcceptedAmount() { return acceptedAmount; }
    public double getPayout() { return payout; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
