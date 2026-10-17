package me.foesio.foOrders;

import me.foesio.core.dialog.DialogIcons;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/** Keeps Paper components behind an API check while retaining Spigot chat/action-bar fallbacks. */
public final class PlayerTextDelivery {
    private static final boolean PAPER_MESSAGE_API = hasPlayerMethod("sendMessage");
    private static final boolean PAPER_ACTION_BAR_API = hasPlayerMethod("sendActionBar");

    private PlayerTextDelivery() {
    }

    public static void sendMessage(Player player, String message) {
        if (PAPER_MESSAGE_API) {
            try {
                PaperDelivery.sendMessage(player, message);
                return;
            } catch (LinkageError ignored) {
                // A server can expose a partial Adventure API; retain the legacy path.
            }
        }
        player.sendMessage(PublicTextFallback.render(message));
    }

    static void sendActionBar(Player player, String message) {
        if (PAPER_ACTION_BAR_API) {
            try {
                PaperDelivery.sendActionBar(player, message);
                return;
            } catch (LinkageError ignored) {
                // Spigot's Bungee-compatible action bar is the fallback.
            }
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(PublicTextFallback.render(message)));
    }

    static void sendClickableUrl(Player player, String message, String url) {
        if (PAPER_MESSAGE_API) {
            try {
                PaperDelivery.sendClickableUrl(player, message, url);
                return;
            } catch (LinkageError ignored) {
                // Keep update notices clickable on Spigot and partial Paper servers.
            }
        }
        BaseComponent[] components = TextComponent.fromLegacyText(PublicTextFallback.render(message));
        ClickEvent click = new ClickEvent(ClickEvent.Action.OPEN_URL, url);
        for (BaseComponent component : components) {
            component.setClickEvent(click);
        }
        player.spigot().sendMessage(components);
    }

    public static boolean usesPaperMessages() {
        return PAPER_MESSAGE_API;
    }

    private static boolean hasPlayerMethod(String methodName) {
        try {
            Class<?> component = Class.forName("net.kyori.adventure.text.Component", false,
                PlayerTextDelivery.class.getClassLoader());
            Player.class.getMethod(methodName, component);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    /** Loaded only when the server's Player API has the matching Adventure method. */
    private static final class PaperDelivery {
        private PaperDelivery() {
        }

        private static void sendMessage(Player player, String message) {
            player.sendMessage(DialogIcons.render(player, message));
        }

        private static void sendActionBar(Player player, String message) {
            player.sendActionBar(DialogIcons.render(player, message));
        }

        private static void sendClickableUrl(Player player, String message, String url) {
            player.sendMessage(DialogIcons.render(player, message)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url)));
        }
    }
}
