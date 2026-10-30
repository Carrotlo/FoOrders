package me.foesio.foOrders.api.event;

import me.foesio.foOrders.api.OrderQuote;
import me.foesio.foOrders.api.OrderRequest;
import org.bukkit.Bukkit;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/** Fired after validation and before player withdrawal or NPC insertion. Cancelling leaves no order or charge. */
public final class OrderBeforeCreateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID ownerId;
    private final OrderRequest request;
    private final OrderQuote quote;
    private final String npcName;
    private boolean cancelled;

    public OrderBeforeCreateEvent(UUID ownerId, OrderRequest request, OrderQuote quote) {
        this(ownerId, request, quote, null);
    }

    public OrderBeforeCreateEvent(UUID ownerId, OrderRequest request, OrderQuote quote, String npcName) {
        super(!Bukkit.isPrimaryThread());
        this.ownerId = ownerId;
        this.request = request;
        this.quote = quote;
        this.npcName = npcName;
    }

    public UUID getOwnerId() { return ownerId; }
    public OrderRequest getRequest() { return request; }
    public OrderQuote getQuote() { return quote; }
    /** Non-null for a virtual NPC order. */
    public String getNpcName() { return npcName; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
