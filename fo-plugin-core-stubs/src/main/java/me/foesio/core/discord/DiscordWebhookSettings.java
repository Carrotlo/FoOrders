package me.foesio.core.discord;

import org.bukkit.configuration.ConfigurationSection;

import java.time.Duration;
import java.util.Map;

public class DiscordWebhookSettings {
    private final boolean enabled;
    private final String webhookUrl;
    private final String username;
    private final String avatarUrl;
    private final boolean logFailures;
    private final Duration timeout;
    private final Map<String, Boolean> eventToggles;

    public DiscordWebhookSettings(
        boolean enabled,
        String webhookUrl,
        String username,
        String avatarUrl,
        boolean logFailures,
        Duration timeout,
        Map<String, Boolean> eventToggles
    ) {
        this.enabled = enabled;
        this.webhookUrl = webhookUrl;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.logFailures = logFailures;
        this.timeout = timeout;
        this.eventToggles = eventToggles;
    }

    public static DiscordWebhookSettings fromSection(ConfigurationSection section, String defaultUsername) {
        return new DiscordWebhookSettings(false, "", defaultUsername, "", false, Duration.ZERO, Map.of());
    }

    public boolean enabled() {
        return enabled;
    }

    public String webhookUrl() {
        return webhookUrl;
    }

    public String username() {
        return username;
    }

    public String avatarUrl() {
        return avatarUrl;
    }

    public boolean logFailures() {
        return logFailures;
    }

    public String normalizedWebhookUrl() {
        return webhookUrl != null ? webhookUrl : "";
    }

    public boolean eventEnabled(String eventKey) {
        return false;
    }
}
