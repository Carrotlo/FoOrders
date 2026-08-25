package me.foesio.core.dialog;

import org.bukkit.configuration.ConfigurationSection;

public class NativeDialogSettings {
    NativeDialogSettings() {}

    public static NativeDialogSettings fromConfig(ConfigurationSection section) {
        return new NativeDialogSettings();
    }
}
