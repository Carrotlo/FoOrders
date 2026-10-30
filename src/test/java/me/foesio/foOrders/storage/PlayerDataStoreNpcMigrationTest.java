package me.foesio.foOrders.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDataStoreNpcMigrationTest {
    @TempDir Path directory;

    @Test
    void existingOrdersGainNpcNameWithoutLosingPlayerRows() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("orders.db"));
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE player_orders (order_id TEXT, owner_uuid TEXT)");
            statement.executeUpdate("INSERT INTO player_orders VALUES ('old-order', 'player-id')");

            PlayerDataStore.ensureNpcNameColumn(statement);
            PlayerDataStore.ensureNpcNameColumn(statement);
            try (var rows = statement.executeQuery("SELECT order_id, owner_uuid, npc_name FROM player_orders")) {
                rows.next();
                assertEquals("old-order", rows.getString("order_id"));
                assertEquals("player-id", rows.getString("owner_uuid"));
                assertEquals(null, rows.getString("npc_name"));
            }
            statement.executeUpdate("UPDATE player_orders SET npc_name = 'Village Trader'");
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("orders.db"));
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT npc_name FROM player_orders")) {
            rows.next();
            assertEquals("Village Trader", rows.getString(1));
        }
    }
}
