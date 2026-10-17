package me.foesio.foOrders;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Validates owner-authored public messages without restoring intentionally removed keys. */
final class PublicMessageFields {
    private PublicMessageFields() { }

    static boolean repair(YamlConfiguration owner, YamlConfiguration bundled, Consumer<String> warn) {
        return check(owner, bundled, false, warn);
    }

    static void validateReload(YamlConfiguration owner, YamlConfiguration bundled) {
        check(owner, bundled, true, ignored -> { });
    }

    private static boolean check(YamlConfiguration owner, YamlConfiguration bundled,
                                 boolean strict, Consumer<String> warn) {
        boolean changed = false;
        for (String path : bundled.getKeys(true)) {
            if (path.equals("numbers") || path.startsWith("numbers.")) continue;
            if (!owner.isSet(path)) continue;
            Object actual = owner.get(path);
            Object expected = bundled.get(path);
            if (bundled.isConfigurationSection(path)) {
                if (owner.isConfigurationSection(path)) continue;
                if (strict) throw invalid(path);
                owner.set(path, null);
                owner.createSection(path);
                copySection(owner, bundled.getConfigurationSection(path), path);
                warn.accept("Invalid owner messages.yml " + path + "=" + actual
                    + "; using newest bundled section");
                changed = true;
                continue;
            }
            if (expected instanceof String) {
                if (actual instanceof String) continue;
                if (strict) throw invalid(path);
                owner.set(path, expected);
                warn.accept("Invalid owner messages.yml " + path + "=" + actual
                    + "; using newest bundled default " + expected);
                changed = true;
                continue;
            }
            if (expected instanceof List<?> defaults) {
                if (!(actual instanceof List<?> values)) {
                    if (strict) throw invalid(path);
                    owner.set(path, new ArrayList<>(defaults));
                    warn.accept("Invalid owner messages.yml " + path + "=" + actual
                        + "; using newest bundled list");
                    changed = true;
                    continue;
                }
                List<Object> repaired = new ArrayList<>(values);
                for (int index = 0; index < values.size(); index++) {
                    if (values.get(index) instanceof String) continue;
                    String indexedPath = path + "[" + index + "]";
                    if (strict) throw invalid(indexedPath);
                    if (index >= defaults.size() || !(defaults.get(index) instanceof String)) {
                        throw new IllegalStateException("No bundled message default for " + indexedPath);
                    }
                    repaired.set(index, defaults.get(index));
                    warn.accept("Invalid owner messages.yml " + indexedPath + "=" + values.get(index)
                        + "; using newest bundled default " + defaults.get(index));
                    changed = true;
                }
                if (!repaired.equals(values)) owner.set(path, repaired);
            }
        }
        String badToken = invalidCustomToken(owner, bundled);
        if (badToken != null) {
            if (strict) throw invalid(badToken);
            warn.accept("Invalid owner messages.yml " + badToken
                + "; no bundled default exists, so the owner value was retained and resolves empty");
        }
        return changed;
    }

    /** An unknown owner token has no safe migration default; leave the upgrade pending. */
    static void validateCustomTokensForUpgrade(YamlConfiguration owner, YamlConfiguration bundled) {
        String badToken = invalidCustomToken(owner, bundled);
        if (badToken != null) {
            throw new IllegalStateException("No bundled message default for " + badToken);
        }
    }

    private static String invalidCustomToken(YamlConfiguration owner, YamlConfiguration bundled) {
        ConfigurationSection tokens = owner.getConfigurationSection("tokens");
        ConfigurationSection knownTokens = bundled.getConfigurationSection("tokens");
        if (tokens == null) return null;
        for (String key : tokens.getKeys(false)) {
            if (knownTokens != null && knownTokens.isSet(key)) continue;
            if (!(tokens.get(key) instanceof String)) return "tokens." + key;
        }
        return null;
    }

    private static void copySection(YamlConfiguration owner, ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            String child = path + "." + key;
            if (section.isConfigurationSection(key)) {
                owner.createSection(child);
                copySection(owner, section.getConfigurationSection(key), child);
            } else {
                owner.set(child, section.get(key));
            }
        }
    }

    private static IllegalStateException invalid(String path) {
        return new IllegalStateException("Invalid owner messages.yml " + path
            + "; prior working messages remain active.");
    }
}
