package me.foesio.foOrders.dialog;

import me.foesio.foOrders.util.OwnerUpgradeBackups;
import me.foesio.foOrders.util.DeferredPublicMigrations;
import me.foesio.core.editor.ChatPromptManager;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.core.dialog.NativeDialogSettings;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.core.text.PromptNormalizer;
import me.foesio.foOrders.FoOrders;
import me.foesio.foOrders.PlayerTextDelivery;
import me.foesio.foOrders.SignInputType;
import me.foesio.foOrders.util.TextFormat;
import me.foesio.foOrders.util.OwnerYaml;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

public final class FoOrdersDialogInputService {
    private static final String OLD_CHAT_CLEAR_HINT = "#a7b8b0Type search text. Leave empty to clear.";
    private static final String CHAT_CLEAR_HINT = "#a7b8b0Type search text. Type #ff5d73{clear_keyword} #a7b8b0to clear.";
    private static final Map<String, String> OLD_TARGET_DEFAULTS = Map.of(
        "amount", "the Amount", "money", "the Price", "search", "the Search");
    private static final Map<String, String> OLD_VISIBLE_TARGET_LABELS = Map.of(
        "amount", "Amount", "money", "Price", "search", "Search");
    private final FoOrders plugin;
    private final FoScheduler scheduler;
    private final FoFileLogger fileLogger;
    private final FoMigrationStore migrations;
    private final ChatPromptManager chatPrompts;
    private boolean bootstrapFreshInstall;
    private boolean startupLoading = true;
    private Map<String, DialogTextPresentation> presentations = Map.of();

    private NativeDialogSupport support;
    private FoOrdersTextDialogService nativeTextService;
    private EnchantDialogConfig enchantDialogConfig;
    private FoOrdersEnchantDialogService enchantDialogService;

    public FoOrdersDialogInputService(FoOrders plugin, FoScheduler scheduler, FoFileLogger fileLogger,
                                      FoMigrationStore migrations, boolean freshInstall) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.fileLogger = fileLogger;
        this.migrations = Objects.requireNonNull(migrations, "migrations");
        this.chatPrompts = new ChatPromptManager(plugin, scheduler);
        this.bootstrapFreshInstall = freshInstall;
        reloadDialogs();
        reloadSupport();
    }

    public void reloadDialogs() {
        if (bootstrapFreshInstall) {
            for (String id : List.of("amount", "money", "search", "enchant-select", "item-select")) {
                File file = dialogFile(id);
                if (!file.isFile()) {
                    plugin.saveResource("dialogs/" + id + ".yml", false);
                }
            }
        }
        runLegacyStep(17, this::migratePublicDialogFields,
            List.of(), List.of("amount", "money", "search", "enchant-select"));
        if (migrations.version() < 17) {
            throw new IllegalStateException("Could not migrate public dialog presentation.");
        }
        migrations.runToVersion(18, this::migrateItemSelectDialog);
        if (migrations.version() < 18) {
            throw new IllegalStateException("Could not install native item-selection dialog presentation.");
        }
        Map<String, DialogTextPresentation> loaded = new LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            File file = dialogFile(id);
            if (!file.isFile()) {
                if (!startupLoading) {
                    throw new IllegalStateException("Missing owner dialog file dialogs/" + id
                        + ".yml; prior working dialogs remain active.");
                }
                warn("Missing owner dialog file dialogs/" + id
                    + ".yml; text input for this dialog is unavailable until repaired.");
                continue;
            }
            try {
                loaded.put(id, DialogTextPresentation.load(OwnerYaml.load(file)));
            } catch (IllegalStateException malformed) {
                if (!startupLoading) throw malformed;
                warn("Unparseable owner dialog dialogs/" + id + ".yml; text input for this dialog "
                    + "is unavailable until repaired: " + malformed.getMessage());
            }
        }
        EnchantDialogConfig loadedEnchant = loadEnchantDialogConfig();
        presentations = Map.copyOf(loaded);
        enchantDialogConfig = loadedEnchant;
        bootstrapFreshInstall = false;
    }

    public void finishStartup() {
        startupLoading = false;
    }

    private void runLegacyStep(int version, BooleanSupplier migration,
                               List<String> requiredIds, List<String> optionalIds) {
        migrations.runToVersion(version, () -> DeferredPublicMigrations.runOrDefer(
            plugin.getDataFolder(), version, dialogFiles(requiredIds), dialogFiles(optionalIds),
            migration, this::warn));
    }

    private List<File> dialogFiles(List<String> ids) {
        return ids.stream().map(this::dialogFile).toList();
    }

    public boolean retryDeferredSteps() {
        boolean complete = true;
        complete = complete && retryStep(17, this::migratePublicDialogFields,
            List.of(), List.of("amount", "money", "search", "enchant-select"));
        complete = complete && retryStep(24, this::migrateEnchantActionOrderFile,
            List.of(), List.of("enchant-select"));
        complete = complete && retryStep(31, () -> migrateSearchClearHintFile(dialogFile("search"), this::warn),
            List.of(), List.of("search"));
        complete = complete && retryStep(34, () -> {
            Map<String, String> bundledTargets = new LinkedHashMap<>();
            for (String id : OLD_TARGET_DEFAULTS.keySet()) {
                bundledTargets.put(id, bundledDialog(id).getString("target-label", ""));
            }
            return migrateLegacyTargetLabels(plugin.getDataFolder(), bundledTargets, this::warn);
        }, List.of(), List.of("amount", "money", "search"));
        complete = complete && retryStep(36, () -> {
            Map<String, Integer> defaults = new LinkedHashMap<>();
            for (String id : List.of("amount", "money", "search")) {
                defaults.put(id, bundledDialog(id).getInt("columns", 0));
            }
            return migrateTextDialogColumns(plugin.getDataFolder(), defaults, this::warn);
        }, List.of(), List.of("amount", "money", "search"));
        complete = complete && retryStep(37, () -> {
            Map<String, FileConfiguration> defaults = new LinkedHashMap<>();
            for (String id : List.of("amount", "money", "search")) {
                defaults.put(id, bundledDialog(id));
            }
            return migrateTargetTemplateUsage(plugin.getDataFolder(), defaults, this::warn);
        }, List.of(), List.of("amount", "money", "search"));
        if (complete) reloadDialogs();
        return complete;
    }

    private boolean retryStep(int version, BooleanSupplier migration,
                              List<String> requiredIds, List<String> optionalIds) {
        return DeferredPublicMigrations.retry(plugin.getDataFolder(), version,
            dialogFiles(requiredIds), dialogFiles(optionalIds), migration, this::warn);
    }

    public void reloadSupport() {
        closeDialogInputs();
        enchantDialogService = null;
        support = NativeDialogSupport.detect(
            plugin,
            NativeDialogSettings.fromConfig(plugin.getConfig().getConfigurationSection("native-dialogs"))
        );
        nativeTextService = createTextDialogService();
        enchantDialogService = createEnchantDialogService();
        logDialogMode();
    }

    public boolean openInput(
        Player player,
        SignInputType inputType,
        String initialValue,
        String targetLabel,
        boolean forceChatFallback,
        Consumer<String> onSubmit,
        Runnable onCancel
    ) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        DialogTextPresentation template = presentations.get(dialogId(inputType));
        if (template == null) {
            return false;
        }
        String current = initialValue == null ? "" : initialValue;
        Map<String, String> presentationValues = new LinkedHashMap<>();
        plugin.messages().ownerTokens().forEach((key, value) ->
            presentationValues.put(key, plugin.messages().resolveOwnerTokens(value)));
        presentationValues.put("current", current);
        presentationValues.put("placeholder", renderConfiguredValue(
            template.placeholder(), presentationValues, plugin.messages()::resolveOwnerTokens));
        String target = targetLabel == null || targetLabel.isBlank() ? template.targetLabel() : targetLabel;
        presentationValues.put("target", renderConfiguredValue(
            target, presentationValues, plugin.messages()::resolveOwnerTokens));
        DialogTextPresentation presentation = template.render(presentationValues);
        Consumer<String> submit = value -> onSubmit.accept(normalizeSearchClearInput(
            inputType, presentation.clearKeyword(), value));
        FoOrdersTextDialogService nativeService = nativeTextService;
        if (shouldAttemptNative(forceChatFallback, nativeService != null, presentation.nativeReady())
            && nativeService.open(player, presentation, submit, onCancel)) {
            return true;
        }
        if (!presentation.fallbackReady()) {
            warn("Public chat fallback has missing or conflicting required owner fields in dialogs/"
                + dialogId(inputType) + ".yml.");
            return false;
        }
        player.closeInventory();
        // Core's prompt/session handling works on Spigot, but its line sender
        // calls Paper's Player.sendMessage(Component). Send owner lines through
        // the compatible delivery path while retaining the same prompt state.
        boolean paperMessages = PlayerTextDelivery.usesPaperMessages();
        chatPrompts.openRaw(player, paperMessages ? presentation.fallbackLines() : List.of(),
            presentation.fallbackCancelKeyword(), submit, onCancel);
        if (!paperMessages) {
            for (String line : presentation.fallbackLines()) {
                PlayerTextDelivery.sendMessage(player, line);
            }
        }
        return true;
    }

    static String renderConfiguredValue(String template, Map<String, String> values,
                                        Function<String, String> resolveOwnerTokens) {
        return TextFormat.applyPlaceholders(resolveOwnerTokens.apply(template == null ? "" : template), values);
    }

    static String normalizeSearchClearInput(SignInputType inputType, String clearKeyword, String value) {
        boolean search = inputType == SignInputType.MAIN_SEARCH || inputType == SignInputType.ITEM_SEARCH;
        return search && value != null && clearKeyword != null && !clearKeyword.isBlank()
            && PromptNormalizer.normalize(value).equalsIgnoreCase(clearKeyword) ? "" : value;
    }

    public void clear(Player player) {
        if (player != null) {
            chatPrompts.clear(player);
        }
    }

    public void shutdown() {
        closeDialogInputs();
        chatPrompts.close();
    }

    public boolean nativeEnabled() {
        return support != null && support.configEnabled();
    }

    public boolean warnOnFallback() {
        return support != null && support.warnOnFallback();
    }

    public boolean willUseFallback(Player player, SignInputType inputType, boolean forceChatFallback) {
        DialogTextPresentation presentation = presentations.get(dialogId(inputType));
        return nativeEnabled() && (!shouldAttemptNative(forceChatFallback, nativeTextService != null,
            presentation != null && presentation.nativeReady())
            || !support.canUseNativeDialogs(player));
    }

    static boolean shouldAttemptNative(boolean forceChatFallback, boolean serviceAvailable, boolean nativeReady) {
        return !forceChatFallback && serviceAvailable && nativeReady;
    }

    public String targetLabel(SignInputType inputType) {
        DialogTextPresentation presentation = presentations.get(dialogId(inputType));
        return presentation == null ? "" : presentation.targetLabel();
    }

    public boolean openEnchantSelection(
        Player player,
        EnchantDialogRequest request,
        Consumer<EnchantDialogAction> onAction
    ) {
        FoOrdersEnchantDialogService currentService = enchantDialogService;
        if (player == null
            || !player.isOnline()
            || request == null
            || onAction == null
            || support == null
            || !support.canUseNativeDialogs(player)
            || currentService == null) {
            return false;
        }
        return currentService.open(player, request, onAction);
    }

    private EnchantDialogConfig loadEnchantDialogConfig() {
        File file = new File(plugin.getDataFolder(), "dialogs" + File.separator + "enchant-select.yml");
        FileConfiguration config;
        try {
            config = file.isFile() ? OwnerYaml.load(file) : new YamlConfiguration();
        } catch (IllegalStateException malformed) {
            if (!startupLoading) throw malformed;
            warn("Unparseable owner dialog dialogs/enchant-select.yml; native enchant input is unavailable "
                + "until repaired: " + malformed.getMessage());
            config = new YamlConfiguration();
        }
        return EnchantDialogConfig.load(config);
    }

    private File dialogFile(String id) {
        return new File(plugin.getDataFolder(), "dialogs" + File.separator + id + ".yml");
    }

    private boolean migrateItemSelectDialog() {
        File file = dialogFile("item-select");
        if (file.isFile()) {
            return true;
        }
        try {
            plugin.saveResource("dialogs/item-select.yml", false);
            return file.isFile();
        } catch (IllegalArgumentException exception) {
            warn("Could not install new native item-selection dialog file: " + exception.getMessage());
            return false;
        }
    }

    public void migrateEnchantActionOrder() {
        runLegacyStep(24, this::migrateEnchantActionOrderFile,
            List.of(), List.of("enchant-select"));
        if (migrations.version() < 24) {
            throw new IllegalStateException("Could not migrate native enchant dialog action order.");
        }
    }

    public void migrateSearchClearHint() {
        runLegacyStep(31, () -> migrateSearchClearHintFile(dialogFile("search"), this::warn),
            List.of(), List.of("search"));
        if (migrations.version() < 31) {
            throw new IllegalStateException("Could not migrate the public chat Search clear hint.");
        }
    }

    public void migrateLegacyTargetLabels() {
        Map<String, String> bundledTargets = new LinkedHashMap<>();
        for (String id : OLD_TARGET_DEFAULTS.keySet()) {
            bundledTargets.put(id, bundledDialog(id).getString("target-label", ""));
        }
        runLegacyStep(34, () -> migrateLegacyTargetLabels(
            plugin.getDataFolder(), bundledTargets, this::warn),
            List.of(), List.of("amount", "money", "search"));
        if (migrations.version() < 34) {
            throw new IllegalStateException("Could not migrate legacy public dialog target labels.");
        }
    }

    public void migrateTextDialogColumns() {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            defaults.put(id, bundledDialog(id).getInt("columns", 0));
        }
        runLegacyStep(36, () -> migrateTextDialogColumns(plugin.getDataFolder(), defaults, this::warn),
            List.of(), List.of("amount", "money", "search"));
        if (migrations.version() < 36) {
            throw new IllegalStateException("Could not migrate public text dialog columns.");
        }
    }

    static boolean migrateTextDialogColumns(File dataFolder, Map<String, Integer> defaults, Consumer<String> warn) {
        Map<File, YamlConfiguration> changed = new LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            File file = new File(dataFolder, "dialogs/" + id + ".yml");
            if (!file.isFile()) {
                warn.accept("Missing owner dialog " + file + "; preserving its removal during columns migration.");
                continue;
            }
            YamlConfiguration active = OwnerYaml.load(file);
            if (active.isSet("columns")) {
                continue;
            }
            Integer columns = defaults.get(id);
            if (columns == null || columns < 1 || columns > 2) {
                warn.accept("Invalid bundled columns for dialogs/" + id + ".yml.");
                return false;
            }
            active.set("columns", columns);
            changed.put(file, active);
        }
        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        try {
            for (File file : changed.keySet()) {
                originalBytes.put(file, Files.readAllBytes(file.toPath()));
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v36-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            for (Map.Entry<File, YamlConfiguration> entry : changed.entrySet()) {
                entry.getValue().save(entry.getKey());
            }
            return true;
        } catch (IOException exception) {
            for (Map.Entry<File, byte[]> original : originalBytes.entrySet()) {
                try {
                    Files.write(original.getKey().toPath(), original.getValue());
                } catch (IOException rollback) {
                    warn.accept("Could not restore dialog " + original.getKey() + "; pre-v36 backup retained.");
                }
            }
            warn.accept("Could not migrate text dialog columns: " + exception.getMessage());
            return false;
        }
    }

    public void migrateTargetTemplateUsage() {
        Map<String, FileConfiguration> defaults = new LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            defaults.put(id, bundledDialog(id));
        }
        runLegacyStep(37, () -> migrateTargetTemplateUsage(plugin.getDataFolder(), defaults, this::warn),
            List.of(), List.of("amount", "money", "search"));
        if (migrations.version() < 37) {
            throw new IllegalStateException("Could not migrate public target-label presentation.");
        }
    }

    static boolean migrateTargetTemplateUsage(File dataFolder, Map<String, FileConfiguration> defaults,
                                              Consumer<String> warn) {
        Map<File, YamlConfiguration> changed = new LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            File file = new File(dataFolder, "dialogs/" + id + ".yml");
            if (!file.isFile()) {
                warn.accept("Missing owner dialog " + file + "; preserving its removal during target template migration.");
                continue;
            }
            YamlConfiguration active = OwnerYaml.load(file);
            if (!active.isString("target-label")) {
                continue;
            }
            FileConfiguration bundled = defaults.get(id);
            if (bundled == null || !bundled.isString("field-label") || !bundled.isList("fallback.lines")
                || bundled.getStringList("fallback.lines").isEmpty()) {
                warn.accept("Missing bundled target templates for dialogs/" + id + ".yml.");
                return false;
            }
            String oldLabel = OLD_VISIBLE_TARGET_LABELS.get(id);
            boolean updated = false;
            if (("#03fc88" + oldLabel).equals(active.getString("field-label"))) {
                active.set("field-label", bundled.getString("field-label"));
                updated = true;
            }
            if (active.isList("fallback.lines")) {
                List<Object> lines = new ArrayList<>(active.getList("fallback.lines"));
                if (!lines.isEmpty() && ("{theme}" + oldLabel).equals(lines.get(0))) {
                    lines.set(0, bundled.getStringList("fallback.lines").get(0));
                    active.set("fallback.lines", lines);
                    updated = true;
                }
            }
            if (updated) {
                changed.put(file, active);
            }
        }
        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        try {
            for (File file : changed.keySet()) {
                originalBytes.put(file, Files.readAllBytes(file.toPath()));
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v37-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            for (Map.Entry<File, YamlConfiguration> entry : changed.entrySet()) {
                entry.getValue().save(entry.getKey());
            }
            return true;
        } catch (IOException exception) {
            for (Map.Entry<File, byte[]> original : originalBytes.entrySet()) {
                try {
                    Files.write(original.getKey().toPath(), original.getValue());
                } catch (IOException rollback) {
                    warn.accept("Could not restore dialog " + original.getKey() + "; pre-v37 backup retained.");
                }
            }
            warn.accept("Could not migrate target-label presentation: " + exception.getMessage());
            return false;
        }
    }

    static boolean migrateLegacyTargetLabels(File dataFolder, Map<String, String> bundledTargets,
                                             Consumer<String> warn) {
        File messagesFile = new File(dataFolder, "messages.yml");
        if (!messagesFile.isFile()) {
            return true;
        }
        YamlConfiguration messages = OwnerYaml.load(messagesFile);
        Map<File, YamlConfiguration> changedFiles = new LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            String oldKey = "chat-input.targets." + (id.equals("money") ? "price" : id);
            if (!messages.isString(oldKey)) {
                continue;
            }
            String oldValue = messages.getString(oldKey, "");
            if (oldValue.equals(OLD_TARGET_DEFAULTS.get(id))) {
                continue;
            }
            String bundledTarget = bundledTargets.get(id);
            if (bundledTarget == null) {
                warn.accept("Missing bundled target label for dialogs/" + id + ".yml.");
                return false;
            }
            File dialog = new File(dataFolder, "dialogs/" + id + ".yml");
            if (!dialog.isFile()) {
                warn.accept("Missing owner dialog " + dialog
                    + "; preserving its removal instead of copying a legacy target label.");
                continue;
            }
            YamlConfiguration active = OwnerYaml.load(dialog);
            // An omitted target or a newer owner value wins over the old message key.
            if (!active.isString("target-label")
                || !bundledTarget.equals(active.getString("target-label"))) {
                continue;
            }
            active.set("target-label", oldValue);
            changedFiles.put(dialog, active);
        }
        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        try {
            for (File file : changedFiles.keySet()) {
                originalBytes.put(file, Files.readAllBytes(file.toPath()));
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v34-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
            for (Map.Entry<File, YamlConfiguration> entry : changedFiles.entrySet()) {
                entry.getValue().save(entry.getKey());
            }
            return true;
        } catch (IOException exception) {
            for (Map.Entry<File, byte[]> original : originalBytes.entrySet()) {
                try {
                    Files.write(original.getKey().toPath(), original.getValue());
                } catch (IOException rollback) {
                    warn.accept("Could not restore dialog " + original.getKey() + "; pre-v34 backup retained.");
                }
            }
            warn.accept("Could not migrate legacy dialog target labels: " + exception.getMessage());
            return false;
        }
    }

    static boolean migrateSearchClearHintFile(File file, Consumer<String> warn) {
        if (!file.isFile()) {
            // A missing owner dialog file is handled by reloadDialogs; do not recreate it.
            return true;
        }
        YamlConfiguration active = OwnerYaml.load(file);
        if (!replaceLegacySearchClearHint(active)) {
            return true;
        }
        byte[] original = null;
        try {
            original = Files.readAllBytes(file.toPath());
            File backup = OwnerUpgradeBackups.forFile(file, ".pre-v31-backup");
            if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            active.save(file);
            return true;
        } catch (IOException exception) {
            if (original != null) {
                try {
                    Files.write(file.toPath(), original);
                } catch (IOException rollback) {
                    warn.accept("Could not restore Search dialog after failed clear-hint migration; backup retained.");
                }
            }
            warn.accept("Could not migrate Search dialog clear hint: " + exception.getMessage());
            return false;
        }
    }

    static boolean replaceLegacySearchClearHint(FileConfiguration active) {
        if (!active.isList("fallback.lines")) {
            return false;
        }
        List<Object> lines = new ArrayList<>(active.getList("fallback.lines"));
        boolean changed = false;
        for (int index = 0; index < lines.size(); index++) {
            if (OLD_CHAT_CLEAR_HINT.equals(lines.get(index))) {
                lines.set(index, CHAT_CLEAR_HINT);
                changed = true;
            }
        }
        if (changed) {
            active.set("fallback.lines", lines);
        }
        return changed;
    }

    private boolean migrateEnchantActionOrderFile() {
        File file = dialogFile("enchant-select");
        if (!file.isFile()) {
            // Published 4.6 already had this file. An absent owner file stays
            // absent; inventory enchant input remains available.
            return true;
        }
        FileConfiguration active = OwnerYaml.load(file);
        if (!addNewActionOrder(active, bundledDialog("enchant-select"))) {
            return true;
        }
        byte[] original = null;
        try {
            original = Files.readAllBytes(file.toPath());
            File backup = OwnerUpgradeBackups.forFile(file, ".pre-v24-backup");
            if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            active.save(file);
            return true;
        } catch (IOException exception) {
            if (original != null) {
                try {
                    Files.write(file.toPath(), original);
                } catch (IOException rollback) {
                    warn("Could not restore enchant dialog after failed action-order migration; backup retained.");
                }
            }
            warn("Could not migrate enchant dialog action order: " + exception.getMessage());
            return false;
        }
    }

    static boolean addNewActionOrder(FileConfiguration active, FileConfiguration defaults) {
        if (active.isSet("action-order") || !defaults.isList("action-order")) {
            return false;
        }
        active.set("action-order", defaults.getStringList("action-order"));
        return true;
    }

    private boolean migratePublicDialogFields() {
        Map<File, byte[]> originalBytes = new LinkedHashMap<>();
        Map<File, FileConfiguration> changedFiles = new LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            File file = dialogFile(id);
            if (!file.isFile()) {
                warn("Missing owner dialog dialogs/" + id + ".yml; preserving its removal during field migration.");
                continue;
            }
            FileConfiguration active = OwnerYaml.load(file);
            FileConfiguration defaults = bundledDialog(id);
            boolean changed = false;
            changed |= migrateLegacyButton(active, "confirm-button", "confirm-tooltip", defaults, this::warn);
            changed |= migrateLegacyButton(active, "cancel-button", "cancel-tooltip", defaults, this::warn);
            changed |= migrateCoreDialogDefaults(active, id);
            for (String path : List.of("title-icon", "target-label", "button-order",
                "fallback.cancel-keyword", "fallback.lines", "clear-keyword")) {
                if (!active.isSet(path) && defaults.isSet(path)) {
                    active.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                try {
                    originalBytes.put(file, Files.readAllBytes(file.toPath()));
                    File backup = OwnerUpgradeBackups.forFile(file, ".pre-v17-backup");
                    if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException exception) {
                    warn("Could not back up dialog migration target " + file + ": " + exception.getMessage());
                    return false;
                }
                changedFiles.put(file, active);
            }
        }
        File enchant = dialogFile("enchant-select");
        if (!preserveMissingEnchantDialog(enchant, this::warn)) {
            FileConfiguration active = OwnerYaml.load(enchant);
            if (migrateEnchantDialogDefaults(active)) {
                try {
                    originalBytes.put(enchant, Files.readAllBytes(enchant.toPath()));
                    File backup = OwnerUpgradeBackups.forFile(enchant, ".pre-v17-backup");
                    if (OwnerUpgradeBackups.wasOriginallyPresent(enchant) && !backup.exists()) {
                        Files.copy(enchant.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException exception) {
                    warn("Could not back up enchant dialog migration target: " + exception.getMessage());
                    return false;
                }
                changedFiles.put(enchant, active);
            }
        }
        for (Map.Entry<File, FileConfiguration> entry : changedFiles.entrySet()) {
            try {
                entry.getValue().save(entry.getKey());
            } catch (IOException exception) {
                for (Map.Entry<File, byte[]> original : originalBytes.entrySet()) {
                    try {
                        Files.write(original.getKey().toPath(), original.getValue());
                    } catch (IOException ignored) {
                        warn("Could not restore dialog migration target " + original.getKey() + ".");
                    }
                }
                warn("Could not save public dialog migration: " + exception.getMessage());
                return false;
            }
        }
        return true;
    }

    static boolean preserveMissingEnchantDialog(File file, Consumer<String> warn) {
        if (file.isFile()) {
            return false;
        }
        // This file was already bundled in FoOrders 4.6. A missing owner file
        // during an upgrade is not evidence that it should be regenerated.
        warn.accept("Owner dialogs/enchant-select.yml is absent; keeping native enchant input disabled "
            + "and using the inventory fallback.");
        return true;
    }

    private FileConfiguration bundledDialog(String id) {
        try (var stream = plugin.getResource("dialogs/" + id + ".yml")) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled public dialog " + id + ".");
            }
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled public dialog " + id + ".", exception);
        }
    }

    private boolean migrateEnchantDialogDefaults(FileConfiguration config) {
        boolean changed = false;
        changed |= replaceStringIfDefault(config, "continue-button.label", "#a7b8b0Skip Enchantments", "#3ecf8eConfirm");
        changed |= replaceStringIfDefault(config, "continue-button.tooltip", "Return to the order setup", "Confirm enchantments");
        changed |= replaceStringIfDefault(config, "continue-button.icon", "barrier", "emerald");
        changed |= replaceStringIfDefault(config, "title-icon", "warning", "experience_bottle");
        changed |= replaceIntIfDefault(config, "item.width", 64, 16);
        changed |= replaceIntIfDefault(config, "item.height", 64, 16);
        changed |= replaceIntIfDefault(config, "item.width", 32, 16);
        changed |= replaceIntIfDefault(config, "item.height", 32, 16);
        changed |= replaceIntIfDefault(config, "enchant-button.width", 180, 144);
        changed |= replaceIntIfDefault(config, "level-button.width", 32, 36);
        changed |= replaceIntIfDefault(config, "level-button.width", 24, 36);
        return changed;
    }

    private FoOrdersEnchantDialogService createEnchantDialogService() {
        EnchantDialogConfig currentConfig = enchantDialogConfig;
        if (support == null || currentConfig == null || !currentConfig.enabled() || !support.canUseNativeDialogs()) {
            return null;
        }
        try {
            Class<?> serviceClass = Class.forName("me.foesio.foOrders.dialog.FoOrdersPaperEnchantDialogService");
            Constructor<?> constructor = serviceClass.getConstructor(
                FoOrders.class,
                NativeDialogSupport.class,
                FoScheduler.class,
                EnchantDialogConfig.class
            );
            return (FoOrdersEnchantDialogService) constructor.newInstance(plugin, support, scheduler, currentConfig);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            support.disableForSession("FoOrders enchant dialog unavailable: " + exception.getMessage());
            warn("Native enchant dialog unavailable. Using inventory fallback: " + exception.getMessage());
            return null;
        }
    }

    private FoOrdersTextDialogService createTextDialogService() {
        if (support == null || !support.canUseNativeDialogs()) {
            return null;
        }
        try {
            Class<?> serviceClass = Class.forName("me.foesio.foOrders.dialog.FoOrdersPaperTextDialogService");
            Constructor<?> constructor = serviceClass.getConstructor(
                FoOrders.class, NativeDialogSupport.class, FoScheduler.class);
            return (FoOrdersTextDialogService) constructor.newInstance(plugin, support, scheduler);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException exception) {
            support.disableForSession("FoOrders text dialog unavailable: " + exception.getMessage());
            warn("Native text dialog unavailable. Using chat fallback: " + exception.getMessage());
            return null;
        }
    }

    static boolean migrateLegacyButton(FileConfiguration config, String buttonPath, String tooltipPath,
                                       FileConfiguration defaults, Consumer<String> warn) {
        if (config.isConfigurationSection(buttonPath)) {
            return false;
        }
        Object rawLabel = config.get(buttonPath);
        Object rawTooltip = config.get(tooltipPath);
        boolean hasButtonWidth = config.isSet("button-width");
        if (rawLabel == null && rawTooltip == null && !hasButtonWidth) {
            return false;
        }
        if (!defaults.isString(buttonPath + ".icon")
            || !defaults.isString(buttonPath + ".label")
            || !defaults.isString(buttonPath + ".tooltip")
            || defaults.getInt(buttonPath + ".width") <= 0) {
            throw new IllegalStateException("Missing valid bundled legacy dialog button defaults for "
                + buttonPath);
        }
        Object rawWidth = config.get("button-width");
        int width = rawWidth instanceof Integer value && value > 0
            ? value : defaults.getInt(buttonPath + ".width");
        if (hasButtonWidth && !(rawWidth instanceof Integer value && value > 0)) {
            warn.accept("Invalid legacy dialog button-width=" + rawWidth
                + "; using newest bundled " + buttonPath + ".width=" + width);
        }
        config.set(buttonPath, null);
        if (rawLabel instanceof String label) {
            config.set(buttonPath + ".label", label);
        } else if (rawLabel != null) {
            String fallback = defaults.getString(buttonPath + ".label");
            config.set(buttonPath + ".label", fallback);
            warn.accept("Invalid legacy dialog " + buttonPath + "=" + rawLabel
                + "; using newest bundled label " + fallback);
        }
        if (rawTooltip instanceof String tooltip) {
            config.set(buttonPath + ".tooltip", tooltip);
        } else if (rawTooltip != null) {
            String fallback = defaults.getString(buttonPath + ".tooltip");
            config.set(buttonPath + ".tooltip", fallback);
            warn.accept("Invalid legacy dialog " + tooltipPath + "=" + rawTooltip
                + "; using newest bundled tooltip " + fallback);
        }
        if (hasButtonWidth) {
            config.set(buttonPath + ".width", width);
        }
        config.set(buttonPath + ".icon", defaults.getString(buttonPath + ".icon"));
        return true;
    }

    private boolean migrateCoreDialogDefaults(FileConfiguration config, String id) {
        return switch (id) {
            case "amount" -> migrateNumberDialogDefaults(config, "#03fc88Amount");
            case "money" -> migrateNumberDialogDefaults(config, "#03fc88Price");
            default -> false;
        };
    }

    private boolean migrateNumberDialogDefaults(FileConfiguration config, String oldTitle) {
        boolean changed = false;
        changed |= replaceStringIfDefault(config, "title", oldTitle, "#03fc88Number Input");
        changed |= replaceStringIfDefault(config, "confirm-button.label", "#3ecf8eSet", "#3ecf8eSave");
        // Empty was the released owner-editable icon value, so keep it empty.
        changed |= replaceIntIfDefault(config, "confirm-button.width", 100, 120);
        return changed;
    }

    private boolean replaceStringIfDefault(FileConfiguration config, String path, String oldValue, String newValue) {
        String current = config.getString(path);
        if (!Objects.equals(current, oldValue)) {
            return false;
        }
        config.set(path, newValue);
        return true;
    }

    private boolean replaceIntIfDefault(FileConfiguration config, String path, int oldValue, int newValue) {
        if (!config.isSet(path) || config.getInt(path) != oldValue) {
            return false;
        }
        config.set(path, newValue);
        return true;
    }

    private String dialogId(SignInputType inputType) {
        return switch (inputType) {
            case AMOUNT -> "amount";
            case PRICE -> "money";
            case MAIN_SEARCH, ITEM_SEARCH -> "search";
        };
    }

    private void closeDialogInputs() {
        nativeTextService = null;
    }

    private void logDialogMode() {
        if (support == null) {
            return;
        }
        if (!support.configEnabled()) {
            info("Native dialogs disabled in config.");
            return;
        }
        if (!support.serverSupportsNativeDialogs()) {
            warn("Native dialogs enabled but unavailable. Using chat fallback: " + support.unavailableReason());
            return;
        }
        if (support.runtimeDisabled()) {
            warn("Native dialogs disabled for this session. Using chat fallback: " + support.runtimeUnavailableReason());
            return;
        }
        info("Native dialogs enabled.");
    }

    private void info(String message) {
        if (fileLogger != null) {
            fileLogger.info(message);
        }
    }

    private void warn(String message) {
        plugin.getLogger().warning(message);
        if (fileLogger != null) {
            fileLogger.warn(message);
        }
    }

}
