package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after claimable count decreases; dropped items may be spawned on a later tick. */
public final class OrderItemsClaimedEvent extends OrderLifecycleEvent {
    public enum Method { INVENTORY, DROP }
    private static final HandlerList HANDLERS = new HandlerList();
    private final int amount;
    private final Method method;

    public OrderItemsClaimedEvent(OrderSnapshot order, UUID ownerId, int amount, Method method) {
        super(order, ownerId);
        this.amount = amount;
        this.method = method;
    }

    public int getAmount() { return amount; }
    public Method getMethod() { return method; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
