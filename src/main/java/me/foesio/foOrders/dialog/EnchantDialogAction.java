package me.foesio.foOrders.dialog;

public record EnchantDialogAction(Type type, String enchantmentKey, int level) {
    public static EnchantDialogAction backToItems() {
        return new EnchantDialogAction(Type.BACK_TO_ITEMS, "", 0);
    }

    public static EnchantDialogAction continueOrder() {
        return new EnchantDialogAction(Type.CONTINUE_ORDER, "", 0);
    }

    public static EnchantDialogAction setLevel(String enchantmentKey, int level) {
        return new EnchantDialogAction(Type.SET_LEVEL, enchantmentKey, level);
    }

    public enum Type {
        BACK_TO_ITEMS,
        CONTINUE_ORDER,
        SET_LEVEL
    }
}
