package me.foesio.foOrders.dialog;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantDialogConfigTest {
    @TempDir
    Path folder;

    @Test
    void nativeEnchantTextDoesNotReprocessInsertedNamesOrLevels() {
        String rendered = FoOrdersPaperEnchantDialogService.renderConfigured(
            "{theme}Set {enchant} to {roman}",
            Map.of("enchant", "{roman} %theme%", "roman", "III"),
            raw -> raw.replace("{theme}", "GREEN ").replace("%theme%", "GREEN ")
        );
        assertEquals("GREEN Set {roman} %theme% to III", rendered);
    }

    @Test
    void removedOrMalformedRequiredPresentationDisablesNativeInsteadOfGeneratingIt() throws Exception {
        YamlConfiguration active = bundled();
        assertTrue(EnchantDialogConfig.load(active).enabled());

        active.set("spacer-button", null);
        assertFalse(EnchantDialogConfig.load(active).enabled());

        active = bundled();
        active.set("level-button.width", "wrong");
        assertFalse(EnchantDialogConfig.load(active).enabled());

        active = bundled();
        active.set("enchant-button.tooltip", "");
        assertTrue(EnchantDialogConfig.load(active).enabled());

        active.set("action-order", null);
        assertFalse(EnchantDialogConfig.load(active).enabled());
    }

    @Test
    void ownerActionOrderIsFinalAndOldSchemaGetsOnlyTheNewField() throws Exception {
        YamlConfiguration defaults = bundled();
        YamlConfiguration active = bundled();
        active.set("action-order", List.of("continue", "back"));
        assertTrue(EnchantDialogConfig.load(active).enabled());
        assertEquals(List.of("continue", "back"), EnchantDialogConfig.load(active).actionOrder());
        assertFalse(FoOrdersDialogInputService.addNewActionOrder(active, defaults));

        active.set("action-order", null);
        assertTrue(FoOrdersDialogInputService.addNewActionOrder(active, defaults));
        assertEquals(List.of("back", "continue"), EnchantDialogConfig.load(active).actionOrder());
        assertFalse(FoOrdersDialogInputService.addNewActionOrder(active, defaults));
    }

    @Test
    void ownerCanRemoveOneNavigationActionWithoutRevivingItsPresentation() throws Exception {
        YamlConfiguration active = bundled();
        active.set("action-order", List.of("continue"));
        active.set("back-button", null);
        EnchantDialogConfig continueOnly = EnchantDialogConfig.load(active);
        assertTrue(continueOnly.enabled());
        assertEquals(List.of("continue"), continueOnly.actionOrder());

        active = bundled();
        active.set("action-order", List.of("back"));
        active.set("continue-button", null);
        EnchantDialogConfig backOnly = EnchantDialogConfig.load(active);
        assertTrue(backOnly.enabled());
        assertEquals(List.of("back"), backOnly.actionOrder());

        active.set("action-order", List.of());
        assertFalse(EnchantDialogConfig.load(active).enabled());
        active.set("action-order", List.of("back", "back"));
        assertFalse(EnchantDialogConfig.load(active).enabled());
        active.set("action-order", List.of("back", "unknown"));
        assertFalse(EnchantDialogConfig.load(active).enabled());
    }

    @Test
    void released46EnchantDialogFileIsNotRecreatedAfterOwnerDeletion() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
            "/fixtures/4.6/dialogs/enchant-select.yml")) {
            assertTrue(stream != null);
            YamlConfiguration released = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertTrue(released.isSet("continue-button.label"));
        }
        Path ownerFile = folder.resolve("enchant-select.yml");
        List<String> warnings = new ArrayList<>();
        assertTrue(FoOrdersDialogInputService.preserveMissingEnchantDialog(ownerFile.toFile(), warnings::add));
        assertFalse(Files.exists(ownerFile));
        assertFalse(EnchantDialogConfig.load(new YamlConfiguration()).enabled());
        assertTrue(warnings.stream().anyMatch(line -> line.contains("inventory fallback")));
        Files.writeString(ownerFile, "title: Owner dialog\n");
        assertFalse(FoOrdersDialogInputService.preserveMissingEnchantDialog(ownerFile.toFile(), warnings::add));
    }

    private YamlConfiguration bundled() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/dialogs/enchant-select.yml")) {
            return YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
