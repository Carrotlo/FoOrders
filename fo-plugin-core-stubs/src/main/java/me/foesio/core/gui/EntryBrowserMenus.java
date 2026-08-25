package me.foesio.core.gui;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public final class EntryBrowserMenus {
    private EntryBrowserMenus() {}

    public static int maxPage(EntryBrowserRequest request) {
        return 0;
    }

    public static Inventory createInventory(EntryBrowserRequest request) {
        return null;
    }

    public static EntryBrowserClick handleClick(int rawSlot, EntryBrowserHolder holder, ClickType clickType) {
        return new EntryBrowserClick();
    }
}
