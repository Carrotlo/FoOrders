package me.foesio.foOrders;

import me.foesio.core.dialog.DialogIcons;
import me.foesio.foOrders.util.TextFormat;

/** Legacy output for clients/servers without component sprites, preserving owner-authored spacing. */
public final class PublicTextFallback {
    private PublicTextFallback() {
    }

    public static String render(String text) {
        String source = text == null ? "" : text;
        return TextFormat.colorize(DialogIcons.containsToken(source)
            ? DialogIcons.fallbackText(source) : source);
    }
}
