package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired when an order leaves the live order list; history retention is independent. */
public final class OrderClosedEvent extends OrderLifecycleEvent {
    public enum Reason { FILLED_AND_CLAIMED, CANCELLED, ADMIN_DELETED, NPC_FILLED }
    private static final HandlerList HANDLERS = new HandlerList();
    private final Reason reason;

    public OrderClosedEvent(OrderSnapshot order, UUID actorId, Reason reason) {
        super(order, actorId);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
