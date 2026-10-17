package me.foesio.foOrders;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicMessageFieldsTest {
    @Test
    void repairsInvalidOldFieldsWithoutReplacingTranslatedSiblingsOrRemovedKeys() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("prefix", "English prefix");
        bundled.set("actionbar.order-busy", "Busy");
        bundled.set("actionbar.inventory-full", "Full");
        bundled.set("actionbar.delivering-frames", List.of("One", "Two"));
        bundled.set("history.disabled", "Disabled");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("prefix", 3);
        owner.set("actionbar.order-busy", "Bezig ✓");
        owner.set("actionbar.inventory-full", false);
        owner.set("actionbar.delivering-frames", List.of("Een ✓", 7));
        List<String> warnings = new ArrayList<>();

        assertTrue(PublicMessageFields.repair(owner, bundled, warnings::add));
        assertEquals("English prefix", owner.getString("prefix"));
        assertEquals("Bezig ✓", owner.getString("actionbar.order-busy"));
        assertEquals("Full", owner.getString("actionbar.inventory-full"));
        assertEquals(List.of("Een ✓", "Two"), owner.getStringList("actionbar.delivering-frames"));
        assertFalse(owner.isSet("history.disabled"));
        assertEquals(3, warnings.size());
    }

    @Test
    void malformedSectionUsesBundledSectionAndCurrentReloadRejectsIt() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("history.disabled", "Disabled");
        bundled.set("history.access-denied", "Denied");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("history", "wrong type");

        assertThrows(IllegalStateException.class,
            () -> PublicMessageFields.validateReload(owner, bundled));
        assertEquals("wrong type", owner.getString("history"));
        assertTrue(PublicMessageFields.repair(owner, bundled, ignored -> { }));
        assertEquals("Disabled", owner.getString("history.disabled"));
        assertEquals("Denied", owner.getString("history.access-denied"));
    }

    @Test
    void invalidCustomTokenWithoutBundledDefaultBlocksUpgrade() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("tokens.theme", "#03fc88");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("tokens.theme", "#123456");
        owner.set("tokens.custom", List.of("wrong type"));

        List<String> warnings = new ArrayList<>();
        assertFalse(PublicMessageFields.repair(owner, bundled, warnings::add));
        assertEquals(1, warnings.size());
        assertThrows(IllegalStateException.class,
            () -> PublicMessageFields.validateCustomTokensForUpgrade(owner, bundled));
        assertEquals("#123456", owner.getString("tokens.theme"));
        assertEquals("", PluginMessages.ownerTokens(owner, "FoOrders").get("custom"));
    }
}
