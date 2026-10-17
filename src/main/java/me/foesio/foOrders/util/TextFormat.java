package me.foesio.foOrders.util;

import me.foesio.core.text.FoText;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormat {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}|%([^%]+)%");

    private TextFormat() {
    }

    public static String colorize(String message) {
        return FoText.color(message);
    }

    public static String applyPlaceholders(String message, Map<String, String> placeholders) {
        Matcher matcher = PLACEHOLDER.matcher(message == null ? "" : message);
        StringBuilder formatted = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            String value = placeholders != null && placeholders.containsKey(key)
                ? placeholders.get(key) : matcher.group();
            matcher.appendReplacement(formatted, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(formatted);
        return formatted.toString();
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
