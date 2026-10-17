package me.foesio.foOrders.storage;

import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.storage.WriteBehindStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HistoryDataStore {
    private static final int DEFAULT_MAX_ENTRIES_PER_TYPE = 100;
    private static final long DEFERRED_FLUSH_DELAY_TICKS = 100L;

    private final Plugin plugin;
    private final Path historyDataFolder;
    private final Map<UUID, PlayerHistoryData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final WriteBehindStore<UUID, PlayerHistoryData> writeBehind;
    private volatile int maxEntriesPerType = DEFAULT_MAX_ENTRIES_PER_TYPE;

    public HistoryDataStore(Plugin plugin, FoScheduler scheduler) {
        this.plugin = plugin;
        this.historyDataFolder = plugin.getDataFolder().toPath().resolve("history data");
        this.writeBehind = WriteBehindStore.create(
            scheduler,
            DEFERRED_FLUSH_DELAY_TICKS,
            this::snapshotCachedPlayer,
            this::saveDirectly
        );
    }

    public void initialize() {
        try {
            Files.createDirectories(historyDataFolder);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create history data folder", exception);
        }
    }

    public void setMaxEntriesPerType(int maxEntriesPerType) {
        this.maxEntriesPerType = Math.max(1, maxEntriesPerType);
    }

    public List<HistoryEntry> getOrderHistory(UUID playerId) {
        synchronized (playerLock(playerId)) {
            return copyEntries(getOrLoad(playerId).orderHistory());
        }
    }

    public List<HistoryEntry> getDeliverHistory(UUID playerId) {
        synchronized (playerLock(playerId)) {
            return copyEntries(getOrLoad(playerId).deliverHistory());
        }
    }

    public void appendOrderHistory(UUID playerId, String action, String details) {
        append(playerId, HistoryType.ORDER, action, details);
    }

    public void appendDeliverHistory(UUID playerId, String action, String details) {
        append(playerId, HistoryType.DELIVER, action, details);
    }

    public void appendOrderEvent(UUID playerId, String eventKey, Map<String, String> values) {
        appendEvent(playerId, HistoryType.ORDER, eventKey, values);
    }

    public void appendDeliverEvent(UUID playerId, String eventKey, Map<String, String> values) {
        appendEvent(playerId, HistoryType.DELIVER, eventKey, values);
    }

    public void saveAndUnload(UUID playerId) {
        writeBehind.snapshotAndWriteAsync(playerId, true);
    }

    public void saveAll() {
        writeBehind.flushSynchronously(new ArrayList<>(cache.keySet()), false);
    }

    private void append(UUID playerId, HistoryType historyType, String action, String details) {
        PlayerHistoryData snapshot;
        synchronized (playerLock(playerId)) {
            PlayerHistoryData data = getOrLoad(playerId);
            String safeAction = rawText(action);
            String safeDetails = rawText(details);
            HistoryEntry entry = new HistoryEntry(System.currentTimeMillis(), safeAction, safeDetails, "", Map.of());
            List<HistoryEntry> entries = historyType == HistoryType.ORDER ? data.orderHistory() : data.deliverHistory();
            entries.add(0, entry);
            trim(entries);
            snapshot = data.copy();
        }
        writeBehind.writeAsync(playerId, snapshot);
    }

    private void appendEvent(UUID playerId, HistoryType historyType, String eventKey, Map<String, String> values) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("History event key is required.");
        }
        Map<String, String> safeValues = values == null ? Map.of() : Map.copyOf(values);
        PlayerHistoryData snapshot;
        synchronized (playerLock(playerId)) {
            PlayerHistoryData data = getOrLoad(playerId);
            HistoryEntry entry = new HistoryEntry(System.currentTimeMillis(), "", "", eventKey, safeValues);
            List<HistoryEntry> entries = historyType == HistoryType.ORDER ? data.orderHistory() : data.deliverHistory();
            entries.add(0, entry);
            trim(entries);
            snapshot = data.copy();
        }
        writeBehind.writeAsync(playerId, snapshot);
    }

    private List<HistoryEntry> copyEntries(List<HistoryEntry> entries) {
        List<HistoryEntry> copy = new ArrayList<>(entries.size());
        for (HistoryEntry entry : entries) {
            copy.add(entry.copy());
        }
        return copy;
    }

    private void trim(List<HistoryEntry> entries) {
        while (entries.size() > maxEntriesPerType) {
            entries.remove(entries.size() - 1);
        }
    }

    private PlayerHistoryData getOrLoad(UUID playerId) {
        PlayerHistoryData cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }

        Path file = getFilePath(playerId);
        if (!Files.exists(file)) {
            PlayerHistoryData created = new PlayerHistoryData(new ArrayList<>(), new ArrayList<>());
            cache.put(playerId, created);
            writeBehind.markDirty(playerId);
            return created;
        }

        PlayerHistoryData loaded = loadDirectly(file);
        cache.put(playerId, loaded);
        return loaded;
    }

    private PlayerHistoryData loadDirectly(Path file) {
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            List<HistoryEntry> orderHistory = deserializeEntries(yaml.getMapList("order-history"));
            List<HistoryEntry> deliverHistory = deserializeEntries(yaml.getMapList("deliver-history"));
            trim(orderHistory);
            trim(deliverHistory);
            return new PlayerHistoryData(orderHistory, deliverHistory);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to read " + file.getFileName() + ": " + exception.getMessage());
            return new PlayerHistoryData(new ArrayList<>(), new ArrayList<>());
        }
    }

    private List<HistoryEntry> deserializeEntries(List<Map<?, ?>> mapList) {
        List<HistoryEntry> entries = new ArrayList<>();
        for (Map<?, ?> rawMap : mapList) {
            if (rawMap == null) {
                continue;
            }
            entries.add(decodeEntry(rawMap));
        }
        return entries;
    }

    static HistoryEntry decodeEntry(Map<?, ?> rawMap) {
        long timestamp = parseTimestamp(rawMap.get("timestamp"));
        String eventKey = rawMap.get("event-key") instanceof String value ? value : "";
        if (!eventKey.isBlank()) {
            Map<String, String> values = new LinkedHashMap<>();
            if (rawMap.get("values") instanceof Map<?, ?> rawValues) {
                for (Map.Entry<?, ?> value : rawValues.entrySet()) {
                    if (value.getKey() instanceof String key && value.getValue() != null) {
                        values.put(key, String.valueOf(value.getValue()));
                    }
                }
            }
            return new HistoryEntry(timestamp, "", "", eventKey, Map.copyOf(values));
        }
        String action = rawText(rawMap.get("action"));
        String details = rawText(rawMap.get("details"));
        return new HistoryEntry(timestamp, action, details, "", Map.of());
    }

    private static long parseTimestamp(Object rawTimestamp) {
        if (rawTimestamp instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        if (rawTimestamp instanceof String stringValue) {
            try {
                return Math.max(0L, Long.parseLong(stringValue.trim()));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private static String rawText(Object rawValue) {
        return rawValue == null ? "" : String.valueOf(rawValue);
    }

    private PlayerHistoryData snapshotCachedPlayer(UUID playerId, boolean unload) {
        synchronized (playerLock(playerId)) {
            PlayerHistoryData data = cache.get(playerId);
            if (data == null) {
                return null;
            }

            trim(data.orderHistory());
            trim(data.deliverHistory());
            PlayerHistoryData snapshot = data.copy();
            if (unload) {
                cache.remove(playerId);
            }
            return snapshot;
        }
    }

    private boolean saveDirectly(UUID playerId, PlayerHistoryData data) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("order-history", serializeEntries(data.orderHistory()));
        yaml.set("deliver-history", serializeEntries(data.deliverHistory()));
        Path file = getFilePath(playerId);

        try {
            yaml.save(file.toFile());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save " + file.getFileName() + ": " + exception.getMessage());
            return false;
        }
    }

    private List<Map<String, Object>> serializeEntries(List<HistoryEntry> entries) {
        List<Map<String, Object>> serialized = new ArrayList<>(entries.size());
        for (HistoryEntry entry : entries) {
            serialized.add(encodeEntry(entry));
        }
        return serialized;
    }

    static Map<String, Object> encodeEntry(HistoryEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("timestamp", Math.max(0L, entry.timestamp()));
        if (entry.eventKey().isBlank()) {
            row.put("action", rawText(entry.action()));
            row.put("details", rawText(entry.details()));
        } else {
            row.put("event-key", entry.eventKey());
            row.put("values", entry.values());
        }
        return row;
    }

    private Path getFilePath(UUID playerId) {
        return historyDataFolder.resolve(playerId + ".yml");
    }

    private Object playerLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    public enum HistoryType {
        ORDER,
        DELIVER
    }

    public record HistoryEntry(long timestamp, String action, String details, String eventKey, Map<String, String> values) {
        public HistoryEntry(long timestamp, String action, String details) {
            this(timestamp, action, details, "", Map.of());
        }

        public HistoryEntry {
            values = values == null ? Map.of() : Map.copyOf(values);
            eventKey = eventKey == null ? "" : eventKey;
        }

        private HistoryEntry copy() {
            return new HistoryEntry(timestamp, action, details, eventKey, values);
        }
    }

    private record PlayerHistoryData(List<HistoryEntry> orderHistory, List<HistoryEntry> deliverHistory) {
        private PlayerHistoryData copy() {
            return new PlayerHistoryData(copyHistoryEntries(orderHistory), copyHistoryEntries(deliverHistory));
        }
    }

    private static List<HistoryEntry> copyHistoryEntries(List<HistoryEntry> entries) {
        List<HistoryEntry> copy = new ArrayList<>(entries.size());
        for (HistoryEntry entry : entries) {
            copy.add(entry.copy());
        }
        return copy;
    }
}
