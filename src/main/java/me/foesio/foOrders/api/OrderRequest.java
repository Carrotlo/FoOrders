package me.foesio.foOrders.api;

import org.bukkit.Material;

import java.util.Map;

/** For a catalog item, set customItemId; otherwise set material. */
public record OrderRequest(String customItemId, Material material, Map<String, Integer> enchantments,
                           int amount, double pricePerItem) {
    public OrderRequest {
        customItemId = customItemId == null || customItemId.isBlank() ? null : customItemId.trim();
        enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
    }
}
