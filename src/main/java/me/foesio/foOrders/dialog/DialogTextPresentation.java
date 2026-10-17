package me.foesio.foOrders.dialog;

import me.foesio.foOrders.util.TextFormat;
import me.foesio.core.text.PromptNormalizer;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Final owner-authored presentation for one public text input and its chat fallback. */
record DialogTextPresentation(
    String title,
    String titleIcon,
    List<String> body,
    String fieldLabel,
    String initialValue,
    String placeholder,
    Button confirm,
    Button cancel,
    List<String> buttonOrder,
    int columns,
    int bodyWidth,
    int inputWidth,
    int maxLength,
    boolean labelVisible,
    boolean canCloseWithEscape,
    boolean pause,
    String targetLabel,
    String clearKeyword,
    String fallbackCancelKeyword,
    List<String> fallbackLines,
    boolean nativeReady,
    boolean fallbackReady
) {
    static DialogTextPresentation load(FileConfiguration file) {
        Button confirm = button(file, "confirm-button");
        Button cancel = button(file, "cancel-button");
        List<String> order = file.getStringList("button-order");
        boolean validOrder = order.contains("confirm") && order.size() <= 2
            && order.stream().allMatch(id -> "confirm".equals(id) || "cancel".equals(id))
            && order.stream().distinct().count() == order.size()
            && (order.contains("cancel") || file.getBoolean("can-close-with-escape"));
        boolean nativeReady = file.isString("title") && file.isString("field-label")
            && file.isString("confirm-button.label")
            && validOrder && file.isInt("columns") && file.getInt("columns") >= 1
            && file.getInt("columns") <= 2
            && hasPositiveInt(file, "body-width") && hasPositiveInt(file, "input-width")
            && hasPositiveInt(file, "max-length") && hasPositiveInt(file, "confirm-button.width")
            && file.isBoolean("label-visible") && file.isBoolean("can-close-with-escape")
            && file.isBoolean("pause");
        if (order.contains("cancel")) {
            nativeReady &= file.isString("cancel-button.label")
                && hasPositiveInt(file, "cancel-button.width");
        }
        boolean fallbackReady = hasText(file, "fallback.cancel-keyword") && file.isList("fallback.lines");
        return new DialogTextPresentation(
            file.getString("title", ""), file.getString("title-icon", ""),
            body(file), file.getString("field-label", ""), file.getString("initial-value", ""),
            file.getString("placeholder", ""), confirm, cancel, List.copyOf(order), file.getInt("columns", 0),
            file.getInt("body-width", 0), file.getInt("input-width", 0), file.getInt("max-length", 0),
            file.getBoolean("label-visible"), file.getBoolean("can-close-with-escape"),
            file.getBoolean("pause"), file.getString("target-label", ""),
            file.getString("clear-keyword", ""), file.getString("fallback.cancel-keyword", ""),
            file.getStringList("fallback.lines"), nativeReady, fallbackReady
        );
    }

    DialogTextPresentation render(Map<String, String> placeholders) {
        Map<String, String> renderedValues = new LinkedHashMap<>(placeholders);
        String renderedClearKeyword = PromptNormalizer.normalize(replace(clearKeyword, renderedValues));
        renderedValues.put("clear_keyword", renderedClearKeyword);
        String renderedCancelKeyword = PromptNormalizer.normalize(replace(fallbackCancelKeyword, renderedValues));
        renderedValues.put("cancel_keyword", renderedCancelKeyword);
        List<String> activeFallbackLines = renderedClearKeyword.isBlank()
            ? fallbackLines.stream().filter(line -> !line.contains("{clear_keyword}")
                && !line.contains("%clear_keyword%")).toList()
            : fallbackLines;
        return new DialogTextPresentation(
            replace(title, renderedValues), replace(titleIcon, renderedValues), replace(body, renderedValues),
            replace(fieldLabel, renderedValues), replace(initialValue, renderedValues),
            replace(placeholder, renderedValues), confirm.render(renderedValues), cancel.render(renderedValues),
            buttonOrder, columns, bodyWidth, inputWidth, maxLength, labelVisible, canCloseWithEscape, pause,
            replace(targetLabel, renderedValues), renderedClearKeyword,
            renderedCancelKeyword, replace(activeFallbackLines, renderedValues),
            nativeReady, fallbackReady && !renderedCancelKeyword.isBlank()
                && (renderedClearKeyword.isBlank()
                    || !renderedClearKeyword.equalsIgnoreCase(renderedCancelKeyword))
        );
    }

    private static Button button(FileConfiguration file, String root) {
        return new Button(file.getString(root + ".label", ""), file.getString(root + ".tooltip", ""),
            file.getString(root + ".icon", ""), file.getInt(root + ".width", 0));
    }

    private static List<String> body(FileConfiguration file) {
        if (file.isList("body")) {
            return file.getStringList("body");
        }
        String raw = file.getString("body", "");
        return raw.isEmpty() ? List.of() : List.of(raw.split("\\R", -1));
    }

    private static boolean hasText(FileConfiguration file, String path) {
        String value = file.getString(path);
        return value != null && !value.isBlank();
    }

    private static boolean hasPositiveInt(FileConfiguration file, String path) {
        return file.isInt(path) && file.getInt(path, 0) > 0;
    }

    private static List<String> replace(List<String> lines, Map<String, String> placeholders) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(replace(line, placeholders));
        }
        return List.copyOf(result);
    }

    private static String replace(String raw, Map<String, String> placeholders) {
        return TextFormat.applyPlaceholders(raw, placeholders);
    }

    record Button(String label, String tooltip, String icon, int width) {
        Button render(Map<String, String> placeholders) {
            return new Button(replace(label, placeholders), replace(tooltip, placeholders),
                replace(icon, placeholders), width);
        }
    }
}
