package me.foesio.foOrders.config;

import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiResourceLoader;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.foOrders.FoOrders;
import me.foesio.foOrders.PluginMessages;
import me.foesio.foOrders.util.TextFormat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class GuiConfigManager {
    private static final String LEGACY_FILE_NAME = "guis/orders.yml";
    // Reserve a plugin-level version above the existing message migrations so
    // later config migrations cannot accidentally skip this split.
    private static final int GUI_SPLIT_MIGRATION_VERSION = 10;
    private static final int GUI_BUTTON_SPRITE_MIGRATION_VERSION = 11;
    private static final int GUI_BACK_SLOT_MIGRATION_VERSION = 12;
    private static final int GUI_PUBLIC_TEXT_MIGRATION_VERSION = 13;
    private static final Map<String, List<String>> NEW_PUBLIC_TEXT_PATHS = Map.of(
        "main", List.of("items.sort.lore", "items.sort.selected-option", "items.sort.other-option",
            "items.filter.lore", "items.filter.selected-option", "items.filter.other-option",
            "items.order-entry.lore.admin-cancel"),
        "your-orders", List.of("items.order-entry.name", "items.order-entry.lore.amount",
            "items.order-entry.lore.price", "items.order-entry.lore.delivered", "items.order-entry.lore.paid"),
        "new-order", List.of("items.enchants.summary-line"),
        "item-select", List.of("items.sort.lore", "items.sort.selected-option", "items.sort.other-option",
            "items.filter.lore", "items.filter.selected-option", "items.filter.other-option",
            "items.entry.name", "items.entry.lore", "items.custom-entry.name", "items.custom-entry.lore",
            "labels.enchant-enabled", "labels.enchant-disabled"),
        "enchant-select", List.of("items.entry.name", "items.entry.lore", "labels.none"),
        "manage-order", List.of("items.claim.lore", "items.claim.stack-line", "items.claim.more-line",
            "items.admin-actions.material", "items.admin-actions.name", "items.admin-actions.lore",
            "items.admin-actions.refund-line", "items.admin-actions.no-refund-line"),
        "history", List.of("labels.order-tab-title", "labels.deliver-tab-title", "labels.unknown-time",
            "labels.unknown-event")
    );
    private static final Map<String, Integer> NEW_BACK_SLOTS = Map.of(
        "item-select", 46,
        "enchant-select", 46,
        "claim-order", 49
    );
    private static final Map<String, String> PUBLIC_GUI_FILES = Map.ofEntries(
        Map.entry("main", "guis/main.yml"),
        Map.entry("your-orders", "guis/your-orders.yml"),
        Map.entry("new-order", "guis/new-order.yml"),
        Map.entry("item-select", "guis/item-select.yml"),
        Map.entry("enchant-select", "guis/enchant-select.yml"),
        Map.entry("manage-order", "guis/manage-order.yml"),
        Map.entry("claim-order", "guis/claim-order.yml"),
        Map.entry("deliver", "guis/deliver.yml"),
        Map.entry("delivery-confirm", "guis/delivery-confirm.yml"),
        Map.entry("history", "guis/history.yml")
    );
    private static final String LEGACY_BUTTONS_FILE_NAME = "guis/buttons.yml";
    private static final List<String> BUTTON_TEMPLATE_KEYS = List.of(
        "back",
        "previous-page",
        "next-page",
        "search"
    );
    private static final String NEW_ORDER_CONFIRM_LORE_PATH = "items.new-order.confirm.lore";
    private static final List<String> LEGACY_NEW_ORDER_CONFIRM_LORE = List.of(
        "#ffffffClick to confirm order",
        "#a7b8b0(Total: ${total})"
    );
    private static final String TAX_DISCLOSURE_LORE = "#a7b8b0Tax ({tax_percent}%): {theme}${tax}";
    private static final String DEFAULT_THEME_COLOR = "#03fc88";
    private static final Pattern DEFAULT_THEME_COLOR_PATTERN = Pattern.compile("(?i)#03fc88");
    private static final List<String> REMOVED_GUI_PATHS = List.of(
        "titles.admin-actions",
        "titles.admin-item-editor",
        "titles.admin-item-edit",
        "items.main.order-entry.lore.admin-cancel",
        "items.main.order-entry.lore.enchants-title",
        "items.manage-order.admin-actions",
        "items.admin-actions",
        "items.admin-item-editor",
        "items.admin-item-edit"
    );

    private final Plugin plugin;
    private final JavaPlugin javaPlugin;
    private final FoMigrationStore migrations;
    private final Set<String> warnedMessages = ConcurrentHashMap.newKeySet();
    private final Map<TitleCacheKey, String> titleCache = new ConcurrentHashMap<>();
    private final Map<GuiItemCacheKey, GuiItem> itemCache = new ConcurrentHashMap<>();
    private final Map<LabelsCacheKey, List<String>> labelsCache = new ConcurrentHashMap<>();
    private final Map<SlotCacheKey, Integer> slotCache = new ConcurrentHashMap<>();
    private final Map<SlotsCacheKey, List<Integer>> slotsCache = new ConcurrentHashMap<>();
    private final Map<String, GuiButtonConfig> buttonConfigs = new ConcurrentHashMap<>();
    private YamlConfiguration guis = new YamlConfiguration();
    private volatile Map<String, Integer> effectiveItemSlots = Map.of();
    private volatile GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private volatile int revision;

    public GuiConfigManager(Plugin plugin, FoMigrationStore migrations) {
        this.plugin = plugin;
        this.javaPlugin = (JavaPlugin) plugin;
        this.migrations = Objects.requireNonNull(migrations, "migrations");
    }

    public void reload() {
        warnedMessages.clear();
        clearCaches();
        migrations.runToVersion(GUI_SPLIT_MIGRATION_VERSION, this::migrateLegacyAggregate);
        if (migrations.version() < GUI_SPLIT_MIGRATION_VERSION) {
            throw new IllegalStateException("Could not migrate the legacy FoOrders GUI file; existing GUI settings were not replaced.");
        }
        migrations.runToVersion(GUI_BUTTON_SPRITE_MIGRATION_VERSION, this::migrateDefaultBackAndSearchSprites);
        if (migrations.version() < GUI_BUTTON_SPRITE_MIGRATION_VERSION) {
            throw new IllegalStateException("Could not finish the FoOrders GUI button migration.");
        }
        migrations.runToVersion(GUI_BACK_SLOT_MIGRATION_VERSION, this::migrateNewBackSlots);
        if (migrations.version() < GUI_BACK_SLOT_MIGRATION_VERSION) {
            throw new IllegalStateException("Could not add the new FoOrders GUI Back button slots.");
        }
        migrations.runToVersion(GUI_PUBLIC_TEXT_MIGRATION_VERSION, this::migratePublicTextTemplates);
        if (migrations.version() < GUI_PUBLIC_TEXT_MIGRATION_VERSION) {
            throw new IllegalStateException("Could not add the FoOrders public GUI text templates.");
        }

        YamlConfiguration loaded = new YamlConfiguration();
        Map<String, GuiButtonConfig> loadedButtons = new LinkedHashMap<>();
        Map<String, Integer> loadedSlots = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            String guiPath = entry.getKey();
            YamlConfiguration active = (YamlConfiguration) GuiResourceLoader.load(javaPlugin, entry.getValue());
            mergeActiveGui(loaded, guiPath, active);
            loadedButtons.put(guiPath, loadButtons(active));
            loadedSlots.putAll(resolveItemSlots(guiPath, active, bundledDefaults(entry.getValue()), this::warn));
        }

        guis = loaded;
        effectiveItemSlots = Map.copyOf(loadedSlots);
        buttonConfigs.clear();
        buttonConfigs.putAll(loadedButtons);
        buttons = buttonConfigs.getOrDefault("main", GuiButtonConfig.defaults());
        revision++;
        clearCaches();
    }

    static void mergeActiveGui(YamlConfiguration combined, String guiPath, YamlConfiguration active) {
        if (active.isSet("title")) {
            combined.set("titles." + guiPath, active.get("title"));
        }
        copySection(active.getConfigurationSection("items"), combined, "items." + guiPath);
        copySection(active.getConfigurationSection("labels"), combined, "labels." + guiPath);
        copySection(active.getConfigurationSection("layout"), combined, "layout." + guiPath);
    }

    private static void copySection(ConfigurationSection source, YamlConfiguration target, String targetPath) {
        if (source == null) {
            return;
        }
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            target.set(targetPath + "." + key, source.get(key));
        }
    }

    /**
     * Splits the old aggregate resource without touching owner values. This is
     * intentionally local to FoOrders because the legacy title is a scalar,
     * while the generic Core section splitter handles section-only mappings.
     */
    private boolean migrateLegacyAggregate() {
        File legacyFile = new File(plugin.getDataFolder(), LEGACY_FILE_NAME);
        if (!legacyFile.isFile()) {
            return true;
        }

        File backup = new File(legacyFile.getParentFile(), legacyFile.getName() + ".legacy-backup");
        try {
            if (!backup.exists()) {
                Files.copy(legacyFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException exception) {
            warn("Could not retain legacy GUI backup " + backup + "; leaving " + LEGACY_FILE_NAME + " untouched.");
            return false;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection legacyButtons = legacy.getConfigurationSection("buttons");
        if (legacyButtons == null) {
            File oldButtonsFile = new File(plugin.getDataFolder(), LEGACY_BUTTONS_FILE_NAME);
            if (oldButtonsFile.isFile()) {
                YamlConfiguration oldButtons = YamlConfiguration.loadConfiguration(oldButtonsFile);
                legacyButtons = oldButtons.getConfigurationSection("buttons");
                if (legacyButtons == null) {
                    legacyButtons = oldButtons;
                }
            }
        }

        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        Map<File, YamlConfiguration> targets = new LinkedHashMap<>();
        for (String guiPath : PUBLIC_GUI_FILES.keySet()) {
            String legacyTitlePath = "titles." + guiPath;
            if (!legacy.isSet(legacyTitlePath)) {
                warn("GUI migration could not map legacy section '" + legacyTitlePath
                    + "' from " + LEGACY_FILE_NAME + "; leaving the legacy file untouched.");
                return false;
            }
        }
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            String guiPath = entry.getKey();
            String resourcePath = entry.getValue();
            String legacyTitlePath = "titles." + guiPath;

            File targetFile = new File(plugin.getDataFolder(), resourcePath);
            boolean existed = targetFile.isFile();
            if (!existed) {
                plugin.saveResource(resourcePath, false);
            }
            try {
                originalBytes.put(targetFile, existed ? Files.readAllBytes(targetFile.toPath()) : null);
            } catch (IOException exception) {
                warn("Could not read GUI migration target " + resourcePath + ".");
                restoreFiles(originalBytes);
                return false;
            }

            YamlConfiguration target = YamlConfiguration.loadConfiguration(targetFile);
            YamlConfiguration defaults = bundledDefaults(resourcePath);
            copyIfUntouched(target, defaults, "title", legacy.get(legacyTitlePath));
            copySectionIfPresent(legacy.getConfigurationSection("items." + guiPath), target, defaults, "items");
            copySectionIfPresent(legacy.getConfigurationSection("layout." + guiPath), target, defaults, "layout");
            copyLabels(legacy, guiPath, target, defaults);
            copyLegacyButtonOverrides(legacy, guiPath, target, defaults);
            copySectionIfPresent(legacyButtons, target, defaults, "buttons");
            targets.put(targetFile, target);
        }

        for (Map.Entry<File, YamlConfiguration> target : targets.entrySet()) {
            try {
                target.getValue().save(target.getKey());
            } catch (IOException exception) {
                restoreFiles(originalBytes);
                warn("GUI split migration failed; all target files were restored.");
                return false;
            }
        }

        plugin.getLogger().info("Migrated FoOrders public GUI settings from " + LEGACY_FILE_NAME
            + " into " + targets.size() + " per-GUI files. The legacy file was retained as an inactive backup.");
        return true;
    }

    private void copyLabels(YamlConfiguration legacy, String guiPath, YamlConfiguration target, YamlConfiguration defaults) {
        ConfigurationSection labels = legacy.getConfigurationSection("labels");
        if (labels == null) {
            return;
        }
        List<String> keys = switch (guiPath) {
            case "main" -> List.of("main-sort-options", "filter-options");
            case "item-select" -> List.of("item-sort-options", "filter-options");
            default -> List.of();
        };
        for (String key : keys) {
            if (labels.isSet(key)) {
                copyIfUntouched(target, defaults, "labels." + key, labels.get(key));
            }
        }
    }

    static void copySectionIfPresent(ConfigurationSection source, YamlConfiguration target,
                                     YamlConfiguration defaults, String targetPath) {
        if (source == null) {
            return;
        }
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            String destination = targetPath + "." + key;
            // Removed legacy options remain in orders.yml and its backup; do
            // not present them as active settings in the new per-GUI file.
            if (defaults.isSet(destination)) {
                copyIfUntouched(target, defaults, destination, source.get(key));
            }
        }
    }

    static void copyLegacyButtonOverrides(YamlConfiguration legacy, String guiPath,
                                          YamlConfiguration target, YamlConfiguration defaults) {
        switch (guiPath) {
            case "main", "item-select", "enchant-select", "claim-order", "history" -> {
                copyLegacyButtonOverride(legacy, guiPath, "previous-page", "previous-page", target, defaults);
                copyLegacyButtonOverride(legacy, guiPath, "next-page", "next-page", target, defaults);
            }
            default -> {
            }
        }
        if (guiPath.equals("main") || guiPath.equals("item-select")) {
            copyLegacyButtonOverride(legacy, guiPath, "search", "search", target, defaults);
        }
        if (guiPath.equals("history")) {
            copyLegacyButtonOverride(legacy, guiPath, "back-to-orders", "back", target, defaults);
        }
    }

    private static void copyLegacyButtonOverride(YamlConfiguration legacy, String guiPath,
                                                 String oldItemKey, String newButtonKey,
                                                 YamlConfiguration target, YamlConfiguration defaults) {
        for (String field : List.of("material", "name", "lore", "custom-model-data")) {
            String sourcePath = "items." + guiPath + "." + oldItemKey + "." + field;
            String destinationPath = "buttons." + newButtonKey + "." + field;
            if (!legacy.isSet(sourcePath)
                || (!defaults.isSet(destinationPath) && !field.equals("custom-model-data"))) {
                continue;
            }
            Object ownerValue = legacy.get(sourcePath);
            if (Objects.equals(ownerValue, oldButtonDefault(oldItemKey, field))) {
                continue;
            }
            if (field.equals("lore") && oldItemKey.equals("search") && ownerValue instanceof List<?> lines) {
                ownerValue = lines.stream().map(line -> line instanceof String text
                    ? text.replace("{search_status}", "{current}") : line).toList();
            }
            copyIfUntouched(target, defaults, destinationPath, ownerValue);
        }
    }

    private static Object oldButtonDefault(String oldItemKey, String field) {
        return switch (oldItemKey) {
            case "previous-page" -> switch (field) {
                case "material" -> "ARROW";
                case "name" -> "#03fc88ʙᴀᴄᴋ";
                case "lore" -> List.of("#ffffffClick to go to the previous page");
                default -> null;
            };
            case "next-page" -> switch (field) {
                case "material" -> "ARROW";
                case "name" -> "#03fc88ɴᴇxᴛ";
                case "lore" -> List.of("#ffffffClick to go to the next page");
                default -> null;
            };
            case "search" -> switch (field) {
                case "material" -> "OAK_SIGN";
                case "name" -> "#03fc88ꜱᴇᴀʀᴄʜ";
                case "lore" -> List.of("#ffffffClick to search", "#a7b8b0{search_status}");
                default -> null;
            };
            case "back-to-orders" -> switch (field) {
                case "material" -> "BARRIER";
                case "name" -> "#ff5d73ʙᴀᴄᴋ";
                case "lore" -> List.of("#ffffffClick to return to orders");
                default -> null;
            };
            default -> null;
        };
    }

    private static void copyIfUntouched(YamlConfiguration target, YamlConfiguration defaults,
                                        String targetPath, Object ownerValue) {
        if (!target.isSet(targetPath) || Objects.deepEquals(target.get(targetPath), defaults.get(targetPath))) {
            target.set(targetPath, ownerValue);
        }
    }

    private YamlConfiguration bundledDefaults(String resourcePath) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream != null) {
                defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            warn("Could not load bundled GUI defaults " + resourcePath + ": " + exception.getMessage());
        }
        return defaults;
    }

    private void restoreFiles(Map<File, byte[]> originalBytes) {
        restoreFiles(originalBytes, this::warn);
    }

    private static void restoreFiles(Map<File, byte[]> originalBytes, Consumer<String> warn) {
        for (Map.Entry<File, byte[]> entry : originalBytes.entrySet()) {
            try {
                if (entry.getValue() == null) {
                    Files.deleteIfExists(entry.getKey().toPath());
                } else {
                    Files.write(entry.getKey().toPath(), entry.getValue());
                }
            } catch (IOException exception) {
                warn.accept("Could not fully restore GUI migration target " + entry.getKey() + ".");
            }
        }
    }

    /**
     * Add only the newly introduced Back slot to older per-GUI files. A
     * version gate means an owner who removes it afterward will not have it
     * written back on every reload.
     */
    private boolean migrateNewBackSlots() {
        return migrateNewBackSlots(plugin.getDataFolder(), this::warn);
    }

    static boolean migrateNewBackSlots(File dataFolder, Consumer<String> warn) {
        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        Map<File, YamlConfiguration> targets = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : NEW_BACK_SLOTS.entrySet()) {
            String resourcePath = PUBLIC_GUI_FILES.get(entry.getKey());
            File targetFile = new File(dataFolder, resourcePath);
            if (!targetFile.isFile()) {
                continue;
            }
            YamlConfiguration target = YamlConfiguration.loadConfiguration(targetFile);
            if (target.isSet("items.back.slot")) {
                continue;
            }
            try {
                originalBytes.put(targetFile, Files.readAllBytes(targetFile.toPath()));
            } catch (IOException exception) {
                warn.accept("Could not read GUI migration target " + resourcePath + ".");
                return false;
            }
            target.set("items.back.slot", entry.getValue());
            targets.put(targetFile, target);
        }
        for (Map.Entry<File, YamlConfiguration> entry : targets.entrySet()) {
            try {
                entry.getValue().save(entry.getKey());
            } catch (IOException exception) {
                restoreFiles(originalBytes, warn);
                warn.accept("Could not add new GUI Back slots; modified GUI files were restored.");
                return false;
            }
        }
        return true;
    }

    private boolean migratePublicTextTemplates() {
        Map<String, YamlConfiguration> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            defaults.put(entry.getKey(), bundledDefaults(entry.getValue()));
        }
        return migratePublicTextTemplates(plugin.getDataFolder(), defaults, this::warn);
    }

    static boolean migratePublicTextTemplates(File dataFolder, Map<String, YamlConfiguration> defaults,
                                              Consumer<String> warn) {
        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        Map<File, YamlConfiguration> targets = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : NEW_PUBLIC_TEXT_PATHS.entrySet()) {
            File targetFile = new File(dataFolder, "guis/" + entry.getKey() + ".yml");
            if (!targetFile.isFile()) {
                continue;
            }
            YamlConfiguration bundled = defaults.get(entry.getKey());
            if (bundled == null) {
                warn.accept("Missing bundled GUI defaults for " + entry.getKey() + ".");
                return false;
            }
            YamlConfiguration active = YamlConfiguration.loadConfiguration(targetFile);
            boolean changed = false;
            if (entry.getKey().equals("main") && !active.isSet("items.order-entry.lore.admin-cancel")) {
                File legacyFile = new File(dataFolder, LEGACY_FILE_NAME);
                if (legacyFile.isFile()) {
                    YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
                    String legacyPath = "items.main.order-entry.lore.admin-cancel";
                    if (legacy.isString(legacyPath)) {
                        active.set("items.order-entry.lore.admin-cancel", legacy.getString(legacyPath));
                        changed = true;
                    }
                }
            }
            for (String path : entry.getValue()) {
                if (!active.isSet(path) && bundled.isSet(path)) {
                    active.set(path, bundled.get(path));
                    changed = true;
                }
            }
            if (entry.getKey().equals("new-order") && active.getStringList("items.enchants.lore").equals(List.of(
                "&8ʙᴜᴛᴛᴏɴ", " ", "&eⓘ Information ↓", "&7&l | &fConfigure item enchantments.",
                " ", "{good}→ Click to Edit Enchants ←"
            ))) {
                active.set("items.enchants.lore", bundled.get("items.enchants.lore"));
                changed = true;
            }
            if (entry.getKey().equals("item-select") && !active.isSet("labels.material-names")) {
                active.set("labels.material-names", Map.of());
                changed = true;
            }
            if (entry.getKey().equals("enchant-select") && !active.isSet("labels.enchantment-names")) {
                active.set("labels.enchantment-names", Map.of());
                changed = true;
            }
            if (!changed) {
                continue;
            }
            try {
                byte[] original = Files.readAllBytes(targetFile.toPath());
                File backup = new File(targetFile.getParentFile(), targetFile.getName() + ".pre-v13-backup");
                if (!backup.exists()) {
                    Files.copy(targetFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                originalBytes.put(targetFile, original);
            } catch (IOException exception) {
                warn.accept("Could not back up GUI text migration target " + targetFile + ".");
                return false;
            }
            targets.put(targetFile, active);
        }
        for (Map.Entry<File, YamlConfiguration> entry : targets.entrySet()) {
            try {
                entry.getValue().save(entry.getKey());
            } catch (IOException exception) {
                restoreFiles(originalBytes, warn);
                warn.accept("Could not migrate public GUI text; modified files were restored.");
                return false;
            }
        }
        return true;
    }

    public int revision() {
        return revision;
    }

    public GuiButtonConfig buttons() {
        return buttons;
    }

    public GuiButtonConfig buttons(String guiPath) {
        return buttonConfigs.getOrDefault(guiPath, buttons);
    }

    public String title(String path, String fallback) {
        return title(path, fallback, Map.of());
    }

    public String title(String path, String fallback, Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return titleCache.computeIfAbsent(new TitleCacheKey(path, fallback), ignored -> buildTitle(path, fallback, Map.of()));
        }
        return buildTitle(path, fallback, placeholders);
    }

    private String buildTitle(String path, String fallback, Map<String, String> placeholders) {
        String raw = guis.getString("titles." + path, fallback);
        return DialogIcons.fallbackText(formatGuiText(raw == null ? fallback : raw, placeholders));
    }

    public String text(String path, String fallback, Map<String, String> placeholders) {
        String raw = guis.getString(path, fallback);
        return formatGuiText(raw == null ? fallback : raw, placeholders);
    }

    public GuiItem item(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        return item(path, fallbackMaterial, fallbackName, fallbackLore, Map.of());
    }

    public GuiItem item(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore, Map<String, String> placeholders) {
        return item(path, fallbackMaterial, fallbackName, fallbackLore, placeholders, List.of());
    }

    public GuiItem item(
        String path,
        Material fallbackMaterial,
        String fallbackName,
        List<String> fallbackLore,
        Map<String, String> placeholders,
        List<String> hiddenLorePlaceholders
    ) {
        return item(path, fallbackMaterial, fallbackName, fallbackLore, placeholders, hiddenLorePlaceholders, Map.of());
    }

    public GuiItem item(
        String path,
        Material fallbackMaterial,
        String fallbackName,
        List<String> fallbackLore,
        Map<String, String> placeholders,
        List<String> hiddenLorePlaceholders,
        Map<String, List<String>> loreExpansions
    ) {
        if ((placeholders == null || placeholders.isEmpty()) && (hiddenLorePlaceholders == null || hiddenLorePlaceholders.isEmpty())) {
            if (loreExpansions != null && !loreExpansions.isEmpty()) {
                return buildItem(path, fallbackMaterial, fallbackName, fallbackLore, Map.of(), List.of(), loreExpansions);
            }
            return itemCache.computeIfAbsent(
                new GuiItemCacheKey(path, fallbackMaterial, fallbackName, fallbackLore),
                ignored -> buildItem(path, fallbackMaterial, fallbackName, fallbackLore, Map.of(), List.of(), Map.of())
            );
        }
        return buildItem(path, fallbackMaterial, fallbackName, fallbackLore, placeholders, hiddenLorePlaceholders, loreExpansions);
    }

    private GuiItem buildItem(
        String path,
        Material fallbackMaterial,
        String fallbackName,
        List<String> fallbackLore,
        Map<String, String> placeholders,
        List<String> hiddenLorePlaceholders,
        Map<String, List<String>> loreExpansions
    ) {
        Map<String, String> safePlaceholders = placeholders == null ? Map.of() : placeholders;
        List<String> safeHiddenLorePlaceholders = hiddenLorePlaceholders == null ? List.of() : hiddenLorePlaceholders;
        String root = "items." + path + ".";
        Material material = material(guis.getString(root + "material"), fallbackMaterial);
        Integer customModelData = customModelData(guis.get(root + "custom-model-data"), null);
        String rawName = guis.getString(root + "name", fallbackName);
        List<String> rawLore = guis.getStringList(root + "lore");
        if (rawLore.isEmpty() && !guis.contains(root + "lore")) {
            rawLore = fallbackLore == null ? List.of() : fallbackLore;
        }
        List<String> lore = new ArrayList<>();
        for (String line : expandLore(rawLore, loreExpansions)) {
            if (containsHiddenPlaceholder(line, safeHiddenLorePlaceholders)) {
                continue;
            }
            lore.add(formatGuiText(line, safePlaceholders));
        }
        return new GuiItem(
            material,
            formatGuiText(rawName == null ? fallbackName : rawName, safePlaceholders),
            lore,
            customModelData
        );
    }

    static List<String> expandLore(List<String> rawLore, Map<String, List<String>> expansions) {
        if (expansions == null || expansions.isEmpty()) {
            return rawLore;
        }
        List<String> expanded = new ArrayList<>();
        for (String line : rawLore) {
            List<String> replacement = expansions.get(line);
            if (replacement == null) {
                expanded.add(line);
            } else {
                expanded.addAll(replacement);
            }
        }
        return expanded;
    }

    public String materialName(Material material, String fallback) {
        return text("labels.item-select.material-names." + material.name(), fallback, Map.of());
    }

    private boolean containsHiddenPlaceholder(String line, List<String> hiddenLorePlaceholders) {
        if (line == null || hiddenLorePlaceholders == null || hiddenLorePlaceholders.isEmpty()) {
            return false;
        }
        for (String placeholder : hiddenLorePlaceholders) {
            if (placeholder == null || placeholder.isBlank()) {
                continue;
            }
            if (line.contains("{" + placeholder + "}") || line.contains("%" + placeholder + "%")) {
                return true;
            }
        }
        return false;
    }

    public List<String> labels(String path, List<String> fallback) {
        return labelsCache.computeIfAbsent(new LabelsCacheKey(path, fallback), ignored -> buildLabels(path, fallback));
    }

    private List<String> buildLabels(String path, List<String> fallback) {
        List<String> configured = guis.getStringList("labels." + path);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < fallback.size(); i++) {
            String rawLabel = i < configured.size() ? configured.get(i) : fallback.get(i);
            if (rawLabel == null || rawLabel.isBlank()) {
                rawLabel = fallback.get(i);
            }
            labels.add(formatGuiText(rawLabel, Map.of()));
        }
        return List.copyOf(labels);
    }

    private String formatGuiText(String raw, Map<String, String> placeholders) {
        return TextFormat.colorize(TextFormat.applyPlaceholders(raw == null ? "" : raw, guiPlaceholders(placeholders)));
    }

    private Map<String, String> guiPlaceholders(Map<String, String> placeholders) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("theme", themeColor());
        merged.put("plugin", plugin.getName());
        if (placeholders != null && !placeholders.isEmpty()) {
            merged.putAll(placeholders);
        }
        return merged;
    }

    private String themeColor() {
        if (plugin instanceof FoOrders foOrders) {
            PluginMessages messages = foOrders.messages();
            if (messages != null) {
                String configured = messages.themeColor();
                if (configured != null && !configured.isBlank()) {
                    return configured;
                }
            }
        }
        return TextFormat.colorize(DEFAULT_THEME_COLOR);
    }

    public int itemSlot(String path, int fallback, int inventorySize) {
        Integer effective = effectiveItemSlots.get(path);
        if (effective != null && effective >= 0 && effective < inventorySize) {
            return effective;
        }
        return slot("items." + path + ".slot", fallback, inventorySize);
    }

    static Map<String, Integer> resolveItemSlots(String guiPath, YamlConfiguration active,
                                                  YamlConfiguration defaults, Consumer<String> warn) {
        ConfigurationSection defaultItems = defaults.getConfigurationSection("items");
        if (defaultItems == null) {
            return Map.of();
        }

        int inventorySize = guiInventorySize(guiPath);
        List<SlotChoice> choices = new ArrayList<>();
        for (String itemKey : defaultItems.getKeys(false)) {
            String path = "items." + itemKey + ".slot";
            if (!defaults.isSet(path)) {
                continue;
            }
            Integer defaultSlot = parseSlot(defaults.get(path));
            if (defaultSlot == null || defaultSlot < 0 || defaultSlot >= inventorySize) {
                warn.accept("Invalid bundled GUI slot at " + PUBLIC_GUI_FILES.get(guiPath) + ":" + path + ".");
                continue;
            }
            Integer configuredSlot = active.isSet(path) ? parseSlot(active.get(path)) : defaultSlot;
            if (configuredSlot == null || configuredSlot < 0 || configuredSlot >= inventorySize) {
                warn.accept("Invalid GUI slot at " + PUBLIC_GUI_FILES.get(guiPath) + ":" + path
                    + ". Using bundled slot " + defaultSlot + ".");
                configuredSlot = defaultSlot;
            }
            choices.add(new SlotChoice(itemKey, configuredSlot, defaultSlot));
        }

        Map<String, Integer> resolved = new LinkedHashMap<>();
        Set<Integer> occupied = new HashSet<>();
        // Unchanged defaults retain their slots when a custom override conflicts with them.
        for (boolean customized : new boolean[]{false, true}) {
            for (SlotChoice choice : choices) {
                if ((choice.requested() != choice.fallback()) != customized) {
                    continue;
                }
                int slot = choice.requested();
                if (!availableSlot(guiPath, slot, inventorySize, occupied)) {
                    slot = availableSlot(guiPath, choice.fallback(), inventorySize, occupied)
                        ? choice.fallback() : firstAvailableSlot(guiPath, inventorySize, occupied);
                    warn.accept("GUI slot conflict at " + PUBLIC_GUI_FILES.get(guiPath) + ":items."
                        + choice.key() + ".slot. Using slot " + slot + ".");
                }
                occupied.add(slot);
                resolved.put(guiPath + "." + choice.key(), slot);
            }
        }
        return resolved;
    }

    private static int guiInventorySize(String guiPath) {
        return switch (guiPath) {
            case "your-orders", "new-order", "manage-order", "delivery-confirm" -> 27;
            case "deliver" -> 36;
            default -> 54;
        };
    }

    private static Integer parseSlot(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            double raw = number.doubleValue();
            return Double.isFinite(raw) && raw == Math.rint(raw) && raw >= Integer.MIN_VALUE && raw <= Integer.MAX_VALUE
                ? number.intValue() : null;
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean availableSlot(String guiPath, int slot, int inventorySize, Set<Integer> occupied) {
        if (slot < 0 || slot >= inventorySize || occupied.contains(slot)) {
            return false;
        }
        if (slot < 45 && (guiPath.equals("item-select") || guiPath.equals("enchant-select")
            || guiPath.equals("claim-order") || guiPath.equals("history"))) {
            return false;
        }
        return !guiPath.equals("manage-order") || slot != 16;
    }

    private static int firstAvailableSlot(String guiPath, int inventorySize, Set<Integer> occupied) {
        int firstPreferred = inventorySize == 54 ? 45 : 0;
        for (int offset = 0; offset < inventorySize; offset++) {
            int slot = (firstPreferred + offset) % inventorySize;
            if (availableSlot(guiPath, slot, inventorySize, occupied)) {
                return slot;
            }
        }
        throw new IllegalStateException("No free control slot in " + PUBLIC_GUI_FILES.get(guiPath));
    }

    private record SlotChoice(String key, int requested, int fallback) {
    }

    public int slot(String path, int fallback, int inventorySize) {
        return slotCache.computeIfAbsent(new SlotCacheKey(path, fallback, inventorySize), ignored -> buildSlot(path, fallback, inventorySize));
    }

    private int buildSlot(String path, int fallback, int inventorySize) {
        if (inventorySize <= 0) {
            warn("Invalid inventory size for GUI slot '" + path + "': " + inventorySize + ".");
            return 0;
        }
        int safeFallback = fallback;
        if (safeFallback < 0 || safeFallback >= inventorySize) {
            warn("Invalid fallback slot for GUI slot '" + path + "': " + safeFallback + ". Using 0.");
            safeFallback = 0;
        }
        if (!guis.isSet(path)) {
            return safeFallback;
        }
        int configured = guis.getInt(path, safeFallback);
        if (configured < 0 || configured >= inventorySize) {
            warn("Invalid GUI slot at '" + path + "': " + configured + ". Expected 0-" + (inventorySize - 1) + ". Using " + safeFallback + ".");
            return safeFallback;
        }
        return configured;
    }

    public List<Integer> slots(String path, List<Integer> fallback, int inventorySize) {
        return slotsCache.computeIfAbsent(new SlotsCacheKey(path, fallback, inventorySize), ignored -> buildSlots(path, fallback, inventorySize));
    }

    private List<Integer> buildSlots(String path, List<Integer> fallback, int inventorySize) {
        List<Integer> configured = guis.getIntegerList(path);
        boolean usingFallback = configured.isEmpty();
        List<Integer> source = usingFallback ? fallback : configured;
        List<Integer> slots = new ArrayList<>();
        Set<Integer> seenSlots = new HashSet<>();
        for (int slot : source) {
            if (slot < 0 || slot >= inventorySize) {
                warn("Invalid GUI slot at '" + path + "': " + slot + ". Expected 0-" + (inventorySize - 1) + ".");
                continue;
            }
            if (isMainOrderControlSlot(path, slot)) {
                warn("Order content slot " + slot + " overlaps a main-menu button. Keeping the button visible.");
                continue;
            }
            if (!seenSlots.add(slot)) {
                warn("Duplicate GUI slot at '" + path + "': " + slot + ". Ignoring duplicate.");
                continue;
            }
            slots.add(slot);
        }
        if (slots.isEmpty() && !usingFallback) {
            warn("No valid GUI slots at '" + path + "'. Using defaults.");
            for (int slot : fallback) {
                if (slot < 0 || slot >= inventorySize) {
                    warn("Invalid fallback GUI slot at '" + path + "': " + slot + ". Expected 0-" + (inventorySize - 1) + ".");
                    continue;
                }
                if (isMainOrderControlSlot(path, slot)) {
                    continue;
                }
                if (!seenSlots.add(slot)) {
                    warn("Duplicate fallback GUI slot at '" + path + "': " + slot + ". Ignoring duplicate.");
                    continue;
                }
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    private boolean isMainOrderControlSlot(String path, int slot) {
        if (!"layout.main.order-slots".equals(path)) {
            return false;
        }
        for (Map.Entry<String, Integer> entry : effectiveItemSlots.entrySet()) {
            if (entry.getKey().startsWith("main.") && entry.getValue() == slot) {
                return true;
            }
        }
        return false;
    }

    public void warnConfig(String message) {
        warn(message);
    }

    private Material material(String configured, Material fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(configured.trim().toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            warn("Ignoring invalid GUI material '" + configured + "'.");
            return fallback;
        }
        return material;
    }

    private Integer customModelData(Object configured, Integer fallback) {
        if (configured == null) {
            return fallback;
        }
        if (configured instanceof Number number) {
            int value = number.intValue();
            return value >= 0 ? value : fallback;
        }
        String text = configured.toString().trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(text);
            if (value >= 0) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the warning and fallback.
        }
        warn("Ignoring invalid GUI custom model data '" + configured + "'.");
        return fallback;
    }

    private boolean backfillMissingDefaults(YamlConfiguration loaded, YamlConfiguration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || loaded.isSet(key)) {
                continue;
            }
            loaded.set(key, defaults.get(key));
            loaded.setComments(key, defaults.getComments(key));
            loaded.setInlineComments(key, defaults.getInlineComments(key));
            changed = true;
        }
        return changed;
    }

    /**
     * Adds the two standard navigation sprites to untouched public-GUI labels
     * without replacing server-owner wording or re-adding a later removal.
     */
    private boolean migrateDefaultBackAndSearchSprites() {
        for (String resourcePath : PUBLIC_GUI_FILES.values()) {
            File targetFile = new File(plugin.getDataFolder(), resourcePath);
            if (!targetFile.isFile()) {
                continue;
            }

            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(targetFile);
            YamlConfiguration defaults = bundledDefaults(resourcePath);
            boolean changed = replaceExactDefault(loaded, defaults, "buttons.back.name",
                "{theme}&lBACK");
            changed |= replaceExactDefault(loaded, defaults, "buttons.search.name",
                "{theme}&lSEARCH");
            if (!changed) {
                continue;
            }

            try {
                loaded.save(targetFile);
            } catch (IOException exception) {
                warn("Could not migrate default button sprites in " + resourcePath + ".");
                return false;
            }
        }
        return true;
    }

    private boolean migrateDefaultButtonStyles(YamlConfiguration loaded, YamlConfiguration defaults) {
        boolean changed = false;
        changed |= replaceExactDefault(loaded, defaults, "buttons.back.name", ":iron_door: {theme}&lBACK");
        changed |= replaceExactDefault(loaded, defaults, "buttons.previous-page.name", ":arrow: {theme}&lPREVIOUS PAGE");
        changed |= replaceExactDefault(loaded, defaults, "buttons.next-page.name", ":arrow: {theme}&lNEXT PAGE");
        changed |= replaceExactDefault(loaded, defaults, "buttons.search.name", ":name_tag: {theme}&lSEARCH");
        changed |= replaceExactDefault(loaded, defaults, "items.main.sort.name", ":cauldron: {theme}&lSORT");
        changed |= replaceExactDefault(loaded, defaults, "items.main.filter.name", ":hopper: {theme}&lFILTER");
        changed |= replaceExactDefault(loaded, defaults, "items.main.refresh.name", ":map: {theme}&lORDERS");
        changed |= replaceExactDefault(loaded, defaults, "items.main.your-orders.name", ":book: {theme}&lYOUR ORDERS");
        changed |= replaceExactDefault(loaded, defaults, "items.main.history.name", ":writable_book: {theme}&lHISTORY");
        changed |= replaceExactDefault(loaded, defaults, "items.your-orders.new-order.name", ":map: {theme}&lNEW ORDER");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.cancel.name", ":red_stained_glass_pane: #ff5d73&lCANCEL");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.amount.name", ":chest: {theme}&lAMOUNT");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.price.name", ":emerald: {theme}&lPRICE");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.enchants.name", ":enchanted_book: {theme}&lENCHANTS");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.confirm.name", ":lime_stained_glass_pane: #3ecf8e&lCONFIRM");
        changed |= replaceExactDefault(loaded, defaults, "items.item-select.sort.name", ":cauldron: {theme}&lSORT");
        changed |= replaceExactDefault(loaded, defaults, "items.item-select.filter.name", ":hopper: {theme}&lFILTER");
        changed |= replaceExactDefault(loaded, defaults, "items.enchant-select.clear.name", ":red_terracotta: #ff5d73&lCLEAR");
        changed |= replaceExactDefault(loaded, defaults, "items.enchant-select.done.name", ":lime_stained_glass_pane: #3ecf8e&lDONE");
        changed |= replaceExactDefault(loaded, defaults, "items.manage-order.cancel.name", ":red_terracotta: #ff5d73&lCANCEL");
        changed |= replaceExactDefault(loaded, defaults, "items.manage-order.claim.name", ":chest: #3ecf8e&lCLAIM ORDER");
        changed |= replaceExactDefault(loaded, defaults, "items.claim-order.drop-page.name", ":dispenser: #a7b8b0&lDROP PAGE");
        changed |= replaceExactDefault(loaded, defaults, "items.delivery-confirm.cancel.name", ":red_stained_glass_pane: #ff5d73&lCANCEL");
        changed |= replaceExactDefault(loaded, defaults, "items.delivery-confirm.confirm.name", ":lime_stained_glass_pane: #3ecf8e&lCONFIRM");
        changed |= replaceExactDefault(loaded, defaults, "items.main.refresh.name", "#03fc88ᴏʀᴅᴇʀꜱ");
        changed |= replaceExactDefault(loaded, defaults, "items.main.refresh.lore", List.of("#ffffffClick to refresh"));
        changed |= replaceExactDefault(loaded, defaults, "items.main.your-orders.name", "#03fc88ʏᴏᴜʀ ᴏʀᴅᴇʀꜱ");
        changed |= replaceExactDefault(loaded, defaults, "items.main.your-orders.lore", List.of("#ffffffClick to view your orders"));
        changed |= replaceExactDefault(loaded, defaults, "items.main.history.name", "#03fc88ʜɪꜱᴛᴏʀʏ");
        changed |= replaceExactDefault(loaded, defaults, "items.main.history.lore", List.of(
                "#ffffffClick to view order history",
                "#ffffffSwitch between order and deliver tabs"
        ));
        changed |= replaceExactDefault(loaded, defaults, "items.your-orders.new-order.name", "#03fc88New Order");
        changed |= replaceExactDefault(loaded, defaults, "items.your-orders.new-order.lore", List.of("#ffffffClick to create new order"));
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.cancel.name", "#ff5d73ᴄᴀɴᴄᴇʟ");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.cancel.lore", List.of("#ffffffClick to return"));
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.item.name", "#03fc88ɪᴛᴇᴍ");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.amount.name", "#03fc88ᴀᴍᴏᴜɴᴛ");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.amount.lore", List.of(
                "#ffffffClick to type number of items",
                "#a7b8b0({amount})"
        ));
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.price.name", "#03fc88ᴘʀɪᴄᴇ");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.price.lore", List.of(
                "#ffffffClick to type the price per item",
                "#a7b8b0(${price})"
        ));
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.enchants.name", "#03fc88ᴇɴᴄʜᴀɴᴛꜱ");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.confirm.name", "#3ecf8eᴄᴏɴꜰɪʀᴍ");
        changed |= replaceExactDefault(loaded, defaults, "items.new-order.confirm.lore", List.of(
                "#ffffffClick to confirm order",
                "#a7b8b0Subtotal: #03fc88${subtotal}",
                "#a7b8b0Tax ({tax_percent}%): #03fc88${tax}",
                "#a7b8b0Total: #3ecf8e${total}"
        ));
        changed |= replaceExactDefault(loaded, defaults, "items.manage-order.cancel.name", "#ff5d73ᴄᴀɴᴄᴇʟ");
        changed |= replaceExactDefault(loaded, defaults, "items.manage-order.cancel.lore", List.of("#ffffffClick to cancel your order"));
        changed |= replaceExactDefault(loaded, defaults, "items.manage-order.claim.name", "#3ecf8eᴄʟᴀɪᴍ ᴏʀᴅᴇʀ");
        changed |= replaceExactDefault(loaded, defaults, "items.claim-order.drop-page.name", "#a7b8b0ᴅʀᴏᴘ ᴘᴀɢᴇ");
        changed |= replaceExactDefault(loaded, defaults, "items.claim-order.drop-page.lore", List.of("#ffffffDrop all items on the page"));
        changed |= replaceExactDefault(loaded, defaults, "items.delivery-confirm.cancel.name", "#ff5d73ᴄᴀɴᴄᴇʟ");
        changed |= replaceExactDefault(loaded, defaults, "items.delivery-confirm.cancel.lore", List.of("#ffffffClick to return"));
        changed |= replaceExactDefault(loaded, defaults, "items.delivery-confirm.confirm.name", "#3ecf8eᴄᴏɴꜰɪʀᴍ");
        changed |= replaceExactDefault(loaded, defaults, "items.delivery-confirm.confirm.lore", List.of("#ffffffClick to confirm order"));
        changed |= replaceExactDefault(loaded, defaults, "items.history.order-tab.name", "#03fc88ᴏʀᴅᴇʀ ʜɪꜱᴛᴏʀʏ");
        changed |= replaceExactDefault(loaded, defaults, "items.history.deliver-tab.name", "#03fc88ᴅᴇʟɪᴠᴇʀʏ ʜɪꜱᴛᴏʀʏ");
        return changed;
    }

    private boolean replaceExactDefault(YamlConfiguration loaded, YamlConfiguration defaults, String path, Object oldValue) {
        if (!loaded.isSet(path) || !Objects.equals(loaded.get(path), oldValue) || !defaults.isSet(path)) {
            return false;
        }
        Object replacement = defaults.get(path);
        if (Objects.equals(loaded.get(path), replacement)) {
            return false;
        }
        loaded.set(path, replacement);
        return true;
    }

    private boolean removeRemovedGuiPaths(YamlConfiguration loaded) {
        boolean changed = false;
        for (String path : REMOVED_GUI_PATHS) {
            if (!loaded.isSet(path)) {
                continue;
            }
            loaded.set(path, null);
            changed = true;
        }
        return changed;
    }

    private boolean migrateThemeTokenPlaceholders(YamlConfiguration loaded) {
        boolean changed = false;
        for (String key : loaded.getKeys(true)) {
            if (loaded.isConfigurationSection(key)) {
                continue;
            }
            if (loaded.isString(key)) {
                String current = loaded.getString(key, "");
                String migrated = replaceDefaultThemeColorToken(current);
                if (!current.equals(migrated)) {
                    loaded.set(key, migrated);
                    changed = true;
                }
                continue;
            }
            if (loaded.isList(key)) {
                List<?> current = loaded.getList(key);
                List<Object> migrated = migrateThemeTokenList(current);
                if (migrated != null) {
                    loaded.set(key, migrated);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private List<Object> migrateThemeTokenList(List<?> current) {
        if (current == null || current.isEmpty()) {
            return null;
        }

        boolean changed = false;
        List<Object> migrated = new ArrayList<>(current.size());
        for (Object value : current) {
            if (value instanceof String text) {
                String migratedText = replaceDefaultThemeColorToken(text);
                migrated.add(migratedText);
                changed |= !text.equals(migratedText);
            } else {
                migrated.add(value);
            }
        }
        return changed ? migrated : null;
    }

    private String replaceDefaultThemeColorToken(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }
        return DEFAULT_THEME_COLOR_PATTERN.matcher(value).replaceAll("{theme}");
    }

    private boolean migrateLegacyButtonsFile(YamlConfiguration loaded) {
        File legacyFile = new File(plugin.getDataFolder(), LEGACY_BUTTONS_FILE_NAME);
        if (!legacyFile.exists()) {
            return false;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection sourceRoot = legacy.getConfigurationSection("buttons");
        if (sourceRoot == null) {
            sourceRoot = legacy;
        }

        boolean changed = false;
        for (String key : BUTTON_TEMPLATE_KEYS) {
            ConfigurationSection section = sourceRoot.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            changed |= copyMissingSection(section, loaded, "buttons." + key);
        }

        if (!legacyFile.delete()) {
            warn("Could not remove old " + LEGACY_BUTTONS_FILE_NAME + ". FoOrders now uses per-GUI files for public GUI buttons.");
        }
        return changed;
    }

    private GuiButtonConfig loadButtons(YamlConfiguration loaded) {
        ConfigurationSection section = loaded.getConfigurationSection("buttons");
        if (section == null) {
            return GuiButtonConfig.defaults();
        }

        YamlConfiguration buttonConfig = new YamlConfiguration();
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            buttonConfig.set(key, resolveButtonConfigValue(section.get(key)));
        }
        return GuiButtonConfig.fromGuiFile(buttonConfig);
    }

    private Object resolveButtonConfigValue(Object value) {
        if (value instanceof String text) {
            return formatGuiText(text, Map.of());
        }
        if (value instanceof List<?> values) {
            List<Object> resolved = new ArrayList<>(values.size());
            for (Object nestedValue : values) {
                resolved.add(resolveButtonConfigValue(nestedValue));
            }
            return resolved;
        }
        return value;
    }

    private boolean copyMissingSection(ConfigurationSection source, YamlConfiguration target, String targetPath) {
        boolean changed = false;
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key)) {
                continue;
            }
            String fullPath = targetPath + "." + key;
            if (target.isSet(fullPath)) {
                continue;
            }
            target.set(fullPath, source.get(key));
            changed = true;
        }
        return changed;
    }

    private boolean backfillNewOrderConfirmTaxLore(YamlConfiguration loaded, YamlConfiguration defaults) {
        if (!loaded.isList(NEW_ORDER_CONFIRM_LORE_PATH)) {
            return false;
        }

        List<String> currentLore = new ArrayList<>(loaded.getStringList(NEW_ORDER_CONFIRM_LORE_PATH));
        if (containsAnyTaxPlaceholder(currentLore)) {
            return false;
        }

        List<String> defaultLore = defaults.getStringList(NEW_ORDER_CONFIRM_LORE_PATH);
        if (!defaultLore.isEmpty() && currentLore.equals(LEGACY_NEW_ORDER_CONFIRM_LORE)) {
            loaded.set(NEW_ORDER_CONFIRM_LORE_PATH, defaultLore);
        } else {
            currentLore.add(TAX_DISCLOSURE_LORE);
            loaded.set(NEW_ORDER_CONFIRM_LORE_PATH, currentLore);
        }
        loaded.setComments(NEW_ORDER_CONFIRM_LORE_PATH, defaults.getComments(NEW_ORDER_CONFIRM_LORE_PATH));
        loaded.setInlineComments(NEW_ORDER_CONFIRM_LORE_PATH, defaults.getInlineComments(NEW_ORDER_CONFIRM_LORE_PATH));
        return true;
    }

    private boolean containsAnyTaxPlaceholder(List<String> lore) {
        for (String line : lore) {
            if (line == null) {
                continue;
            }
            if (line.contains("{tax}") || line.contains("{tax_percent}") || line.contains("{subtotal}")) {
                return true;
            }
        }
        return false;
    }

    public record GuiItem(Material material, String name, List<String> lore, Integer customModelData) {
        public GuiItem {
            lore = copyStringList(lore);
        }
    }

    private void clearCaches() {
        titleCache.clear();
        itemCache.clear();
        labelsCache.clear();
        slotCache.clear();
        slotsCache.clear();
    }

    private static List<String> copyStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private static List<Integer> copyIntegerList(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }

    private record TitleCacheKey(String path, String fallback) {
    }

    private record GuiItemCacheKey(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        private GuiItemCacheKey {
            fallbackLore = copyStringList(fallbackLore);
        }
    }

    private record LabelsCacheKey(String path, List<String> fallback) {
        private LabelsCacheKey {
            fallback = copyStringList(fallback);
        }
    }

    private record SlotCacheKey(String path, int fallback, int inventorySize) {
    }

    private record SlotsCacheKey(String path, List<Integer> fallback, int inventorySize) {
        private SlotsCacheKey {
            fallback = copyIntegerList(fallback);
        }
    }

    private void warn(String message) {
        if (!warnedMessages.add(message)) {
            return;
        }
        plugin.getLogger().warning(message);
        if (plugin instanceof FoOrders foOrders && foOrders.fileLogger() != null) {
            foOrders.fileLogger().warn(message);
        }
    }
}
