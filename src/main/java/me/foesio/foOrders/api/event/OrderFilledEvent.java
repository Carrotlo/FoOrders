package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired once when accepted delivery first fills the ordered amount. Items may remain unclaimed. */
public final class OrderFilledEvent extends OrderLifecycleEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public OrderFilledEvent(OrderSnapshot order, UUID delivererId) { super(order, delivererId); }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
