package me.foesio.foOrders.command;

import me.foesio.foOrders.OrdersMenuManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class OrderCommand implements CommandExecutor, TabCompleter {
    private final OrdersMenuManager ordersMenuManager;

    public OrderCommand(OrdersMenuManager ordersMenuManager) {
        this.ordersMenuManager = ordersMenuManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            ordersMenuManager.messages().send(sender, "general.only-players");
            return true;
        }

        String search = args.length == 0 ? "" : String.join(" ", args);
        ordersMenuManager.openOrdersMenuFromCommand(player, search);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
