package me.foesio.foOrders.dialog;

import me.foesio.core.dialog.ConfiguredTextDialogs;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogInputs;
import me.foesio.core.dialog.NativeDialogSettings;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.scheduler.FoScheduler;
import me.foesio.foOrders.FoOrders;
import me.foesio.foOrders.SignInputType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class FoOrdersDialogInputService {
    private final FoOrders plugin;
    private final FoScheduler scheduler;
    private final FoFileLogger fileLogger;
    private final ConfiguredTextDialogs dialogs;

    private NativeDialogSupport support;
    private DialogInputs inputs;
    private EnchantDialogConfig enchantDialogConfig;
    private FoOrdersEnchantDialogService enchantDialogService;

    public FoOrdersDialogInputService(FoOrders plugin, FoScheduler scheduler, FoFileLogger fileLogger) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.fileLogger = fileLogger;
        this.dialogs = ConfiguredTextDialogs.create(plugin)
            .register("amount", fallbackRequest(SignInputType.AMOUNT, "{current}", "{target}"))
            .register("money", fallbackRequest(SignInputType.PRICE, "{current}", "{target}"))
            .register("search", fallbackRequest(SignInputType.MAIN_SEARCH, "{current}", "{target}"));
        reloadDialogs();
        reloadSupport();
    }

    public void reloadDialogs() {
        ensureEnchantDialogFile();
        migrateLegacyDialogFiles();
        migrateEnchantDialogDefaults();
        dialogs.reload();
        enchantDialogConfig = loadEnchantDialogConfig();
    }

    public void reloadSupport() {
        closeDialogInputs();
        enchantDialogService = null;
        support = NativeDialogSupport.detect(
            plugin,
            NativeDialogSettings.fromConfig(plugin.getConfig().getConfigurationSection("native-dialogs"))
        );
        inputs = DialogInputs.create(plugin, support, scheduler);
        enchantDialogService = createEnchantDialogService();
        logDialogMode();
    }

    public boolean openInput(
        Player player,
        SignInputType inputType,
        String initialValue,
        String targetLabel,
        Consumer<String> onSubmit,
        Runnable onCancel
    ) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        TextDialogRequest request = request(inputType, initialValue, targetLabel);
        DialogInputs currentInputs = inputs;
        if (currentInputs == null) {
            return false;
        }

        currentInputs.textInput().openTextInput(player, request, onSubmit, onCancel);
        return true;
    }

    public void clear(Player player) {
        DialogInputs currentInputs = inputs;
        if (player != null && currentInputs != null) {
            currentInputs.clear(player);
        }
    }

    public void shutdown() {
        closeDialogInputs();
    }

    public boolean nativeEnabled() {
        return support != null && support.configEnabled();
    }

    public boolean warnOnFallback() {
        return support != null && support.warnOnFallback();
    }

    public boolean willUseFallback() {
        return nativeEnabled() && (support == null || !support.canUseNativeDialogs() || inputs == null);
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
            || !support.canUseNativeDialogs()
            || currentService == null) {
            return false;
        }
        return currentService.open(player, request, onAction);
    }

    private TextDialogRequest request(SignInputType inputType, String currentValue, String targetLabel) {
        String current = currentValue == null ? "" : currentValue;
        String target = targetLabel == null || targetLabel.isBlank() ? fallbackTarget(inputType) : targetLabel;
        return dialogs.request(dialogId(inputType), fallbackRequest(inputType, current, target), Map.of(
            "current", current,
            "target", target
        ));
    }

    private TextDialogRequest fallbackRequest(SignInputType inputType, String currentValue, String targetLabel) {
        return switch (inputType) {
            case AMOUNT -> numberRequest(List.of("#a7b8b0Type a positive whole number."), currentValue, "64", 16);
            case PRICE -> TextDialogRequest.number(List.of("#a7b8b0Type price per item."), currentValue, "100");
            case MAIN_SEARCH, ITEM_SEARCH -> new TextDialogRequest(
                "Search",
                List.of("#a7b8b0Type search text. Leave empty to clear."),
                targetLabel,
                currentValue,
                "diamond",
                DialogButton.search("Apply", "Apply search", 100),
                DialogButton.cancel("Cancel", "Keep current search", 100),
                320,
                300,
                128,
                true,
                true,
                false
            );
        };
    }

    private TextDialogRequest numberRequest(List<String> body, String currentValue, String placeholder, int maxLength) {
        TextDialogRequest request = TextDialogRequest.number(body, currentValue, placeholder);
        return new TextDialogRequest(
            request.title(),
            request.body(),
            request.fieldLabel(),
            request.initialValue(),
            request.placeholder(),
            request.submitButton(),
            request.cancelButton(),
            request.bodyWidth(),
            request.inputWidth(),
            maxLength,
            request.labelVisible(),
            request.canCloseWithEscape(),
            request.pause()
        );
    }

    private void migrateLegacyDialogFiles() {
        migrateLegacyDialogFile("amount", fallbackRequest(SignInputType.AMOUNT, "{current}", "{target}"));
        migrateLegacyDialogFile("money", fallbackRequest(SignInputType.PRICE, "{current}", "{target}"));
        migrateLegacyDialogFile("search", fallbackRequest(SignInputType.MAIN_SEARCH, "{current}", "{target}"));
    }

    private void ensureEnchantDialogFile() {
        File file = new File(plugin.getDataFolder(), "dialogs" + File.separator + "enchant-select.yml");
        if (file.exists()) {
            return;
        }
        try {
            plugin.saveResource("dialogs/enchant-select.yml", false);
        } catch (IllegalArgumentException exception) {
            warn("Could not create enchant dialog config: " + exception.getMessage());
        }
    }

    private EnchantDialogConfig loadEnchantDialogConfig() {
        File file = new File(plugin.getDataFolder(), "dialogs" + File.separator + "enchant-select.yml");
        FileConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        return EnchantDialogConfig.load(config);
    }

    private void migrateEnchantDialogDefaults() {
        File file = new File(plugin.getDataFolder(), "dialogs" + File.separator + "enchant-select.yml");
        if (!file.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
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
        changed |= removeIfSet(config, "page-size");
        changed |= removeIfSet(config, "previous-page-button");
        changed |= removeIfSet(config, "next-page-button");
        if (!changed) {
            return;
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            warn("Could not migrate enchant dialog config " + file.getName() + ": " + exception.getMessage());
        }
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

    private void migrateLegacyDialogFile(String id, TextDialogRequest fallback) {
        File file = new File(plugin.getDataFolder(), "dialogs" + File.separator + id + ".yml");
        if (!file.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = migrateLegacyButton(config, "confirm-button", "confirm-tooltip", fallback.submitButton());
        changed |= migrateLegacyButton(config, "cancel-button", "cancel-tooltip", fallback.cancelButton());
        changed |= migrateCoreDialogDefaults(config, id);
        if (!changed) {
            return;
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            warn("Could not migrate legacy dialog config " + file.getName() + ": " + exception.getMessage());
        }
    }

    private boolean migrateLegacyButton(FileConfiguration config, String buttonPath, String tooltipPath, DialogButton fallback) {
        if (config.isConfigurationSection(buttonPath)) {
            return false;
        }
        String label = config.getString(buttonPath);
        String tooltip = config.getString(tooltipPath);
        boolean hasButtonWidth = config.isSet("button-width");
        if (label == null && tooltip == null && !hasButtonWidth) {
            return false;
        }

        int width = config.getInt("button-width", fallback.width());
        config.set(buttonPath, null);
        config.set(buttonPath + ".label", label == null ? fallback.label() : label);
        config.set(buttonPath + ".tooltip", tooltip == null ? fallback.tooltip() : tooltip);
        config.set(buttonPath + ".width", width);
        config.set(buttonPath + ".icon", fallback.icon());
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
        changed |= replaceStringIfDefault(config, "confirm-button.icon", "", DialogButton.SAVE_ICON);
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

    private boolean removeIfSet(FileConfiguration config, String path) {
        if (!config.isSet(path)) {
            return false;
        }
        config.set(path, null);
        return true;
    }

    private String dialogId(SignInputType inputType) {
        return switch (inputType) {
            case AMOUNT -> "amount";
            case PRICE -> "money";
            case MAIN_SEARCH, ITEM_SEARCH -> "search";
        };
    }

    private String fallbackTarget(SignInputType inputType) {
        return switch (inputType) {
            case AMOUNT -> "Amount";
            case PRICE -> "Price";
            case MAIN_SEARCH, ITEM_SEARCH -> "Search";
        };
    }

    private void closeDialogInputs() {
        if (inputs == null) {
            return;
        }
        inputs.close();
        inputs = null;
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
