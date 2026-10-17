package me.foesio.foOrders.config;

import me.foesio.foOrders.util.OwnerUpgradeBackups;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.foOrders.PublicTextFallback;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.foOrders.FoOrders;
import me.foesio.foOrders.PluginMessages;
import me.foesio.foOrders.util.TextFormat;
import me.foesio.foOrders.util.OwnerYaml;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class GuiConfigManager {
    private static final String LEGACY_FILE_NAME = "guis/orders.yml";
    private static final String LEGACY_PENDING_MARKER = "guis/.legacy-aggregate-pending";
    public static final int CORE_PUBLIC_GUI_VERSION = 38;
    private static final String LEGACY_28_FIELDS_RESOURCE = "migrations/orders-v2.8-present-fields.txt";
    private static final String LEGACY_29_FIELDS_RESOURCE = "migrations/orders-v2.9-present-fields.txt";
    private static final String MATERIAL_NAMES_FILE = "guis/material-names.yml";
    // Reserve a plugin-level version above the existing message migrations so
    // later config migrations cannot accidentally skip this split.
    private static final int GUI_SPLIT_MIGRATION_VERSION = 10;
    private static final int GUI_BUTTON_SPRITE_MIGRATION_VERSION = 11;
    private static final int GUI_BACK_SLOT_MIGRATION_VERSION = 12;
    private static final int GUI_PUBLIC_TEXT_MIGRATION_VERSION = 13;
    private static final int GUI_LORE_LAYOUT_MIGRATION_VERSION = 14;
    private static final int GUI_LAYOUT_MIGRATION_VERSION = 15;
    private static final Map<String, List<String>> NEW_LORE_LAYOUT_PATHS = Map.of(
        "main", List.of("items.order-entry.lore-layout", "labels.search-empty"),
        "your-orders", List.of("items.order-entry.lore-layout"),
        "item-select", List.of("labels.search-empty")
    );
    private static final Map<String, List<String>> NEW_LAYOUT_PATHS = Map.ofEntries(
        Map.entry("main", List.of("size", "layout.frame-slots", "layout.empty-slots")),
        Map.entry("your-orders", List.of("size", "layout.order-slots")),
        Map.entry("new-order", List.of("size")),
        Map.entry("item-select", List.of("size", "layout.content-slots")),
        Map.entry("enchant-select", List.of("size", "layout.content-slots")),
        Map.entry("manage-order", List.of("size")),
        Map.entry("claim-order", List.of("size", "layout.content-slots")),
        Map.entry("deliver", List.of("size")),
        Map.entry("delivery-confirm", List.of("size")),
        Map.entry("history", List.of("size", "layout.content-slots"))
    );
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
    private final FoMigrationStore migrations;
    private final Set<String> warnedMessages = ConcurrentHashMap.newKeySet();
    private final Map<TitleCacheKey, String> titleCache = new ConcurrentHashMap<>();
    private final Map<GuiItemCacheKey, GuiItem> itemCache = new ConcurrentHashMap<>();
    private final Map<LabelsCacheKey, List<String>> labelsCache = new ConcurrentHashMap<>();
    private final Map<SlotCacheKey, Integer> slotCache = new ConcurrentHashMap<>();
    private final Map<SlotsCacheKey, List<Integer>> slotsCache = new ConcurrentHashMap<>();
    private final Map<String, GuiButtonConfig> buttonConfigs = new ConcurrentHashMap<>();
    private YamlConfiguration guis = new YamlConfiguration();
    private YamlConfiguration materialNames = new YamlConfiguration();
    private YamlConfiguration bundledMaterialNames = new YamlConfiguration();
    private volatile Map<String, Integer> effectiveItemSlots = Map.of();
    private volatile Set<String> requiredStaticMaterialPaths = Set.of();
    private volatile GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private volatile int revision;
    private boolean bootstrapFreshInstall;
    private boolean bootstrapCoreFreshInstall;
    private boolean deferLegacyPresentationLoad;
    private boolean publicTextActivated;
    private boolean publicTextUnavailable;
    private boolean legacyAggregateUnavailable;
    private volatile Function<String, PublicGuiDefinition> publicDefinition;

    public GuiConfigManager(Plugin plugin, FoMigrationStore migrations, boolean freshInstall) {
        this.plugin = plugin;
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        this.bootstrapFreshInstall = freshInstall;
    }

    /** During the Core migration, old YAML is only staging input, never active presentation. */
    public void deferLegacyPresentationLoad() {
        deferLegacyPresentationLoad = true;
    }

    public void reload() {
        if (legacyAggregateUnavailable) return;
        if (bootstrapFreshInstall && migrations.version() < GUI_SPLIT_MIGRATION_VERSION
            && !new File(plugin.getDataFolder(), LEGACY_FILE_NAME).isFile()
            && PUBLIC_GUI_FILES.values().stream().noneMatch(path -> new File(plugin.getDataFolder(), path).isFile())) {
            for (String resourcePath : PUBLIC_GUI_FILES.values()) {
                plugin.saveResource(resourcePath, false);
            }
            plugin.saveResource("public-text.yml", false);
            plugin.saveResource(MATERIAL_NAMES_FILE, false);
            migrations.runToVersion(GUI_LAYOUT_MIGRATION_VERSION, () -> true);
            if (migrations.version() < GUI_LAYOUT_MIGRATION_VERSION) {
                throw new IllegalStateException("Could not initialize fresh public GUI schema version.");
            }
            bootstrapCoreFreshInstall = true;
            bootstrapFreshInstall = false;
        }
        File pendingAggregate = new File(plugin.getDataFolder(), LEGACY_PENDING_MARKER);
        if (pendingAggregate.isFile()) {
            if (!retryPendingLegacyAggregate(pendingAggregate.toPath(),
                this::migrateLegacyAggregate, this::warn)) {
                legacyAggregateUnavailable = true;
                return;
            }
        }
        if (bootstrapCoreFreshInstall) {
            reloadPublicText();
            return;
        }
        if (migrations.version() >= CORE_PUBLIC_GUI_VERSION) {
            reloadPublicText();
            return;
        }
        if (deferLegacyPresentationLoad
            && new File(plugin.getDataFolder(), "public-text.yml").isFile()) {
            reloadPublicText();
            return;
        }
        warnedMessages.clear();
        clearCaches();
        boolean freshInstall = bootstrapFreshInstall
            && !new File(plugin.getDataFolder(), LEGACY_FILE_NAME).isFile()
            && PUBLIC_GUI_FILES.values().stream()
                .noneMatch(path -> new File(plugin.getDataFolder(), path).isFile());
        if (freshInstall) {
            for (String resourcePath : PUBLIC_GUI_FILES.values()) {
                plugin.saveResource(resourcePath, false);
            }
        }
        try {
            migrations.runToVersion(GUI_SPLIT_MIGRATION_VERSION, this::migrateLegacyAggregate);
        } catch (RuntimeException failure) {
            markLegacyAggregatePending(failure);
            return;
        }
        if (migrations.version() < GUI_SPLIT_MIGRATION_VERSION) {
            markLegacyAggregatePending(new IllegalStateException(
                "Could not migrate the legacy FoOrders GUI file; existing settings were retained."));
            return;
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
        migrations.runToVersion(GUI_LORE_LAYOUT_MIGRATION_VERSION, this::migrateLoreLayouts);
        if (migrations.version() < GUI_LORE_LAYOUT_MIGRATION_VERSION) {
            throw new IllegalStateException("Could not add FoOrders public order lore layouts.");
        }
        migrations.runToVersion(GUI_LAYOUT_MIGRATION_VERSION, this::migrateLayouts);
        if (migrations.version() < GUI_LAYOUT_MIGRATION_VERSION) {
            throw new IllegalStateException("Could not add FoOrders public GUI layouts.");
        }

        if (deferLegacyPresentationLoad) {
            return;
        }

        YamlConfiguration loaded = new YamlConfiguration();
        Map<String, GuiButtonConfig> loadedButtons = new LinkedHashMap<>();
        Map<String, Integer> loadedSlots = new LinkedHashMap<>();
        Set<String> staticMaterials = new HashSet<>();
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            String guiPath = entry.getKey();
            File activeFile = new File(plugin.getDataFolder(), entry.getValue());
            if (!activeFile.isFile()) {
                throw new IllegalStateException("Missing owner public GUI file " + entry.getValue()
                    + "; it was not replaced with bundled defaults.");
            }
            YamlConfiguration active = OwnerYaml.load(activeFile);
            requireSize(entry.getKey(), active);
            requirePreviewLimits(entry.getKey(), active);
            YamlConfiguration bundled = bundledDefaults(entry.getValue());
            requireControlSlots(entry.getKey(), active, bundled, migrations.version() >= 19);
            requireItemFlags(entry.getValue(), active);
            requireVisualMetadata(entry.getValue(), active, Material::isItem);
            if (migrations.version() >= 19) {
                requireItemAmounts(entry.getValue(), active, bundled);
            }
            mergeActiveGui(loaded, guiPath, active);
            loadedButtons.put(guiPath, loadButtons(active));
            Map<String, Integer> menuSlots = resolveItemSlots(guiPath, active, bundled, this::warn,
                migrations.version() >= 19);
            requireLayoutSlots(entry.getValue(), active, menuSlots);
            loadedSlots.putAll(menuSlots);
            ConfigurationSection items = bundled.getConfigurationSection("items");
            if (items != null) {
                for (String itemKey : items.getKeys(false)) {
                    if (bundled.isSet("items." + itemKey + ".material")) {
                        staticMaterials.add("items." + guiPath + "." + itemKey + ".material");
                    }
                }
            }
        }

        YamlConfiguration loadedMaterialNames = new YamlConfiguration();
        YamlConfiguration knownMaterialNames = bundledDefaults(MATERIAL_NAMES_FILE);
        if (migrations.version() >= 19) {
            File ownerMaterials = new File(plugin.getDataFolder(), MATERIAL_NAMES_FILE);
            if (!ownerMaterials.isFile()) {
                throw new IllegalStateException("Missing owner public material names file " + MATERIAL_NAMES_FILE
                    + "; it was not replaced with English defaults.");
            }
            loadedMaterialNames = OwnerYaml.load(ownerMaterials);
        }

        guis = loaded;
        materialNames = loadedMaterialNames;
        bundledMaterialNames = knownMaterialNames;
        effectiveItemSlots = Map.copyOf(loadedSlots);
        requiredStaticMaterialPaths = Set.copyOf(staticMaterials);
        buttonConfigs.clear();
        buttonConfigs.putAll(loadedButtons);
        buttons = buttonConfigs.getOrDefault("main", GuiButtonConfig.defaults());
        revision++;
        clearCaches();
        bootstrapFreshInstall = false;
    }

    private void markLegacyAggregatePending(RuntimeException failure) {
        legacyAggregateUnavailable = true;
        File marker = new File(plugin.getDataFolder(), LEGACY_PENDING_MARKER);
        try {
            retainLegacyPendingMarker(marker.toPath(),
                OwnerUpgradeBackups.originalVersion(plugin.getDataFolder(), migrations.version()));
        } catch (IOException markerFailure) {
            throw new IllegalStateException("Could not retain legacy public GUI retry marker; "
                + "later schema steps must not advance without a retry path.", markerFailure);
        }
        warn("Legacy public GUI aggregate unavailable: " + failure.getMessage()
            + "; its installed file was retained and order menus are unavailable until retry.");
    }

    static void retainLegacyPendingMarker(java.nio.file.Path marker, int originalVersion) throws IOException {
        Files.createDirectories(marker.getParent());
        if (!Files.isRegularFile(marker)) Files.writeString(marker,
            "original-version: " + originalVersion + "\n",
            StandardCharsets.UTF_8);
    }

    static boolean retryPendingLegacyAggregate(java.nio.file.Path marker,
                                               java.util.function.BooleanSupplier migration,
                                               Consumer<String> warn) {
        try {
            if (!migration.getAsBoolean()) return false;
            Files.delete(marker);
            return true;
        } catch (IOException | RuntimeException failure) {
            warn.accept("Legacy public GUI aggregate remains unavailable: " + failure.getMessage()
                + "; installed files and retry marker were retained.");
            return false;
        }
    }

    /** Called after Core has loaded the installed, strict-schema GUI files. */
    public void usePublicDefinitions(Function<String, PublicGuiDefinition> publicDefinition) {
        this.publicDefinition = Objects.requireNonNull(publicDefinition, "publicDefinition");
        publicTextActivated = true;
        revision++;
        clearCaches();
    }

    public boolean wasFreshCoreInstall() {
        return bootstrapCoreFreshInstall;
    }

    public boolean legacyAggregateUnavailable() {
        return legacyAggregateUnavailable;
    }

    public boolean publicTextUnavailable() {
        return publicTextUnavailable;
    }

    public void validatePublicText() {
        if (publicTextActivated) {
            validatePublicTextCandidate(OwnerYaml.load(new File(plugin.getDataFolder(), "public-text.yml")),
                bundledDefaults("public-text.yml"), true, this::warn);
            validatePublicTextCandidate(OwnerYaml.load(new File(plugin.getDataFolder(), MATERIAL_NAMES_FILE)),
                bundledDefaults(MATERIAL_NAMES_FILE), true, this::warn);
        }
    }

    private void reloadPublicText() {
        File active = new File(plugin.getDataFolder(), "public-text.yml");
        if (!active.isFile()) {
            if (publicTextActivated) {
                throw new IllegalStateException("Missing owner public-text.yml; previous working labels remain active.");
            }
            publicTextUnavailable = true;
            warn("Missing owner public-text.yml; public menus remain unavailable until restart after repair.");
            return;
        }
        File names = new File(plugin.getDataFolder(), MATERIAL_NAMES_FILE);
        if (!names.isFile()) {
            if (publicTextActivated) {
                throw new IllegalStateException("Missing owner " + MATERIAL_NAMES_FILE
                    + "; previous working labels remain active.");
            }
            publicTextUnavailable = true;
            warn("Missing owner " + MATERIAL_NAMES_FILE
                + "; public menus remain unavailable until restart after repair.");
            return;
        }
        YamlConfiguration next;
        YamlConfiguration nextNames;
        try {
            next = OwnerYaml.load(active);
            nextNames = OwnerYaml.load(names);
            validatePublicTextCandidate(next, bundledDefaults("public-text.yml"), publicTextActivated,
                this::warn);
            validatePublicTextCandidate(nextNames, bundledDefaults(MATERIAL_NAMES_FILE), publicTextActivated,
                this::warn);
        } catch (IllegalStateException invalid) {
            if (publicTextActivated) throw invalid;
            publicTextUnavailable = true;
            warn("Owner public text remains unavailable: " + invalid.getMessage()
                + "; installed files were left unchanged.");
            return;
        }
        guis = next;
        materialNames = nextNames;
        bundledMaterialNames = bundledDefaults(MATERIAL_NAMES_FILE);
        publicTextUnavailable = false;
        revision++;
        clearCaches();
        bootstrapFreshInstall = false;
    }

    static void validatePublicTextCandidate(YamlConfiguration candidate, YamlConfiguration bundled,
                                            boolean strictReload, Consumer<String> warn) {
        validatePublicTextCandidate(candidate, bundled, strictReload,
            "in-memory bundled default", warn);
    }

    /** During a versioned upgrade, repair invalid old values in the staged file. */
    public static void repairLegacyPublicTextCandidate(YamlConfiguration candidate,
                                                       YamlConfiguration bundled,
                                                       Consumer<String> warn) {
        validatePublicTextCandidate(candidate, bundled, false,
            "newest bundled default in staged upgrade", warn);
    }

    private static void validatePublicTextCandidate(YamlConfiguration candidate, YamlConfiguration bundled,
                                                    boolean strictReload, String fallbackDescription,
                                                    Consumer<String> warn) {
        bundled.getKeys(true).stream().filter(bundled::isConfigurationSection)
            .sorted(java.util.Comparator.comparingInt(String::length)).forEach(path -> {
                if (!candidate.isSet(path) || candidate.isConfigurationSection(path)) return;
                Object actual = candidate.get(path);
                if (strictReload) {
                    throw new IllegalStateException("Invalid owner public text " + path + "=" + actual
                        + "; previous working values remain active.");
                }
                candidate.set(path, null);
                candidate.createSection(path);
                ConfigurationSection replacement = bundled.getConfigurationSection(path);
                for (String child : replacement.getKeys(true)) {
                    if (!replacement.isConfigurationSection(child)) {
                        candidate.set(path + "." + child, replacement.get(child));
                    }
                }
                warn.accept("Invalid owner public text " + path + "=" + actual
                    + "; using " + fallbackDescription + " for this section"
                    + (fallbackDescription.startsWith("in-memory") ? "; installed file unchanged." : "."));
            });
        for (String path : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(path) || !candidate.isSet(path)) continue;
            Object actual = candidate.get(path);
            Object expected = bundled.get(path);
            if (!samePublicTextType(actual, expected) || !validPublicTextValue(path, actual)) {
                if (actual instanceof List<?> values && expected instanceof List<?> defaults) {
                    if (strictReload) {
                        throw new IllegalStateException("Invalid owner public text " + path + "=" + actual
                            + "; previous working values remain active.");
                    }
                    List<Object> repaired = new ArrayList<>(values);
                    for (int index = 0; index < values.size(); index++) {
                        Object value = values.get(index);
                        if (value instanceof String) continue;
                        if (index >= defaults.size() || !(defaults.get(index) instanceof String)) {
                            throw new IllegalStateException("No bundled public text list default for " + path
                                + "[" + index + "]; owner file was left unchanged.");
                        }
                        repaired.set(index, defaults.get(index));
                        warn.accept("Invalid owner public text " + path + "[" + index + "]=" + value
                            + "; using " + fallbackDescription + " " + defaults.get(index)
                            + (fallbackDescription.startsWith("in-memory") ? "; installed file unchanged." : "."));
                    }
                    candidate.set(path, repaired);
                    continue;
                }
                rejectOrReplacePublicText(candidate, bundled, path, actual, strictReload,
                    fallbackDescription, warn);
            }
        }
        for (String root : List.of("labels.item-select.material-names", "materials")) {
            if (candidate.isSet(root) && !candidate.isConfigurationSection(root)) {
                rejectOrReplacePublicText(candidate, bundled, root, candidate.get(root), strictReload,
                    fallbackDescription, warn);
                continue;
            }
            ConfigurationSection section = candidate.getConfigurationSection(root);
            if (section == null) continue;
            for (String id : section.getKeys(false)) {
                String path = root + "." + id;
                if (bundled.isSet(path)) continue;
                Object actual = candidate.get(path);
                if (!(actual instanceof String)) {
                    rejectOrReplacePublicText(candidate, bundled, path, actual, strictReload,
                        fallbackDescription, warn);
                }
            }
        }
    }

    private static void rejectOrReplacePublicText(YamlConfiguration candidate, YamlConfiguration bundled,
                                                  String path, Object actual, boolean strictReload,
                                                  String fallbackDescription,
                                                  Consumer<String> warn) {
        Object replacement = bundled.get(path);
        if (strictReload) {
            throw new IllegalStateException("Invalid owner public text " + path + "=" + actual
                + "; previous working values remain active.");
        }
        if (replacement == null) {
            throw new IllegalStateException("No bundled public text default for " + path
                + "=" + actual + "; owner file was left unchanged.");
        }
        candidate.set(path, replacement);
        warn.accept("Invalid owner public text " + path + "=" + actual
            + "; using " + fallbackDescription + " " + replacement
            + (fallbackDescription.startsWith("in-memory") ? "; installed file unchanged." : "."));
    }

    private static boolean samePublicTextType(Object actual, Object expected) {
        if (expected instanceof String) return actual instanceof String;
        if (expected instanceof List<?>) return actual instanceof List<?> list
            && list.stream().allMatch(String.class::isInstance);
        if (expected instanceof Integer) return actual instanceof Integer;
        if (expected instanceof Boolean) return actual instanceof Boolean;
        return actual != null && expected != null && actual.getClass() == expected.getClass();
    }

    private static boolean validPublicTextValue(String path, Object value) {
        if (path.equals("labels.main.item-display.max-enchants")
            || path.equals("items.manage-order.claim.max-stack-lines")) {
            return value instanceof Integer number && number >= 0;
        }
        if (path.equals("labels.history.timestamp-pattern")) {
            try { java.time.format.DateTimeFormatter.ofPattern((String) value); return !((String) value).isBlank(); }
            catch (IllegalArgumentException invalid) { return false; }
        }
        if (path.equals("labels.history.timestamp-zone")) {
            String zone = (String) value;
            if (zone.isBlank()) return false;
            if (zone.equalsIgnoreCase("system")) return true;
            try { java.time.ZoneId.of(zone); return true; }
            catch (java.time.DateTimeException invalid) { return false; }
        }
        if (path.equals("labels.history.timestamp-locale")) {
            String language = (String) value;
            if (language.isBlank()) return false;
            try { new java.util.Locale.Builder().setLanguageTag(language).build(); return true; }
            catch (java.util.IllformedLocaleException invalid) { return false; }
        }
        return true;
    }

    private boolean skipFreshCoreStep(int version) {
        if (!bootstrapCoreFreshInstall) {
            return false;
        }
        migrations.runToVersion(version, () -> true);
        if (migrations.version() < version) {
            throw new IllegalStateException("Could not advance fresh public GUI schema to " + version + ".");
        }
        return true;
    }

    private PublicGuiDefinition publicGui(String guiPath) {
        Function<String, PublicGuiDefinition> resolver = publicDefinition;
        return resolver == null ? null : resolver.apply(guiPath);
    }

    static void mergeActiveGui(YamlConfiguration combined, String guiPath, YamlConfiguration active) {
        if (active.isSet("title")) {
            combined.set("titles." + guiPath, active.get("title"));
        }
        if (active.isSet("size")) {
            combined.set("sizes." + guiPath, active.get("size"));
        }
        copySection(active.getConfigurationSection("items"), combined, "items." + guiPath);
        copySection(active.getConfigurationSection("buttons"), combined, "buttons." + guiPath);
        copySection(active.getConfigurationSection("labels"), combined, "labels." + guiPath);
        copySection(active.getConfigurationSection("layout"), combined, "layout." + guiPath);
        copySection(active.getConfigurationSection("events"), combined, "events." + guiPath);
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

        File backup = OwnerUpgradeBackups.forFile(legacyFile, ".legacy-backup");
        try {
            if (OwnerUpgradeBackups.wasOriginallyPresent(legacyFile) && !backup.exists()) {
                Files.copy(legacyFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (IOException exception) {
            warn("Could not retain legacy GUI backup " + backup + "; leaving " + LEGACY_FILE_NAME + " untouched.");
            return false;
        }

        YamlConfiguration legacy = OwnerYaml.load(legacyFile);
        Set<String> knownLegacyFields = legacyKnownFields(legacy);
        ConfigurationSection legacyButtons = legacy.getConfigurationSection("buttons");
        if (legacyButtons == null) {
            File oldButtonsFile = new File(plugin.getDataFolder(), LEGACY_BUTTONS_FILE_NAME);
            if (oldButtonsFile.isFile()) {
                YamlConfiguration oldButtons = OwnerYaml.load(oldButtonsFile);
                legacyButtons = oldButtons.getConfigurationSection("buttons");
                if (legacyButtons == null) {
                    legacyButtons = oldButtons;
                }
            }
        }

        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        Map<File, YamlConfiguration> targets = new LinkedHashMap<>();
        try {
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            String guiPath = entry.getKey();
            String resourcePath = entry.getValue();
            String legacyTitlePath = "titles." + guiPath;

            File targetFile = new File(plugin.getDataFolder(), resourcePath);
            boolean existed = targetFile.isFile();
            // A server owner may already have a per-GUI file. It wins over the
            // aggregate file, including fields intentionally removed from it.
            if (existed) {
                continue;
            }
            if (!existed) {
                originalBytes.put(targetFile, null);
                installLegacyGuiResource(resourcePath, targetFile);
            }
            try {
                if (existed) originalBytes.put(targetFile, Files.readAllBytes(targetFile.toPath()));
            } catch (IOException exception) {
                warn("Could not read GUI migration target " + resourcePath + ".");
                restoreFiles(originalBytes);
                return false;
            }

            YamlConfiguration target = OwnerYaml.load(targetFile);
            YamlConfiguration defaults = bundledDefaults(resourcePath);
            preserveLegacyDeletions(legacy, legacyButtons, target, defaults, guiPath, knownLegacyFields);
            copyIfUntouched(target, defaults, "title", legacy.get(legacyTitlePath));
            copySectionIfPresent(legacy.getConfigurationSection("items." + guiPath), target, defaults, "items");
            copySectionIfPresent(legacy.getConfigurationSection("layout." + guiPath), target, defaults, "layout");
            copyLabels(legacy, guiPath, target, defaults);
            copyLegacyButtonOverrides(legacy, guiPath, target, defaults);
            copySectionIfPresent(legacyButtons, target, defaults, "buttons");
            targets.put(targetFile, target);
        }
        } catch (RuntimeException failure) {
            restoreFiles(originalBytes);
            warn("Legacy GUI split failed before commit: " + failure.getMessage());
            return false;
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

    private Set<String> legacyKnownFields(YamlConfiguration legacy) {
        Set<String> oldest = readLegacyKnownFields(LEGACY_28_FIELDS_RESOURCE);
        Set<String> later = readLegacyKnownFields(LEGACY_29_FIELDS_RESOURCE);
        return chooseLegacyKnownFields(legacy, oldest, later);
    }

    private Set<String> readLegacyKnownFields(String resourcePath) {
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing checked-in legacy GUI field inventory "
                    + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read legacy GUI field inventory", exception);
        }
    }

    static Set<String> chooseLegacyKnownFields(YamlConfiguration legacy,
                                                Set<String> oldest, Set<String> later) {
        long laterSlotMarkers = later.stream()
            .filter(path -> !oldest.contains(path) && path.startsWith("items.")
                && path.endsWith(".slot") && legacy.isSet(path))
            .limit(2)
            .count();
        return laterSlotMarkers >= 2 ? later : oldest;
    }

    /** Preserve fields demonstrably present in the old aggregate schema but absent in the owner's file. */
    static void preserveLegacyDeletions(YamlConfiguration legacy, ConfigurationSection legacyButtons,
                                        YamlConfiguration target,
                                        YamlConfiguration defaults, String guiPath,
                                        Set<String> knownLegacyFields) {
        String titlePath = "titles." + guiPath;
        if (knownLegacyFields.contains(titlePath) && !legacy.isSet(titlePath)) {
            target.set("title", null);
        }
        Set<String> oldItemKeys = new HashSet<>();
        for (String oldPath : knownLegacyFields) {
            String targetPath = null;
            if (oldPath.startsWith("items." + guiPath + ".")) {
                String relative = oldPath.substring(("items." + guiPath + ".").length());
                oldItemKeys.add(relative.substring(0, relative.indexOf('.')));
                targetPath = "items." + relative;
            } else if (oldPath.startsWith("layout." + guiPath + ".")) {
                targetPath = "layout." + oldPath.substring(("layout." + guiPath + ".").length());
            } else if ((guiPath.equals("main") && (oldPath.equals("labels.main-sort-options")
                || oldPath.equals("labels.filter-options")))
                || (guiPath.equals("item-select") && (oldPath.equals("labels.item-sort-options")
                || oldPath.equals("labels.filter-options")))) {
                targetPath = oldPath;
            }
            if (targetPath != null && defaults.isSet(targetPath) && !legacy.isSet(oldPath)) {
                target.set(targetPath, null);
            }
        }
        for (String itemKey : oldItemKeys) {
            if (!legacy.isConfigurationSection("items." + guiPath + "." + itemKey)
                && defaults.isSet("items." + itemKey + ".enabled")) {
                target.set("items." + itemKey + ".enabled", false);
            }
        }
        for (String oldKey : List.of("previous-page", "next-page", "search", "back-to-orders")) {
            String newKey = oldKey.equals("back-to-orders") ? "back" : oldKey;
            for (String field : List.of("material", "name", "lore", "custom-model-data")) {
                String oldPath = "items." + guiPath + "." + oldKey + "." + field;
                String newPath = "buttons." + newKey + "." + field;
                if (knownLegacyFields.contains(oldPath) && !legacy.isSet(oldPath)
                    && defaults.isSet(newPath)) {
                    target.set(newPath, null);
                }
            }
        }
        for (String oldPath : knownLegacyFields) {
            if (oldPath.startsWith("buttons.") && defaults.isSet(oldPath)
                && (legacyButtons == null || !legacyButtons.isSet(oldPath.substring("buttons.".length())))) {
                target.set(oldPath, null);
            }
        }
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
        String bundledPath = migrations.version() < CORE_PUBLIC_GUI_VERSION
            && resourcePath.startsWith("guis/") && !resourcePath.equals(MATERIAL_NAMES_FILE)
            ? "migrations/legacy-public-defaults/current/" + resourcePath : resourcePath;
        try (InputStream stream = plugin.getResource(bundledPath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled GUI resource " + resourcePath + ".");
            }
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load bundled GUI resource " + resourcePath
                + "; owner files were not rewritten.", exception);
        }
        return defaults;
    }

    private void installLegacyGuiResource(String resourcePath, File targetFile) {
        try (InputStream stream = plugin.getResource(
            "migrations/legacy-public-defaults/current/" + resourcePath)) {
            if (stream == null) throw new IOException("Missing legacy GUI default " + resourcePath);
            Files.createDirectories(targetFile.toPath().getParent());
            Files.copy(stream, targetFile.toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not install prior GUI resource " + resourcePath, exception);
        }
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
            YamlConfiguration target = OwnerYaml.load(targetFile);
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
            YamlConfiguration active = OwnerYaml.load(targetFile);
            boolean changed = false;
            boolean legacyAdminHintRemoved = false;
            if (entry.getKey().equals("main") && !active.isSet("items.order-entry.lore.admin-cancel")) {
                File legacyFile = new File(dataFolder, LEGACY_FILE_NAME);
                if (legacyFile.isFile()) {
                    YamlConfiguration legacy = OwnerYaml.load(legacyFile);
                    String legacyPath = "items.main.order-entry.lore.admin-cancel";
                    if (legacy.isString(legacyPath)) {
                        active.set("items.order-entry.lore.admin-cancel", legacy.getString(legacyPath));
                        changed = true;
                    } else {
                        legacyAdminHintRemoved = true;
                    }
                }
            }
            for (String path : entry.getValue()) {
                if (legacyAdminHintRemoved && path.equals("items.order-entry.lore.admin-cancel")) {
                    continue;
                }
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
                File backup = OwnerUpgradeBackups.forFile(targetFile, ".pre-v13-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(targetFile) && !backup.exists()) {
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

    private boolean migrateLoreLayouts() {
        Map<String, YamlConfiguration> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            defaults.put(entry.getKey(), bundledDefaults(entry.getValue()));
        }
        return migrateLoreLayouts(plugin.getDataFolder(), defaults, this::warn);
    }

    static boolean migrateLoreLayouts(File dataFolder, Map<String, YamlConfiguration> defaults,
                                      Consumer<String> warn) {
        return migrateIntroducedFields(dataFolder, defaults, NEW_LORE_LAYOUT_PATHS, "v14", warn);
    }

    private boolean migrateLayouts() {
        Map<String, YamlConfiguration> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            defaults.put(entry.getKey(), bundledDefaults(entry.getValue()));
        }
        return migrateLayouts(plugin.getDataFolder(), defaults, this::warn);
    }

    static boolean migrateLayouts(File dataFolder, Map<String, YamlConfiguration> defaults,
                                  Consumer<String> warn) {
        return migrateIntroducedFields(dataFolder, defaults, NEW_LAYOUT_PATHS, "v15", warn);
    }

    /** Called after the dialog v18 step so plugin-wide versions cannot skip migrations. */
    public void migrateNewItemFlags() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(19)) return;
        Map<String, YamlConfiguration> defaults = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : PUBLIC_GUI_FILES.entrySet()) {
            defaults.put(entry.getKey(), bundledDefaults(entry.getValue()));
        }
        migrations.runToVersion(19, () -> {
            File ownerMaterials = new File(plugin.getDataFolder(), MATERIAL_NAMES_FILE);
            boolean created = !ownerMaterials.exists();
            if (created) {
                try {
                    plugin.saveResource(MATERIAL_NAMES_FILE, false);
                } catch (IllegalArgumentException exception) {
                    warn("Could not install material names: " + exception.getMessage());
                    return false;
                }
            }
            boolean migrated = false;
            try {
                migrated = migrateItemFlags(plugin.getDataFolder(), defaults, this::warn);
                return migrated;
            } finally {
                if (created && !migrated) {
                    try {
                        Files.deleteIfExists(ownerMaterials.toPath());
                    } catch (IOException exception) {
                        warn("Could not roll back new material names file after failed GUI migration.");
                    }
                }
            }
        });
        if (migrations.version() < 19) {
            throw new IllegalStateException("Could not migrate FoOrders public GUI item flags.");
        }
    }

    /** Adds only the new presentation fields to installations older than v21. */
    public void migrateSearchAndHistoryPresentation() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(21)) return;
        Map<String, YamlConfiguration> defaults = new LinkedHashMap<>();
        for (String menu : List.of("main", "item-select", "history")) {
            defaults.put(menu, bundledDefaults(PUBLIC_GUI_FILES.get(menu)));
        }
        migrations.runToVersion(21, () -> migrateSearchAndHistoryPresentation(
            plugin.getDataFolder(), defaults, this::warn));
        if (migrations.version() < 21) {
            throw new IllegalStateException("Could not migrate FoOrders public search and history presentation.");
        }
    }

    static boolean migrateSearchAndHistoryPresentation(File dataFolder,
                                                        Map<String, YamlConfiguration> defaults,
                                                        Consumer<String> warn) {
        Map<String, List<String>> introduced = Map.of(
            "main", List.of("labels.search-current"),
            "item-select", List.of("labels.search-current"),
            "history", List.of("items.entry.order-material", "items.entry.deliver-material")
        );
        Map<String, List<String>> eligible = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : introduced.entrySet()) {
            File file = new File(dataFolder, "guis/" + entry.getKey() + ".yml");
            YamlConfiguration active = file.isFile() ? OwnerYaml.load(file)
                : new YamlConfiguration();
            eligible.put(entry.getKey(), entry.getValue().stream()
                .filter(path -> !path.startsWith("items.entry.")
                    || active.isConfigurationSection("items.entry"))
                .toList());
        }
        return migrateIntroducedFields(dataFolder, defaults, eligible, "v21", warn);
    }

    public void migrateHistoryTabNames() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(23)) return;
        Map<String, YamlConfiguration> defaults = Map.of("history", bundledDefaults(PUBLIC_GUI_FILES.get("history")));
        migrations.runToVersion(23, () -> migrateHistoryTabNames(
            plugin.getDataFolder(), defaults, this::warn));
        if (migrations.version() < 23) {
            throw new IllegalStateException("Could not migrate FoOrders history tab state names.");
        }
    }

    static boolean migrateHistoryTabNames(File dataFolder, Map<String, YamlConfiguration> defaults,
                                          Consumer<String> warn) {
        Map<String, List<String>> introduced = Map.of("history", List.of(
            "items.order-tab.selected-name", "items.order-tab.unselected-name",
            "items.deliver-tab.selected-name", "items.deliver-tab.unselected-name"));
        return migrateIntroducedFields(dataFolder, defaults, introduced, "v23", warn);
    }

    public void migrateClaimEntryPresentation() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(28)) return;
        Map<String, YamlConfiguration> defaults = Map.of(
            "claim-order", bundledDefaults(PUBLIC_GUI_FILES.get("claim-order")));
        migrations.runToVersion(28, () -> migrateClaimEntryPresentation(
            plugin.getDataFolder(), defaults, this::warn));
        if (migrations.version() < 28) {
            throw new IllegalStateException("Could not migrate FoOrders claim-entry presentation.");
        }
    }

    static boolean migrateClaimEntryPresentation(File dataFolder,
                                                 Map<String, YamlConfiguration> defaults,
                                                 Consumer<String> warn) {
        File file = new File(dataFolder, PUBLIC_GUI_FILES.get("claim-order"));
        if (!file.isFile()) {
            return true;
        }
        YamlConfiguration active = OwnerYaml.load(file);
        if (!active.isConfigurationSection("items")) {
            // This family existed in released files; an owner may have removed it.
            return true;
        }
        return migrateIntroducedFields(dataFolder, defaults, Map.of("claim-order", List.of(
            "items.entry.enabled", "items.entry.name", "items.entry.lore")), "v28", warn);
    }

    public void migrateHistoryUnknownDetails() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(29)) return;
        Map<String, YamlConfiguration> defaults = Map.of(
            "history", bundledDefaults(PUBLIC_GUI_FILES.get("history")));
        migrations.runToVersion(29, () -> migrateHistoryUnknownDetails(
            plugin.getDataFolder(), defaults, this::warn));
        if (migrations.version() < 29) {
            throw new IllegalStateException("Could not migrate FoOrders history empty-details label.");
        }
    }

    static boolean migrateHistoryUnknownDetails(File dataFolder,
                                                Map<String, YamlConfiguration> defaults,
                                                Consumer<String> warn) {
        File file = new File(dataFolder, PUBLIC_GUI_FILES.get("history"));
        if (!file.isFile()) {
            return true;
        }
        if (!OwnerYaml.load(file).isConfigurationSection("labels")) {
            // A removed released label family remains owner-removed.
            return true;
        }
        return migrateIntroducedFields(dataFolder, defaults, Map.of(
            "history", List.of("labels.unknown-details")), "v29", warn);
    }

    public void migrateCustomItemPickerLore() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(30)) return;
        migrations.runToVersion(30, () -> migrateCustomItemPickerLore(
            plugin.getDataFolder(), this::warn));
        if (migrations.version() < 30) {
            throw new IllegalStateException("Could not migrate FoOrders custom-item picker lore.");
        }
    }

    static boolean migrateCustomItemPickerLore(File dataFolder, Consumer<String> warn) {
        File file = new File(dataFolder, PUBLIC_GUI_FILES.get("item-select"));
        if (!file.isFile()) {
            warn.accept("Could not migrate custom-item picker lore: missing owner file " + file + ".");
            return false;
        }
        YamlConfiguration active = OwnerYaml.load(file);
        String itemPath = "items.custom-entry";
        String lorePath = itemPath + ".lore";
        if (!active.isConfigurationSection(itemPath)) {
            // A removed optional control must stay removed.
            return true;
        }
        if (active.isSet(lorePath) && !active.isList(lorePath)) {
            warn.accept("Could not migrate invalid owner lore at " + file + ":" + lorePath + ".");
            return false;
        }
        List<String> oldLore = active.getStringList(lorePath);
        if (!active.isSet(lorePath) || oldLore.isEmpty()) {
            // An absent/empty owner list is an explicit request for no GUI lore.
            // Do not encode the old Java-generated template lore into it.
            return true;
        }
        if (oldLore.contains("{template_lore}")) {
            return true;
        }
        // Preserve the older displayed template lore only when the owner also
        // kept GUI lore; an empty owner list takes precedence.
        List<String> migratedLore = new ArrayList<>(oldLore.size() + 1);
        migratedLore.add("{template_lore}");
        migratedLore.addAll(oldLore);
        active.set(lorePath, migratedLore);
        try {
            byte[] originalBytes = Files.readAllBytes(file.toPath());
            File backup = OwnerUpgradeBackups.forFile(file, ".pre-v30-backup");
            if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            try {
                active.save(file);
            } catch (IOException exception) {
                restoreFiles(Map.of(file, originalBytes), warn);
                warn.accept("Could not migrate custom-item picker lore; owner file was restored.");
                return false;
            }
            return true;
        } catch (IOException exception) {
            warn.accept("Could not back up custom-item picker lore before v30 migration.");
            return false;
        }
    }

    public void restoreRemovedCustomItemPickerLore() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(35)) return;
        migrations.runToVersion(35, () -> restoreRemovedCustomItemPickerLore(
            plugin.getDataFolder(), this::warn));
        if (migrations.version() < 35) {
            throw new IllegalStateException("Could not restore owner-removed custom-item picker lore.");
        }
    }

    static boolean restoreRemovedCustomItemPickerLore(File dataFolder, Consumer<String> warn) {
        File file = new File(dataFolder, PUBLIC_GUI_FILES.get("item-select"));
        File oldBackup = OwnerUpgradeBackups.forFile(file, ".pre-v30-backup");
        if (!oldBackup.isFile()) {
            File earlierReleaseBackup = new File(file.getParentFile(), file.getName() + ".pre-v30-backup");
            if (earlierReleaseBackup.isFile()) oldBackup = earlierReleaseBackup;
        }
        if (!file.isFile() || !oldBackup.isFile()) {
            return true;
        }
        String path = "items.custom-entry.lore";
        YamlConfiguration beforeV30 = OwnerYaml.load(oldBackup);
        YamlConfiguration active = OwnerYaml.load(file);
        if (beforeV30.isSet(path) && !beforeV30.isList(path)) {
            return true;
        }
        if (!beforeV30.getStringList(path).isEmpty()
            || !active.isList(path)
            || !active.getStringList(path).equals(List.of("{template_lore}"))) {
            return true;
        }
        // Only reverse the exact line v30 synthesized from an absent/empty
        // owner list. Any later owner edit or extra line wins.
        active.set(path, beforeV30.isSet(path) ? List.of() : null);
        try {
            byte[] originalBytes = Files.readAllBytes(file.toPath());
            File backup = OwnerUpgradeBackups.forFile(file, ".pre-v35-backup");
            if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            try {
                active.save(file);
                return true;
            } catch (IOException exception) {
                restoreFiles(Map.of(file, originalBytes), warn);
                warn.accept("Could not restore owner-removed custom-item picker lore; owner file was restored.");
                return false;
            }
        } catch (IOException exception) {
            warn.accept("Could not back up custom-item picker lore before v35 migration.");
            return false;
        }
    }

    public void migratePreviewLimits() {
        if (legacyAggregateUnavailable) return;
        if (skipFreshCoreStep(32)) return;
        Map<String, YamlConfiguration> defaults = Map.of(
            "main", bundledDefaults(PUBLIC_GUI_FILES.get("main")),
            "manage-order", bundledDefaults(PUBLIC_GUI_FILES.get("manage-order")));
        migrations.runToVersion(32, () -> migratePreviewLimits(plugin.getDataFolder(), defaults, this::warn));
        if (migrations.version() < 32) {
            throw new IllegalStateException("Could not migrate FoOrders public item preview limits.");
        }
    }

    static boolean migratePreviewLimits(File dataFolder, Map<String, YamlConfiguration> defaults,
                                        Consumer<String> warn) {
        Map<String, List<String>> introduced = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : Map.of(
            "main", "labels.item-display", "manage-order", "items.claim").entrySet()) {
            File file = new File(dataFolder, PUBLIC_GUI_FILES.get(entry.getKey()));
            if (!file.isFile()) {
                warn.accept("Could not migrate public item preview limits: missing owner file " + file + ".");
                return false;
            }
            if (OwnerYaml.load(file).isConfigurationSection(entry.getValue())) {
                introduced.put(entry.getKey(), List.of(entry.getValue() + "." +
                    (entry.getKey().equals("main") ? "max-enchants" : "max-stack-lines")));
            }
        }
        return migrateIntroducedFields(dataFolder, defaults, introduced, "v32", warn);
    }

    static boolean migrateItemFlags(File dataFolder, Map<String, YamlConfiguration> defaults,
                                    Consumer<String> warn) {
        Map<String, List<String>> introduced = new LinkedHashMap<>();
        for (Map.Entry<String, YamlConfiguration> entry : defaults.entrySet()) {
            File ownerFile = new File(dataFolder, "guis/" + entry.getKey() + ".yml");
            YamlConfiguration active = ownerFile.isFile()
                ? OwnerYaml.load(ownerFile) : new YamlConfiguration();
            List<String> paths = entry.getValue().getKeys(true).stream()
                .filter(path -> path.matches("(items|buttons)\\.[^.]+\\.(item-flags|amount|enabled)")
                        && active.isConfigurationSection(path.substring(0, path.lastIndexOf('.')))
                    || entry.getKey().equals("item-select") && path.equals("labels.unknown-item")
                    || entry.getKey().equals("main") && entry.getValue().isString(path)
                        && path.startsWith("labels.item-display.")
                    || entry.getKey().equals("enchant-select") && entry.getValue().isString(path)
                        && (path.startsWith("labels.enchantment-names.")
                            && active.isConfigurationSection("labels.enchantment-names")
                            || path.equals("labels.unknown-enchantment")
                            || path.equals("labels.level-format"))
                    || entry.getKey().equals("enchant-select")
                        && path.matches("items\\.entry\\.(selected|unselected)-material")
                        && active.isConfigurationSection("items.entry")
                    || entry.getKey().equals("history") && entry.getValue().isString(path)
                        && (path.startsWith("events.")
                        || path.startsWith("labels.timestamp-"))
                    || entry.getKey().equals("history")
                        && path.matches("items\\.(order-tab|deliver-tab)\\.(selected|unselected)-(material|color|lore)")
                        && active.isConfigurationSection(path.substring(0, path.lastIndexOf('.'))))
                .toList();
            introduced.put(entry.getKey(), paths);
        }
        return migrateIntroducedFields(dataFolder, defaults, introduced, "v19", warn);
    }

    private static boolean migrateIntroducedFields(File dataFolder, Map<String, YamlConfiguration> defaults,
                                                   Map<String, List<String>> introduced, String version,
                                                   Consumer<String> warn) {
        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        Map<File, YamlConfiguration> targets = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : introduced.entrySet()) {
            File targetFile = new File(dataFolder, "guis/" + entry.getKey() + ".yml");
            if (!targetFile.isFile()) {
                continue;
            }
            YamlConfiguration bundled = defaults.get(entry.getKey());
            if (bundled == null) {
                warn.accept("Missing bundled GUI defaults for " + entry.getKey() + ".");
                return false;
            }
            YamlConfiguration active = OwnerYaml.load(targetFile);
            boolean changed = false;
            if (version.equals("v19") && entry.getKey().equals("history")) {
                changed |= migrateHistoryTabDefaults(active, bundled);
            }
            if (version.equals("v23") && entry.getKey().equals("history")) {
                changed |= migrateHistoryTabNameDefaults(active, bundled);
            }
            for (String path : entry.getValue()) {
                if (!active.isSet(path) && bundled.isSet(path)
                    && (!version.equals("v23") || active.isConfigurationSection(
                        path.substring(0, path.lastIndexOf('.'))))) {
                    active.set(path, bundled.get(path));
                    changed = true;
                }
            }
            if (!changed) {
                continue;
            }
            try {
                originalBytes.put(targetFile, Files.readAllBytes(targetFile.toPath()));
                File backup = OwnerUpgradeBackups.forFile(targetFile, ".pre-" + version + "-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(targetFile) && !backup.exists()) {
                    Files.copy(targetFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            } catch (IOException exception) {
                warn.accept("Could not back up GUI " + version + " migration target " + targetFile + ".");
                return false;
            }
            targets.put(targetFile, active);
        }
        for (Map.Entry<File, YamlConfiguration> entry : targets.entrySet()) {
            try {
                entry.getValue().save(entry.getKey());
            } catch (IOException exception) {
                restoreFiles(originalBytes, warn);
                warn.accept("Could not migrate GUI " + version + " fields; modified files were restored.");
                return false;
            }
        }
        return true;
    }

    private static boolean migrateHistoryTabDefaults(YamlConfiguration active, YamlConfiguration bundled) {
        boolean changed = false;
        for (String key : List.of("order-tab", "deliver-tab")) {
            String root = "items." + key + ".";
            String noun = key.equals("order-tab") ? "order" : "delivery";
            String oldName = key.equals("order-tab")
                ? "{theme}&lORDER HISTORY" : "{theme}&lDELIVERY HISTORY";
            if (oldName.equals(active.getString(root + "name"))) {
                active.set(root + "name", key.equals("order-tab")
                    ? "{state_color}&lORDER HISTORY" : "{state_color}&lDELIVERY HISTORY");
                changed = true;
            }
            List<String> oldLore = List.of(
                "&8ʙᴜᴛᴛᴏɴ", " ", "&eⓘ Information ↓",
                "&7&l | &fView " + noun + " history entries.", " ",
                key.equals("order-tab") ? "{good}→ Click to View Orders ←"
                    : "{good}→ Click to View Deliveries ←"
            );
            if (active.isList(root + "lore") && active.getStringList(root + "lore").equals(oldLore)) {
                active.set(root + "lore", bundled.getStringList(root + "lore"));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean migrateHistoryTabNameDefaults(YamlConfiguration active, YamlConfiguration bundled) {
        boolean changed = false;
        for (String key : List.of("order-tab", "deliver-tab")) {
            String path = "items." + key + ".name";
            String oldDefault = "{state_color}&l" + (key.equals("order-tab")
                ? "ORDER HISTORY" : "DELIVERY HISTORY");
            if (oldDefault.equals(active.getString(path))) {
                active.set(path, bundled.getString(path));
                changed = true;
            }
        }
        return changed;
    }

    private static void requireSize(String guiPath, YamlConfiguration active) {
        Object raw = active.get("size");
        Integer size = parseSlot(raw);
        if (size == null || size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalStateException("Invalid or missing public GUI size in " + PUBLIC_GUI_FILES.get(guiPath)
                + "; preserve the owner file and set size to 9, 18, 27, 36, 45, or 54.");
        }
    }

    static void requirePreviewLimits(String guiPath, YamlConfiguration active) {
        String path = switch (guiPath) {
            case "main" -> "labels.item-display.max-enchants";
            case "manage-order" -> "items.claim.max-stack-lines";
            default -> null;
        };
        if (path != null && active.isSet(path) && (!active.isInt(path) || active.getInt(path) < 0)) {
            throw new IllegalStateException("Invalid public preview limit " + path + " in "
                + PUBLIC_GUI_FILES.get(guiPath) + "; use a non-negative integer or remove it to hide the preview.");
        }
    }

    static void requireControlSlots(String guiPath, YamlConfiguration active,
                                            YamlConfiguration defaults, boolean currentSchema) {
        ConfigurationSection items = defaults.getConfigurationSection("items");
        if (items == null) {
            return;
        }
        for (String item : items.getKeys(false)) {
            String path = "items." + item + ".slot";
            if (defaults.isSet(path) && !active.isSet(path)
                && controlEnabled(active, defaults, item, currentSchema)) {
                throw new IllegalStateException("Missing public GUI control slot " + path + " in "
                    + PUBLIC_GUI_FILES.get(guiPath) + "; no Java default was inserted.");
            }
        }
    }

    public int size(String guiPath) {
        PublicGuiDefinition definition = publicGui(guiPath);
        if (definition != null) {
            return definition.size();
        }
        int size = guis.getInt("sizes." + guiPath, 0);
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalStateException("Public GUI size is unavailable for " + guiPath + ".");
        }
        return size;
    }

    public List<Integer> contentSlots(String guiPath, String key) {
        PublicGuiDefinition definition = publicGui(guiPath);
        if (definition != null) {
            String entryKey = key.equals("order-slots") ? "order-entry" : "entry";
            PublicGuiDefinition.Entry entry = definition.entries().get(entryKey);
            if (entry != null) {
                return entry.enabled() ? entry.slots() : List.of();
            }
            if (key.equals("frame-slots") && definition.decorations().containsKey("frame")) {
                return definition.decorations().get("frame").slots();
            }
            return List.of();
        }
        return slots("layout." + guiPath + "." + key, List.of(), size(guiPath));
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
        String raw = guis.getString("titles." + path, "");
        return PublicTextFallback.render(formatGuiText(raw, placeholders));
    }

    public String text(String path, String fallback, Map<String, String> placeholders) {
        return configuredText(path, placeholders);
    }

    public String configuredText(String path, Map<String, String> placeholders) {
        return guis.isString(path) ? formatGuiText(guis.getString(path, ""), placeholders) : "";
    }

    public String rawString(String path) {
        return guis.isString(path) ? guis.getString(path, "") : "";
    }

    public boolean has(String path) {
        return guis.isSet(path);
    }

    public List<String> lines(String path, Map<String, String> placeholders,
                              Map<String, List<String>> expansions) {
        if (!guis.isList(path)) {
            return List.of();
        }
        return renderLoreLines(guis.getStringList(path), expansions,
            ignored -> true, line -> formatGuiText(line, placeholders));
    }

    public GuiItem item(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        return item(path, fallbackMaterial, fallbackName, fallbackLore, Map.of());
    }

    /** Return only an explicitly configured public button; never invoke Core's English/style defaults. */
    public GuiItem button(String menu, String key, Map<String, String> placeholders) {
        String root = "buttons." + menu + "." + key + ".";
        if (!guis.isConfigurationSection("buttons." + menu + "." + key)
            || !guis.getBoolean(root + "enabled", false)) {
            return null;
        }
        Material configuredMaterial = material(guis.getString(root + "material"), null);
        if (configuredMaterial == null) {
            warn("Missing or invalid public GUI button material at '" + root + "material'; button disabled.");
            return null;
        }
        Map<String, String> values = placeholders == null ? Map.of() : placeholders;
        String name = guis.isSet(root + "name") ? guis.getString(root + "name", "") : "";
        List<String> lore = guis.isList(root + "lore") ? guis.getStringList(root + "lore") : List.of();
        List<String> renderedLore = new ArrayList<>(lore.size());
        for (String line : lore) {
            renderedLore.add(formatGuiText(line, values));
        }
        return new GuiItem(configuredMaterial, formatGuiText(name, values), renderedLore,
            customModelData(guis.get(root + "custom-model-data"), null),
            itemFlags(guis.get(root + "item-flags")), guis.isSet(root + "item-flags"),
            guis.getInt(root + "amount", 1));
    }

    public boolean buttonEnabled(String menu, String key) {
        PublicGuiDefinition definition = publicGui(menu);
        if (definition != null) {
            String id = key.equals("back") && menu.equals("history") ? "back-to-orders" : key;
            PublicGuiDefinition.Button button = definition.buttons().get(id);
            if (button != null) {
                return button.enabled();
            }
            PublicGuiDefinition.Entry entry = definition.entries().get(id);
            return entry != null && entry.enabled();
        }
        String root = "buttons." + menu + "." + key;
        return guis.isConfigurationSection(root) && guis.getBoolean(root + ".enabled", false)
            && material(guis.getString(root + ".material"), null) != null;
    }

    public boolean itemEnabled(String path) {
        int separator = path.indexOf('.');
        if (separator > 0) {
            PublicGuiDefinition definition = publicGui(path.substring(0, separator));
            if (definition != null) {
                String id = path.substring(separator + 1);
                if (id.equals("custom-entry") && path.startsWith("item-select.")) {
                    id = "entry";
                }
                PublicGuiDefinition.Button button = definition.buttons().get(id);
                if (button != null) {
                    return button.enabled();
                }
                PublicGuiDefinition.Entry entry = definition.entries().get(id);
                return entry != null && entry.enabled();
            }
        }
        return itemEnabled(guis, requiredStaticMaterialPaths, path, Material::isItem);
    }

    public Material stateMaterial(String path, boolean selected) {
        return stateMaterial(path, selected ? "selected" : "unselected");
    }

    /** Dynamic source stacks supply their own material; an optional override is not visibility. */
    public boolean sourceEntryStateEnabled(String path, String state) {
        int separator = path.indexOf('.');
        if (separator > 0) {
            PublicGuiDefinition definition = publicGui(path.substring(0, separator));
            if (definition != null) {
                return sourceEntryStateEnabled(definition, path.substring(separator + 1), state);
            }
        }
        return stateMaterial(path, state) != null;
    }

    public static boolean sourceEntryStateEnabled(PublicGuiDefinition definition, String entryId, String state) {
        PublicGuiDefinition.Entry entry = definition.entries().get(entryId);
        if (entry == null || !entry.enabled() || entry.sourceItem() == null
            || !entry.sourceItem().loreStates().containsKey(state)) {
            return false;
        }
        return entry.sourceItem().forState(state).enabled();
    }

    public Material stateMaterial(String path, String state) {
        int separator = path.indexOf('.');
        if (separator > 0) {
            PublicGuiDefinition definition = publicGui(path.substring(0, separator));
            if (definition != null) {
                String id = path.substring(separator + 1);
                PublicGuiDefinition.Entry entry = definition.entries().get(id);
                if (entry != null && entry.enabled() && entry.sourceItem() != null) {
                    var visual = entry.sourceItem().stateVisuals().get(state);
                    return visual == null ? entry.sourceItem().materialOverride() : visual.material();
                }
                PublicGuiDefinition.Button button = definition.buttons().get(id);
                if (button != null && button.enabled()) {
                    var visual = button.states().get(state);
                    return visual == null ? button.item().material() : visual.item().material();
                }
                return null;
            }
        }
        return stateMaterial(guis, requiredStaticMaterialPaths, path, state, Material::isItem, this::warn);
    }

    static Material stateMaterial(YamlConfiguration active, Set<String> staticMaterialPaths,
                                  String path, String state, Predicate<Material> isItem, Consumer<String> warn) {
        if (!itemEnabled(active, staticMaterialPaths, path, isItem)) {
            return null;
        }
        String key = "items." + path + "." + state + "-material";
        String configured = active.getString(key);
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(configured.trim().toUpperCase(Locale.ROOT));
        if (material == null || !isItem.test(material)) {
            warn.accept("Ignoring invalid GUI material '" + configured + "'.");
            return null;
        }
        return material;
    }

    public String stateColor(String path, boolean selected) {
        String key = "items." + path + "." + (selected ? "selected" : "unselected") + "-color";
        return configuredText(key, Map.of());
    }

    static boolean itemEnabled(YamlConfiguration active, Set<String> staticMaterialPaths,
                               String path, Predicate<Material> isItem) {
        String root = "items." + path;
        if (!active.isConfigurationSection(root) || !active.getBoolean(root + ".enabled", false)) {
            return false;
        }
        String materialPath = root + ".material";
        if (!staticMaterialPaths.contains(materialPath)) {
            return true;
        }
        Material material = Material.matchMaterial(active.getString(materialPath, ""));
        return material != null && isItem.test(material);
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
        String rawName = guis.getString(root + "name", "");
        List<String> rawLore = guis.getStringList(root + "lore");
        List<String> lore = renderLoreLines(rawLore, loreExpansions,
            line -> !containsHiddenPlaceholder(line, safeHiddenLorePlaceholders),
            line -> formatGuiText(line, safePlaceholders));
        return new GuiItem(
            material,
            formatGuiText(rawName, safePlaceholders),
            lore,
            customModelData,
            itemFlags(guis.get(root + "item-flags")),
            guis.isSet(root + "item-flags"),
            guis.getInt(root + "amount", 1)
        );
    }

    static List<ItemFlag> itemFlags(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String text && text.equalsIgnoreCase("all")) {
            return List.copyOf(Arrays.asList(ItemFlag.values()));
        }
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("Expected 'all' or a list of Bukkit ItemFlag names.");
        }
        List<ItemFlag> flags = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String name)) {
                throw new IllegalArgumentException("Item flags must be Bukkit ItemFlag names.");
            }
            try {
                flags.add(ItemFlag.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown ItemFlag '" + name + "'.", exception);
            }
        }
        return List.copyOf(flags);
    }

    private static void requireItemFlags(String file, YamlConfiguration active) {
        for (String path : active.getKeys(true)) {
            if (!path.endsWith(".item-flags")) {
                continue;
            }
            try {
                itemFlags(active.get(path));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid public GUI item flags at " + file + ":" + path
                    + "; owner file preserved: " + exception.getMessage(), exception);
            }
        }
    }

    static void requireVisualMetadata(String file, YamlConfiguration active,
                                      Predicate<Material> isItem) {
        for (String path : active.getKeys(true)) {
            if (!(path.startsWith("items.") || path.startsWith("buttons."))) {
                continue;
            }
            if (path.endsWith(".material") || path.endsWith("-material")) {
                String raw = active.getString(path, "");
                if (raw.isBlank()) {
                    continue;
                }
                Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
                if (material == null || !isItem.test(material)) {
                    throw new IllegalStateException("Invalid public GUI item material at " + file + ":" + path
                        + "; owner file preserved.");
                }
            }
            if (path.endsWith(".custom-model-data")) {
                Object raw = active.get(path);
                if (raw == null || raw instanceof String text && text.isBlank()) {
                    continue;
                }
                Integer value = parseSlot(raw);
                if (value == null || value < 0) {
                    throw new IllegalStateException("Invalid public GUI custom model data at " + file + ":" + path
                        + "; use a nonnegative whole number or empty string to clear it.");
                }
            }
        }
    }

    static void requireLayoutSlots(String file, YamlConfiguration active,
                                   Map<String, Integer> controlSlots) {
        int size = active.getInt("size", 0);
        if (active.isSet("layout") && !active.isConfigurationSection("layout")) {
            throw new IllegalStateException("Invalid public GUI layout section in " + file
                + "; owner file preserved.");
        }
        ConfigurationSection layout = active.getConfigurationSection("layout");
        if (layout == null) {
            return;
        }
        for (String key : layout.getKeys(false)) {
            String path = "layout." + key;
            if (!key.endsWith("-slots") || !active.isSet(path)) {
                continue;
            }
            if (!active.isList(path)) {
                throw new IllegalStateException("Invalid public GUI layout at " + file + ":" + path
                    + "; expected a list of zero-based slots.");
            }
            Set<Integer> used = new HashSet<>();
            for (Object raw : active.getList(path, List.of())) {
                Integer slot = parseSlot(raw);
                if (slot == null || slot < 0 || slot >= size || !used.add(slot)) {
                    throw new IllegalStateException("Invalid or duplicate public GUI layout slot at " + file
                        + ":" + path + "; owner file preserved.");
                }
                if ((key.equals("content-slots") || key.equals("order-slots"))
                    && controlSlots.containsValue(slot)) {
                    throw new IllegalStateException("Public GUI content/control collision at " + file + ":" + path
                        + " slot " + slot + "; move one of the slots in the owner file.");
                }
            }
        }
    }

    static void requireItemAmounts(String file, YamlConfiguration active,
                                   YamlConfiguration bundled) {
        for (String path : bundled.getKeys(true)) {
            if (!(path.startsWith("items.") || path.startsWith("buttons."))
                || !path.endsWith(".amount") || !bundled.isInt(path)) {
                continue;
            }
            String root = path.substring(0, path.length() - ".amount".length());
            if (!active.isConfigurationSection(root) || !active.getBoolean(root + ".enabled", false)) {
                continue;
            }
            Object raw = active.get(path);
            if (!(raw instanceof Integer amount) || amount < 1 || amount > 64) {
                throw new IllegalStateException("Invalid or missing public GUI item amount at " + file + ":" + path
                    + "; owner file preserved. Set an amount from 1 to 64 or disable the item.");
            }
        }
    }

    public void applyItemFlags(String path, ItemMeta meta) {
        if (meta == null || !guis.isSet(path)) {
            return;
        }
        ItemFlag[] current = meta.getItemFlags().toArray(ItemFlag[]::new);
        if (current.length > 0) {
            meta.removeItemFlags(current);
        }
        List<ItemFlag> requested = itemFlags(guis.get(path));
        if (!requested.isEmpty()) {
            meta.addItemFlags(requested.toArray(ItemFlag[]::new));
        }
    }

    static List<String> expandLore(List<String> rawLore, Map<String, List<String>> expansions) {
        return renderLoreLines(rawLore, expansions, ignored -> true, Function.identity());
    }

    static List<String> renderLoreLines(List<String> rawLore, Map<String, List<String>> expansions,
                                        Predicate<String> include, Function<String, String> formatOwnerLine) {
        List<String> rendered = new ArrayList<>();
        for (String line : rawLore) {
            if (!include.test(line)) {
                continue;
            }
            rendered.addAll(renderLoreLine(line, expansions, formatOwnerLine));
        }
        return rendered;
    }

    private static List<String> renderLoreLine(String line, Map<String, List<String>> expansions,
                                               Function<String, String> formatOwnerLine) {
        if (expansions == null || expansions.isEmpty()) {
            return List.of(formatOwnerLine.apply(line));
        }
        List<String> result = new ArrayList<>(List.of(""));
        int offset = 0;
        boolean found = false;
        while (offset < line.length()) {
            String token = null;
            int position = Integer.MAX_VALUE;
            for (Map.Entry<String, List<String>> entry : expansions.entrySet()) {
                String candidate = entry.getKey();
                if (candidate == null || candidate.isEmpty() || entry.getValue() == null) {
                    continue;
                }
                int index = line.indexOf(candidate, offset);
                if (index >= 0 && (index < position
                    || (index == position && (token == null || candidate.length() > token.length())))) {
                    token = candidate;
                    position = index;
                }
            }
            if (token == null) {
                break;
            }
            found = true;
            List<String> values = expansions.get(token);
            if (values.isEmpty()) {
                return List.of();
            }
            String ownerPrefix = line.substring(offset, position);
            String renderedPrefix = ownerPrefix.isEmpty() ? "" : formatOwnerLine.apply(ownerPrefix);
            List<String> next = new ArrayList<>(result.size() * values.size());
            for (String previous : result) {
                for (String value : values) {
                    // Inserted player/item data is final. Only owner-authored segments are formatted.
                    next.add(previous + renderedPrefix + value);
                }
            }
            result = next;
            offset = position + token.length();
        }
        if (!found) {
            return List.of(formatOwnerLine.apply(line));
        }
        String ownerSuffix = line.substring(offset);
        String renderedSuffix = ownerSuffix.isEmpty() ? "" : formatOwnerLine.apply(ownerSuffix);
        List<String> finished = new ArrayList<>(result.size());
        for (String value : result) {
            finished.add(value + renderedSuffix);
        }
        return finished;
    }

    public String materialName(Material material) {
        String raw = rawMaterialName(guis, materialNames, bundledMaterialNames, material);
        return formatGuiText(raw, Map.of("material", material.name()));
    }

    static String rawMaterialName(YamlConfiguration guis, YamlConfiguration ownerMaterials,
                                  YamlConfiguration bundledMaterials, Material material) {
        String path = "labels.item-select.material-names." + material.name();
        if (guis.isString(path)) {
            return guis.getString(path, "");
        }
        String materialPath = "materials." + material.name();
        if (ownerMaterials.isString(materialPath)) {
            return ownerMaterials.getString(materialPath, "");
        }
        if (bundledMaterials.isSet(materialPath)) {
            // A known default key removed by the owner must stay absent.
            return "";
        }
        return ownerMaterials.getString("unknown-material", "");
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

    /** A removed current-schema limit suppresses its generated public preview. */
    public int previewLimit(String path) {
        return previewLimit(guis, path);
    }

    static int previewLimit(YamlConfiguration active, String path) {
        return active.getInt(path, 0);
    }

    private List<String> buildLabels(String path, List<String> fallback) {
        List<String> configured = guis.getStringList("labels." + path);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < fallback.size(); i++) {
            String rawLabel = i < configured.size() ? configured.get(i) : "";
            labels.add(formatGuiText(rawLabel, Map.of()));
        }
        return List.copyOf(labels);
    }

    private String formatGuiText(String raw, Map<String, String> placeholders) {
        return TextFormat.colorize(TextFormat.applyPlaceholders(raw, guiPlaceholders(placeholders)));
    }

    private Map<String, String> guiPlaceholders(Map<String, String> placeholders) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (plugin instanceof FoOrders foOrders && foOrders.messages() != null) {
            Map<String, String> ownerTokens = foOrders.messages().ownerTokens();
            ownerTokens.forEach((key, value) -> {
                String nested = foOrders.messages().resolveOwnerTokens(value);
                merged.put(key, TextFormat.applyPlaceholders(nested, placeholders));
            });
        } else {
            for (String token : List.of("theme", "muted", "white", "yellow", "good", "bad")) {
                merged.put(token, "");
            }
        }
        merged.put("plugin", plugin.getName());
        if (placeholders != null && !placeholders.isEmpty()) {
            merged.putAll(placeholders);
        }
        return merged;
    }

    public int itemSlot(String path, int fallback, int inventorySize) {
        Integer effective = effectiveItemSlots.get(path);
        if (effective != null && effective >= 0 && effective < inventorySize) {
            return effective;
        }
        return slot("items." + path + ".slot", fallback, inventorySize);
    }

    /** Dispatch uses only controls that survived owner visibility and slot validation. */
    public int activeControlSlot(String path) {
        int separator = path.indexOf('.');
        if (separator > 0) {
            PublicGuiDefinition definition = publicGui(path.substring(0, separator));
            if (definition != null) {
                String id = path.substring(separator + 1);
                PublicGuiDefinition.Button button = definition.buttons().get(id);
                if (button != null) {
                    return button.enabled() ? button.slot() : -1;
                }
                PublicGuiDefinition.Entry entry = definition.entries().get(id);
                if (entry != null && entry.enabled() && entry.slots().size() == 1) {
                    return entry.slots().get(0);
                }
                return -1;
            }
        }
        return activeControlSlot(effectiveItemSlots, path);
    }

    static int activeControlSlot(Map<String, Integer> resolved, String path) {
        return resolved.getOrDefault(path, -1);
    }

    static Map<String, Integer> resolveItemSlots(String guiPath, YamlConfiguration active,
                                                  YamlConfiguration defaults, Consumer<String> warn) {
        return resolveItemSlots(guiPath, active, defaults, warn, false);
    }

    static Map<String, Integer> resolveItemSlots(String guiPath, YamlConfiguration active,
                                                  YamlConfiguration defaults, Consumer<String> warn,
                                                  boolean currentSchema) {
        ConfigurationSection defaultItems = defaults.getConfigurationSection("items");
        if (defaultItems == null) {
            return Map.of();
        }

        Integer configuredSize = parseSlot(active.get("size"));
        int inventorySize = configuredSize == null ? guiInventorySize(guiPath) : configuredSize;
        List<SlotChoice> choices = new ArrayList<>();
        for (String itemKey : defaultItems.getKeys(false)) {
            String path = "items." + itemKey + ".slot";
            if (!defaults.isSet(path)) {
                continue;
            }
            if (!controlEnabled(active, defaults, itemKey, currentSchema)) {
                continue;
            }
            Integer defaultSlot = parseSlot(defaults.get(path));
            if (defaultSlot == null || defaultSlot < 0 || defaultSlot >= 54) {
                warn.accept("Invalid bundled GUI slot at " + PUBLIC_GUI_FILES.get(guiPath) + ":" + path + ".");
                continue;
            }
            Integer configuredSlot = active.isSet(path) ? parseSlot(active.get(path)) : defaultSlot;
            if (configuredSlot == null || configuredSlot < 0 || configuredSlot >= inventorySize) {
                throw new IllegalStateException("Invalid GUI slot " + path + " in "
                    + PUBLIC_GUI_FILES.get(guiPath) + " for size " + inventorySize + ".");
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
                    throw new IllegalStateException("GUI slot conflict at " + PUBLIC_GUI_FILES.get(guiPath)
                        + ":items." + choice.key() + ".slot (" + slot + ").");
                }
                occupied.add(slot);
                resolved.put(guiPath + "." + choice.key(), slot);
            }
        }
        return resolved;
    }

    private static boolean controlEnabled(YamlConfiguration active, YamlConfiguration defaults,
                                          String itemKey, boolean currentSchema) {
        if (!active.isConfigurationSection("items." + itemKey)) {
            return false;
        }
        String buttonKey = itemKey.equals("back-to-orders") ? "back" : itemKey;
        String root = !defaults.isSet("items." + itemKey + ".name")
            && defaults.isConfigurationSection("buttons." + buttonKey)
            ? "buttons." + buttonKey : "items." + itemKey;
        if (!active.isConfigurationSection(root)) {
            return false;
        }
        if (!active.getBoolean(root + ".enabled", !currentSchema)) {
            return false;
        }
        if (defaults.isSet(root + ".material")) {
            Material material = Material.matchMaterial(active.getString(root + ".material", ""));
            // Paper's isItem() requires live RegistryAccess; itemEnabled() performs
            // that final runtime check before rendering the material.
            return material != null;
        }
        return true;
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
        return !guiPath.equals("manage-order") || slot != 16;
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
        List<Integer> configured = parseConfiguredSlots(guis.getList(path, List.of()));
        boolean usingFallback = !guis.isSet(path);
        if (!usingFallback && guis.isList(path) && configured.isEmpty()) {
            return List.of();
        }
        List<Integer> source = usingFallback ? fallback : configured;
        List<Integer> slots = new ArrayList<>();
        Set<Integer> seenSlots = new HashSet<>();
        for (int slot : source) {
            if (slot < 0 || slot >= inventorySize) {
                warn("Invalid GUI slot at '" + path + "': " + slot + ". Expected 0-" + (inventorySize - 1) + ".");
                continue;
            }
            if (isContentControlSlot(path, slot)) {
                warn("Content slot " + slot + " overlaps a configured button at '" + path + "'. Keeping the button visible.");
                continue;
            }
            if (!seenSlots.add(slot)) {
                warn("Duplicate GUI slot at '" + path + "': " + slot + ". Ignoring duplicate.");
                continue;
            }
            slots.add(slot);
        }
        if (slots.isEmpty() && !usingFallback) {
            warn("No valid GUI slots at '" + path + "'; configured region is empty.");
        }
        return List.copyOf(slots);
    }

    static List<Integer> parseConfiguredSlots(List<?> values) {
        List<Integer> slots = new ArrayList<>(values.size());
        for (Object raw : values) {
            Integer slot = parseSlot(raw);
            if (slot == null) {
                throw new IllegalStateException("Invalid public GUI layout slot: " + raw);
            }
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    private boolean isContentControlSlot(String path, int slot) {
        if (!path.endsWith(".content-slots") && !path.endsWith(".order-slots")) {
            return false;
        }
        String guiPath = path.substring("layout.".length(), path.lastIndexOf('.'));
        for (Map.Entry<String, Integer> entry : effectiveItemSlots.entrySet()) {
            if (entry.getKey().startsWith(guiPath + ".") && entry.getValue() == slot) {
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

            YamlConfiguration loaded = OwnerYaml.load(targetFile);
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

        YamlConfiguration legacy = OwnerYaml.load(legacyFile);
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

    public record GuiItem(Material material, String name, List<String> lore, Integer customModelData,
                          List<ItemFlag> itemFlags, boolean flagsConfigured, int amount) {
        public GuiItem {
            lore = copyStringList(lore);
            itemFlags = itemFlags == null ? List.of() : List.copyOf(itemFlags);
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
