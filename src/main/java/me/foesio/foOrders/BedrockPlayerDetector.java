package me.foesio.foOrders;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.UUID;

final class BedrockPlayerDetector {
    private static final PlayerChecker NO_PLAYERS = playerId -> false;

    private final PluginManager pluginManager;
    private volatile PlayerChecker checker;

    BedrockPlayerDetector(FoOrders plugin) {
        this.pluginManager = plugin.getServer().getPluginManager();
    }

    boolean isBedrockPlayer(Player player) {
        return player != null && checker().isBedrockPlayer(player.getUniqueId());
    }

    private PlayerChecker checker() {
        PlayerChecker currentChecker = checker;
        if (currentChecker != null) {
            return currentChecker;
        }

        synchronized (this) {
            if (checker == null) {
                checker = resolveChecker();
            }
            return checker;
        }
    }

    private PlayerChecker resolveChecker() {
        PlayerChecker floodgateChecker = floodgateChecker();
        return floodgateChecker != null ? floodgateChecker : geyserChecker();
    }

    private PlayerChecker floodgateChecker() {
        Plugin floodgate = findEnabledPlugin("floodgate");
        if (floodgate == null) {
            return null;
        }

        try {
            Class<?> apiClass = Class.forName(
                "org.geysermc.floodgate.api.FloodgateApi",
                true,
                floodgate.getClass().getClassLoader()
            );
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return playerId -> invokePlayerCheck(api, isFloodgatePlayer, playerId);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private PlayerChecker geyserChecker() {
        Plugin geyser = findEnabledPlugin("Geyser-Spigot", "Geyser");
        if (geyser == null) {
            return NO_PLAYERS;
        }

        try {
            Class<?> apiClass = Class.forName(
                "org.geysermc.geyser.api.GeyserApi",
                true,
                geyser.getClass().getClassLoader()
            );
            Object api = apiClass.getMethod("api").invoke(null);
            Method isBedrockPlayer = apiClass.getMethod("isBedrockPlayer", UUID.class);
            return playerId -> invokePlayerCheck(api, isBedrockPlayer, playerId);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return NO_PLAYERS;
        }
    }

    private Plugin findEnabledPlugin(String... names) {
        for (Plugin candidate : pluginManager.getPlugins()) {
            if (!candidate.isEnabled()) {
                continue;
            }
            for (String name : names) {
                if (name.equalsIgnoreCase(candidate.getName())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean invokePlayerCheck(Object api, Method method, UUID playerId) {
        if (api == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(api, playerId));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    @FunctionalInterface
    private interface PlayerChecker {
        boolean isBedrockPlayer(UUID playerId);
    }
}
