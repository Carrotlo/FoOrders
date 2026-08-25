package me.foesio.core.discord;

public class DiscordWebhookEmbed {
    DiscordWebhookEmbed() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        public Builder title(String title) {
            return this;
        }

        public Builder description(String description) {
            return this;
        }

        public Builder color(int color) {
            return this;
        }

        public Builder footer(String footer) {
            return this;
        }

        public Builder timestampNow() {
            return this;
        }

        public Builder field(String name, String value, boolean inline) {
            return this;
        }

        public DiscordWebhookEmbed build() {
            return new DiscordWebhookEmbed();
        }
    }
}
