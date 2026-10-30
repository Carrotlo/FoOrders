package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

import java.util.UUID;

/** Base payload for completed in-memory transitions. Database writes may still be queued. */
public abstract class OrderLifecycleEvent extends Event {
    private final OrderSnapshot order;
    private final UUID actorId;

    protected OrderLifecycleEvent(OrderSnapshot order, UUID actorId) {
        super(!Bukkit.isPrimaryThread());
        this.order = order;
        this.actorId = actorId;
    }

    public final OrderSnapshot getOrder() { return order; }
    /** Null for NPC creation or cleanup without a player actor. */
    public final UUID getActorId() { return actorId; }
}
