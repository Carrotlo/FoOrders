package me.foesio.foOrders.storage;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
        CustomItemDefinition updated = new CustomItemDefinition(id, template.clone(), allowOrderEnchants,
            createdAt, existing == null ? null : existing.registeredBy());
        cache.put(id, updated);
        try {
            persist();
        } catch (RuntimeException failure) {
            if (existing == null) cache.remove(id);
            else cache.put(id, existing);
            throw failure;
        }
        return updated.copy();
    }

    public synchronized boolean remove(String rawId) {
        String id = normalizeId(rawId);
        if (id == null) {
            return false;
        }
        CustomItemDefinition removed = cache.remove(id);
        if (removed == null) {
            return false;
        }
        try {
            persist();
        } catch (RuntimeException failure) {
            cache.put(id, removed);
            throw failure;
        }
        return true;
    }

    /** Plugin-managed entries cannot take over an owner-created ID or another plugin's entry. */
    public synchronized CustomItemDefinition registerManaged(String ownerPlugin, String rawId,
                                                              ItemStack template, boolean allowOrderEnchants) {
        if (ownerPlugin == null || ownerPlugin.isBlank()) throw new IllegalArgumentException("Plugin name is required");
        String id = normalizeId(rawId);
        if (id == null || !isValidTemplate(template)) throw new IllegalArgumentException("Invalid custom item");
        CustomItemDefinition old = cache.get(id);
        if (old != null && !ownerPlugin.equals(old.registeredBy())) {
            throw new IllegalArgumentException("Custom item ID is owned by another source");
        }
        long createdAt = old == null ? System.currentTimeMillis() : old.createdAtEpochMillis();
        CustomItemDefinition updated = new CustomItemDefinition(id, template.clone(), allowOrderEnchants,
            createdAt, ownerPlugin);
        cache.put(id, updated);
        try {
            persist();
        } catch (RuntimeException failure) {
            if (old == null) cache.remove(id);
            else cache.put(id, old);
            throw failure;
        }
        return updated.copy();
    }

    public synchronized boolean removeManaged(String ownerPlugin, String rawId) {
        String id = normalizeId(rawId);
        CustomItemDefinition old = id == null ? null : cache.get(id);
        if (old == null || !ownerPlugin.equals(old.registeredBy())) return false;
        return remove(id);
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
            String registeredBy = itemSection.getString("registered-by");
            cache.put(id, new CustomItemDefinition(id, template.clone(), allowOrderEnchants, createdAt, registeredBy));
        }

    }

    private synchronized void persist() {
        YamlConfiguration config = new YamlConfiguration();
        for (CustomItemDefinition definition : cache.values()) {
            String basePath = "items." + definition.id();
            config.set(basePath + ".template", definition.template().clone());
            config.set(basePath + ".allow-order-enchants", definition.allowOrderEnchants());
            config.set(basePath + ".created-at-epoch-millis", definition.createdAtEpochMillis());
            if (definition.registeredBy() != null) {
                config.set(basePath + ".registered-by", definition.registeredBy());
            }
        }

        Path target = file.toPath();
        Path staged = null;
        try {
            staged = Files.createTempFile(target.getParent(), "custom-items-", ".tmp");
            Files.writeString(staged, config.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save custom-items.yml", exception);
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ignored) {
                }
            }
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

    public record CustomItemDefinition(String id, ItemStack template, boolean allowOrderEnchants,
                                       long createdAtEpochMillis, String registeredBy) {
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
            return new CustomItemDefinition(id, template.clone(), allowOrderEnchants, createdAtEpochMillis, registeredBy);
        }
    }
}
