package me.foesio.core.gui;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public class GuiButtonConfig {
    GuiButtonConfig() {}

    public static GuiButtonConfig defaults() {
        return new GuiButtonConfig();
    }

    public static GuiButtonConfig fromGuiFile(YamlConfiguration yaml) {
        return new GuiButtonConfig();
    }

    public ItemStack search(String searchText) {
        return null;
    }

    public ItemStack previousPage(int currentPage, int maxPage) {
        return null;
    }

    public ItemStack nextPage(int currentPage, int maxPage) {
        return null;
    }

    public ItemStack back() {
        return null;
    }
}
