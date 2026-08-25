package me.foesio.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class EntryBrowserHolder implements InventoryHolder {
    public EntryBrowserRequest request() {
        return null;
    }

    public int maxPage() {
        return 0;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
