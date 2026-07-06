package me.foesio.foOrders.util;

import me.foesio.core.text.FoText;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TextFormat {
    private TextFormat() {
    }

    public static String colorize(String message) {
        return FoText.color(message);
    }

    public static String applyPlaceholders(String message, Map<String, String> placeholders) {
        String formatted = message == null ? "" : message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            formatted = formatted
                .replace("{" + entry.getKey() + "}", value)
                .replace("%" + entry.getKey() + "%", value);
        }
        return formatted;
    }

    public static Map<String, String> placeholders(Object... values) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        if (values == null) {
            return placeholders;
        }
        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(String.valueOf(values[index]), String.valueOf(values[index + 1]));
        }
        return placeholders;
    }
}
