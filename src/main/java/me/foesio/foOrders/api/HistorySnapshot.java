package me.foesio.foOrders.api;

import java.util.Map;

/** A retained history row; eventKey may be empty for legacy free-text rows. */
public record HistorySnapshot(long timestamp, String action, String details, String eventKey,
                              Map<String, String> values) {
    public HistorySnapshot {
        values = Map.copyOf(values);
    }
}
