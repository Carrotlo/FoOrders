package me.foesio.foOrders.dialog;

import org.bukkit.entity.Player;

import java.util.function.Consumer;

interface FoOrdersTextDialogService {
    boolean open(Player player, DialogTextPresentation presentation,
                 Consumer<String> onSubmit, Runnable onCancel);
}
