package me.foesio.foOrders.storage;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomItemStore {
    private final Plugin plugin;
    private final File file;
    private final Map<String, CustomItemDefinition> cache = new LinkedHashMap<>();

    public CustomItemStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "custom-items.yml");
    }

    public void initialize() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create custom item data folder");
        }
        load();
    }

    public synchronized List<CustomItemDefinition> listAll() {
        List<CustomItemDefinition> entries = new ArrayList<>();
        for (CustomItemDefinition definition : cache.values()) {
            entries.add(definition.copy());
        }
        return entries;
    }

    public synchronized CustomItemDefinition get(String rawId) {
        String id = normalizeId(rawId);
        if (id == null) {
            return null;
        }
        CustomItemDefinition definition = cache.get(id);
        return definition == null ? null : definition.copy();
    }

    public synchronized CustomItemDefinition save(String rawId, ItemStack template, boolean allowOrderEnchants) {
        String id = normalizeId(rawId);
        if (id == null) {
            throw new IllegalArgumentException("Invalid custom item id");
        }
        if (!isValidTemplate(template)) {
            throw new IllegalArgumentException("Invalid custom item template");
        }

        CustomItemDefinition existing = cache.get(id);
        long createdAt = existing == null ? System.currentTimeMillis() : existing.createdAtEpochMillis();
        CustomItemDefinition updated = new CustomItemDefinition(id, template.clone(), allowOrderEnchants, createdAt);
        cache.put(id, updated);
        persist();
        return updated.copy();
    }

    public synchronized boolean remove(String rawId) {
        String id = normalizeId(rawId);
        if (id == null) {
            return false;
        }
        if (cache.remove(id) == null) {
            return false;
        }
        persist();
        return true;
    }

    private synchronized void load() {
        cache.clear();
        if (!file.exists()) {
            persist();
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("items");
        if (section == null) {
            return;
        }

        for (String rawId : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(rawId);
            if (itemSection == null) {
                continue;
            }

            String id = normalizeId(rawId);
            if (id == null) {
                plugin.getLogger().warning("Ignoring invalid custom item id: '" + rawId + "'.");
                continue;
            }

            ItemStack template = itemSection.getItemStack("template");
            if (!isValidTemplate(template)) {
                plugin.getLogger().warning("Ignoring custom item '" + id + "' because template is invalid.");
                continue;
            }

            boolean allowOrderEnchants = itemSection.getBoolean("allow-order-enchants", false);
            long createdAt = itemSection.getLong("created-at-epoch-millis", System.currentTimeMillis());
            cache.put(id, new CustomItemDefinition(id, template.clone(), allowOrderEnchants, createdAt));
        }

        persist();
    }

    private synchronized void persist() {
        YamlConfiguration config = new YamlConfiguration();
        for (CustomItemDefinition definition : cache.values()) {
            String basePath = "items." + definition.id();
            config.set(basePath + ".template", definition.template().clone());
            config.set(basePath + ".allow-order-enchants", definition.allowOrderEnchants());
            config.set(basePath + ".created-at-epoch-millis", definition.createdAtEpochMillis());
        }

        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save custom-items.yml: " + exception.getMessage());
        }
    }

    private boolean isValidTemplate(ItemStack template) {
        return template != null && template.getType() != Material.AIR && template.getType().isItem();
    }

    public static String normalizeId(String rawId) {
        if (rawId == null) {
            return null;
        }

        String normalized = rawId
            .trim()
            .toLowerCase(Locale.ROOT)
            .replace(' ', '_')
            .replaceAll("[^a-z0-9_-]", "");
        if (normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    public record CustomItemDefinition(String id, ItemStack template, boolean allowOrderEnchants, long createdAtEpochMillis) {
        public CustomItemDefinition {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Custom item id cannot be blank");
            }

            ItemStack safeTemplate = template == null ? new ItemStack(Material.STONE) : template.clone();
            if (safeTemplate.getType() == Material.AIR || !safeTemplate.getType().isItem()) {
                safeTemplate = new ItemStack(Material.STONE);
            }
            template = safeTemplate;

            if (createdAtEpochMillis < 0) {
                createdAtEpochMillis = 0;
            }
        }

        public CustomItemDefinition copy() {
            return new CustomItemDefinition(id, template.clone(), allowOrderEnchants, createdAtEpochMillis);
        }
    }
}
