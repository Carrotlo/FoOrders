package me.foesio.foOrders;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdersMenuHistoryFeedbackTest {
    @Test
    void revokedHistoryAccessUsesTheSameConfiguredMessagesAsInitialDenial() throws IOException {
        assertEquals("history.disabled-config", OrdersMenuViewSupport.historyAccessFeedbackPath(false, false));
        assertEquals("history.disabled-config", OrdersMenuViewSupport.historyAccessFeedbackPath(false, true));
        assertEquals("history.access-denied", OrdersMenuViewSupport.historyAccessFeedbackPath(true, false));
        assertEquals("history.access-denied-player", OrdersMenuViewSupport.historyAccessFeedbackPath(true, true));

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("messages.yml")) {
            assertTrue(stream != null);
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            for (String path : new String[]{"history.disabled-config", "history.access-denied",
                "history.access-denied-player"}) {
                assertTrue(bundled.isString(path), path);
            }
        }
    }
}
