package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after admin deletion and any required refund. */
public final class OrderDeletedByAdminEvent extends OrderLifecycleEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final double refund;

    public OrderDeletedByAdminEvent(OrderSnapshot order, UUID adminId, double refund) {
        super(order, adminId);
        this.refund = refund;
    }

    public double getRefund() { return refund; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
