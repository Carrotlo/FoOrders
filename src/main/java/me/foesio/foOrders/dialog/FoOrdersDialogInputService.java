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
        migrateLegacyDialogFiles();
        dialogs.reload();
    }

    public void reloadSupport() {
        closeDialogInputs();
        support = NativeDialogSupport.detect(
            plugin,
            NativeDialogSettings.fromConfig(plugin.getConfig().getConfigurationSection("native-dialogs"))
        );
        inputs = DialogInputs.create(plugin, support, scheduler);
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
