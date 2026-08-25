package me.foesio.core.inventory;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InventoryCloseSuppressor {
    private final Set<UUID> suppressed = ConcurrentHashMap.newKeySet();

    public void suppressNextClose(Player player) {
        if (player != null) {
            suppressed.add(player.getUniqueId());
        }
    }

    public boolean consumeSuppressedClose(Player player) {
        if (player == null) {
            return false;
        }
        return suppressed.remove(player.getUniqueId());
    }

    public void clear(Player player) {
        if (player != null) {
            suppressed.remove(player.getUniqueId());
        }
    }
}
