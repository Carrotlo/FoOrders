package me.foesio.core.update;

import org.bukkit.command.CommandSender;

import java.util.Map;

public class UpdateNoticeService {
    public UpdateNoticeService start() {
        return this;
    }

    public void sendVersion(CommandSender sender) {}

    public interface UpdateMessenger {
        void send(CommandSender sender, String template, Map<String, String> placeholders);
        void sendClickable(CommandSender sender, String template, String url, Map<String, String> placeholders);
    }
}
