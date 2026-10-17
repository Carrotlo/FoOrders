package me.foesio.foOrders.dialog;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

record EnchantDialogConfig(
    boolean enabled,
    String title,
    String titleIcon,
    boolean canCloseWithEscape,
    boolean pause,
    int maxLevelColumns,
    int columns,
    List<String> actionOrder,
    int itemWidth,
    int itemHeight,
    boolean showItemDecorations,
    boolean showItemTooltip,
    EnchantButton enchantButton,
    LevelButton levelButton,
    Button backButton,
    Button continueButton,
    Button spacerButton
) {
    private static final EnchantButton DEFAULT_ENCHANT_BUTTON = new EnchantButton(
        "{color}{enchant}",
        "Click to toggle {enchant}",
        144,
        "#a7b8b0",
        "#a7b8b0",
        "#ff5d73"
    );
    private static final LevelButton DEFAULT_LEVEL_BUTTON = new LevelButton(
        "{color}{roman}",
        "Set {enchant} to {roman}",
        36,
        "#a7b8b0",
        "#03fc88"
    );
    private static final Button DEFAULT_BACK_BUTTON = new Button("#a7b8b0Back to Items", "Choose another item", 180, "arrow");
    private static final Button DEFAULT_CONTINUE_BUTTON = new Button("#3ecf8eConfirm", "Confirm enchantments", 180, "emerald");
    private static final Button DEFAULT_SPACER_BUTTON = new Button(" ", "", 1, "");

    static EnchantDialogConfig load(FileConfiguration config) {
        boolean complete = config.isBoolean("enabled") && config.isString("title")
            && config.isBoolean("can-close-with-escape") && config.isBoolean("pause")
            && hasIntInRange(config, "max-level-columns", 1, 10)
            && hasIntInRange(config, "columns", 2, 12)
            && validActionOrder(config.getStringList("action-order"))
            && hasIntInRange(config, "item.width", 1, 256)
            && hasIntInRange(config, "item.height", 1, 256)
            && config.isBoolean("item.show-decorations") && config.isBoolean("item.show-tooltip")
            && config.isString("enchant-button.label")
            && hasIntInRange(config, "enchant-button.width", 1, 1024)
            && config.isString("level-button.label")
            && hasIntInRange(config, "level-button.width", 1, 1024)
            && config.isString("spacer-button.label")
            && hasIntInRange(config, "spacer-button.width", 1, 1024);
        List<String> actionOrder = config.getStringList("action-order");
        if (actionOrder.contains("back")) {
            complete &= config.isString("back-button.label")
                && hasIntInRange(config, "back-button.width", 1, 1024);
        }
        if (actionOrder.contains("continue")) {
            complete &= config.isString("continue-button.label")
                && hasIntInRange(config, "continue-button.width", 1, 1024);
        }
        int maxLevelColumns = clamp(config.getInt("max-level-columns", 5), 1, 10);
        return new EnchantDialogConfig(
            complete && config.getBoolean("enabled"),
            config.getString("title", ""),
            config.getString("title-icon", ""),
            config.getBoolean("can-close-with-escape", true),
            config.getBoolean("pause", false),
            maxLevelColumns,
            clamp(config.getInt("columns", maxLevelColumns + 1), 2, 12),
            List.copyOf(actionOrder),
            clamp(config.getInt("item.width", 16), 1, 256),
            clamp(config.getInt("item.height", 16), 1, 256),
            config.getBoolean("item.show-decorations", true),
            config.getBoolean("item.show-tooltip", true),
            enchantButton(config.getConfigurationSection("enchant-button")),
            levelButton(config.getConfigurationSection("level-button")),
            button(config.getConfigurationSection("back-button"), DEFAULT_BACK_BUTTON),
            button(config.getConfigurationSection("continue-button"), DEFAULT_CONTINUE_BUTTON),
            button(config.getConfigurationSection("spacer-button"), DEFAULT_SPACER_BUTTON)
        );
    }

    private static EnchantButton enchantButton(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT_ENCHANT_BUTTON;
        }
        return new EnchantButton(
            section.getString("label", ""),
            section.getString("tooltip", ""),
            clamp(section.getInt("width", DEFAULT_ENCHANT_BUTTON.width()), 1, 1024),
            section.getString("color", ""),
            section.getString("selected-color", ""),
            section.getString("curse-color", "")
        );
    }

    private static LevelButton levelButton(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT_LEVEL_BUTTON;
        }
        return new LevelButton(
            section.getString("label", ""),
            section.getString("tooltip", ""),
            clamp(section.getInt("width", DEFAULT_LEVEL_BUTTON.width()), 1, 1024),
            section.getString("color", ""),
            section.getString("selected-color", "")
        );
    }

    private static Button button(ConfigurationSection section, Button fallback) {
        if (section == null) {
            return fallback;
        }
        return new Button(
            section.getString("label", ""),
            section.getString("tooltip", ""),
            clamp(section.getInt("width", fallback.width()), 1, 1024),
            section.getString("icon", "")
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean validActionOrder(List<String> order) {
        return !order.isEmpty() && order.size() <= 2
            && order.stream().allMatch(id -> "back".equals(id) || "continue".equals(id))
            && order.stream().distinct().count() == order.size();
    }

    private static boolean hasIntInRange(FileConfiguration config, String path, int min, int max) {
        return config.isInt(path) && config.getInt(path) >= min && config.getInt(path) <= max;
    }

    record EnchantButton(
        String label,
        String tooltip,
        int width,
        String color,
        String selectedColor,
        String curseColor
    ) {
    }

    record LevelButton(
        String label,
        String tooltip,
        int width,
        String color,
        String selectedColor
    ) {
    }

    record Button(String label, String tooltip, int width, String icon) {
    }
}
