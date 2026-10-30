package me.foesio.foOrders.api;

import org.bukkit.Material;

import java.util.Map;
import java.util.UUID;

/** Immutable snapshot captured from an order; closed orders are absent from live queries. */
public record OrderSnapshot(UUID ownerId, String orderId, String customItemId, Material material,
                            Map<String, Integer> enchantments, int amountOrdered, int amountDelivered,
                            int amountClaimable, double pricePerItem, double amountPaid,
                            boolean cancelled, long createdAtEpochMillis, String npcName) {
    public OrderSnapshot {
        enchantments = Map.copyOf(enchantments);
    }

    /** Preserves the original constructor for existing integrations. */
    public OrderSnapshot(UUID ownerId, String orderId, String customItemId, Material material,
                         Map<String, Integer> enchantments, int amountOrdered, int amountDelivered,
                         int amountClaimable, double pricePerItem, double amountPaid,
                         boolean cancelled, long createdAtEpochMillis) {
        this(ownerId, orderId, customItemId, material, enchantments, amountOrdered, amountDelivered,
            amountClaimable, pricePerItem, amountPaid, cancelled, createdAtEpochMillis, null);
    }

    public boolean npcOrder() {
        return npcName != null;
    }

    public int amountRemaining() {
        return Math.max(0, amountOrdered - amountDelivered);
    }

    public boolean acceptsDeliveries() {
        return !cancelled && amountRemaining() > 0;
    }
}
