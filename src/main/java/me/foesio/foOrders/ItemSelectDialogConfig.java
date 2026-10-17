package me.foesio.foOrders;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Set;

record ItemSelectDialogConfig(
    boolean enabled,
    String title,
    String emptyMessage,
    int bodyWidth,
    int columns,
    int pageSize,
    List<String> buttonOrder,
    boolean canCloseWithEscape,
    boolean pause,
    int previewWidth,
    int previewHeight,
    boolean previewDecorations,
    boolean previewTooltip,
    String searchInputLabel,
    int searchInputWidth,
    boolean searchInputLabelVisible,
    int searchInputMaxLength,
    String searchButtonLabel,
    String searchButtonTooltip,
    int searchButtonWidth,
    Button sortButton,
    Button filterButton,
    Button previousButton,
    Button nextButton,
    String choiceLabel,
    String customChoiceLabel,
    String currentChoiceTooltip,
    String otherChoiceTooltip,
    String currentChoiceColor,
    String otherChoiceColor,
    int choiceButtonWidth
) {
    private static final Set<String> BUTTON_IDS = Set.of("search", "sort", "filter", "previous", "choices", "next");

    static ItemSelectDialogConfig load(FileConfiguration config) {
        boolean complete = config.isBoolean("enabled") && config.isString("title")
            && optionalString(config, "empty-message") && config.isSet("body-width")
            && config.isSet("columns") && config.isSet("page-size")
            && config.isList("button-order") && config.isBoolean("can-close-with-escape")
            && config.isBoolean("pause") && config.isSet("preview.width")
            && config.isSet("preview.height") && config.isBoolean("preview.show-decorations")
            && config.isBoolean("preview.show-tooltip")
            && config.isString("choice-button.label") && config.isString("choice-button.custom-label")
            && optionalString(config, "choice-button.current-tooltip")
            && optionalString(config, "choice-button.other-tooltip")
            && optionalString(config, "choice-button.current-color")
            && optionalString(config, "choice-button.other-color") && config.isSet("choice-button.width");
        for (String path : new String[] {"body-width", "columns", "preview.width", "preview.height",
            "page-size", "choice-button.width"}) {
            complete &= config.isInt(path) && config.getInt(path) > 0;
        }
        List<String> order = config.getStringList("button-order");
        complete &= validButtonOrder(order);
        if (order.contains("search")) {
            complete &= config.isString("search-input.label")
                && config.isBoolean("search-input.label-visible")
                && positiveInt(config, "search-input.width")
                && positiveInt(config, "search-input.max-length")
                && buttonReady(config, "search-button");
        }
        if (order.contains("sort")) {
            complete &= buttonReady(config, "sort-button");
        }
        if (order.contains("filter")) {
            complete &= buttonReady(config, "filter-button");
        }
        if (order.contains("previous")) {
            complete &= buttonReady(config, "previous-button") && buttonReady(config, "next-button");
        }
        return new ItemSelectDialogConfig(
            complete && config.getBoolean("enabled"),
            config.getString("title", ""),
            config.getString("empty-message", ""),
            positive(config, "body-width"),
            positive(config, "columns"),
            positive(config, "page-size"),
            List.copyOf(order),
            config.getBoolean("can-close-with-escape"),
            config.getBoolean("pause"),
            positive(config, "preview.width"),
            positive(config, "preview.height"),
            config.getBoolean("preview.show-decorations"),
            config.getBoolean("preview.show-tooltip"),
            config.getString("search-input.label", ""),
            positive(config, "search-input.width"),
            config.getBoolean("search-input.label-visible"),
            positive(config, "search-input.max-length"),
            config.getString("search-button.label", ""),
            config.getString("search-button.tooltip", ""),
            positive(config, "search-button.width"),
            button(config, "sort-button"),
            button(config, "filter-button"),
            button(config, "previous-button"),
            button(config, "next-button"),
            config.getString("choice-button.label", ""),
            config.getString("choice-button.custom-label", ""),
            config.getString("choice-button.current-tooltip", ""),
            config.getString("choice-button.other-tooltip", ""),
            config.getString("choice-button.current-color", ""),
            config.getString("choice-button.other-color", ""),
            positive(config, "choice-button.width")
        );
    }

    private static int positive(FileConfiguration config, String path) {
        int value = config.getInt(path);
        return value > 0 ? value : 1;
    }

    private static boolean buttonReady(FileConfiguration config, String root) {
        return config.isString(root + ".label") && optionalString(config, root + ".tooltip")
            && positiveInt(config, root + ".width");
    }

    private static boolean optionalString(FileConfiguration config, String path) {
        return !config.isSet(path) || config.isString(path);
    }

    private static boolean positiveInt(FileConfiguration config, String path) {
        return config.isInt(path) && config.getInt(path) > 0;
    }

    private static boolean validButtonOrder(List<String> order) {
        return order.contains("choices") && order.stream().allMatch(id -> id != null && BUTTON_IDS.contains(id))
            && Set.copyOf(order).size() == order.size()
            && order.contains("previous") == order.contains("next");
    }

    boolean canReachChoiceCount(int choiceCount) {
        return (canCloseWithEscape || choiceCount > 0)
            && (buttonOrder.contains("next") || choiceCount <= pageSize);
    }

    private static Button button(FileConfiguration config, String root) {
        return new Button(config.getString(root + ".label", ""),
            config.getString(root + ".tooltip", ""), positive(config, root + ".width"));
    }

    record Button(String label, String tooltip, int width) {
    }
}
