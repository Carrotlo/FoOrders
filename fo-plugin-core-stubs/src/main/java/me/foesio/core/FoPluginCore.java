package me.foesio.core;

import org.bukkit.plugin.java.JavaPlugin;

public final class FoPluginCore {
    private FoPluginCore() {}

    public static FoCoreContext create(JavaPlugin plugin) {
        return new FoCoreContext(plugin);
    }
}
