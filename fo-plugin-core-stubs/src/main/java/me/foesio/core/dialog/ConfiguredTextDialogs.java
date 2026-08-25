package me.foesio.core.dialog;

import org.bukkit.plugin.Plugin;

import java.util.Map;

public class ConfiguredTextDialogs {
    private ConfiguredTextDialogs() {}

    public static ConfiguredTextDialogs create(Plugin plugin) {
        return new ConfiguredTextDialogs();
    }

    public ConfiguredTextDialogs register(String id, TextDialogRequest fallback) {
        return this;
    }

    public void reload() {}

    public TextDialogRequest request(String id, TextDialogRequest fallback, Map<String, String> placeholders) {
        return fallback;
    }
}
