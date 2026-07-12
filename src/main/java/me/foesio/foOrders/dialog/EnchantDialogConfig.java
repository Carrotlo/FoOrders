package me.foesio.foOrders.dialog;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

record EnchantDialogConfig(
    boolean enabled,
    String title,
    String titleIcon,
    boolean canCloseWithEscape,
    boolean pause,
    int maxLevelColumns,
    int columns,
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
        int maxLevelColumns = clamp(config.getInt("max-level-columns", 5), 1, 10);
        return new EnchantDialogConfig(
            config.getBoolean("enabled", true),
            config.getString("title", "Choose Enchantments"),
            config.getString("title-icon", "experience_bottle"),
            config.getBoolean("can-close-with-escape", true),
            config.getBoolean("pause", false),
            maxLevelColumns,
            clamp(config.getInt("columns", maxLevelColumns + 1), 2, 12),
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
            section.getString("label", DEFAULT_ENCHANT_BUTTON.label()),
            section.getString("tooltip", DEFAULT_ENCHANT_BUTTON.tooltip()),
            clamp(section.getInt("width", DEFAULT_ENCHANT_BUTTON.width()), 1, 1024),
            section.getString("color", DEFAULT_ENCHANT_BUTTON.color()),
            section.getString("selected-color", DEFAULT_ENCHANT_BUTTON.selectedColor()),
            section.getString("curse-color", DEFAULT_ENCHANT_BUTTON.curseColor())
        );
    }

    private static LevelButton levelButton(ConfigurationSection section) {
        if (section == null) {
            return DEFAULT_LEVEL_BUTTON;
        }
        return new LevelButton(
            section.getString("label", DEFAULT_LEVEL_BUTTON.label()),
            section.getString("tooltip", DEFAULT_LEVEL_BUTTON.tooltip()),
            clamp(section.getInt("width", DEFAULT_LEVEL_BUTTON.width()), 1, 1024),
            section.getString("color", DEFAULT_LEVEL_BUTTON.color()),
            section.getString("selected-color", DEFAULT_LEVEL_BUTTON.selectedColor())
        );
    }

    private static Button button(ConfigurationSection section, Button fallback) {
        if (section == null) {
            return fallback;
        }
        return new Button(
            section.getString("label", fallback.label()),
            section.getString("tooltip", fallback.tooltip()),
            clamp(section.getInt("width", fallback.width()), 1, 1024),
            section.getString("icon", fallback.icon())
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
