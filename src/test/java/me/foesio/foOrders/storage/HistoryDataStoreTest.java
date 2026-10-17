package me.foesio.foOrders.storage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryDataStoreTest {
    @Test
    void newEventsPersistRawValuesWithoutSavingRenderedEnglish() {
        HistoryDataStore.HistoryEntry event = new HistoryDataStore.HistoryEntry(123L, "", "",
            "order-created", Map.of("material", "DIAMOND", "amount", "5", "price", "1.25"));
        Map<String, Object> encoded = HistoryDataStore.encodeEntry(event);

        assertEquals("order-created", encoded.get("event-key"));
        assertFalse(encoded.containsKey("action"));
        assertFalse(encoded.containsKey("details"));
        assertEquals(event, HistoryDataStore.decodeEntry(encoded));
    }

    @Test
    void priorRenderedHistorySurvivesWithoutRewriting() {
        Map<String, Object> old = Map.of("timestamp", 456L,
            "action", "Order Claimed", "details", "Claimed 2x Diamond (Inventory)");
        HistoryDataStore.HistoryEntry restored = HistoryDataStore.decodeEntry(old);

        assertEquals("", restored.eventKey());
        assertEquals("Order Claimed", restored.action());
        assertEquals("Claimed 2x Diamond (Inventory)", restored.details());
        assertTrue(HistoryDataStore.encodeEntry(restored).containsKey("action"));
    }

    @Test
    void incompleteLegacyHistoryDoesNotPersistHardcodedEnglish() {
        HistoryDataStore.HistoryEntry incomplete = HistoryDataStore.decodeEntry(
            Map.of("timestamp", 789L));
        assertEquals("", incomplete.action());
        assertEquals("", incomplete.details());
        Map<String, Object> encoded = HistoryDataStore.encodeEntry(incomplete);
        assertEquals("", encoded.get("action"));
        assertEquals("", encoded.get("details"));

        HistoryDataStore.HistoryEntry authored = HistoryDataStore.decodeEntry(
            Map.of("timestamp", 790L, "action", "  Mijn bestelling  ", "details", "  Beschrijving  "));
        assertEquals("  Mijn bestelling  ", authored.action());
        assertEquals("  Beschrijving  ", HistoryDataStore.encodeEntry(authored).get("details"));
    }
}
