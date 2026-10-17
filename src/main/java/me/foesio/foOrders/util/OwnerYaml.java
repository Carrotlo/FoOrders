package me.foesio.foOrders.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/** Never treat a malformed owner file as an empty configuration during migration or reload. */
public final class OwnerYaml {
    private OwnerYaml() {
    }

    public static YamlConfiguration load(File file) {
        Objects.requireNonNull(file, "file");
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
            return loaded;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not parse owner YAML " + file
                + "; original file was not rewritten.", exception);
        }
    }
}
