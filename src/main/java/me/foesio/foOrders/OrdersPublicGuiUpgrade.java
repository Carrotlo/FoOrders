package me.foesio.foOrders;

import me.foesio.core.gui.publicgui.GuiDefinitionException;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.foOrders.config.GuiConfigManager;
import me.foesio.foOrders.config.PublicSoundFiles;
import me.foesio.foOrders.dialog.PublicDialogUpgrade;
import me.foesio.foOrders.util.OwnerYaml;
import me.foesio.foOrders.util.OwnerUpgradeBackups;
import me.foesio.foOrders.util.DeferredPublicMigrations;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** One-time conversion from the released FoOrders GUI shape to Core 1.10. */
final class OrdersPublicGuiUpgrade {
    static final List<String> SCREENS = List.of("main", "your-orders", "new-order", "item-select",
        "enchant-select", "manage-order", "claim-order", "deliver", "delivery-confirm", "history");
    private static final List<String> HISTORY = List.of("current", "4.6", "4.5", "4.4", "4.1");
    private static final List<String> ITEM_FIELDS = List.of("material", "name", "lore", "amount",
        "custom-model-data", "glow", "item-flags", "skull-skin-url");
    private static final List<String> SOURCE_FIELDS = List.of("material", "amount", "glow", "item-flags");
    private static final List<String> ALL_FLAGS = List.of("HIDE_ENCHANTS", "HIDE_ATTRIBUTES",
        "HIDE_UNBREAKABLE", "HIDE_DESTROYS", "HIDE_PLACED_ON", "HIDE_ADDITIONAL_TOOLTIP",
        "HIDE_DYE", "HIDE_ARMOR_TRIM", "HIDE_STORED_ENCHANTS");

    private OrdersPublicGuiUpgrade() { }

    static Set<String> migrate(FoOrders plugin, FoMigrationStore versions, int originalVersion,
                               boolean freshCoreInstall) {
        if (versions.version() >= GuiConfigManager.CORE_PUBLIC_GUI_VERSION) {
            try {
                OwnerUpgradeBackups.clearPublicGuiOrigin(plugin.getDataFolder());
            } catch (IOException cleanupFailure) {
                plugin.getLogger().warning("Could not remove completed public GUI origin marker: "
                    + cleanupFailure.getMessage());
            }
            return Set.copyOf(SCREENS);
        }
        if (freshCoreInstall) {
            versions.runToVersion(GuiConfigManager.CORE_PUBLIC_GUI_VERSION, () -> true);
            return versions.version() >= GuiConfigManager.CORE_PUBLIC_GUI_VERSION
                ? Set.copyOf(SCREENS) : Set.of();
        }
        Map<Path, byte[]> before = new LinkedHashMap<>();
        Map<Path, byte[]> proposed = new LinkedHashMap<>();
        Set<String> available = new java.util.LinkedHashSet<>();
        Set<String> pending = new java.util.LinkedHashSet<>();
        try {
            OwnerUpgradeBackups.retainPublicGuiOrigin(plugin.getDataFolder(), originalVersion);
            Path root = plugin.getDataFolder().toPath();
            YamlConfiguration publicText = loadBundled(plugin, "public-text.yml");
            File priorText = root.resolve("public-text.yml").toFile();
            if (priorText.isFile()) {
                publicText = OwnerYaml.load(priorText);
            }
            for (String screen : SCREENS) {
                String relative = "guis/" + screen + ".yml";
                Path active = root.resolve(relative);
                try {
                    if (!Files.isRegularFile(active)) {
                        throw new IOException("Missing prior GUI " + relative + "; cannot infer owner intent");
                    }
                    byte[] original = Files.readAllBytes(active);
                    YamlConfiguration old = parseStrict(active.toFile());
                    if (old.isSet("rows") && !old.isSet("size")) {
                        // A prior partial upgrade installed this screen. Keep valid owner edits,
                        // but repair invalid fields while the versioned upgrade is still pending.
                        YamlConfiguration bundled = loadBundled(plugin, relative);
                        if (repairPartialCoreScreen(screen, old, bundled, plugin.getLogger()::warning)) {
                            before.put(active, original);
                            proposed.put(active, old.saveToString().getBytes(StandardCharsets.UTF_8));
                        }
                        available.add(screen);
                        continue;
                    }
                    List<YamlConfiguration> defaults = historicalDefaults(plugin, screen);
                    YamlConfiguration newest = loadBundled(plugin, relative);
                    YamlConfiguration nextText = copy(publicText);
                    convertScreen(screen, old, defaults, newest, nextText, originalVersion,
                        plugin.getLogger()::warning);
                    validate(screen, newest);
                    before.put(active, original);
                    proposed.put(active, newest.saveToString().getBytes(StandardCharsets.UTF_8));
                    publicText = nextText;
                    available.add(screen);
                } catch (IOException | RuntimeException error) {
                    pending.add(screen);
                    plugin.getLogger().severe("Public GUI " + relative + " was left unchanged: "
                        + error.getMessage());
                }
            }
            GuiConfigManager.repairLegacyPublicTextCandidate(publicText,
                loadBundled(plugin, "public-text.yml"), plugin.getLogger()::warning);
            Path textPath = root.resolve("public-text.yml");
            before.put(textPath, Files.isRegularFile(textPath) ? Files.readAllBytes(textPath) : null);
            proposed.put(textPath, publicText.saveToString().getBytes(StandardCharsets.UTF_8));
            Path messagesPath = root.resolve("messages.yml");
            YamlConfiguration bundledMessages = loadBundled(plugin, "messages.yml");
            AuxiliaryResult messageResult = stageAuxiliary(messagesPath, before, proposed,
                owner -> {
                    boolean changed = PublicMessageFields.repair(owner, bundledMessages,
                        plugin.getLogger()::warning);
                    changed |= PluginMessages.repairPresentInvalidNumberFields(owner, bundledMessages,
                        plugin.getLogger()::warning);
                    PublicMessageFields.validateCustomTokensForUpgrade(owner, bundledMessages);
                    return changed;
                },
                plugin.getLogger()::severe);
            boolean messagesChanged = messageResult == AuxiliaryResult.CHANGED;
            if (messageResult == AuxiliaryResult.PENDING) pending.add("messages.yml");
            Path soundPath = root.resolve("sounds.yml");
            YamlConfiguration bundledSounds = loadBundled(plugin, "sounds.yml");
            AuxiliaryResult soundResult = stageAuxiliary(soundPath, before, proposed,
                owner -> PublicSoundFiles.repairLegacyInvalidFields(owner,
                    bundledSounds, plugin.getLogger()::warning),
                plugin.getLogger()::severe);
            boolean soundChanged = soundResult == AuxiliaryResult.CHANGED;
            if (soundResult == AuxiliaryResult.PENDING) pending.add("sounds.yml");
            boolean dialogChanged = false;
            for (String dialog : List.of("amount", "money", "search", "item-select", "enchant-select")) {
                Path dialogPath = root.resolve("dialogs").resolve(dialog + ".yml");
                YamlConfiguration bundledDialog = loadBundled(plugin, "dialogs/" + dialog + ".yml");
                AuxiliaryResult result = stageAuxiliary(dialogPath, before, proposed,
                    owner -> PublicDialogUpgrade.repairInvalidFields(dialog, owner,
                        bundledDialog,
                        plugin.getLogger()::warning), plugin.getLogger()::severe);
                if (result == AuxiliaryResult.CHANGED) dialogChanged = true;
                if (result == AuxiliaryResult.PENDING) pending.add("dialogs/" + dialog + ".yml");
            }
            if (DeferredPublicMigrations.hasPending(plugin.getDataFolder())) {
                pending.add("deferred legacy public file steps");
            }
            installConverted(before, proposed, originalVersion, () -> {
                if (!pending.isEmpty()) return true;
                versions.runToVersion(GuiConfigManager.CORE_PUBLIC_GUI_VERSION, () -> true);
                return versions.version() >= GuiConfigManager.CORE_PUBLIC_GUI_VERSION;
            });
            if (versions.version() >= GuiConfigManager.CORE_PUBLIC_GUI_VERSION) {
                try {
                    OwnerUpgradeBackups.clearPublicGuiOrigin(plugin.getDataFolder());
                } catch (IOException cleanupFailure) {
                    plugin.getLogger().warning("Could not remove completed public GUI origin marker: "
                        + cleanupFailure.getMessage());
                }
            }
            if (messagesChanged) plugin.reloadMessages();
            if (soundChanged) plugin.reloadSoundFiles();
            if (dialogChanged) plugin.reloadDialogFiles();
            if (!pending.isEmpty()) {
                plugin.getLogger().severe("Public interface migration remains pending for " + pending
                    + "; converted screens are available and will not be rewritten on retry.");
            }
            return available;
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().severe("Core public GUI migration failed; installed files were preserved or restored: "
                + exception.getMessage());
            return Set.of();
        }
    }

    enum AuxiliaryResult { UNCHANGED, CHANGED, PENDING }

    @FunctionalInterface
    interface AuxiliaryRepair {
        boolean repair(YamlConfiguration owner);
    }

    static AuxiliaryResult stageAuxiliary(Path path, Map<Path, byte[]> before,
                                          Map<Path, byte[]> proposed, AuxiliaryRepair repair,
                                          Consumer<String> warn) {
        if (!Files.isRegularFile(path)) return AuxiliaryResult.UNCHANGED;
        try {
            byte[] original = Files.readAllBytes(path);
            YamlConfiguration owner = parseStrict(path.toFile());
            if (!repair.repair(owner)) return AuxiliaryResult.UNCHANGED;
            before.put(path, original);
            proposed.put(path, owner.saveToString().getBytes(StandardCharsets.UTF_8));
            return AuxiliaryResult.CHANGED;
        } catch (IOException | RuntimeException failure) {
            warn.accept("Public interface " + path + " was left unchanged: " + failure.getMessage());
            return AuxiliaryResult.PENDING;
        }
    }

    private static YamlConfiguration copy(YamlConfiguration source) throws IOException {
        YamlConfiguration next = new YamlConfiguration();
        try {
            next.loadFromString(source.saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IOException("Could not stage public-text.yml", error);
        }
        return next;
    }

    /** Stages the complete set, retains original bytes once, then commits or rolls back. */
    static void installConverted(Map<Path, byte[]> before, Map<Path, byte[]> proposed,
                                 int originalVersion, BooleanSupplier persistVersion) throws IOException {
        Map<Path, Path> staged = new LinkedHashMap<>();
        boolean attemptedCommit = false;
        try {
            if (!before.keySet().equals(proposed.keySet())) {
                throw new IOException("Migration snapshot and proposed file set differ");
            }
            for (Map.Entry<Path, byte[]> entry : proposed.entrySet()) {
                staged.put(entry.getKey(), stage(entry.getKey(), entry.getValue()));
            }
            for (Map.Entry<Path, byte[]> entry : before.entrySet()) {
                if (entry.getValue() == null
                    || !OwnerUpgradeBackups.wasOriginallyPresent(entry.getKey().toFile())) continue;
                Path backup = entry.getKey().resolveSibling(entry.getKey().getFileName()
                    + ".gui-v" + originalVersion + "-backup");
                if (!Files.exists(backup)) Files.write(backup, entry.getValue());
            }
            attemptedCommit = true;
            for (Map.Entry<Path, Path> entry : staged.entrySet()) replace(entry.getValue(), entry.getKey());
            if (!persistVersion.getAsBoolean()) {
                throw new IOException("Could not persist the new public GUI schema version");
            }
        } catch (IOException | RuntimeException failure) {
            if (attemptedCommit) {
                IOException rollbackError = null;
                for (Map.Entry<Path, byte[]> entry : before.entrySet()) {
                    try {
                        if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                        else replace(stage(entry.getKey(), entry.getValue()), entry.getKey());
                    } catch (IOException error) {
                        if (rollbackError == null) rollbackError = error;
                        else rollbackError.addSuppressed(error);
                    }
                }
                if (rollbackError != null) failure.addSuppressed(rollbackError);
            }
            if (failure instanceof IOException io) throw io;
            throw new IOException("Migration transaction failed", failure);
        } finally {
            staged.values().forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        }
    }

    private static Path stage(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".gui-core-", ".stage");
        Files.write(temporary, bytes);
        return temporary;
    }

    private static void replace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static YamlConfiguration parseStrict(File file) {
        return OwnerYaml.load(file);
    }

    private static YamlConfiguration loadBundled(FoOrders plugin, String resource) throws IOException {
        try (InputStream stream = plugin.getResource(resource)) {
            if (stream == null) throw new IOException("Missing bundled resource " + resource);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IOException("Invalid bundled YAML " + resource, exception);
        }
    }

    private static List<YamlConfiguration> historicalDefaults(FoOrders plugin, String screen) throws IOException {
        List<YamlConfiguration> defaults = new ArrayList<>();
        for (String version : HISTORY) {
            String path = "migrations/legacy-public-defaults/" + version + "/guis/" + screen + ".yml";
            try (InputStream stream = plugin.getResource(path)) {
                if (stream == null) continue;
                YamlConfiguration yaml = new YamlConfiguration();
                try {
                    yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
                    throw new IOException("Invalid historical default " + path, exception);
                }
                defaults.add(yaml);
            }
        }
        if (defaults.isEmpty()) throw new IOException("No released defaults for " + screen);
        return defaults;
    }

    private static void validate(String screen, YamlConfiguration yaml) throws IOException {
        try {
            PublicGuiDefinition definition = PublicGuiSchema.parse("orders." + screen, "guis/" + screen + ".yml", yaml,
                OrdersPublicGuis.contractFor(screen));
            validateGameplayRequirements(screen, definition);
        } catch (GuiDefinitionException exception) {
            throw new IOException(screen + ":" + exception.key() + ": " + exception.getMessage(), exception);
        }
    }

    /** Match the fields required by OrdersPublicGuis.start before a retry marks a screen available. */
    private static void validateGameplayRequirements(String screen, PublicGuiDefinition definition)
            throws GuiDefinitionException {
        switch (screen) {
            case "main" -> requireEntryLore(definition, "order-entry", "normal", "admin");
            case "your-orders" -> requireEntryLore(definition, "order-entry", "normal");
            case "new-order" -> {
                requireEntry(definition, "item", 1);
                requireButtonStates(definition, "confirm", "taxed", "untaxed");
                requireButtonStates(definition, "enchants", "hidden");
            }
            case "item-select" -> requireEntryLore(definition, "entry", "vanilla", "custom");
            case "enchant-select" -> requireEntryLore(definition, "entry", "selected", "unselected");
            case "claim-order" -> requireEntry(definition, "entry", 0);
            case "history" -> {
                requireEntryLore(definition, "entry", "order", "deliver");
                requireButtonStates(definition, "order-tab", "selected", "unselected");
                requireButtonStates(definition, "deliver-tab", "selected", "unselected");
            }
            default -> { }
        }
    }

    private static PublicGuiDefinition.Entry requireEntry(PublicGuiDefinition definition, String id, int slots)
            throws GuiDefinitionException {
        PublicGuiDefinition.Entry entry = definition.entries().get(id);
        if (entry == null) throw new GuiDefinitionException("entries." + id, "required gameplay entry is missing");
        if (slots > 0 && entry.slots().size() != slots) {
            throw new GuiDefinitionException("entries." + id + ".slots", "required gameplay entry has the wrong slot count");
        }
        return entry;
    }

    private static void requireEntryLore(PublicGuiDefinition definition, String id, String... states)
            throws GuiDefinitionException {
        PublicGuiDefinition.Entry entry = requireEntry(definition, id, 0);
        if (entry.sourceItem() == null) {
            throw new GuiDefinitionException("entries." + id + ".source-item", "required source item is missing");
        }
        for (String state : states) {
            if (!entry.sourceItem().loreStates().containsKey(state)) {
                throw new GuiDefinitionException("entries." + id + ".source-item.lore-states." + state,
                    "required gameplay lore state is missing");
            }
        }
    }

    private static void requireButtonStates(PublicGuiDefinition definition, String id, String... states)
            throws GuiDefinitionException {
        PublicGuiDefinition.Button button = definition.buttons().get(id);
        if (button == null) throw new GuiDefinitionException("buttons." + id, "required gameplay button is missing");
        for (String state : states) {
            if (!button.states().containsKey(state)) {
                throw new GuiDefinitionException("buttons." + id + ".states." + state,
                    "required gameplay button state is missing");
            }
        }
    }

    static boolean repairPartialCoreScreen(String screen, YamlConfiguration owner,
                                           YamlConfiguration bundled, Consumer<String> warn) throws IOException {
        boolean changed = false;
        Set<String> repaired = new java.util.HashSet<>();
        while (true) {
            try {
                validate(screen, owner);
                return changed;
            } catch (IOException invalid) {
                String key = invalid.getCause() instanceof GuiDefinitionException definitionError
                    ? definitionError.key() : "";
                String listPath = key.replaceFirst("\\[\\d+]$", "");
                boolean indexed = !listPath.equals(key);
                String path = indexed ? listPath : key;
                if (path.isBlank() || !bundled.isSet(path) || !repaired.add(key)) {
                    throw new IOException("Cannot isolate invalid partially migrated GUI field for "
                        + screen + " (schema key " + key + "); leaving this screen unchanged", invalid);
                }
                Object prior = owner.get(path);
                Object fallback = bundled.get(path);
                if (indexed && prior instanceof List<?> ownerList && fallback instanceof List<?> bundledList) {
                    int index = Integer.parseInt(key.substring(listPath.length() + 1, key.length() - 1));
                    if (index < ownerList.size() && index < bundledList.size()) {
                        List<Object> repairedList = new ArrayList<>(ownerList);
                        repairedList.set(index, bundledList.get(index));
                        owner.set(path, repairedList);
                        prior = ownerList.get(index);
                        fallback = bundledList.get(index);
                    } else {
                        throw new IOException("No newest bundled list entry for " + screen + ":" + key
                            + "; leaving this screen unchanged", invalid);
                    }
                } else {
                    owner.set(path, fallback);
                }
                warn.accept("Invalid partially migrated GUI value " + screen + ":" + key
                    + "=" + Objects.toString(prior) + "; using newest bundled default "
                    + Objects.toString(fallback));
                changed = true;
            }
        }
    }

    static void convertScreen(String screen, YamlConfiguration old,
                              List<YamlConfiguration> defaults, YamlConfiguration newest,
                              YamlConfiguration publicText, int originalVersion,
                              Consumer<String> warn) throws IOException {
        Map<String, Object> candidates = new LinkedHashMap<>();
        add(old, defaults, candidates, "title", "title");
        if (custom(old, defaults, "size")) {
            Object size = old.get("size");
            candidates.put("rows", size instanceof Number number && number.intValue() % 9 == 0
                ? number.intValue() / 9 : size);
        }
        ConfigurationSection buttons = newest.getConfigurationSection("buttons");
        if (buttons != null) for (String id : buttons.getKeys(false)) {
            String prior = id.equals("back-to-orders") ? "back" : id;
            mapVisual(old, defaults, candidates, prior, "buttons." + id, true,
                screen.equals("new-order"), originalVersion);
            add(old, defaults, candidates, "items." + id + ".slot", "buttons." + id + ".slot");
            if (id.equals("sort") || id.equals("filter")) {
                add(old, defaults, candidates, "items." + id + ".selected-option",
                    "buttons." + id + ".cycle.selected-format");
                add(old, defaults, candidates, "items." + id + ".other-option",
                    "buttons." + id + ".cycle.unselected-format");
            }
            if (screen.equals("history") && (id.equals("order-tab") || id.equals("deliver-tab"))) {
                mapHistoryTab(old, defaults, candidates, id, originalVersion);
            }
        }
        ConfigurationSection entries = newest.getConfigurationSection("entries");
        if (entries != null) for (String id : entries.getKeys(false)) {
            String target = "entries." + id;
            if (newest.isConfigurationSection(target + ".item")) {
                mapVisual(old, defaults, candidates, id, target + ".item", false, false,
                    originalVersion);
            }
            if (newest.isConfigurationSection(target + ".source-item")) {
                mapSource(screen, old, defaults, candidates, id, target + ".source-item",
                    newest, originalVersion, warn);
            }
            if (newest.isSet(target + ".slots") && !id.equals("admin-actions")) {
                String oldLayout = id.equals("order-entry") ? "order-slots" : "content-slots";
                if (id.equals("order-entry") || id.equals("entry")) {
                    add(old, defaults, candidates, "layout." + oldLayout, target + ".slots");
                } else {
                    addSingleSlot(old, defaults, candidates, "items." + id + ".slot", target + ".slots");
                }
            }
        }
        if (screen.equals("main")) {
            mapVisual(old, defaults, candidates, "empty-order-slot",
                "entries.order-entry.empty-item", false, false, originalVersion);
            mapVisual(old, defaults, candidates, "gray-pane", "decorations.frame", false, false,
                originalVersion);
            if (custom(old, defaults, "layout.frame-slots")) {
                Object slots = old.get("layout.frame-slots");
                if (slots instanceof List<?> source) {
                    int maxSlot = newest.getInt("rows") * 9;
                    for (int index = 0; index < source.size(); index++) {
                        Object slot = source.get(index);
                        if (!(slot instanceof Integer number) || number < 0 || number >= maxSlot) {
                            throw new IOException("Cannot safely map invalid legacy frame slot main:layout.frame-slots["
                                + index + "]=" + Objects.toString(slot) + "; leaving this screen unchanged");
                        }
                    }
                    if (source.stream().distinct().count() != source.size()) {
                        throw new IOException("Cannot safely map duplicate legacy frame slots "
                            + "main:layout.frame-slots=" + source + "; leaving this screen unchanged");
                    }
                    Set<Integer> occupied = occupiedControlSlots(newest, candidates);
                    candidates.put("decorations.frame.slots", source.stream()
                        .map(Integer.class::cast)
                        .filter(slot -> !occupied.contains(slot)).toList());
                } else candidates.put("decorations.frame.slots", slots);
            }
        }
        if (screen.equals("enchant-select")) {
            mapVisual(old, defaults, candidates, "controls", "decorations.controls", false, false,
                originalVersion);
            addSingleSlot(old, defaults, candidates, "items.controls.slot", "decorations.controls.slots");
        }
        if (screen.equals("history")) {
            mapVisual(old, defaults, candidates, "info", "decorations.info", false, false,
                originalVersion);
            addSingleSlot(old, defaults, candidates, "items.info.slot", "decorations.info.slots");
        }
        mapPublicText(screen, old, defaults, publicText, originalVersion, warn);
        applyCandidates(warn, screen, newest, candidates);
    }

    private static void mapVisual(YamlConfiguration old, List<YamlConfiguration> defaults,
                                  Map<String, Object> candidates, String id, String target,
                                  boolean button, boolean newOrderScreen, int originalVersion) {
        for (String field : ITEM_FIELDS) {
            String itemPath = "items." + id + "." + field;
            String sharedPath = "buttons." + (id.equals("back-to-orders") ? "back" : id) + "." + field;
            String sourcePath = old.isSet(itemPath) ? itemPath : sharedPath;
            if (custom(old, defaults, sourcePath)) {
                Object value = old.get(sourcePath);
                if (field.equals("item-flags")) value = expandFlags(value);
                if (field.equals("lore") && (id.equals("enchants") || id.equals("claim"))) {
                    value = replaceListToken(value, id.equals("enchants") ? "{enchantments}" : "{claim_stacks}",
                        "{options}");
                }
                candidates.put(target + "." + field, value);
                if (button && newOrderScreen && id.equals("confirm")
                    && target.equals("buttons.confirm")) {
                    candidates.put("buttons.confirm.states.taxed." + field, value);
                    if (field.equals("lore") && value instanceof List<?> lines) {
                        candidates.put("buttons.confirm.states.untaxed.lore", lines.stream()
                            .filter(line -> !Objects.toString(line, "").contains("{tax}"))
                            .toList());
                    } else {
                        candidates.put("buttons.confirm.states.untaxed." + field, value);
                    }
                }
            } else if (originalVersion >= 37 && !old.isSet(itemPath) && !old.isSet(sharedPath)
                && (defaults.get(0).isSet(itemPath) || defaults.get(0).isSet(sharedPath))) {
                candidates.put(target + "." + field, null);
            }
        }
        String itemEnabled = "items." + id + ".enabled";
        String sharedEnabled = "buttons." + (id.equals("back-to-orders") ? "back" : id) + ".enabled";
        String enabled = old.isSet(itemEnabled) ? itemEnabled : sharedEnabled;
        if (custom(old, defaults, enabled)) {
            candidates.put(button ? target + ".enabled" : target.substring(0, target.lastIndexOf('.')) + ".enabled",
                old.get(enabled));
        } else if (originalVersion >= 37 && !old.isSet(itemEnabled) && !old.isSet(sharedEnabled)
            && (defaults.get(0).isSet(itemEnabled) || defaults.get(0).isSet(sharedEnabled))) {
            candidates.put(button ? target + ".enabled" : target.substring(0, target.lastIndexOf('.')) + ".enabled",
                false);
        }
    }

    private static void mapSource(String screen, YamlConfiguration old, List<YamlConfiguration> defaults,
                                  Map<String, Object> candidates, String id, String target,
                                  YamlConfiguration newest, int originalVersion,
                                  Consumer<String> warn) throws IOException {
        String source = "items." + id;
        add(old, defaults, candidates, source + ".name", target + ".default-name");
        add(old, defaults, candidates, source + ".enabled", target + ".enabled");
        if (originalVersion >= 37 && defaults.get(0).isSet(source + ".enabled")
            && !old.isSet(source + ".enabled")) {
            candidates.put(target + ".enabled", false);
        }
        for (String field : SOURCE_FIELDS) {
            if (custom(old, defaults, source + "." + field)) {
                candidates.put(target + "." + field,
                    field.equals("item-flags") ? expandFlags(old.get(source + "." + field))
                        : old.get(source + "." + field));
            }
        }
        if (custom(old, defaults, source + ".custom-model-data")) {
            candidates.put(target + ".custom-model-data", Map.of("mode", "replace",
                "value", old.get(source + ".custom-model-data")));
        }
        if (screen.equals("main") || screen.equals("your-orders")) {
            if (changedTree(old, defaults, source + ".lore", originalVersion)
                || custom(old, defaults, source + ".lore-layout")
                || originalVersion >= 37 && !old.isSet(source + ".lore-layout")
                    && defaults.get(0).isSet(source + ".lore-layout")) {
                Object layout = old.get(source + ".lore-layout");
                if (old.isSet(source + ".lore-layout")
                    && (!(layout instanceof List<?> lines)
                        || lines.stream().anyMatch(line -> !(line instanceof String)))) {
                    warn.accept("Invalid legacy GUI value " + screen + ":" + source
                        + ".lore-layout=" + Objects.toString(layout)
                        + "; using newest bundled default " + target + ".lore-states.*.lines");
                    return;
                }
                List<String> normal = flattenOrderLore(screen, old, defaults.get(0), newest,
                    source, target, false, warn);
                candidates.put(target + ".lore-states.normal.lines", normal);
                if (screen.equals("main")) {
                    candidates.put(target + ".lore-states.admin.lines", flattenOrderLore(
                        screen, old, defaults.get(0), newest, source, target, true, warn));
                }
            }
        } else if (screen.equals("item-select")) {
            add(old, defaults, candidates, source + ".name", target + ".default-name");
            add(old, defaults, candidates, source + ".lore", target + ".lore-states.vanilla.lines");
            for (String field : List.of("name", "lore", "material", "amount", "enabled", "item-flags")) {
                String oldPath = "items.custom-entry." + field;
                String mapped = switch (field) {
                    case "name" -> "default-name";
                    case "lore" -> "lines";
                    default -> field;
                };
                if (custom(old, defaults, oldPath)) {
                    Object value = old.get(oldPath);
                    if (field.equals("lore")) value = replaceListToken(value, "{template_lore}", "{source_lore}");
                    if (field.equals("item-flags")) value = expandFlags(value);
                    candidates.put(target + ".lore-states.custom." + mapped, value);
                }
            }
            add(old, defaults, candidates, source + ".enabled", target + ".lore-states.vanilla.enabled");
            if (custom(old, defaults, "items.custom-entry.custom-model-data")) {
                candidates.put(target + ".lore-states.custom.custom-model-data",
                    Map.of("mode", "replace", "value", old.get("items.custom-entry.custom-model-data")));
            }
        } else if (screen.equals("enchant-select") || screen.equals("history")) {
            if (screen.equals("enchant-select")) {
                add(old, defaults, candidates, source + ".lore", target + ".lore-states.selected.lines");
                add(old, defaults, candidates, source + ".lore", target + ".lore-states.unselected.lines");
                add(old, defaults, candidates, source + ".selected-material",
                    target + ".lore-states.selected.material");
                add(old, defaults, candidates, source + ".unselected-material",
                    target + ".lore-states.unselected.material");
            } else {
                add(old, defaults, candidates, source + ".lore", target + ".lore-states.order.lines");
                add(old, defaults, candidates, source + ".lore", target + ".lore-states.deliver.lines");
                add(old, defaults, candidates, source + ".order-material",
                    target + ".lore-states.order.material");
                add(old, defaults, candidates, source + ".deliver-material",
                    target + ".lore-states.deliver.material");
            }
        } else if (screen.equals("claim-order")) {
            if (custom(old, defaults, source + ".lore")) {
                Object value = old.get(source + ".lore");
                if (value instanceof List<?> lines) value = lines.stream()
                    .filter(line -> !Objects.equals(line, "{template_lore}")).toList();
                candidates.put(target + ".lore.lines", value);
            }
        } else if (screen.equals("new-order")) {
            add(old, defaults, candidates, source + ".lore", target + ".lore.lines");
        }
        if (originalVersion >= 37 && defaults.get(0).isSet(source + ".lore")
            && !old.isSet(source + ".lore")) {
            switch (screen) {
                case "item-select" -> candidates.put(target + ".lore-states.vanilla.lines", List.of());
                case "enchant-select" -> {
                    candidates.put(target + ".lore-states.selected.lines", List.of());
                    candidates.put(target + ".lore-states.unselected.lines", List.of());
                }
                case "history" -> {
                    candidates.put(target + ".lore-states.order.lines", List.of());
                    candidates.put(target + ".lore-states.deliver.lines", List.of());
                }
                case "claim-order", "new-order" -> candidates.put(target + ".lore.lines", List.of());
                default -> { }
            }
        }
        if (screen.equals("item-select") && originalVersion >= 37
            && defaults.get(0).isSet("items.custom-entry.lore")
            && !old.isSet("items.custom-entry.lore")) {
            candidates.put(target + ".lore-states.custom.lines", List.of());
        }
    }

    private static void mapHistoryTab(YamlConfiguration old, List<YamlConfiguration> defaults,
                                      Map<String, Object> candidates, String id, int originalVersion) throws IOException {
        for (String state : List.of("selected", "unselected")) {
            String source = "items." + id + "." + state;
            String target = "buttons." + id + ".states." + state;
            add(old, defaults, candidates, source + "-material", target + ".material");
            if (originalVersion >= 23 && defaults.get(0).isSet(source + "-name")
                && !old.isSet(source + "-name")) {
                candidates.put(target + ".name", "");
            } else if (custom(old, defaults, source + "-name")
                || custom(old, defaults, source + "-color")) {
                Object name = old.get(source + "-name");
                Object color = old.isSet(source + "-color") ? old.get(source + "-color") : "";
                if (!(name instanceof String validName) || !(color instanceof String validColor)) {
                    throw new IOException("Cannot safely map invalid legacy history tab name/color "
                        + source + "-name=" + Objects.toString(name) + ", " + source + "-color="
                        + Objects.toString(color) + "; leaving this screen unchanged");
                }
                candidates.put(target + ".name", validName.replace("{state_color}", validColor));
            }
            add(old, defaults, candidates, source + "-lore", target + ".lore");
        }
    }

    private static List<String> flattenOrderLore(String screen, YamlConfiguration old,
                                                 YamlConfiguration legacyDefault, YamlConfiguration newest,
                                                 String source, String target, boolean admin,
                                                 Consumer<String> warn) throws IOException {
        List<String> result = new ArrayList<>();
        for (String line : old.getStringList(source + ".lore-layout")) {
            if (line.equals("{admin_cancel_line}")) {
                if (admin) addOrderLoreLine(screen, old, legacyDefault, newest, source,
                    target, "admin", line, "admin-cancel", result, warn);
            } else if (line.matches("\\{[a-z_-]+_line}")) {
                String key = line.substring(1, line.length() - 6);
                addOrderLoreLine(screen, old, legacyDefault, newest, source, target,
                    admin ? "admin" : "normal", line, key, result, warn);
            } else result.add(line);
        }
        return result;
    }

    private static void addOrderLoreLine(String screen, YamlConfiguration old,
                                         YamlConfiguration legacyDefault, YamlConfiguration newest,
                                         String source, String target, String state, String token,
                                         String key, List<String> result, Consumer<String> warn) throws IOException {
        String path = source + ".lore." + key;
        if (!old.isSet(path)) return;
        if (old.isString(path)) {
            result.add(old.getString(path));
            return;
        }
        int index = legacyDefault.getStringList(source + ".lore-layout").indexOf(token);
        List<String> bundled = newest.getStringList(target + ".lore-states." + state + ".lines");
        if (index < 0 || index >= bundled.size()) {
            throw new IOException("Cannot isolate invalid legacy lore field " + screen + ":" + path);
        }
        String fallback = bundled.get(index);
        result.add(fallback);
        warn.accept("Invalid legacy GUI value " + screen + ":" + path + "="
            + Objects.toString(old.get(path)) + "; using newest bundled default " + fallback);
    }

    private static Set<Integer> occupiedControlSlots(YamlConfiguration newest, Map<String, Object> candidates) {
        java.util.HashSet<Integer> occupied = new java.util.HashSet<>();
        for (String group : List.of("buttons", "entries")) {
            ConfigurationSection controls = newest.getConfigurationSection(group);
            if (controls == null) continue;
            for (String id : controls.getKeys(false)) {
                String path = group + "." + id + (group.equals("buttons") ? ".slot" : ".slots");
                Object value = candidates.getOrDefault(path, newest.get(path));
                if (value instanceof Integer integer) occupied.add(integer);
                if (value instanceof List<?> slots) slots.stream().filter(Integer.class::isInstance)
                    .map(Integer.class::cast).forEach(occupied::add);
            }
        }
        return occupied;
    }

    private static void mapPublicText(String screen, YamlConfiguration old, List<YamlConfiguration> defaults,
                                      YamlConfiguration text, int originalVersion, Consumer<String> warn) {
        for (String root : List.of("labels", "events")) {
            ConfigurationSection section = old.getConfigurationSection(root);
            if (section == null) continue;
            for (String path : section.getKeys(true)) {
                String legacy = root + "." + path;
                if (old.isConfigurationSection(legacy)) continue;
                String target = root.equals("labels") ? "labels." + screen + "." + path
                    : "events." + screen + "." + path;
                boolean materialName = screen.equals("item-select")
                    && target.startsWith("labels.item-select.material-names.");
                if (!text.isSet(target) && !materialName) continue;
                if (custom(old, defaults, legacy)) {
                    Object value = old.get(legacy);
                    if ((materialName ? value instanceof String : sameType(value, text.get(target)))
                        && validPublicText(target, value)) {
                        text.set(target, value);
                    }
                    else warn.accept("Invalid legacy public text " + screen + ":" + legacy
                        + "=" + Objects.toString(value) + "; using newest bundled default "
                        + target + "=" + Objects.toString(text.get(target)));
                }
            }
            if (originalVersion >= 37) {
                String destinationRoot = root + "." + screen;
                ConfigurationSection destination = text.getConfigurationSection(destinationRoot);
                ConfigurationSection knownOld = defaults.get(0).getConfigurationSection(root);
                if (destination != null && knownOld != null) {
                    for (String key : new ArrayList<>(destination.getKeys(true))) {
                        if (destination.isConfigurationSection(key)) continue;
                        if (knownOld.isSet(key) && !old.isSet(root + "." + key)) {
                            text.set(destinationRoot + "." + key, null);
                        }
                    }
                }
            }
        }
        for (String path : List.of("items.manage-order.claim.max-stack-lines",
                "items.manage-order.claim.stack-line", "items.manage-order.claim.more-line",
                "items.manage-order.admin-actions.refund-line",
                "items.manage-order.admin-actions.no-refund-line",
                "items.new-order.enchants.summary-line")) {
            String prior = path.substring("items.".length());
            if (!prior.startsWith(screen + ".")) continue;
            prior = "items." + prior.substring(screen.length() + 1);
            if (custom(old, defaults, prior)) {
                Object value = old.get(prior);
                if (sameType(value, text.get(path)) && validPublicText(path, value)) {
                    text.set(path, value);
                } else {
                    warn.accept("Invalid legacy public text " + screen + ":" + prior
                        + "=" + Objects.toString(value) + "; using newest bundled default " + path);
                }
            } else if (originalVersion >= 37 && defaults.get(0).isSet(prior)
                && !old.isSet(prior)) {
                text.set(path, null);
            }
        }
    }

    private static boolean sameType(Object value, Object fallback) {
        if (value instanceof String) return fallback instanceof String;
        if (value instanceof List<?>) return fallback instanceof List;
        if (value instanceof Number) return fallback instanceof Number;
        if (value instanceof Boolean) return fallback instanceof Boolean;
        return false;
    }

    private static boolean validPublicText(String path, Object value) {
        if (path.equals("labels.main.item-display.max-enchants")
            || path.equals("items.manage-order.claim.max-stack-lines")) {
            return value instanceof Integer number && number >= 0;
        }
        if (path.equals("labels.history.timestamp-pattern")) {
            if (!(value instanceof String pattern) || pattern.isBlank()) return false;
            try { DateTimeFormatter.ofPattern(pattern); return true; }
            catch (IllegalArgumentException invalid) { return false; }
        }
        if (path.equals("labels.history.timestamp-zone")) {
            if (!(value instanceof String zone) || zone.isBlank()) return false;
            if (zone.equalsIgnoreCase("system")) return true;
            try { ZoneId.of(zone); return true; }
            catch (java.time.DateTimeException invalid) { return false; }
        }
        if (path.equals("labels.history.timestamp-locale")) {
            if (!(value instanceof String language) || language.isBlank()) return false;
            try { new java.util.Locale.Builder().setLanguageTag(language).build(); return true; }
            catch (java.util.IllformedLocaleException invalid) { return false; }
        }
        return true;
    }

    private static Object expandFlags(Object value) {
        return Objects.equals(value, "all") ? ALL_FLAGS : value;
    }

    private static Object replaceListToken(Object value, String from, String to) {
        if (!(value instanceof List<?> list)) return value;
        return list.stream().map(line -> Objects.equals(line, from) ? to : line).toList();
    }

    private static void add(YamlConfiguration old, List<YamlConfiguration> defaults,
                            Map<String, Object> candidates, String oldPath, String targetPath) {
        if (custom(old, defaults, oldPath)) candidates.put(targetPath, old.get(oldPath));
    }

    private static void addSingleSlot(YamlConfiguration old, List<YamlConfiguration> defaults,
                                      Map<String, Object> candidates, String oldPath, String targetPath) {
        if (custom(old, defaults, oldPath)) candidates.put(targetPath, List.of(old.get(oldPath)));
    }

    private static boolean changedTree(YamlConfiguration old, List<YamlConfiguration> defaults,
                                       String root, int originalVersion) {
        ConfigurationSection section = old.getConfigurationSection(root);
        if (section == null) return custom(old, defaults, root)
            || originalVersion >= 37 && defaults.get(0).isSet(root);
        for (String key : section.getKeys(true)) {
            String path = root + "." + key;
            if (!old.isConfigurationSection(path) && custom(old, defaults, path)) return true;
        }
        if (originalVersion >= 37) {
            ConfigurationSection baseline = defaults.get(0).getConfigurationSection(root);
            if (baseline != null) for (String key : baseline.getKeys(true)) {
                String path = root + "." + key;
                if (!baseline.isConfigurationSection(key) && !old.isSet(path)) return true;
            }
        }
        return false;
    }

    private static boolean custom(YamlConfiguration old, List<YamlConfiguration> defaults, String path) {
        if (!old.isSet(path)) return false;
        Object value = old.get(path);
        for (YamlConfiguration known : defaults) {
            if (known.isSet(path) && Objects.equals(value, known.get(path))) return false;
        }
        return true;
    }

    private static void applyCandidates(Consumer<String> warn, String screen, YamlConfiguration newest,
                                        Map<String, Object> candidates) throws IOException {
        Map<String, Object> bundled = new LinkedHashMap<>();
        for (Map.Entry<String, Object> candidate : candidates.entrySet()) {
            String path = candidate.getKey();
            bundled.put(path, newest.get(path));
            newest.set(path, candidate.getValue());
        }
        Map<String, Object> pending = new LinkedHashMap<>(candidates);
        Set<String> repairedIndices = new java.util.HashSet<>();
        while (true) {
            try {
                validate(screen, newest);
                return;
            } catch (IOException invalid) {
                if (pending.isEmpty()) throw invalid;
                String key = invalid.getCause() instanceof GuiDefinitionException definitionError
                    ? definitionError.key() : "";
                String rejected = pending.keySet().stream()
                    .filter(path -> key.equals(path) || key.startsWith(path + ".")
                        || key.startsWith(path + "[")
                        || path.startsWith(key + "."))
                    .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
                if (rejected == null) {
                    rejected = pending.keySet().stream()
                        .filter(path -> invalid.getMessage().contains(path))
                        .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
                }
                if (rejected == null) {
                    throw new IOException("Cannot isolate invalid migrated GUI field for " + screen
                        + " (schema key " + key + "); leaving this screen unchanged", invalid);
                }
                String suffix = key.startsWith(rejected) ? key.substring(rejected.length()) : "";
                if (suffix.matches("\\[\\d+]") && newest.get(rejected) instanceof List<?> values
                    && bundled.get(rejected) instanceof List<?> defaults) {
                    int index = Integer.parseInt(suffix.substring(1, suffix.length() - 1));
                    if (index >= values.size() || index >= defaults.size() || !repairedIndices.add(key)) {
                        throw new IOException("Cannot safely repair invalid migrated GUI list entry "
                            + screen + ":" + key + "; leaving this screen unchanged", invalid);
                    }
                    List<Object> repaired = new ArrayList<>(values);
                    Object prior = repaired.set(index, defaults.get(index));
                    newest.set(rejected, repaired);
                    warn.accept("Invalid legacy GUI value " + screen + ":" + key
                        + "=" + Objects.toString(prior) + " (" + invalid.getMessage()
                        + "); using newest bundled default " + Objects.toString(defaults.get(index)));
                    continue;
                }
                Object value = pending.remove(rejected);
                Object fallback = bundled.get(rejected);
                newest.set(rejected, fallback);
                warn.accept("Invalid legacy GUI value " + screen + ":" + rejected
                    + "=" + Objects.toString(value) + " (" + invalid.getMessage()
                    + "); using newest bundled default " + Objects.toString(fallback));
            }
        }
    }
}
