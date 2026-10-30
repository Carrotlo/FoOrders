package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after a successful refund, if due, and cancellation. */
public final class OrderCancelledEvent extends OrderLifecycleEvent {
    public enum Cause { OWNER, ADMIN }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Cause cause;
    private final double refund;

    public OrderCancelledEvent(OrderSnapshot order, UUID actorId, Cause cause, double refund) {
        super(order, actorId);
        this.cause = cause;
        this.refund = refund;
    }

    public Cause getCause() { return cause; }
    public double getRefund() { return refund; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
