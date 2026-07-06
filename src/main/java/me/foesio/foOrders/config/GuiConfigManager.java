package me.foesio.foOrders.config;

import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.foOrders.FoOrders;
import me.foesio.foOrders.util.TextFormat;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiConfigManager {
    private static final String FILE_NAME = "guis/orders.yml";
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
    private static final String TAX_DISCLOSURE_LORE = "#a7b8b0Tax ({tax_percent}%): #03fc88${tax}";
    private static final List<String> REMOVED_ADMIN_GUI_PATHS = List.of(
        "titles.admin-actions",
        "titles.admin-item-editor",
        "titles.admin-item-edit",
        "items.main.order-entry.lore.admin-cancel",
        "items.manage-order.admin-actions",
        "items.admin-actions",
        "items.admin-item-editor",
        "items.admin-item-edit"
    );

    private final Plugin plugin;
    private final Set<String> warnedMessages = ConcurrentHashMap.newKeySet();
    private final Map<TitleCacheKey, String> titleCache = new ConcurrentHashMap<>();
    private final Map<GuiItemCacheKey, GuiItem> itemCache = new ConcurrentHashMap<>();
    private final Map<LabelsCacheKey, List<String>> labelsCache = new ConcurrentHashMap<>();
    private final Map<SlotCacheKey, Integer> slotCache = new ConcurrentHashMap<>();
    private final Map<SlotsCacheKey, List<Integer>> slotsCache = new ConcurrentHashMap<>();
    private YamlConfiguration guis = new YamlConfiguration();
    private volatile GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private volatile int revision;

    public GuiConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        warnedMessages.clear();
        clearCaches();
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaultsStream = plugin.getResource(FILE_NAME)) {
            if (defaultsStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
                );
                loaded.setDefaults(defaults);
                boolean changed = migrateLegacyButtonsFile(loaded);
                changed |= backfillMissingDefaults(loaded, defaults);
                changed |= backfillNewOrderConfirmTaxLore(loaded, defaults);
                changed |= removeAdminOnlyGuiPaths(loaded);
                if (changed) {
                    loaded.save(file);
                }
            }
        } catch (IOException exception) {
            warn("Could not update " + FILE_NAME + " defaults: " + exception.getMessage());
        }

        guis = loaded;
        buttons = loadButtons(loaded);
        revision++;
        clearCaches();
    }

    public int revision() {
        return revision;
    }

    public GuiButtonConfig buttons() {
        return buttons;
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
        return TextFormat.colorize(TextFormat.applyPlaceholders(raw == null ? fallback : raw, placeholders));
    }

    public String text(String path, String fallback, Map<String, String> placeholders) {
        String raw = guis.getString(path, fallback);
        return TextFormat.colorize(TextFormat.applyPlaceholders(raw == null ? fallback : raw, placeholders));
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
        if ((placeholders == null || placeholders.isEmpty()) && (hiddenLorePlaceholders == null || hiddenLorePlaceholders.isEmpty())) {
            return itemCache.computeIfAbsent(
                new GuiItemCacheKey(path, fallbackMaterial, fallbackName, fallbackLore),
                ignored -> buildItem(path, fallbackMaterial, fallbackName, fallbackLore, Map.of(), List.of())
            );
        }
        return buildItem(path, fallbackMaterial, fallbackName, fallbackLore, placeholders, hiddenLorePlaceholders);
    }

    private GuiItem buildItem(
        String path,
        Material fallbackMaterial,
        String fallbackName,
        List<String> fallbackLore,
        Map<String, String> placeholders,
        List<String> hiddenLorePlaceholders
    ) {
        Map<String, String> safePlaceholders = placeholders == null ? Map.of() : placeholders;
        List<String> safeHiddenLorePlaceholders = hiddenLorePlaceholders == null ? List.of() : hiddenLorePlaceholders;
        String root = "items." + path + ".";
        Material material = material(guis.getString(root + "material"), fallbackMaterial);
        String rawName = guis.getString(root + "name", fallbackName);
        List<String> rawLore = guis.getStringList(root + "lore");
        if (rawLore.isEmpty() && !guis.contains(root + "lore")) {
            rawLore = fallbackLore == null ? List.of() : fallbackLore;
        }
        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            if (containsHiddenPlaceholder(line, safeHiddenLorePlaceholders)) {
                continue;
            }
            lore.add(TextFormat.colorize(TextFormat.applyPlaceholders(line, safePlaceholders)));
        }
        return new GuiItem(
            material,
            TextFormat.colorize(TextFormat.applyPlaceholders(rawName == null ? fallbackName : rawName, safePlaceholders)),
            lore
        );
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
            labels.add(TextFormat.colorize(rawLabel));
        }
        return List.copyOf(labels);
    }

    public int itemSlot(String path, int fallback, int inventorySize) {
        return slot("items." + path + ".slot", fallback, inventorySize);
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
                if (!seenSlots.add(slot)) {
                    warn("Duplicate fallback GUI slot at '" + path + "': " + slot + ". Ignoring duplicate.");
                    continue;
                }
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
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

    private boolean removeAdminOnlyGuiPaths(YamlConfiguration loaded) {
        boolean changed = false;
        for (String path : REMOVED_ADMIN_GUI_PATHS) {
            if (!loaded.isSet(path)) {
                continue;
            }
            loaded.set(path, null);
            changed = true;
        }
        return changed;
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
            warn("Could not remove old " + LEGACY_BUTTONS_FILE_NAME + ". FoOrders now uses " + FILE_NAME + " for public GUI buttons.");
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
            buttonConfig.set(key, section.get(key));
        }
        return GuiButtonConfig.fromGuiFile(buttonConfig);
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

    public record GuiItem(Material material, String name, List<String> lore) {
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
