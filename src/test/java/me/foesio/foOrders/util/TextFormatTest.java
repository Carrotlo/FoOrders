package me.foesio.foOrders.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextFormatTest {
    @Test
    void insertedPlayerDataCannotTurnIntoAnotherPlaceholder() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("current", "{target} %theme%");
        values.put("target", "Zoeken");
        values.put("theme", "GREEN");

        assertEquals("Current {target} %theme%; target Zoeken; theme GREEN",
            TextFormat.applyPlaceholders("Current {current}; target {target}; theme %theme%", values));
        assertEquals("Leave {unknown} untouched", TextFormat.applyPlaceholders("Leave {unknown} untouched", values));
    }
}
