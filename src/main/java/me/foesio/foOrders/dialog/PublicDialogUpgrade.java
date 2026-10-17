package me.foesio.foOrders.dialog;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Consumer;

/** Repairs invalid old dialog presentation only during a versioned upgrade. */
public final class PublicDialogUpgrade {
    private static final Set<String> ITEM_BUTTONS = Set.of(
        "search", "sort", "filter", "previous", "choices", "next");

    private PublicDialogUpgrade() { }

    public static boolean repairInvalidFields(String id, YamlConfiguration owner,
                                              YamlConfiguration bundled, Consumer<String> warn) {
        boolean changed = false;
        for (String path : bundled.getKeys(true)) {
            if (!bundled.isConfigurationSection(path) || !owner.isSet(path)
                || owner.isConfigurationSection(path)) continue;
            Object invalid = owner.get(path);
            owner.set(path, null);
            ConfigurationSection target = owner.createSection(path);
            ConfigurationSection source = bundled.getConfigurationSection(path);
            for (String child : source.getKeys(true)) {
                if (!source.isConfigurationSection(child)) target.set(child, source.get(child));
            }
            warn.accept("Invalid legacy dialog " + id + ":" + path + "=" + invalid
                + "; using newest bundled default section");
            changed = true;
        }
        for (String path : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(path) || !owner.isSet(path)) continue;
            Object value = owner.get(path);
            if (valid(id, path, value, owner, bundled.get(path))) continue;
            if (path.equals("body") && value instanceof List<?> && bundled.get(path) instanceof String) {
                throw new IllegalStateException("No bundled dialog list default for " + id + ":body; "
                    + "owner lines were left unchanged");
            }
            if (!path.equals("button-order") && !path.equals("action-order")
                && value instanceof List<?> values && bundled.get(path) instanceof List<?> defaults) {
                List<Object> repaired = new ArrayList<>(values);
                for (int index = 0; index < values.size(); index++) {
                    if (values.get(index) instanceof String) continue;
                    String indexedPath = path + "[" + index + "]";
                    if (index >= defaults.size() || !(defaults.get(index) instanceof String)) {
                        throw new IllegalStateException("No bundled dialog default for " + id + ":" + indexedPath);
                    }
                    repaired.set(index, defaults.get(index));
                    warn.accept("Invalid legacy dialog " + id + ":" + indexedPath + "=" + values.get(index)
                        + "; using newest bundled default " + defaults.get(index));
                    changed = true;
                }
                if (!repaired.equals(values)) {
                    owner.set(path, repaired);
                    if (valid(id, path, repaired, owner, bundled.get(path))) continue;
                }
            }
            Object fallback = bundled.get(path);
            owner.set(path, fallback);
            warn.accept("Invalid legacy dialog " + id + ":" + path + "=" + value
                + "; using newest bundled default " + fallback);
            changed = true;
        }
        return changed;
    }

    private static boolean valid(String id, String path, Object value,
                                 YamlConfiguration owner, Object fallback) {
        if (path.equals("body") && !id.equals("item-select") && !id.equals("enchant-select")) {
            return value instanceof String || stringList(value) != null;
        }
        if (fallback instanceof String) {
            return value instanceof String text
                && (!path.equals("fallback.cancel-keyword") || !text.isBlank());
        }
        if (fallback instanceof Boolean) return value instanceof Boolean;
        if (fallback instanceof Integer) {
            if (!(value instanceof Integer number)) return false;
            if (id.equals("enchant-select")) {
                if (path.equals("max-level-columns")) return number >= 1 && number <= 10;
                if (path.equals("columns")) return number >= 2 && number <= 12;
                if (path.equals("item.width") || path.equals("item.height")) {
                    return number >= 1 && number <= 256;
                }
                if (path.endsWith(".width")) return number >= 1 && number <= 1024;
            }
            if (path.equals("columns") && !id.equals("item-select")) return number >= 1 && number <= 2;
            return number > 0;
        }
        List<String> lines = stringList(value);
        if (fallback instanceof List<?>) {
            if (lines == null) return false;
            if (path.equals("button-order") && id.equals("item-select")) {
                return lines.contains("choices") && lines.stream().allMatch(ITEM_BUTTONS::contains)
                    && lines.stream().distinct().count() == lines.size()
                    && lines.contains("previous") == lines.contains("next");
            }
            if (path.equals("button-order")) {
                return lines.contains("confirm") && lines.size() <= 2
                    && lines.stream().allMatch(item -> item.equals("confirm") || item.equals("cancel"))
                    && lines.stream().distinct().count() == lines.size()
                    && (lines.contains("cancel") || owner.getBoolean("can-close-with-escape"));
            }
            if (path.equals("action-order")) {
                return !lines.isEmpty() && lines.size() <= 2
                    && lines.stream().allMatch(item -> item.equals("back") || item.equals("continue"))
                    && lines.stream().distinct().count() == lines.size();
            }
            return true;
        }
        return value != null && fallback != null && value.getClass() == fallback.getClass();
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> source) || source.stream().anyMatch(item -> !(item instanceof String))) {
            return null;
        }
        return source.stream().map(String.class::cast).toList();
    }
}
