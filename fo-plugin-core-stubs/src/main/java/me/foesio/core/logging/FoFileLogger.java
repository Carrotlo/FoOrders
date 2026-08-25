package me.foesio.core.logging;

import org.bukkit.plugin.Plugin;

public class FoFileLogger {
    private FoFileLogger() {}

    public static FoFileLogger create(Plugin plugin) {
        return new FoFileLogger();
    }

    public void configureFromConfig(String path, boolean defaultValue) {}

    public void info(String message) {}

    public void warn(String message) {}

    public void error(String message, Throwable throwable) {}

    public void close() {}
}
