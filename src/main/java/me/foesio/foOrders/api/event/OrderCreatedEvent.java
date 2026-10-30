package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after insertion. Player orders have a Vault charge; NPC orders report zero charge. */
public final class OrderCreatedEvent extends OrderLifecycleEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final double tax;
    private final double chargedTotal;

    public OrderCreatedEvent(OrderSnapshot order, UUID actorId, double tax, double chargedTotal) {
        super(order, actorId);
        this.tax = tax;
        this.chargedTotal = chargedTotal;
    }

    public double getTax() { return tax; }
    public double getChargedTotal() { return chargedTotal; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
