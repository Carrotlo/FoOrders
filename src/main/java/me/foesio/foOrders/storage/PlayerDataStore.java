package me.foesio.foOrders.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.storage.WriteBehindStore;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.sqlite.JDBC;

import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerDataStore {
    private static final int SORT_OPTION_COUNT = 4;
    private static final int FILTER_OPTION_COUNT = 9;
    private static final long DEFERRED_FLUSH_DELAY_TICKS = 100L;
    private static final String DATABASE_FILE_NAME = "foorders.db";
    private static final String LEGACY_PLAYER_DATA_FOLDER = "playerdata";
    private static final String LEGACY_ARCHIVE_FOLDER = "old-userdata";
    private static final String META_JSON_IMPORT_COMPLETE = "playerdata_json_import_complete";
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Plugin plugin;
    private final Path dataFolder;
    private final Path playerDataFolder;
    private final Path databaseFile;
    private final Gson gson;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerData> allPlayerDataSnapshot = new ConcurrentHashMap<>();
    private final Map<UUID, Object> playerLocks = new ConcurrentHashMap<>();
    private final WriteBehindStore<UUID, PlayerData> writeBehind;

    public PlayerDataStore(Plugin plugin, FoScheduler scheduler) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
        this.playerDataFolder = dataFolder.resolve(LEGACY_PLAYER_DATA_FOLDER);
        this.databaseFile = dataFolder.resolve(DATABASE_FILE_NAME);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.writeBehind = WriteBehindStore.create(
            scheduler,
            DEFERRED_FLUSH_DELAY_TICKS,
            this::snapshotCachedPlayer,
            this::saveToDatabase
        );
    }

    public void initialize() {
        try {
            Files.createDirectories(dataFolder);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create FoOrders data folder", exception);
        }
        initializeDatabase();
        migrateLegacyJsonIfNeeded();
        loadAllPlayerDataSnapshots();
    }

    public PlayerData getOrCreate(UUID playerId) {
        PlayerData data = cache.get(playerId);
        if (data != null) {
            return data;
        }

        synchronized (playerLock(playerId)) {
            data = cache.get(playerId);
            if (data != null) {
                return data;
            }

            PlayerData loaded = loadFromDatabase(playerId);
            if (loaded == null) {
                PlayerData created = PlayerData.defaults();
                cache.put(playerId, created);
                save(playerId);
                return created;
            }

            cache.put(playerId, loaded);
            updatePlayerDataSnapshot(playerId, loaded);
            return loaded;
        }
    }

    public void save(UUID playerId) {
        markDirty(playerId, false);
    }

    public void saveUrgent(UUID playerId) {
        markDirty(playerId, true);
    }

    public void saveAndUnload(UUID playerId) {
        writeBehind.snapshotAndWriteAsync(playerId, true);
    }

    public void saveAll() {
        writeBehind.flushSynchronously(new ArrayList<>(cache.keySet()), false);
    }

    public List<PlayerOrderRecord> getAllOrdersSnapshot() {
        Map<UUID, PlayerData> allData = new HashMap<>();
        for (Map.Entry<UUID, PlayerData> entry : allPlayerDataSnapshot.entrySet()) {
            allData.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<UUID, PlayerData> entry : cache.entrySet()) {
            synchronized (playerLock(entry.getKey())) {
                allData.put(entry.getKey(), entry.getValue().copy());
            }
        }

        List<PlayerOrderRecord> orders = new ArrayList<>();
        for (Map.Entry<UUID, PlayerData> entry : allData.entrySet()) {
            for (OrderEntry order : entry.getValue().getOrders()) {
                orders.add(new PlayerOrderRecord(entry.getKey(), order.copy()));
            }
        }
        return orders;
    }

    private void markDirty(UUID playerId, boolean urgent) {
        PlayerData snapshot = null;
        synchronized (playerLock(playerId)) {
            PlayerData data = cache.get(playerId);
            if (data == null) {
                return;
            }

            data.sanitize();
            updatePlayerDataSnapshot(playerId, data);
            if (urgent) {
                snapshot = data.copy();
            }
        }

        if (urgent) {
            writeBehind.writeAsync(playerId, snapshot);
            return;
        }
        writeBehind.markDirty(playerId);
    }

    private PlayerData snapshotCachedPlayer(UUID playerId, boolean unload) {
        synchronized (playerLock(playerId)) {
            PlayerData data = cache.get(playerId);
            if (data == null) {
                return null;
            }

            data.sanitize();
            updatePlayerDataSnapshot(playerId, data);
            PlayerData snapshot = data.copy();
            if (unload) {
                cache.remove(playerId);
            }
            return snapshot;
        }
    }

    private void loadAllPlayerDataSnapshots() {
        allPlayerDataSnapshot.clear();
        try (Connection connection = openConnection()) {
            Map<UUID, PlayerData> loadedData = loadPlayerSettings(connection);
            loadPlayerOrders(connection, loadedData);
            cacheLoadedSnapshots(loadedData);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load player data from SQLite", exception);
        }
    }

    private Map<UUID, PlayerData> loadPlayerSettings(Connection connection) throws SQLException {
        Map<UUID, PlayerData> loadedData = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT player_uuid, sort_index, filter_index FROM player_settings")) {
            while (resultSet.next()) {
                UUID playerId = parsePlayerId(resultSet.getString("player_uuid"));
                if (playerId == null) {
                    continue;
                }
                PlayerData data = PlayerData.defaults();
                data.sortIndex = resultSet.getInt("sort_index");
                data.filterIndex = resultSet.getInt("filter_index");
                loadedData.put(playerId, data);
            }
        }
        return loadedData;
    }

    private void loadPlayerOrders(Connection connection, Map<UUID, PlayerData> loadedData) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                 "SELECT owner_uuid, order_id, custom_item_id, material, enchantments_json, amount_ordered, amount_delivered, amount_claimable, price_per_item, amount_paid, cancelled, created_at_epoch_millis " +
                     "FROM player_orders ORDER BY created_at_epoch_millis ASC, order_id ASC"
             )) {
            while (resultSet.next()) {
                UUID playerId = parsePlayerId(resultSet.getString("owner_uuid"));
                if (playerId == null) {
                    continue;
                }
                PlayerData data = loadedData.computeIfAbsent(playerId, ignored -> PlayerData.defaults());
                data.orders.add(readOrderEntry(resultSet));
            }
        }
    }

    private void cacheLoadedSnapshots(Map<UUID, PlayerData> loadedData) {
        for (Map.Entry<UUID, PlayerData> entry : loadedData.entrySet()) {
            PlayerData data = entry.getValue();
            data.sanitize();
            updatePlayerDataSnapshot(entry.getKey(), data);
        }
    }

    private void updatePlayerDataSnapshot(UUID playerId, PlayerData data) {
        if (playerId == null || data == null) {
            return;
        }
        allPlayerDataSnapshot.put(playerId, data.copy());
    }

    private UUID parsePlayerId(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".json")) {
            return null;
        }
        String rawId = fileName.substring(0, fileName.length() - ".json".length());
        return parsePlayerId(rawId);
    }

    private UUID parsePlayerId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private PlayerData loadLegacyJson(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            PlayerData loaded = gson.fromJson(reader, PlayerData.class);
            if (loaded == null) {
                loaded = PlayerData.defaults();
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | JsonParseException exception) {
            plugin.getLogger().warning("Failed to parse " + path.getFileName() + ": " + exception.getMessage());
            return null;
        }
    }

    private void initializeDatabase() {
        try {
            Class.forName(JDBC.class.getName());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("SQLite JDBC driver is missing", exception);
        }
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS storage_meta (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_settings (
                    player_uuid TEXT PRIMARY KEY,
                    sort_index INTEGER NOT NULL DEFAULT 0,
                    filter_index INTEGER NOT NULL DEFAULT 0
                )
                """);
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_orders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    order_id TEXT NOT NULL,
                    owner_uuid TEXT NOT NULL,
                    custom_item_id TEXT,
                    material TEXT NOT NULL,
                    enchantments_json TEXT NOT NULL DEFAULT '{}',
                    amount_ordered INTEGER NOT NULL,
                    amount_delivered INTEGER NOT NULL,
                    amount_claimable INTEGER NOT NULL,
                    price_per_item REAL NOT NULL,
                    amount_paid REAL NOT NULL,
                    cancelled INTEGER NOT NULL DEFAULT 0,
                    created_at_epoch_millis INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(owner_uuid) REFERENCES player_settings(player_uuid) ON DELETE CASCADE
                )
                """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_player_orders_owner ON player_orders(owner_uuid)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize FoOrders SQLite database", exception);
        }
    }

    private void migrateLegacyJsonIfNeeded() {
        if (isMetaEnabled(META_JSON_IMPORT_COMPLETE) || !Files.isDirectory(playerDataFolder)) {
            return;
        }

        Map<UUID, PlayerData> legacyData = new LinkedHashMap<>();
        try (var files = Files.list(playerDataFolder)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(path -> {
                    UUID playerId = parsePlayerId(path);
                    if (playerId == null) {
                        return;
                    }
                    PlayerData loaded = loadLegacyJson(path);
                    if (loaded != null) {
                        legacyData.put(playerId, loaded);
                    }
                });
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read old playerdata folder for SQLite import", exception);
        }

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                for (Map.Entry<UUID, PlayerData> entry : legacyData.entrySet()) {
                    writePlayerData(connection, entry.getKey(), entry.getValue());
                }
                setMeta(connection, META_JSON_IMPORT_COMPLETE, "true");
                connection.commit();
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not import old playerdata JSON files into SQLite", exception);
        }

        archiveLegacyPlayerDataFolder();
        plugin.getLogger().info("Imported " + legacyData.size() + " old playerdata file(s) into SQLite.");
    }

    private PlayerData loadFromDatabase(UUID playerId) {
        try (Connection connection = openConnection()) {
            return loadFromDatabase(connection, playerId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load player data for " + playerId + " from SQLite", exception);
        }
    }

    private PlayerData loadFromDatabase(Connection connection, UUID playerId) throws SQLException {
        PlayerData data = PlayerData.defaults();
        boolean found = false;
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT sort_index, filter_index FROM player_settings WHERE player_uuid = ?"
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    data.sortIndex = resultSet.getInt("sort_index");
                    data.filterIndex = resultSet.getInt("filter_index");
                    found = true;
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT order_id, custom_item_id, material, enchantments_json, amount_ordered, amount_delivered, amount_claimable, price_per_item, amount_paid, cancelled, created_at_epoch_millis " +
                "FROM player_orders WHERE owner_uuid = ? ORDER BY created_at_epoch_millis ASC, order_id ASC"
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    data.orders.add(readOrderEntry(resultSet));
                    found = true;
                }
            }
        }
        if (!found) {
            return null;
        }
        data.sanitize();
        return data;
    }

    private boolean saveToDatabase(UUID playerId, PlayerData data) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                writePlayerData(connection, playerId, data);
                connection.commit();
                return true;
            } catch (SQLException exception) {
                rollbackQuietly(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save player data for " + playerId + " to SQLite: " + exception.getMessage());
            return false;
        }
    }

    private void writePlayerData(Connection connection, UUID playerId, PlayerData data) throws SQLException {
        data.sanitize();
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO player_settings (player_uuid, sort_index, filter_index)
            VALUES (?, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                sort_index = excluded.sort_index,
                filter_index = excluded.filter_index
            """)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, data.getSortIndex());
            statement.setInt(3, data.getFilterIndex());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM player_orders WHERE owner_uuid = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO player_orders (
                order_id,
                owner_uuid,
                custom_item_id,
                material,
                enchantments_json,
                amount_ordered,
                amount_delivered,
                amount_claimable,
                price_per_item,
                amount_paid,
                cancelled,
                created_at_epoch_millis
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            for (OrderEntry order : data.getOrders()) {
                statement.setString(1, order.getOrderId());
                statement.setString(2, playerId.toString());
                statement.setString(3, order.getCustomItemId());
                statement.setString(4, order.getMaterial());
                statement.setString(5, gson.toJson(order.getEnchantments()));
                statement.setInt(6, order.getAmountOrdered());
                statement.setInt(7, order.getAmountDelivered());
                statement.setInt(8, order.getAmountClaimable());
                statement.setDouble(9, order.getPricePerItem());
                statement.setDouble(10, order.getAmountPaid());
                statement.setInt(11, order.isCancelled() ? 1 : 0);
                statement.setLong(12, order.getCreatedAtEpochMillis());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private OrderEntry readOrderEntry(ResultSet resultSet) throws SQLException {
        return new OrderEntry(
            resultSet.getString("order_id"),
            resultSet.getString("custom_item_id"),
            resultSet.getString("material"),
            parseEnchantments(resultSet.getString("enchantments_json")),
            resultSet.getInt("amount_ordered"),
            resultSet.getInt("amount_delivered"),
            resultSet.getInt("amount_claimable"),
            resultSet.getDouble("price_per_item"),
            resultSet.getDouble("amount_paid"),
            resultSet.getInt("cancelled") != 0,
            resultSet.getLong("created_at_epoch_millis")
        );
    }

    private Map<String, Integer> parseEnchantments(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<?, ?> raw = gson.fromJson(rawJson, Map.class);
            Map<String, Integer> enchantments = new LinkedHashMap<>();
            if (raw == null) {
                return enchantments;
            }
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                Integer level = parseEnchantmentLevel(entry.getValue());
                if (level != null) {
                    enchantments.put(String.valueOf(entry.getKey()), level);
                }
            }
            return enchantments;
        } catch (JsonParseException exception) {
            return new LinkedHashMap<>();
        }
    }

    private Integer parseEnchantmentLevel(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
        try {
            configureConnection(connection);
            return connection;
        } catch (SQLException exception) {
            closeQuietly(connection, exception);
            throw exception;
        }
    }

    private void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
        }
    }

    private void rollbackQuietly(Connection connection, SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    private void closeQuietly(Connection connection, SQLException cause) {
        try {
            connection.close();
        } catch (SQLException closeException) {
            cause.addSuppressed(closeException);
        }
    }

    private boolean isMetaEnabled(String key) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT value FROM storage_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && "true".equalsIgnoreCase(resultSet.getString("value"));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read storage metadata from SQLite", exception);
        }
    }

    private void setMeta(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO storage_meta (key, value)
            VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }

    private void archiveLegacyPlayerDataFolder() {
        if (!Files.isDirectory(playerDataFolder)) {
            return;
        }
        Path target = nextArchiveFolder();
        try {
            Files.move(playerDataFolder, target);
            plugin.getLogger().info("Renamed old playerdata folder to " + target.getFileName() + ".");
        } catch (IOException exception) {
            plugin.getLogger().warning("Imported old playerdata into SQLite, but could not rename playerdata folder: " + exception.getMessage());
        }
    }

    private Path nextArchiveFolder() {
        Path target = dataFolder.resolve(LEGACY_ARCHIVE_FOLDER);
        if (!Files.exists(target)) {
            return target;
        }
        String timestamp = ARCHIVE_TIMESTAMP_FORMAT.format(LocalDateTime.now());
        for (int attempt = 0; attempt < 100; attempt++) {
            Path candidate = dataFolder.resolve(LEGACY_ARCHIVE_FOLDER + "-" + timestamp + (attempt == 0 ? "" : "-" + attempt));
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        return dataFolder.resolve(LEGACY_ARCHIVE_FOLDER + "-" + System.currentTimeMillis());
    }

    private Object playerLock(UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, ignored -> new Object());
    }

    public static final class PlayerData {
        private int sortIndex;
        private int filterIndex;
        private List<OrderEntry> orders;

        private static PlayerData defaults() {
            PlayerData data = new PlayerData();
            data.sortIndex = 0;
            data.filterIndex = 0;
            data.orders = new ArrayList<>();
            return data;
        }

        private boolean sanitize() {
            boolean changed = false;
            if (sortIndex < 0 || sortIndex >= SORT_OPTION_COUNT) {
                sortIndex = 0;
                changed = true;
            }
            if (filterIndex < 0 || filterIndex >= FILTER_OPTION_COUNT) {
                filterIndex = 0;
                changed = true;
            }
            if (orders == null) {
                orders = new ArrayList<>();
                changed = true;
            }
            int originalSize = orders.size();
            for (OrderEntry order : orders) {
                if (order != null) {
                    changed |= order.sanitize();
                }
            }
            orders.removeIf(Objects::isNull);
            return changed || orders.size() != originalSize;
        }

        public int getSortIndex() {
            return sortIndex;
        }

        public void setSortIndex(int sortIndex) {
            this.sortIndex = sortIndex;
        }

        public int getFilterIndex() {
            return filterIndex;
        }

        public void setFilterIndex(int filterIndex) {
            this.filterIndex = filterIndex;
        }

        public List<OrderEntry> getOrders() {
            return orders;
        }

        private PlayerData copy() {
            PlayerData copy = new PlayerData();
            copy.sortIndex = sortIndex;
            copy.filterIndex = filterIndex;
            copy.orders = new ArrayList<>();
            for (OrderEntry order : orders) {
                copy.orders.add(order.copy());
            }
            return copy;
        }
    }

    public static final class OrderEntry {
        private String orderId;
        private String customItemId;
        private String material;
        private Map<String, Integer> enchantments;
        private int amountOrdered;
        private int amountDelivered;
        private int amountClaimable;
        private double pricePerItem;
        private double amountPaid;
        private boolean cancelled;
        private long createdAtEpochMillis;

        public OrderEntry() {
        }

        public OrderEntry(
            String orderId,
            String material,
            Map<String, Integer> enchantments,
            int amountOrdered,
            int amountDelivered,
            int amountClaimable,
            double pricePerItem,
            double amountPaid,
            boolean cancelled,
            long createdAtEpochMillis
        ) {
            this(orderId, null, material, enchantments, amountOrdered, amountDelivered, amountClaimable, pricePerItem, amountPaid, cancelled, createdAtEpochMillis);
        }

        public OrderEntry(
            String orderId,
            String customItemId,
            String material,
            Map<String, Integer> enchantments,
            int amountOrdered,
            int amountDelivered,
            int amountClaimable,
            double pricePerItem,
            double amountPaid,
            boolean cancelled,
            long createdAtEpochMillis
        ) {
            this.orderId = orderId;
            this.customItemId = customItemId;
            this.material = material;
            this.enchantments = enchantments;
            this.amountOrdered = amountOrdered;
            this.amountDelivered = amountDelivered;
            this.amountClaimable = amountClaimable;
            this.pricePerItem = pricePerItem;
            this.amountPaid = amountPaid;
            this.cancelled = cancelled;
            this.createdAtEpochMillis = createdAtEpochMillis;
            sanitize();
        }

        public String getOrderId() {
            return orderId;
        }

        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }

        public String getMaterial() {
            return material;
        }

        public void setMaterial(String material) {
            this.material = material;
        }

        public String getCustomItemId() {
            return customItemId;
        }

        public void setCustomItemId(String customItemId) {
            this.customItemId = customItemId;
        }

        public Map<String, Integer> getEnchantments() {
            return enchantments;
        }

        public void setEnchantments(Map<String, Integer> enchantments) {
            this.enchantments = enchantments;
        }

        public int getAmountOrdered() {
            return amountOrdered;
        }

        public void setAmountOrdered(int amountOrdered) {
            this.amountOrdered = amountOrdered;
        }

        public int getAmountDelivered() {
            return amountDelivered;
        }

        public void setAmountDelivered(int amountDelivered) {
            this.amountDelivered = amountDelivered;
        }

        public int getAmountClaimable() {
            return amountClaimable;
        }

        public void setAmountClaimable(int amountClaimable) {
            this.amountClaimable = amountClaimable;
        }

        public double getPricePerItem() {
            return pricePerItem;
        }

        public void setPricePerItem(double pricePerItem) {
            this.pricePerItem = pricePerItem;
        }

        public double getAmountPaid() {
            return amountPaid;
        }

        public void setAmountPaid(double amountPaid) {
            this.amountPaid = amountPaid;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        public double getTotalCost() {
            return amountOrdered * pricePerItem;
        }

        public long getCreatedAtEpochMillis() {
            return createdAtEpochMillis;
        }

        private boolean sanitize() {
            boolean changed = false;
            if (orderId == null || orderId.isBlank()) {
                orderId = UUID.randomUUID().toString();
                changed = true;
            }
            if (customItemId != null) {
                String originalCustomItemId = customItemId;
                customItemId = customItemId.trim().toLowerCase(Locale.ROOT);
                if (customItemId.isBlank()) {
                    customItemId = null;
                }
                if (!Objects.equals(originalCustomItemId, customItemId)) {
                    changed = true;
                }
            }
            String originalMaterial = material;
            Material resolvedMaterial = Material.matchMaterial(material == null ? "" : material);
            if (resolvedMaterial == null || !resolvedMaterial.isItem()) {
                material = Material.STONE.name();
                resolvedMaterial = Material.STONE;
            } else {
                material = resolvedMaterial.name();
            }
            if (!Objects.equals(originalMaterial, material)) {
                changed = true;
            }
            if (enchantments == null) {
                enchantments = new LinkedHashMap<>();
                changed = true;
            } else {
                Map<String, Integer> normalizedEnchantments = new LinkedHashMap<>();
                ItemStack probeItem = new ItemStack(resolvedMaterial);
                for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                    if (entry == null) {
                        continue;
                    }
                    String rawKey = entry.getKey() == null ? "" : entry.getKey();
                    NamespacedKey key = NamespacedKey.fromString(rawKey);
                    if (key == null) {
                        String normalized = rawKey.toLowerCase(Locale.ROOT);
                        key = normalized.contains(":")
                            ? NamespacedKey.fromString(normalized)
                            : NamespacedKey.minecraft(normalized);
                    }
                    if (key == null) {
                        continue;
                    }
                    Enchantment enchantment = Enchantment.getByKey(key);
                    if (enchantment == null) {
                        continue;
                    }
                    if (resolvedMaterial != Material.ENCHANTED_BOOK && !enchantment.canEnchantItem(probeItem)) {
                        continue;
                    }
                    Integer rawLevel = entry.getValue();
                    if (rawLevel == null || rawLevel <= 0) {
                        continue;
                    }
                    int maxLevel = Math.max(1, enchantment.getMaxLevel());
                    int level = Math.max(1, Math.min(maxLevel, rawLevel));
                    normalizedEnchantments.put(enchantment.getKey().toString(), level);
                }
                if (!enchantments.equals(normalizedEnchantments)) {
                    changed = true;
                }
                enchantments = normalizedEnchantments;
            }
            if (amountOrdered <= 0) {
                amountOrdered = 1;
                changed = true;
            }
            if (amountDelivered < 0) {
                amountDelivered = 0;
                changed = true;
            }
            if (amountClaimable < 0) {
                amountClaimable = 0;
                changed = true;
            }
            if (amountClaimable > amountDelivered) {
                amountClaimable = amountDelivered;
                changed = true;
            }
            if (!Double.isFinite(pricePerItem) || pricePerItem <= 0) {
                pricePerItem = 1;
                changed = true;
            }
            if (!Double.isFinite(amountPaid) || amountPaid < 0) {
                amountPaid = 0;
                changed = true;
            }
            double totalCost = getTotalCost();
            if (amountPaid > totalCost) {
                amountPaid = totalCost;
                changed = true;
            }
            if (createdAtEpochMillis < 0) {
                createdAtEpochMillis = 0;
                changed = true;
            }
            return changed;
        }

        private OrderEntry copy() {
            return new OrderEntry(
                orderId,
                customItemId,
                material,
                new LinkedHashMap<>(enchantments),
                amountOrdered,
                amountDelivered,
                amountClaimable,
                pricePerItem,
                amountPaid,
                cancelled,
                createdAtEpochMillis
            );
        }
    }

    public static final class PlayerOrderRecord {
        private final UUID playerId;
        private final OrderEntry order;

        public PlayerOrderRecord(UUID playerId, OrderEntry order) {
            this.playerId = playerId;
            this.order = order;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public OrderEntry getOrder() {
            return order;
        }
    }
}
