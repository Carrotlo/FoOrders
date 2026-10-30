package me.foesio.foOrders.api;

import org.bukkit.inventory.ItemStack;

/** Template is copied on construction and access. */
public record CustomItemSnapshot(String id, ItemStack template, boolean allowOrderEnchants,
                                 long createdAtEpochMillis, String registeredBy) {
    public CustomItemSnapshot {
        template = template.clone();
    }

    @Override
    public ItemStack template() {
        return template.clone();
    }
}
