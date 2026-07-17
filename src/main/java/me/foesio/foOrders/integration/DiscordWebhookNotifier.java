package me.foesio.foOrders.integration;

import me.foesio.core.discord.DiscordWebhookEmbed;
import me.foesio.core.discord.DiscordWebhookService;
import me.foesio.core.discord.DiscordWebhookSettings;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DiscordWebhookNotifier {
    private static final String DEFAULT_TITLE = "FoOrders";
    private static final Duration WEBHOOK_TIMEOUT = Duration.ofSeconds(6);

    private final DiscordWebhookService webhookService;

    private volatile DiscordWebhookSettings webhookSettings = disabledSettings(DEFAULT_TITLE);

    public DiscordWebhookNotifier(JavaPlugin plugin) {
        this.webhookService = DiscordWebhookService.create(plugin, () -> webhookSettings);
    }

    public void reloadFromConfig(FileConfiguration config) {
        DiscordWebhookSettings baseSettings = DiscordWebhookSettings.fromSection(
            config.getConfigurationSection("discord-webhook"),
            DEFAULT_TITLE
        );
        String webhookUsername = baseSettings.username();
        if (webhookUsername.isBlank()) {
            webhookUsername = DEFAULT_TITLE;
        }

        EnumSet<WebhookCategory> parsedCategories = EnumSet.noneOf(WebhookCategory.class);
        for (WebhookCategory category : WebhookCategory.values()) {
            if (categoryEnabled(config, category)) {
                parsedCategories.add(category);
            }
        }
        webhookSettings = new DiscordWebhookSettings(
            baseSettings.enabled(),
            baseSettings.webhookUrl(),
            webhookUsername,
            baseSettings.avatarUrl(),
            baseSettings.logFailures(),
            WEBHOOK_TIMEOUT,
            eventToggles(parsedCategories)
        );
    }

    public void sendEmbed(
        WebhookCategory category,
        String title,
        String description,
        int color,
        List<EmbedField> fields
    ) {
        DiscordWebhookSettings settings = webhookSettings;
        if (!settings.enabled()
            || settings.normalizedWebhookUrl().isBlank()
            || !settings.eventEnabled(category.configKey())) {
            return;
        }

        DiscordWebhookEmbed.Builder embed = DiscordWebhookEmbed.builder()
            .title(title)
            .description(description)
            .color(color)
            .footer(settings.username())
            .timestampNow();
        if (fields != null) {
            for (EmbedField field : fields) {
                if (field != null) {
                    embed.field(field.name(), field.value(), field.inline());
                }
            }
        }
        webhookService.sendEmbed(category.configKey(), embed.build());
    }

    private boolean categoryEnabled(FileConfiguration config, WebhookCategory category) {
        String legacyPath = "discord-webhook.categories." + category.configKey();
        if (config.isBoolean(legacyPath)) {
            return config.getBoolean(legacyPath);
        }

        String eventPath = "discord-webhook.events." + category.configKey();
        if (config.isBoolean(eventPath)) {
            return config.getBoolean(eventPath);
        }

        String notifyPath = "discord-webhook.notify." + category.configKey();
        if (config.isBoolean(notifyPath)) {
            return config.getBoolean(notifyPath);
        }

        String flatPath = "discord-webhook." + category.configKey();
        if (config.isBoolean(flatPath)) {
            return config.getBoolean(flatPath);
        }

        return category.defaultEnabled();
    }

    private Map<String, Boolean> eventToggles(Set<WebhookCategory> categories) {
        EnumSet<WebhookCategory> enabledSet = categories == null || categories.isEmpty()
            ? EnumSet.noneOf(WebhookCategory.class)
            : EnumSet.copyOf(categories);
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        for (WebhookCategory category : WebhookCategory.values()) {
            toggles.put(category.configKey(), enabledSet.contains(category));
        }
        return Map.copyOf(toggles);
    }

    private DiscordWebhookSettings disabledSettings(String pluginTitle) {
        return new DiscordWebhookSettings(false, "", pluginTitle, "", false, WEBHOOK_TIMEOUT, Map.of());
    }

    public enum WebhookCategory {
        CREATING("creating-orders", true),
        CANCELLING("cancelling-orders", true),
        DELIVERING("delivering-orders", false),
        CLAIMING("claiming-orders", false),
        ADMIN_MODERATION("admin-cancelling-or-deleting", true);

        private final String configKey;
        private final boolean defaultEnabled;

        WebhookCategory(String configKey, boolean defaultEnabled) {
            this.configKey = configKey;
            this.defaultEnabled = defaultEnabled;
        }

        String configKey() {
            return configKey;
        }

        boolean defaultEnabled() {
            return defaultEnabled;
        }
    }

    public record EmbedField(String name, String value, boolean inline) {
    }
}
