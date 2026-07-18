package me.foesio.foOrders;

import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

interface FoOrdersItemSelectionDialogService {
    boolean open(Player player, String currentChoiceKey, Consumer<OrderableItemOption> onSelect, Runnable onFallback);

    void clearCache();

    void clearPending(UUID playerId);
}
