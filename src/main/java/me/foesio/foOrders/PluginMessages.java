package me.foesio.foOrders;

import me.foesio.core.message.FoStyle;
import me.foesio.core.plugin.FoPluginTitle;
import me.foesio.core.text.FoText;
import me.foesio.foOrders.util.TextFormat;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PluginMessages {
    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();

    public PluginMessages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), FILE_NAME);
        boolean createdFile = false;
        if (!messagesFile.exists()) {
            plugin.saveResource(FILE_NAME, false);
            createdFile = true;
        }

        YamlConfiguration loadedMessages = YamlConfiguration.loadConfiguration(messagesFile);
        try (InputStream defaultsStream = plugin.getResource(FILE_NAME)) {
            if (defaultsStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)
                );
                loadedMessages.setDefaults(defaults);
                boolean changed = backfillMissingDefaults(loadedMessages, defaults);
                changed |= migratePluginTitleDefaults(loadedMessages);
                if (changed && !createdFile) {
                    loadedMessages.save(messagesFile);
                }
            }
        } catch (IOException exception) {
            warn("Could not update messages.yml defaults: " + exception.getMessage());
        }

        messages = loadedMessages;
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
            sender.sendMessage(message);
        }
    }

    public String renderTemplate(String template, Map<String, String> placeholders) {
        return colorize(applyTemplatePlaceholders(template, placeholders == null ? Map.of() : placeholders));
    }

    public void sendTemplate(CommandSender sender, String template, Map<String, String> placeholders) {
        String message = renderTemplate(template, placeholders);
        if (!message.isBlank()) {
            sender.sendMessage(message);
        }
    }

    public void sendClickableTemplate(CommandSender sender, String template, String url, Map<String, String> placeholders) {
        String message = renderTemplate(template, placeholders);
        if (message.isBlank()) {
            return;
        }
        if (!(sender instanceof Player player) || url == null || url.isBlank()) {
            sender.sendMessage(message);
            return;
        }

        BaseComponent[] components = TextComponent.fromLegacyText(message);
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        for (BaseComponent component : components) {
            component.setClickEvent(clickEvent);
        }
        player.spigot().sendMessage(components);
    }

    public static Map<String, String> placeholders(Object... values) {
        return TextFormat.placeholders(values);
    }

    private String applyMessagePlaceholders(String message, Map<String, String> placeholders) {
        String formatted = message == null ? "" : message;
        Map<String, String> safePlaceholders = placeholders == null ? Map.of() : placeholders;
        for (Map.Entry<String, String> entry : safePlaceholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            formatted = formatted
                .replace("{" + entry.getKey() + "}", value)
                .replace("%" + entry.getKey() + "%", value);
        }
        for (Map.Entry<String, String> entry : tokenValues().entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return formatted;
    }

    private String applyTemplatePlaceholders(String template, Map<String, String> placeholders) {
        String formatted = template == null ? "" : template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            formatted = formatted.replace("{" + entry.getKey() + "}", value);
        }
        for (Map.Entry<String, String> entry : tokenValues().entrySet()) {
            formatted = formatted.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return formatted;
    }

    private Map<String, String> tokenValues() {
        Map<String, String> tokens = new LinkedHashMap<>(FoStyle.runtimeTokens(FoPluginTitle.resolve(plugin)));
        tokens.put("prefix", messages.getString("prefix", tokens.getOrDefault("prefix", "")));

        ConfigurationSection section = messages.getConfigurationSection("tokens");
        if (section == null) {
            return tokens;
        }
        for (String key : section.getKeys(false)) {
            tokens.put(key, section.getString(key, tokens.getOrDefault(key, "")));
        }
        return tokens;
    }

    private boolean backfillMissingDefaults(YamlConfiguration loadedMessages, YamlConfiguration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || loadedMessages.isSet(key)) {
                continue;
            }
            loadedMessages.set(key, defaults.get(key));
            changed = true;
        }
        return changed;
    }

    private boolean migratePluginTitleDefaults(YamlConfiguration loadedMessages) {
        boolean changed = false;
        changed |= replaceStringIfDefault(
            loadedMessages,
            "prefix",
            "#03fc88FoOrders &8» #a7b8b0",
            "#03fc88{plugin} &8» #a7b8b0"
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
