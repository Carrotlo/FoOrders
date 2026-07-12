package me.foesio.foOrders.dialog;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record EnchantDialogRequest(
    Material material,
    ItemStack displayItem,
    String itemName,
    List<EnchantDialogRow> rows,
    int selectedCount
) {
}
