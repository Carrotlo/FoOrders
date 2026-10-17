package me.foesio.foOrders;

import me.foesio.foOrders.util.TextFormat;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdersMenuEnchantPreviewTest {
    @Test
    void translatedReorderedSummaryHonorsOwnerCountAndRemovedExtraText() {
        YamlConfiguration active = new YamlConfiguration();
        active.set("labels.main.item-display.enchant-summary", "{enchants} voor {item}");
        active.set("labels.main.item-display.enchant-level", "{level}:{enchant}");
        active.set("labels.main.item-display.enchant-separator", " / ");
        active.set("labels.main.item-display.extra-enchants", "plus {count}");
        active.set("items.new-order.enchants.summary-line", "Betovering {enchant}: {level}");
        BiFunction<String, Map<String, String>, String> text = (path, values) ->
            TextFormat.applyPlaceholders(active.getString(path, ""), values);
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("z", 1);
        enchants.put("b", 1);
        enchants.put("a", 2);
        java.util.function.Function<String, String> name = key -> switch (key) {
            case "a" -> "Alpha";
            case "b" -> "Beta";
            default -> "Zeta";
        };
        java.util.function.IntFunction<String> roman = level -> level == 2 ? "II" : "I";

        assertEquals("II:Alpha / I:Beta / plus 1 voor Zwaard",
            OrdersMenuItemSupport.renderEnchantmentName("Zwaard", enchants, 2, true, text, name, roman));
        assertEquals("II:Alpha / I:Beta / I:Zeta voor Zwaard",
            OrdersMenuItemSupport.renderEnchantmentName("Zwaard", enchants, 3, true, text, name, roman));
        assertEquals("Zwaard",
            OrdersMenuItemSupport.renderEnchantmentName("Zwaard", enchants, 0, true, text, name, roman));

        active.set("labels.main.item-display.extra-enchants", null);
        assertEquals("II:Alpha / I:Beta voor Zwaard",
            OrdersMenuItemSupport.renderEnchantmentName("Zwaard", enchants, 2, true, text, name, roman));
        assertEquals("Zwaard",
            OrdersMenuItemSupport.renderEnchantmentName("Zwaard", enchants, 2, false, text, name, roman));
        active.set("labels.main.item-display.enchant-summary", "");
        assertEquals("",
            OrdersMenuItemSupport.renderEnchantmentName("Zwaard", enchants, 2, true, text, name, roman));

        assertEquals(java.util.List.of("Betovering Alpha: II", "Betovering Beta: I", "Betovering Zeta: I"),
            OrdersMenuItemSupport.renderEnchantmentSummaryLore(enchants, text, name, roman));
        active.set("items.new-order.enchants.summary-line", null);
        assertEquals(java.util.List.of(),
            OrdersMenuItemSupport.renderEnchantmentSummaryLore(enchants, text, name, roman));
    }
}
