package me.foesio.core.discord;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Supplier;

public class DiscordWebhookService {
    private DiscordWebhookService() {}

    public static DiscordWebhookService create(JavaPlugin plugin, Supplier<DiscordWebhookSettings> settingsSupplier) {
        return new DiscordWebhookService();
    }

    public void sendEmbed(String eventKey, DiscordWebhookEmbed embed) {}
}
