package me.foesio.foOrders.dialog;

public record EnchantDialogRow(
    String enchantmentKey,
    String name,
    int maxLevel,
    int selectedLevel,
    boolean cursed
) {
}
