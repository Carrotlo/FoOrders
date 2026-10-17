package me.foesio.foOrders;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Exclusive close route for the physical-input delivery inventory. */
final class OrdersDeliveryGuiListener implements Listener {
    private final OrdersMenuManager manager;

    OrdersDeliveryGuiListener(OrdersMenuManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof OrdersMenuHolder holder
            && holder.getMenuType() == MenuType.DELIVER) {
            manager.interactionSupport.onInventoryClose(event);
        }
    }
}
