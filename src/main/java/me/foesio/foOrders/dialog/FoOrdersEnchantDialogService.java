package me.foesio.foOrders.dialog;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

interface FoOrdersEnchantDialogService {
    boolean open(Player player, EnchantDialogRequest request, Consumer<EnchantDialogAction> onAction);
}
