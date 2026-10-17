package me.foesio.foOrders.dialog;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PublicDialogUpgradeTest {
    @Test
    void textDialogRepairsInvalidWidthAndOrderButKeepsTranslationAndRemoval() {
        YamlConfiguration defaults = resource("amount");
        YamlConfiguration owner = resource("amount");
        owner.set("title", "Menge ✓");
        owner.set("columns", 99);
        owner.set("button-order", List.of("confirm", "confirm"));
        owner.set("confirm-button.tooltip", null);
        List<String> warnings = new ArrayList<>();

        assertTrue(PublicDialogUpgrade.repairInvalidFields("amount", owner, defaults, warnings::add));

        assertEquals("Menge ✓", owner.getString("title"));
        assertEquals(defaults.getInt("columns"), owner.getInt("columns"));
        assertEquals(defaults.getStringList("button-order"), owner.getStringList("button-order"));
        assertFalse(owner.isSet("confirm-button.tooltip"));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("amount:columns")));
    }

    @Test
    void itemPickerRepairsMalformedSectionAndInvalidOrderIndependently() {
        YamlConfiguration defaults = resource("item-select");
        YamlConfiguration owner = resource("item-select");
        owner.set("title", "Kies voorwerp ✓");
        owner.set("preview", "bad section");
        owner.set("button-order", List.of("search", "next"));

        assertTrue(PublicDialogUpgrade.repairInvalidFields("item-select", owner, defaults, ignored -> {}));

        assertEquals("Kies voorwerp ✓", owner.getString("title"));
        assertEquals(defaults.getInt("preview.width"), owner.getInt("preview.width"));
        assertEquals(defaults.getStringList("button-order"), owner.getStringList("button-order"));
    }

    @Test
    void enchantPickerRepairsInvalidNumericFieldButKeepsOwnerOrder() {
        YamlConfiguration defaults = resource("enchant-select");
        YamlConfiguration owner = resource("enchant-select");
        owner.set("item.width", 999);
        owner.set("action-order", List.of("continue", "back"));

        assertTrue(PublicDialogUpgrade.repairInvalidFields("enchant-select", owner, defaults, ignored -> {}));

        assertEquals(defaults.getInt("item.width"), owner.getInt("item.width"));
        assertEquals(List.of("continue", "back"), owner.getStringList("action-order"));
    }

    @Test
    void legacyButtonUsesBundledDefaultsForInvalidFieldsAndKeepsValidTooltip() {
        YamlConfiguration defaults = resource("amount");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("confirm-button", List.of("wrong type"));
        owner.set("confirm-tooltip", "Bewaar hoeveelheid ✓");
        owner.set("button-width", "wide");
        List<String> warnings = new ArrayList<>();

        assertTrue(FoOrdersDialogInputService.migrateLegacyButton(owner,
            "confirm-button", "confirm-tooltip", defaults, warnings::add));

        assertEquals(defaults.getString("confirm-button.label"), owner.getString("confirm-button.label"));
        assertEquals("Bewaar hoeveelheid ✓", owner.getString("confirm-button.tooltip"));
        assertEquals(defaults.getInt("confirm-button.width"), owner.getInt("confirm-button.width"));
        assertEquals(defaults.getString("confirm-button.icon"), owner.getString("confirm-button.icon"));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("confirm-button")));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("button-width")));
    }

    @Test
    void legacyButtonKeepsRemovedOptionalTooltipAbsent() {
        YamlConfiguration defaults = resource("money");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("cancel-button", "Terug ✓");

        assertTrue(FoOrdersDialogInputService.migrateLegacyButton(owner,
            "cancel-button", "cancel-tooltip", defaults, ignored -> { }));

        assertEquals("Terug ✓", owner.getString("cancel-button.label"));
        assertFalse(owner.isSet("cancel-button.tooltip"));
        assertEquals(defaults.getString("cancel-button.icon"), owner.getString("cancel-button.icon"));
    }

    @Test
    void mixedFallbackLinesRepairOnlyInvalidEntry() {
        YamlConfiguration defaults = resource("search");
        YamlConfiguration owner = resource("search");
        owner.set("fallback.lines", List.of("Eigen eerste regel ✓", 42, "Eigen laatste regel ✓"));
        List<String> warnings = new ArrayList<>();

        assertTrue(PublicDialogUpgrade.repairInvalidFields("search", owner, defaults, warnings::add));

        assertEquals("Eigen eerste regel ✓", owner.getList("fallback.lines").get(0));
        assertEquals(defaults.getStringList("fallback.lines").get(1), owner.getList("fallback.lines").get(1));
        assertEquals("Eigen laatste regel ✓", owner.getList("fallback.lines").get(2));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("fallback.lines[1]")));
    }

    @Test
    void mixedBodyListWithoutMatchingBundledListStaysUntouchedForRetry() {
        YamlConfiguration defaults = resource("amount");
        YamlConfiguration owner = resource("amount");
        owner.set("body", List.of("Eigen tekst ✓", 42));

        assertThrows(IllegalStateException.class,
            () -> PublicDialogUpgrade.repairInvalidFields("amount", owner, defaults, ignored -> { }));

        assertEquals(List.of("Eigen tekst ✓", 42), owner.getList("body"));
    }

    private static YamlConfiguration resource(String id) {
        try (InputStream stream = PublicDialogUpgradeTest.class.getResourceAsStream("/dialogs/" + id + ".yml")) {
            if (stream == null) throw new IllegalStateException("Missing bundled dialog " + id);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
