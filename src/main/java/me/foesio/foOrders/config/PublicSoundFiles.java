package me.foesio.foOrders.config;

import me.foesio.foOrders.util.OwnerUpgradeBackups;
import me.foesio.foOrders.util.DeferredPublicMigrations;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foOrders.util.OwnerYaml;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Keeps Core's sound loader from persisting or playing removed current-schema defaults. */
public final class PublicSoundFiles {
    private static final String RESOURCE = "sounds.yml";
    private static final int VARIATION_MIGRATION_VERSION = 16;

    private PublicSoundFiles() {
    }

    public static void migrateVariations(JavaPlugin plugin, FoMigrationStore migrations) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        migrations.runToVersion(VARIATION_MIGRATION_VERSION, () -> DeferredPublicMigrations.runOrDefer(
            plugin.getDataFolder(), VARIATION_MIGRATION_VERSION, java.util.List.of(),
            java.util.List.of(file), () -> migrateVariationsFile(plugin)
                || DeferredPublicMigrations.defer(plugin.getDataFolder(), VARIATION_MIGRATION_VERSION,
                    "sounds.yml cannot yet be migrated safely", plugin.getLogger()::warning),
            plugin.getLogger()::warning));
        if (migrations.version() < VARIATION_MIGRATION_VERSION) {
            throw new IllegalStateException("Could not migrate FoOrders sound pitch variation settings.");
        }
    }

    public static boolean retryDeferredVariation(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        return DeferredPublicMigrations.retry(plugin.getDataFolder(), VARIATION_MIGRATION_VERSION,
            java.util.List.of(), java.util.List.of(file), () -> migrateVariationsFile(plugin),
            plugin.getLogger()::warning);
    }

    private static boolean migrateVariationsFile(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        if (preserveMissingSoundFile(file, plugin.getLogger()::warning)) {
            return true;
        }
        YamlConfiguration defaults = bundled(plugin);
        YamlConfiguration active = OwnerYaml.load(file);
        String blocker = variationUpgradeBlocker(active, defaults);
        if (blocker != null) {
            plugin.getLogger().warning("Sound pitch variation migration deferred: " + blocker);
            return false;
        }
        boolean changed = migrateVariationFields(active, defaults);
        if (!changed) {
            return true;
        }
        File backup = OwnerUpgradeBackups.forFile(file, ".pre-v16-backup");
        byte[] original = null;
        try {
            original = Files.readAllBytes(file.toPath());
            if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            active.save(file);
            return true;
        } catch (IOException exception) {
            try {
                if (original != null) {
                    Files.write(file.toPath(), original);
                }
            } catch (IOException ignored) {
                // The backup remains available for a manual recovery.
            }
            plugin.getLogger().warning("Could not migrate sounds.yml pitch variation: " + exception.getMessage());
            return false;
        }
    }

    static boolean migrateVariationFields(YamlConfiguration active, YamlConfiguration defaults) {
        boolean changed = false;
        for (String path : definitionPaths(defaults)) {
            if (active.isString(path)) {
                String soundKey = active.getString(path, "");
                ConfigurationSection bundledDefinition = defaults.getConfigurationSection(path);
                active.set(path, null);
                ConfigurationSection migrated = active.createSection(path);
                migrated.set("enabled", true);
                migrated.set("sound", soundKey);
                for (String field : Set.of("category", "volume", "pitch")) {
                    migrated.set(field, bundledDefinition.get(field));
                }
                changed = true;
            }
            String variationPath = path + ".pitch-variation";
            if (defaults.isSet(variationPath) && completeDefinition(defaults, active, path)
                && !active.isSet(variationPath)) {
                active.set(variationPath, defaults.get(variationPath));
                changed = true;
            }
        }
        return changed;
    }

    /** Check a copy because version 38 can repair bundled fields later, while unknown invalid fields must wait. */
    static String variationUpgradeBlocker(YamlConfiguration active, YamlConfiguration defaults) {
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(active.saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            return "cannot stage sounds.yml for validation: " + exception.getMessage();
        }
        try {
            repairLegacyInvalidFields(candidate, defaults, ignored -> { });
            return null;
        } catch (IllegalStateException exception) {
            return exception.getMessage();
        }
    }

    private static boolean completeDefinition(YamlConfiguration defaults, YamlConfiguration active, String path) {
        ConfigurationSection bundled = defaults.getConfigurationSection(path);
        if (bundled == null || !active.isConfigurationSection(path)) {
            return false;
        }
        for (String field : bundled.getKeys(false)) {
            if (!field.equals("pitch-variation") && !active.isSet(path + "." + field)) {
                return false;
            }
        }
        return true;
    }

    static boolean preserveMissingSoundFile(File file, Consumer<String> warn) {
        if (file.isFile()) {
            return false;
        }
        // FoOrders 3.9-4.6 shipped sounds.yml. A missing file on an existing
        // server may be an owner deletion; migration state is insufficient to
        // distinguish that from a pre-sound install. Fresh installs are copied
        // explicitly by FoOrders.onEnable().
        warn.accept("Owner sounds.yml is absent; preserving that choice. "
            + "Copy the bundled file manually if you want the default sounds.");
        return true;
    }

    public static Snapshot capture(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        return capture(file, bundled(plugin), plugin.getLogger()::warning);
    }

    static Snapshot capture(File file, YamlConfiguration defaults, Consumer<String> warn) {
        try {
            byte[] original = file.isFile() ? Files.readAllBytes(file.toPath()) : null;
            YamlConfiguration active;
            try {
                active = file.isFile() ? OwnerYaml.load(file) : new YamlConfiguration();
            } catch (IllegalStateException malformed) {
                warn.accept("Unparseable owner sounds.yml; all plugin sounds are suppressed until the file is repaired: "
                    + malformed.getMessage());
                return new Snapshot(file.toPath(), original, definitionPaths(defaults), Set.of());
            }
            Set<String> suppress = suppressedDefinitions(defaults, active, warn);
            Set<String> missingVariation = new LinkedHashSet<>();
            for (String path : definitionPaths(defaults)) {
                String variationPath = path + ".pitch-variation";
                if (defaults.isSet(variationPath) && !active.isSet(variationPath)) {
                    missingVariation.add(variationPath);
                }
            }
            return new Snapshot(file.toPath(), original, suppress, missingVariation);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not snapshot owner sounds.yml before Core load", exception);
        }
    }

    static Set<String> missingDefinitions(YamlConfiguration defaults, YamlConfiguration active) {
        Set<String> missing = new LinkedHashSet<>();
        for (String path : definitionPaths(defaults)) {
            ConfigurationSection definition = defaults.getConfigurationSection(path);
            if (definition == null) {
                continue;
            }
            for (String field : definition.getKeys(false)) {
                if ("pitch-variation".equals(field)) {
                    continue;
                }
                if (!active.isSet(path + "." + field)) {
                    missing.add(path);
                    break;
                }
            }
        }
        return missing;
    }

    static Set<String> suppressedDefinitions(YamlConfiguration defaults, YamlConfiguration active,
                                             Consumer<String> warn) {
        Set<String> suppressed = missingDefinitions(defaults, active);
        suppressed.addAll(invalidDefinitions(defaults, active, warn));
        return suppressed;
    }

    static Set<String> invalidDefinitions(YamlConfiguration defaults, YamlConfiguration active,
                                          Consumer<String> warn) {
        Set<String> invalid = new LinkedHashSet<>();
        for (String path : definitionPaths(defaults)) {
            if (!active.isConfigurationSection(path)) {
                continue;
            }
            String reason = invalidDefinitionReason(active, path);
            if (reason != null) {
                invalid.add(path);
                warn.accept("Invalid owner sounds.yml:" + path + "." + reason
                    + "; suppressing this sound instead of using a Core default.");
            }
        }
        return invalid;
    }

    /** Reload must reject invalid owner fields before changing the live Core sound cache. */
    public static void validateReload(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        if (!file.isFile()) return;
        String problem = firstInvalidReloadField(bundled(plugin), OwnerYaml.load(file));
        if (problem != null) {
            throw new IllegalStateException("Invalid owner sounds.yml:" + problem
                + "; prior working sounds remain active.");
        }
    }

    static String firstInvalidReloadField(YamlConfiguration defaults, YamlConfiguration active) {
        for (String path : definitionPaths(defaults)) {
            if (!active.isSet(path)) continue;
            if (!active.isConfigurationSection(path)) {
                if (!(active.get(path) instanceof String)) return path;
                continue;
            }
            String reason = invalidDefinitionReason(active, path);
            if (reason != null) return path + "." + reason;
        }
        return firstCoreInvalidField(active, "");
    }

    /** Repair only invalid fields in a versioned public-interface upgrade. */
    public static boolean repairLegacyInvalidFields(YamlConfiguration active, YamlConfiguration defaults,
                                                    Consumer<String> warn) {
        boolean changed = false;
        for (String definition : definitionPaths(defaults)) {
            if (active.isSet(definition) && !active.isConfigurationSection(definition)) {
                Object old = active.get(definition);
                active.set(definition, null);
                ConfigurationSection replacement = active.createSection(definition);
                ConfigurationSection bundled = defaults.getConfigurationSection(definition);
                for (String field : bundled.getKeys(false)) {
                    replacement.set(field, bundled.get(field));
                }
                warn.accept("Invalid legacy sound " + definition + "=" + old
                    + "; using newest bundled default section");
                changed = true;
                continue;
            }
            if (!active.isConfigurationSection(definition)) continue;
            for (String field : new String[] {"enabled", "sound", "category", "volume", "pitch",
                "pitch-variation"}) {
                String path = definition + "." + field;
                if (!active.isSet(path)) continue; // An old owner removal stays removed.
                Object old = active.get(path);
                if (!invalidLegacyField(field, old)) continue;
                if (!defaults.isSet(path)) {
                    throw new IllegalStateException("No newest bundled sound default for invalid " + path);
                }
                Object fallback = defaults.get(path);
                active.set(path, fallback);
                warn.accept("Invalid legacy sound " + path + "=" + old
                    + "; using newest bundled default " + fallback);
                changed = true;
            }
        }
        String invalidCoreField = firstCoreInvalidField(active, "");
        if (invalidCoreField != null) {
            throw new IllegalStateException("No bundled default for invalid sound field "
                + invalidCoreField);
        }
        return changed;
    }

    /** Mirror Core's load-time checks so the schema version cannot advance past a rejected sound file. */
    private static String firstCoreInvalidField(ConfigurationSection parent, String prefix) {
        for (String key : parent.getKeys(false)) {
            ConfigurationSection section = parent.getConfigurationSection(key);
            if (section == null) continue;
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            boolean definition = false;
            for (String field : new String[] {"sound", "key", "enabled", "category", "volume", "pitch"}) {
                if (section.contains(field, false)) {
                    definition = true;
                    break;
                }
            }
            if (!definition) {
                String nested = firstCoreInvalidField(section, path);
                if (nested != null) return nested;
                continue;
            }
            for (String field : new String[] {"sound", "key"}) {
                if (section.contains(field, false) && !(section.get(field) instanceof String)) {
                    return path + "." + field;
                }
            }
            if (section.contains("enabled", false) && !(section.get("enabled") instanceof Boolean)) {
                return path + ".enabled";
            }
            if (section.contains("category", false)) {
                Object raw = section.get("category");
                if (!(raw instanceof String name)) return path + ".category";
                try {
                    SoundCategory.valueOf(name.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException invalid) {
                    return path + ".category";
                }
            }
            for (String field : new String[] {"volume", "pitch"}) {
                if (section.contains(field, false)
                    && invalidNumber(section.get(field), field.equals("pitch") ? 0.5D : 0D,
                        field.equals("pitch") ? 2D : Double.MAX_VALUE)) {
                    return path + "." + field;
                }
            }
        }
        return null;
    }

    private static boolean invalidLegacyField(String field, Object raw) {
        return switch (field) {
            case "enabled" -> !(raw instanceof Boolean);
            case "sound" -> !(raw instanceof String key) || key.isBlank();
            case "category" -> {
                if (!(raw instanceof String name)) yield true;
                try {
                    SoundCategory.valueOf(name.trim().toUpperCase(Locale.ROOT));
                    yield false;
                } catch (IllegalArgumentException invalid) {
                    yield true;
                }
            }
            case "volume" -> invalidNumber(raw, 0D, Float.MAX_VALUE);
            case "pitch" -> invalidNumber(raw, 0.5D, 2D);
            case "pitch-variation" -> invalidNumber(raw, 0D, 2D);
            default -> false;
        };
    }

    private static String invalidDefinitionReason(YamlConfiguration active, String path) {
        Object enabled = active.get(path + ".enabled");
        if (enabled != null && !(enabled instanceof Boolean)) {
            return "enabled";
        }
        Object sound = active.get(path + ".sound");
        if (sound != null && (!(sound instanceof String key) || key.isBlank())) {
            return "sound";
        }
        Object category = active.get(path + ".category");
        if (category != null) {
            if (!(category instanceof String name)) {
                return "category";
            }
            try {
                SoundCategory.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return "category";
            }
        }
        if (invalidNumber(active.get(path + ".volume"), 0D, Float.MAX_VALUE)) {
            return "volume";
        }
        if (invalidNumber(active.get(path + ".pitch"), 0.5D, 2D)) {
            return "pitch";
        }
        if (invalidNumber(active.get(path + ".pitch-variation"), 0D, 2D)) {
            return "pitch-variation";
        }
        return null;
    }

    private static boolean invalidNumber(Object raw, double minimum, double maximum) {
        if (raw == null) {
            return false;
        }
        if (!(raw instanceof Number number)) {
            return true;
        }
        double value = number.doubleValue();
        return !Double.isFinite(value) || value < minimum || value > maximum;
    }

    private static Set<String> definitionPaths(YamlConfiguration defaults) {
        Set<String> paths = new LinkedHashSet<>();
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) && defaults.isSet(path + ".sound")) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static YamlConfiguration bundled(JavaPlugin plugin) {
        try (InputStream stream = plugin.getResource(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Bundled sounds.yml is unavailable.");
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled sounds.yml", exception);
        }
    }

    public record Snapshot(Path file, byte[] original, Set<String> suppress,
                           Set<String> missingVariation) {
        public void restoreAfterCoreLoad(FoSoundService sounds) {
            for (String path : suppress) {
                sounds.remove(path);
            }
            for (String path : missingVariation) {
                if (suppress.stream().noneMatch(sound -> path.startsWith(sound + "."))) {
                    sounds.configuration().set(path, null);
                }
            }
            try {
                if (original == null) {
                    Files.deleteIfExists(file);
                    return;
                }
                if (Files.isRegularFile(file) && Arrays.equals(original, Files.readAllBytes(file))) {
                    return;
                }
                Path staged = Files.createTempFile(file.getParent(), "foorders-sounds-restore-", ".yml");
                try {
                    Files.write(staged, original);
                    Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(staged);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Could not restore owner sounds.yml after Core backfill", exception);
            }
        }
    }
}
