package me.foesio.foOrders;

import me.foesio.foOrders.api.OrderSnapshot;
import me.foesio.foOrders.storage.PlayerDataStore;
import org.bukkit.Material;

import java.util.UUID;

final class OrderSnapshots {
    private OrderSnapshots() {
    }

    static OrderSnapshot of(UUID ownerId, PlayerDataStore.OrderEntry order) {
        Material material = Material.matchMaterial(order.getMaterial());
        return new OrderSnapshot(ownerId, order.getOrderId(), order.getCustomItemId(),
            material == null ? Material.STONE : material, order.getEnchantments(),
            order.getAmountOrdered(), order.getAmountDelivered(), order.getAmountClaimable(),
            order.getPricePerItem(), order.getAmountPaid(), order.isCancelled(),
            order.getCreatedAtEpochMillis(), order.getNpcName());
    }
}
