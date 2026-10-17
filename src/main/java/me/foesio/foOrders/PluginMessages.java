package me.foesio.foOrders;

import me.foesio.foOrders.util.OwnerUpgradeBackups;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.migration.FoMigrationStore;
import me.foesio.core.text.FoText;
import me.foesio.foOrders.util.TextFormat;
import me.foesio.foOrders.util.OwnerYaml;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class PluginMessages {
    private static final String FILE_NAME = "messages.yml";
    // Released pre-sprite defaults. The v1 migration must never decorate owner text.
    private static final Map<String, String> PRE_SPRITE_DEFAULTS = Map.ofEntries(
        Map.entry("prefix", "#03fc88FoOrders &8» #a7b8b0"),
        Map.entry("admin.usage", "{prefix}Usage: #03fc88/{label} <reload|version|editor|history <player>>"),
        Map.entry("admin.economy-missing", "{prefix}#ff5d73Vault economy provider not found."),
        Map.entry("admin.reload-success", "{prefix}#3ecf8eFoOrders config, messages, dialogs, and GUIs reloaded."),
        Map.entry("admin.reload-failed", "{prefix}#ff5d73Reload failed at #03fc88{step}#ff5d73: #a7b8b0{error}"),
        Map.entry("custom-items.hold-template", "{prefix}#ff5d73Hold the template item in your main hand first."),
        Map.entry("custom-items.saved", "{prefix}#3ecf8eSaved custom item #03fc88{id}#3ecf8e."),
        Map.entry("custom-items.removed", "{prefix}#3ecf8eRemoved custom item #03fc88{id}#3ecf8e."),
        Map.entry("orders.delivered", "{prefix}Items #03fc88Delivered#a7b8b0! You earned #3ecf8e${payout}"),
        Map.entry("orders.cancelled", "{prefix}#ff5d73Order was cancelled."),
        Map.entry("orders.refunded", "{prefix}Refunded: #3ecf8e${amount}"),
        Map.entry("orders.created-broadcast", "{prefix}#03fc88{player} #a7b8b0created an order for #03fc88{amount}x {item} #a7b8b0at #03fc88${price} #a7b8b0per item (#03fc88${total} #a7b8b0total).")
    );

    private final JavaPlugin plugin;
    private boolean bootstrapFreshInstall;
    private YamlConfiguration messages = new YamlConfiguration();
    private volatile String themeColor = "";
    private volatile int revision = 0;
    private volatile PublicNumberFormat numberFormat;
    private boolean startupLoading = true;

    public PluginMessages(JavaPlugin plugin, boolean freshInstall) {
        this.plugin = plugin;
        this.bootstrapFreshInstall = freshInstall;
    }

    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), FILE_NAME);
        boolean createdFile = false;
        int schemaVersion = plugin.getConfig().getInt("config-version", 0);
        if (!messagesFile.exists() && bootstrapFreshInstall && schemaVersion == 0) {
            plugin.saveResource(FILE_NAME, false);
            createdFile = true;
        }
        if (!messagesFile.isFile()) {
            throw new IllegalStateException("Missing owner messages.yml; bundled English defaults were not restored.");
        }
        bootstrapFreshInstall = false;

        YamlConfiguration loadedMessages = OwnerYaml.load(messagesFile);
        boolean hadThemeToken = loadedMessages.isSet("tokens.theme");
        try {
            // A missing value in an installed owner file is intentional or
            // ambiguous. Never backfill bundled English on every reload.
            boolean changed = false;
            if (schemaVersion < 13 && messagesFile.isFile()) {
                changed |= migratePluginTitleDefaults(loadedMessages);
                if (!hadThemeToken) {
                    String bundledTheme = bundledThemeColor();
                    if (bundledTheme != null) {
                        loadedMessages.set("tokens.theme", bundledTheme);
                        changed = true;
                    }
                }
            }
            if (changed && !createdFile) {
                File backup = OwnerUpgradeBackups.forFile(messagesFile, ".pre-v13-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(messagesFile) && !backup.exists()) {
                    Files.copy(messagesFile.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                loadedMessages.save(messagesFile);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not migrate messages.yml; owner file and migration version were preserved.", exception);
        }

        if (schemaVersion >= 20) {
            if (requiresStartupNumberFallback(loadedMessages, startupLoading)) {
                repairInvalidNumberFields(loadedMessages, bundledNumberDefaults(),
                    warning -> warn("Startup number format fallback: " + warning
                        + "; installed messages.yml was not changed."));
            }
        }
        if (schemaVersion >= 38) {
            YamlConfiguration bundled = bundledMessages();
            if (startupLoading) {
                PublicMessageFields.repair(loadedMessages, bundled,
                    warning -> warn("Startup message fallback: " + warning
                        + "; installed messages.yml was not changed."));
            } else {
                PublicMessageFields.validateReload(loadedMessages, bundled);
            }
        }
        PublicNumberFormat nextNumberFormat = PublicNumberFormat.load(loadedMessages);
        String nextThemeColor = colorize(resolveTokens(resolveThemeColor(loadedMessages),
            ownerTokens(loadedMessages, plugin.getName())));
        messages = loadedMessages;
        numberFormat = nextNumberFormat;
        themeColor = nextThemeColor;
        revision++;
    }

    public String themeColor() {
        return themeColor;
    }

    public void finishStartup() {
        startupLoading = false;
    }

    public Map<String, String> ownerTokens() {
        return ownerTokens(messages, plugin.getName());
    }

    public String resolveOwnerTokens(String raw) {
        return resolveTokens(raw == null ? "" : raw, ownerTokens());
    }

    public int revision() {
        return revision;
    }

    public String formatPlainNumber(double value) {
        PublicNumberFormat current = numberFormat;
        return current == null ? "" : current.plain(value);
    }

    public String formatCompactNumber(double value) {
        PublicNumberFormat current = numberFormat;
        return current == null ? "" : current.compact(value);
    }

    public String frame(String path, int index) {
        String selected = selectFrame(messages.getStringList(path), index);
        if (selected.isBlank()) {
            return "";
        }
        return colorize(applyMessagePlaceholders(selected, Map.of()));
    }

    static String selectFrame(List<String> frames, int index) {
        return frames == null || frames.isEmpty() ? "" : frames.get(Math.floorMod(index, frames.size()));
    }

    public boolean migrateNumberFormats(FoMigrationStore store) {
        store.runToVersion(20, () -> {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            if (!file.isFile()) {
                warn("Cannot add numeric presentation to a missing owner messages.yml.");
                return false;
            }
            YamlConfiguration active = OwnerYaml.load(file);
            YamlConfiguration defaults;
            try (InputStream stream = plugin.getResource(FILE_NAME)) {
                if (stream == null) {
                    return false;
                }
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                warn("Could not read bundled numeric presentation: " + exception.getMessage());
                return false;
            }
            boolean changed = false;
            for (String path : List.of("numbers.locale", "numbers.plain-pattern", "numbers.compact-pattern",
                "numbers.compact-suffixes")) {
                if (!active.isSet(path) && defaults.isSet(path)) {
                    active.set(path, defaults.get(path));
                    changed = true;
                }
            }
            changed |= repairInvalidNumberFields(active, defaults,
                warning -> warn("Invalid old number format: " + warning));
            changed |= addDeliveringFrames(active);
            if (!changed) {
                return true;
            }
            try {
                byte[] original = Files.readAllBytes(file.toPath());
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v20-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                try {
                    active.save(file);
                } catch (IOException exception) {
                    Files.write(file.toPath(), original);
                    throw exception;
                }
                return true;
            } catch (IOException exception) {
                warn("Could not migrate numeric presentation: " + exception.getMessage());
                return false;
            }
        });
        return store.version() >= 20;
    }

    static boolean requiresStartupNumberFallback(YamlConfiguration candidate, boolean startupLoading) {
        String invalidPath = PublicNumberFormat.firstInvalidPath(candidate);
        if (invalidPath == null) return false;
        if (!startupLoading) {
            throw new IllegalStateException("Invalid owner messages.yml " + invalidPath
                + "; previous working messages and number format remain active.");
        }
        return true;
    }

    private YamlConfiguration bundledNumberDefaults() {
        YamlConfiguration defaults = bundledMessages();
        if (PublicNumberFormat.firstInvalidPath(defaults) != null) {
            throw new IllegalStateException("Bundled messages.yml number format is invalid");
        }
        return defaults;
    }

    private YamlConfiguration bundledMessages() {
        try (InputStream stream = plugin.getResource(FILE_NAME)) {
            if (stream == null) throw new IllegalStateException("Bundled messages.yml is unavailable");
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            return defaults;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read bundled messages.yml", exception);
        }
    }

    static boolean repairInvalidNumberFields(YamlConfiguration candidate, YamlConfiguration bundled,
                                             Consumer<String> warn) {
        boolean changed = false;
        int maximumRepairs = 4 + bundled.getStringList("numbers.compact-suffixes").size();
        for (int attempt = 0; attempt < maximumRepairs; attempt++) {
            String path = PublicNumberFormat.firstInvalidPath(candidate);
            if (path == null) return changed;
            if (PublicNumberFormat.firstInvalidPath(bundled) != null) {
                throw new IllegalStateException("No valid bundled number format default for " + path);
            }
            Object invalid;
            Object replacement;
            if (path.equals("numbers")) {
                invalid = candidate.get(path);
                ConfigurationSection section = bundled.getConfigurationSection(path);
                candidate.set(path, null);
                candidate.createSection(path);
                for (String key : section.getKeys(false)) {
                    candidate.set(path + "." + key, section.get(key));
                }
                replacement = section.getValues(false);
            } else if (path.startsWith("numbers.compact-suffixes[")) {
                int index = Integer.parseInt(path.substring("numbers.compact-suffixes[".length(), path.length() - 1));
                List<?> ownerSuffixes = candidate.getList("numbers.compact-suffixes");
                List<?> defaultSuffixes = bundled.getList("numbers.compact-suffixes");
                if (ownerSuffixes == null || defaultSuffixes == null || index >= defaultSuffixes.size()) {
                    throw new IllegalStateException("No matching bundled suffix default for " + path);
                }
                invalid = ownerSuffixes.get(index);
                replacement = defaultSuffixes.get(index);
                List<Object> repaired = new ArrayList<>(ownerSuffixes);
                repaired.set(index, replacement);
                candidate.set("numbers.compact-suffixes", repaired);
            } else {
                if (!bundled.isSet(path)) {
                    throw new IllegalStateException("No matching bundled number format default for " + path);
                }
                invalid = candidate.get(path);
                replacement = bundled.get(path);
                candidate.set(path, replacement);
            }
            warn.accept(path + "=" + invalid + " -> newest bundled default " + replacement);
            changed = true;
        }
        if (PublicNumberFormat.firstInvalidPath(candidate) != null) {
            throw new IllegalStateException("Could not repair installed number format field by field");
        }
        return changed;
    }

    /** Repair explicit bad values in later upgrades without restoring owner-removed fields. */
    static boolean repairPresentInvalidNumberFields(YamlConfiguration candidate, YamlConfiguration bundled,
                                                    Consumer<String> warn) {
        if (!candidate.isSet("numbers")) {
            warn.accept("Owner messages.yml numbers section is absent; preserving its removal. "
                + "Startup uses bundled values in memory until the owner restores valid formats.");
            return false;
        }
        if (!candidate.isConfigurationSection("numbers")) {
            return repairInvalidNumberFields(candidate, bundled, warn);
        }
        List<String> fields = List.of("numbers.locale", "numbers.plain-pattern",
            "numbers.compact-pattern", "numbers.compact-suffixes");
        YamlConfiguration staged = new YamlConfiguration();
        try {
            staged.loadFromString(candidate.saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not stage owner number formats", exception);
        }
        for (String path : fields) {
            if (!candidate.isSet(path)) {
                warn.accept("Owner messages.yml " + path + " is absent; preserving its removal. "
                    + "Startup uses the bundled value in memory.");
                staged.set(path, bundled.get(path));
            }
        }
        repairInvalidNumberFields(staged, bundled, warn);
        boolean changed = false;
        for (String path : fields) {
            if (!candidate.isSet(path)) continue;
            Object original = candidate.get(path);
            Object repaired = staged.get(path);
            if (!java.util.Objects.equals(original, repaired)) {
                candidate.set(path, repaired);
                changed = true;
            }
        }
        return changed;
    }

    static boolean addDeliveringFrames(YamlConfiguration active) {
        if (!active.isConfigurationSection("actionbar") || active.isSet("actionbar.delivering-frames")) {
            return false;
        }
        if (active.isString("actionbar.delivering")) {
            String base = active.getString("actionbar.delivering", "");
            active.set("actionbar.delivering-frames", base.isEmpty() ? List.of()
                : List.of(base + ".", base + "..", base + "..."));
        } else {
            active.set("actionbar.delivering-frames", List.of());
        }
        return true;
    }

    public boolean migrateStyleTokens(FoMigrationStore store) {
        store.runToVersion(22, () -> {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            if (!file.isFile()) {
                warn("Cannot add owner style tokens to a missing messages.yml.");
                return false;
            }
            YamlConfiguration active = OwnerYaml.load(file);
            YamlConfiguration defaults;
            try (InputStream stream = plugin.getResource(FILE_NAME)) {
                if (stream == null) {
                    return false;
                }
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                warn("Could not read bundled owner style tokens: " + exception.getMessage());
                return false;
            }
            boolean changed = false;
            for (String key : List.of("muted", "white", "yellow", "good", "bad")) {
                String path = "tokens." + key;
                if (!active.isSet(path)) {
                    active.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (!changed) {
                return true;
            }
            try {
                byte[] original = Files.readAllBytes(file.toPath());
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v22-backup");
                if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                    Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                }
                try {
                    active.save(file);
                    return true;
                } catch (IOException exception) {
                    Files.write(file.toPath(), original);
                    throw exception;
                }
            } catch (IOException exception) {
                warn("Could not migrate owner style tokens: " + exception.getMessage());
                return false;
            }
        });
        return store.version() >= 22;
    }

    public boolean migrateUnavailableInputFeedback(FoMigrationStore store) {
        store.runToVersion(25, () -> {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            if (!file.isFile()) {
                warn("Cannot add input failure feedback to a missing owner messages.yml.");
                return false;
            }
            YamlConfiguration active = OwnerYaml.load(file);
            YamlConfiguration defaults;
            try (InputStream stream = plugin.getResource(FILE_NAME)) {
                if (stream == null) {
                    return false;
                }
                defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                warn("Could not read bundled input failure feedback: " + exception.getMessage());
                return false;
            }
            if (!addUnavailableInputFeedback(active, defaults)) {
                return true;
            }
            byte[] original = null;
            try {
                original = Files.readAllBytes(file.toPath());
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v25-backup");
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
                        warn("Could not restore messages.yml after failed input feedback migration.");
                    }
                }
                warn("Could not migrate input failure feedback: " + exception.getMessage());
                return false;
            }
        });
        return store.version() >= 25;
    }

    static boolean addUnavailableInputFeedback(YamlConfiguration active, YamlConfiguration defaults) {
        String path = "chat-input.unavailable";
        if (!active.isConfigurationSection("chat-input") || active.isSet(path) || !defaults.isString(path)) {
            return false;
        }
        active.set(path, defaults.getString(path));
        return true;
    }

    public boolean migrateRefreshCooldownFeedback(FoMigrationStore store) {
        store.runToVersion(26, () -> {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            if (!file.isFile()) {
                warn("Cannot add refresh cooldown feedback to a missing owner messages.yml.");
                return false;
            }
            YamlConfiguration active = OwnerYaml.load(file);
            YamlConfiguration defaults;
            try (InputStream stream = plugin.getResource(FILE_NAME)) {
                if (stream == null) {
                    return false;
                }
                defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                warn("Could not read bundled refresh cooldown feedback: " + exception.getMessage());
                return false;
            }
            if (!addRefreshCooldownFeedback(active, defaults)) {
                return true;
            }
            byte[] original = null;
            try {
                original = Files.readAllBytes(file.toPath());
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v26-backup");
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
                        warn("Could not restore messages.yml after failed refresh feedback migration.");
                    }
                }
                warn("Could not migrate refresh cooldown feedback: " + exception.getMessage());
                return false;
            }
        });
        return store.version() >= 26;
    }

    static boolean addRefreshCooldownFeedback(YamlConfiguration active, YamlConfiguration defaults) {
        String path = "actionbar.refresh-cooldown";
        if (!active.isConfigurationSection("actionbar") || active.isSet(path) || !defaults.isString(path)) {
            return false;
        }
        active.set(path, defaults.getString(path));
        return true;
    }

    public boolean migrateActionFailureFeedback(FoMigrationStore store) {
        store.runToVersion(27, () -> {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            if (!file.isFile()) {
                warn("Cannot add action failure feedback to a missing owner messages.yml.");
                return false;
            }
            YamlConfiguration active = OwnerYaml.load(file);
            YamlConfiguration defaults;
            try (InputStream stream = plugin.getResource(FILE_NAME)) {
                if (stream == null) {
                    return false;
                }
                defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                warn("Could not read bundled action failure feedback: " + exception.getMessage());
                return false;
            }
            if (!addActionFailureFeedback(active, defaults)) {
                return true;
            }
            byte[] original = null;
            try {
                original = Files.readAllBytes(file.toPath());
                File backup = OwnerUpgradeBackups.forFile(file, ".pre-v27-backup");
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
                        warn("Could not restore messages.yml after failed action feedback migration.");
                    }
                }
                warn("Could not migrate action failure feedback: " + exception.getMessage());
                return false;
            }
        });
        return store.version() >= 27;
    }

    static boolean addActionFailureFeedback(YamlConfiguration active, YamlConfiguration defaults) {
        if (!active.isConfigurationSection("actionbar")) {
            return false;
        }
        boolean changed = false;
        for (String path : List.of("actionbar.action-unavailable", "actionbar.order-busy",
            "actionbar.inventory-full", "actionbar.no-items-accepted")) {
            if (!active.isSet(path) && defaults.isString(path)) {
                active.set(path, defaults.getString(path));
                changed = true;
            }
        }
        return changed;
    }

    public boolean migrateInvalidPriceFeedback(FoMigrationStore store) {
        store.runToVersion(33, () -> {
            File file = new File(plugin.getDataFolder(), FILE_NAME);
            YamlConfiguration defaults;
            try (InputStream stream = plugin.getResource(FILE_NAME)) {
                if (stream == null) {
                    return false;
                }
                defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            } catch (IOException exception) {
                warn("Could not read bundled price input feedback: " + exception.getMessage());
                return false;
            }
            return migrateInvalidPriceFeedbackFile(file, defaults, this::warn);
        });
        return store.version() >= 33;
    }

    static boolean migrateInvalidPriceFeedbackFile(File file, YamlConfiguration defaults,
                                                    Consumer<String> warn) {
        if (!file.isFile()) {
            warn.accept("Cannot add price input feedback to a missing owner messages.yml.");
            return false;
        }
        YamlConfiguration active = OwnerYaml.load(file);
        if (!addInvalidPriceFeedback(active, defaults)) {
            return true;
        }
        byte[] original = null;
        try {
            original = Files.readAllBytes(file.toPath());
            File backup = OwnerUpgradeBackups.forFile(file, ".pre-v33-backup");
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
                    warn.accept("Could not restore messages.yml after failed price feedback migration.");
                }
            }
            warn.accept("Could not migrate price input feedback: " + exception.getMessage());
            return false;
        }
    }

    static boolean addInvalidPriceFeedback(YamlConfiguration active, YamlConfiguration defaults) {
        String path = "actionbar.invalid-price";
        if (!active.isConfigurationSection("actionbar") || active.isSet(path) || !defaults.isString(path)) {
            return false;
        }
        active.set(path, defaults.getString(path));
        return true;
    }

    public String get(String path) {
        return get(path, Map.of());
    }

    public String get(String path, Map<String, String> placeholders) {
        String rawMessage = messages.getString(path, "");
        if (rawMessage == null || rawMessage.isBlank()) {
            return "";
        }
        return colorize(applyMessagePlaceholders(rawMessage, placeholders));
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, String> placeholders) {
        String message = get(path, placeholders);
        if (!message.isBlank()) {
            sendRendered(sender, message);
        }
    }

    /** Keep a permission-scoped public broadcast viewer-aware, including sprite tokens. */
    public void broadcast(String path, Map<String, String> placeholders, String permission) {
        String message = get(path, placeholders);
        if (message.isBlank()) {
            return;
        }
        for (Permissible recipient : plugin.getServer().getPluginManager().getPermissionSubscriptions(permission)) {
            if (recipient.hasPermission(permission) && recipient instanceof CommandSender sender) {
                sendRendered(sender, message);
            }
        }
    }

    private void sendRendered(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            PlayerTextDelivery.sendMessage(player, message);
        } else {
            sender.sendMessage(DialogIcons.fallbackText(message));
        }
    }

    public String renderTemplate(String template, Map<String, String> placeholders) {
        return colorize(applyTemplatePlaceholders(template, placeholders == null ? Map.of() : placeholders));
    }

    public void sendTemplate(CommandSender sender, String template, Map<String, String> placeholders) {
        String message = renderTemplate(template, placeholders);
        if (!message.isBlank()) {
            if (sender instanceof Player player) {
                PlayerTextDelivery.sendMessage(player, message);
            } else {
                sender.sendMessage(DialogIcons.fallbackText(message));
            }
        }
    }

    public void sendClickableTemplate(CommandSender sender, String template, String url, Map<String, String> placeholders) {
        String message = renderTemplate(template, placeholders);
        if (message.isBlank()) {
            return;
        }
        if (!(sender instanceof Player player) || url == null || url.isBlank()) {
            sender.sendMessage(DialogIcons.fallbackText(message));
            return;
        }

        PlayerTextDelivery.sendClickableUrl(player, message, url);
    }

    public boolean migrateSprites(FoMigrationStore store, int version) {
        File messagesFile = new File(plugin.getDataFolder(), FILE_NAME);
        return store.runToVersion(version, () -> {
            boolean changed = false;
            changed |= addSpriteIfMissing("prefix", ":chest_minecart:", null);
            changed |= addSpriteIfMissing("admin.usage", ":paper:");
            changed |= addSpriteIfMissing("admin.economy-missing", ":redstone:");
            changed |= addSpriteIfMissing("admin.reload-success", ":emerald:");
            changed |= addSpriteIfMissing("admin.reload-failed", ":redstone:");
            changed |= addSpriteIfMissing("custom-items.hold-template", ":paper:");
            changed |= addSpriteIfMissing("custom-items.saved", ":emerald:");
            changed |= addSpriteIfMissing("custom-items.removed", ":lava_bucket:");
            changed |= addSpriteIfMissing("orders.delivered", ":emerald:");
            changed |= addSpriteIfMissing("orders.cancelled", ":redstone:");
            changed |= addSpriteIfMissing("orders.refunded", ":gold_ingot:");
            changed |= addSpriteIfMissing("orders.created-broadcast", ":paper:");
            if (!changed) {
                return true;
            }
            return saveMessageMigration(messagesFile, ".pre-v1-backup", "sprite");
        });
    }

    private boolean addSpriteIfMissing(String path, String token) {
        return addSpriteIfMissing(path, token, "{prefix}");
    }

    private boolean addSpriteIfMissing(String path, String token, String anchor) {
        return addSpriteIfOldDefault(messages, path, token, anchor);
    }

    static boolean addSpriteIfOldDefault(YamlConfiguration active, String path, String token, String anchor) {
        String current = active.getString(path, "");
        boolean releasedDefault = current.equals(PRE_SPRITE_DEFAULTS.get(path))
            || path.equals("prefix") && current.equals("{theme}FoOrders &8» #a7b8b0");
        if (!releasedDefault || DialogIcons.containsToken(current)) {
            return false;
        }
        return FoMessageService.addMissingToken(active, path, token, anchor);
    }

    public boolean migratePrefix(FoMigrationStore store, int version) {
        File messagesFile = new File(plugin.getDataFolder(), FILE_NAME);
        return store.runToVersion(version, () -> {
            String oldPrefix = ":chest: {theme}FoOrders &8» #a7b8b0";
            if (!oldPrefix.equals(messages.getString("prefix"))) {
                return true;
            }
            messages.set("prefix", ":chest_minecart: {theme}FoOrders &8» #a7b8b0");
            return saveMessageMigration(messagesFile, ".pre-v2-backup", "prefix sprite");
        });
    }

    private boolean saveMessageMigration(File file, String backupSuffix, String description) {
        byte[] original = null;
        try {
            original = Files.readAllBytes(file.toPath());
            File backup = OwnerUpgradeBackups.forFile(file, backupSuffix);
            if (OwnerUpgradeBackups.wasOriginallyPresent(file) && !backup.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
            messages.save(file);
            return true;
        } catch (IOException exception) {
            if (original != null) {
                try {
                    Files.write(file.toPath(), original);
                } catch (IOException rollback) {
                    warn("Could not restore messages.yml after failed " + description + " migration; backup retained.");
                }
            }
            warn("Could not save " + description + " migration: " + exception.getMessage());
            return false;
        }
    }

    public static Map<String, String> placeholders(Object... values) {
        return TextFormat.placeholders(values);
    }

    private String applyMessagePlaceholders(String message, Map<String, String> placeholders) {
        return renderOwnerTemplate(message, placeholders, tokenValues());
    }

    private String applyTemplatePlaceholders(String template, Map<String, String> placeholders) {
        return renderOwnerTemplate(template, placeholders, tokenValues());
    }

    static String renderOwnerTemplate(String template, Map<String, String> placeholders,
                                      Map<String, String> ownerTokens) {
        Map<String, String> dynamic = placeholders == null ? Map.of() : placeholders;
        Map<String, String> tokens = ownerTokens == null ? Map.of() : ownerTokens;
        Map<String, String> values = new LinkedHashMap<>();
        tokens.forEach((key, value) -> values.put(key,
            TextFormat.applyPlaceholders(resolveTokens(value == null ? "" : value, tokens), dynamic)));
        values.putAll(dynamic);
        // Expand the owner's template once. Player/data values must never be rescanned as tokens.
        return TextFormat.applyPlaceholders(template, values);
    }

    private Map<String, String> tokenValues() {
        return ownerTokens(messages, plugin.getName());
    }

    static Map<String, String> ownerTokens(YamlConfiguration active, String pluginName) {
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("plugin", pluginName == null ? "" : pluginName);
        tokens.put("prefix", active.getString("prefix", ""));
        for (String key : List.of("theme", "muted", "white", "yellow", "good", "bad")) {
            tokens.put(key, active.getString("tokens." + key, ""));
        }
        ConfigurationSection section = active.getConfigurationSection("tokens");
        if (section == null) {
            return tokens;
        }
        for (String key : section.getKeys(false)) {
            tokens.put(key, section.isString(key)
                ? section.getString(key, tokens.getOrDefault(key, "")) : "");
        }
        return tokens;
    }

    private boolean migratePluginTitleDefaults(YamlConfiguration loadedMessages) {
        boolean changed = false;
        changed |= replaceStringIfDefault(
            loadedMessages,
            "prefix",
            "#03fc88FoOrders &8» #a7b8b0",
            "{theme}FoOrders &8» #a7b8b0"
        );
        changed |= replaceStringIfDefault(
            loadedMessages,
            "prefix",
            "#03fc88{plugin} &8» #a7b8b0",
            "{theme}FoOrders &8» #a7b8b0"
        );
        changed |= replaceStringIfDefault(
            loadedMessages,
            "prefix",
            "{theme}{plugin} &8» #a7b8b0",
            "{theme}FoOrders &8» #a7b8b0"
        );
        changed |= replaceStringIfDefault(
            loadedMessages,
            "admin.reload-success",
            "{prefix}#3ecf8eFoOrders config, messages, dialogs, and GUIs reloaded.",
            "{prefix}#3ecf8e{plugin} config, messages, dialogs, and GUIs reloaded."
        );
        return changed;
    }

    private boolean replaceStringIfDefault(YamlConfiguration loadedMessages, String path, String oldValue, String newValue) {
        String current = loadedMessages.getString(path);
        if (!oldValue.equals(current)) {
            return false;
        }
        loadedMessages.set(path, newValue);
        return true;
    }

    private String bundledThemeColor() {
        try (InputStream stream = plugin.getResource(FILE_NAME)) {
            if (stream == null) {
                warn("Bundled messages.yml is unavailable while introducing the theme token.");
                return null;
            }
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            return bundled.getString("tokens.theme");
        } catch (IOException exception) {
            warn("Could not read bundled theme token: " + exception.getMessage());
            return null;
        }
    }

    static String resolveTokens(String formatted, Map<String, String> tokens) {
        for (int pass = 0; pass <= tokens.size(); pass++) {
            String before = formatted;
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                formatted = formatted.replace("{" + entry.getKey() + "}",
                    entry.getValue() == null ? "" : entry.getValue());
            }
            if (formatted.equals(before)) {
                break;
            }
        }
        for (String key : tokens.keySet()) {
            formatted = formatted.replace("{" + key + "}", "");
        }
        return formatted;
    }

    private String resolveThemeColor(YamlConfiguration loadedMessages) {
        return loadedMessages.getString("tokens.theme", "");
    }

    private String colorize(String message) {
        return FoText.color(message);
    }

    private void warn(String message) {
        plugin.getLogger().warning(message);
        if (plugin instanceof FoOrders foOrders && foOrders.fileLogger() != null) {
            foOrders.fileLogger().warn(message);
        }
    }
}
