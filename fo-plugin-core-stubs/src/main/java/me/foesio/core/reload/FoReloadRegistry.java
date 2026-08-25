package me.foesio.core.reload;

import org.bukkit.plugin.java.JavaPlugin;

public class FoReloadRegistry {
    private FoReloadRegistry() {}

    public static FoReloadRegistry create() {
        return new FoReloadRegistry();
    }

    public FoReloadRegistry addConfig(JavaPlugin plugin) {
        return this;
    }

    public FoReloadRegistry add(String name, Runnable step) {
        return this;
    }

    public FoReloadResult reload() {
        return new FoReloadResult();
    }
}
