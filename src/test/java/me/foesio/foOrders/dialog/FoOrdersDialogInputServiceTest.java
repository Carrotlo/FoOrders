package me.foesio.foOrders.dialog;

import me.foesio.foOrders.SignInputType;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoOrdersDialogInputServiceTest {
    @TempDir
    Path folder;

    @Test
    void oldMigrationSkipsRemovedDialogFilesAndMigratesPresentSibling() throws Exception {
        Path dialogs = Files.createDirectory(folder.resolve("dialogs"));
        Path amountFile = dialogs.resolve("amount.yml");
        YamlConfiguration amount = new YamlConfiguration();
        amount.set("target-label", "Amount");
        amount.set("field-label", "#03fc88Amount");
        amount.set("fallback.lines", List.of("{theme}Amount"));
        amount.save(amountFile.toFile());
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("chat-input.targets.amount", "die Menge");
        messages.set("chat-input.targets.price", "der Preis");
        messages.save(folder.resolve("messages.yml").toFile());
        List<String> warnings = new ArrayList<>();

        assertTrue(FoOrdersDialogInputService.migrateLegacyTargetLabels(folder.toFile(),
            Map.of("amount", "Amount", "money", "Price", "search", "Search"), warnings::add));
        assertTrue(FoOrdersDialogInputService.migrateTextDialogColumns(folder.toFile(),
            Map.of("amount", 2, "money", 2, "search", 2), warnings::add));
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("field-label", "#03fc88{target}");
        bundled.set("fallback.lines", List.of("{theme}{target}"));
        assertTrue(FoOrdersDialogInputService.migrateTargetTemplateUsage(folder.toFile(),
            Map.of("amount", bundled, "money", bundled, "search", bundled), warnings::add));

        assertEquals("die Menge", YamlConfiguration.loadConfiguration(amountFile.toFile())
            .getString("target-label"));
        assertEquals(2, YamlConfiguration.loadConfiguration(amountFile.toFile()).getInt("columns"));
        assertFalse(Files.exists(dialogs.resolve("money.yml")));
        assertFalse(Files.exists(dialogs.resolve("search.yml")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("preserving its removal")));
    }

    @Test
    void detectedBedrockPlayerAlwaysSkipsNativeTextInput() {
        assertFalse(FoOrdersDialogInputService.shouldAttemptNative(true, true, true));
        assertTrue(FoOrdersDialogInputService.shouldAttemptNative(false, true, true));
        assertFalse(FoOrdersDialogInputService.shouldAttemptNative(false, false, true));
        assertFalse(FoOrdersDialogInputService.shouldAttemptNative(false, true, false));
    }

    @Test
    void customizedOldMessageTargetSurvivesIntoFinalDialogField() throws Exception {
        Path dialogs = Files.createDirectory(folder.resolve("dialogs"));
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("chat-input.targets.amount", "die Menge");
        messages.save(folder.resolve("messages.yml").toFile());
        Map<String, FileConfiguration> templates = new java.util.LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            String visible = id.equals("amount") ? "Amount" : id.equals("money") ? "Price" : "Search";
            YamlConfiguration active = new YamlConfiguration();
            active.set("target-label", visible);
            active.set("field-label", "#03fc88" + visible);
            active.set("fallback.lines", List.of("{theme}" + visible));
            active.save(dialogs.resolve(id + ".yml").toFile());
            YamlConfiguration bundled = new YamlConfiguration();
            bundled.set("field-label", "#03fc88{target}");
            bundled.set("fallback.lines", List.of("{theme}{target}"));
            templates.put(id, bundled);
        }
        assertTrue(FoOrdersDialogInputService.migrateLegacyTargetLabels(folder.toFile(),
            Map.of("amount", "Amount", "money", "Price", "search", "Search"), warning -> {}));
        assertTrue(FoOrdersDialogInputService.migrateTargetTemplateUsage(folder.toFile(), templates, warning -> {}));
        YamlConfiguration active = YamlConfiguration.loadConfiguration(dialogs.resolve("amount.yml").toFile());
        DialogTextPresentation rendered = DialogTextPresentation.load(active).render(
            Map.of("target", active.getString("target-label")));
        assertEquals("#03fc88die Menge", rendered.fieldLabel());
        assertEquals("{theme}die Menge", rendered.fallbackLines().get(0));
    }

    @Test
    void oldLiteralTargetsBecomeLiveTemplatesWithoutReplacingOwnerText() throws Exception {
        Path dialogs = Files.createDirectory(folder.resolve("dialogs"));
        Map<String, FileConfiguration> defaults = new java.util.LinkedHashMap<>();
        for (String id : List.of("amount", "money", "search")) {
            YamlConfiguration bundled = new YamlConfiguration();
            bundled.set("field-label", "#03fc88{target}");
            bundled.set("fallback.lines", List.of("{theme}{target}", "Bundled second line"));
            defaults.put(id, bundled);
        }
        YamlConfiguration amount = new YamlConfiguration();
        amount.set("target-label", "die Menge");
        amount.set("field-label", "#03fc88Amount");
        amount.set("fallback.lines", List.of("{theme}Amount", "Custom second line"));
        amount.save(dialogs.resolve("amount.yml").toFile());
        byte[] oldAmount = Files.readAllBytes(dialogs.resolve("amount.yml"));
        YamlConfiguration money = new YamlConfiguration();
        money.set("target-label", "der Preis");
        money.set("field-label", "Owner field");
        money.set("fallback.lines", List.of("Owner prompt", "Custom second line"));
        money.save(dialogs.resolve("money.yml").toFile());
        byte[] oldMoney = Files.readAllBytes(dialogs.resolve("money.yml"));
        YamlConfiguration search = new YamlConfiguration();
        search.set("field-label", "#03fc88Search");
        search.set("fallback.lines", List.of("{theme}Search"));
        search.save(dialogs.resolve("search.yml").toFile());
        byte[] oldSearch = Files.readAllBytes(dialogs.resolve("search.yml"));
        List<String> warnings = new ArrayList<>();

        assertTrue(FoOrdersDialogInputService.migrateTargetTemplateUsage(folder.toFile(), defaults, warnings::add));
        assertTrue(warnings.isEmpty());
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(dialogs.resolve("amount.yml").toFile());
        assertEquals("#03fc88{target}", migrated.getString("field-label"));
        assertEquals(List.of("{theme}{target}", "Custom second line"), migrated.getStringList("fallback.lines"));
        assertEquals("die Menge", migrated.getString("target-label"));
        assertArrayEquals(oldAmount, Files.readAllBytes(dialogs.resolve("amount.yml.pre-v37-backup")));
        assertArrayEquals(oldMoney, Files.readAllBytes(dialogs.resolve("money.yml")));
        assertArrayEquals(oldSearch, Files.readAllBytes(dialogs.resolve("search.yml")));
        byte[] migratedAmount = Files.readAllBytes(dialogs.resolve("amount.yml"));
        assertTrue(FoOrdersDialogInputService.migrateTargetTemplateUsage(folder.toFile(), defaults, warnings::add));
        assertArrayEquals(migratedAmount, Files.readAllBytes(dialogs.resolve("amount.yml")));
    }

    @Test
    void newDialogColumnsBackfillOnlyOldMissingFieldsWithBackupAndNoCurrentBackfill() throws Exception {
        Path dialogs = Files.createDirectory(folder.resolve("dialogs"));
        Map<String, Integer> defaults = Map.of("amount", 2, "money", 2, "search", 2);
        YamlConfiguration amount = new YamlConfiguration();
        amount.set("title", "Menge");
        amount.save(dialogs.resolve("amount.yml").toFile());
        YamlConfiguration money = new YamlConfiguration();
        money.set("columns", 1);
        money.save(dialogs.resolve("money.yml").toFile());
        YamlConfiguration search = new YamlConfiguration();
        search.set("title", "Suche");
        search.save(dialogs.resolve("search.yml").toFile());
        byte[] oldAmount = Files.readAllBytes(dialogs.resolve("amount.yml"));
        byte[] oldMoney = Files.readAllBytes(dialogs.resolve("money.yml"));
        byte[] oldSearch = Files.readAllBytes(dialogs.resolve("search.yml"));
        List<String> warnings = new ArrayList<>();

        assertTrue(FoOrdersDialogInputService.migrateTextDialogColumns(folder.toFile(), defaults, warnings::add));
        assertTrue(warnings.isEmpty());
        assertEquals(2, YamlConfiguration.loadConfiguration(dialogs.resolve("amount.yml").toFile()).getInt("columns"));
        assertEquals(1, YamlConfiguration.loadConfiguration(dialogs.resolve("money.yml").toFile()).getInt("columns"));
        assertEquals(2, YamlConfiguration.loadConfiguration(dialogs.resolve("search.yml").toFile()).getInt("columns"));
        assertArrayEquals(oldAmount, Files.readAllBytes(dialogs.resolve("amount.yml.pre-v36-backup")));
        assertArrayEquals(oldSearch, Files.readAllBytes(dialogs.resolve("search.yml.pre-v36-backup")));
        assertArrayEquals(oldMoney, Files.readAllBytes(dialogs.resolve("money.yml")));
        assertFalse(Files.exists(dialogs.resolve("money.yml.pre-v36-backup")));
        byte[] migratedAmount = Files.readAllBytes(dialogs.resolve("amount.yml"));
        assertTrue(FoOrdersDialogInputService.migrateTextDialogColumns(folder.toFile(), defaults, warnings::add));
        assertArrayEquals(migratedAmount, Files.readAllBytes(dialogs.resolve("amount.yml")));

        YamlConfiguration current = YamlConfiguration.loadConfiguration(dialogs.resolve("amount.yml").toFile());
        current.set("columns", null);
        current.save(dialogs.resolve("amount.yml").toFile());
        byte[] removed = Files.readAllBytes(dialogs.resolve("amount.yml"));
        // Version gating prevents this helper being called on current schema;
        // the runtime loader itself must reject an absent native field.
        assertFalse(DialogTextPresentation.load(current).nativeReady());
        assertArrayEquals(removed, Files.readAllBytes(dialogs.resolve("amount.yml")));
    }

    @Test
    void customLegacyTargetsMoveToUnchangedDialogLabelsWithBackups() throws Exception {
        Path dialogs = Files.createDirectory(folder.resolve("dialogs"));
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("chat-input.targets.amount", "die Menge");
        messages.set("chat-input.targets.price", "der Preis");
        messages.set("chat-input.targets.search", "die Suche");
        messages.save(folder.resolve("messages.yml").toFile());
        Map<String, String> defaults = Map.of("amount", "Amount", "money", "Price", "search", "Search");
        Map<String, String> expected = Map.of("amount", "die Menge", "money", "der Preis", "search", "die Suche");
        Map<String, byte[]> original = new java.util.HashMap<>();
        for (String id : defaults.keySet()) {
            Path file = dialogs.resolve(id + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            config.set("target-label", defaults.get(id));
            config.set("title", "Custom title");
            config.save(file.toFile());
            original.put(id, Files.readAllBytes(file));
        }
        List<String> warnings = new ArrayList<>();

        assertTrue(FoOrdersDialogInputService.migrateLegacyTargetLabels(folder.toFile(), defaults, warnings::add));
        assertTrue(warnings.isEmpty());
        for (String id : defaults.keySet()) {
            Path file = dialogs.resolve(id + ".yml");
            assertArrayEquals(original.get(id), Files.readAllBytes(dialogs.resolve(id + ".yml.pre-v34-backup")));
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file.toFile());
            assertEquals(expected.get(id), config.getString("target-label"));
            assertEquals("Custom title", config.getString("title"));
            byte[] migrated = Files.readAllBytes(file);
            assertTrue(FoOrdersDialogInputService.migrateLegacyTargetLabels(folder.toFile(), defaults, warnings::add));
            assertArrayEquals(migrated, Files.readAllBytes(file));
        }
    }

    @Test
    void existingDialogOverridesAndRemovedLabelsWinOverLegacyMessages() throws Exception {
        Path dialogs = Files.createDirectory(folder.resolve("dialogs"));
        YamlConfiguration messages = new YamlConfiguration();
        messages.set("chat-input.targets.amount", "the Amount");
        messages.set("chat-input.targets.price", "legacy price");
        messages.set("chat-input.targets.search", "legacy search");
        messages.save(folder.resolve("messages.yml").toFile());
        YamlConfiguration amount = new YamlConfiguration();
        amount.set("target-label", "Amount");
        amount.save(dialogs.resolve("amount.yml").toFile());
        YamlConfiguration money = new YamlConfiguration();
        money.set("target-label", "owner price");
        money.save(dialogs.resolve("money.yml").toFile());
        YamlConfiguration search = new YamlConfiguration();
        search.set("title", "Search title without target label");
        search.save(dialogs.resolve("search.yml").toFile());
        byte[] originalAmount = Files.readAllBytes(dialogs.resolve("amount.yml"));
        byte[] originalMoney = Files.readAllBytes(dialogs.resolve("money.yml"));
        byte[] originalSearch = Files.readAllBytes(dialogs.resolve("search.yml"));

        assertTrue(FoOrdersDialogInputService.migrateLegacyTargetLabels(folder.toFile(),
            Map.of("amount", "Amount", "money", "Price", "search", "Search"), warning -> {}));
        assertArrayEquals(originalAmount, Files.readAllBytes(dialogs.resolve("amount.yml")));
        assertArrayEquals(originalMoney, Files.readAllBytes(dialogs.resolve("money.yml")));
        assertArrayEquals(originalSearch, Files.readAllBytes(dialogs.resolve("search.yml")));
        assertFalse(Files.exists(dialogs.resolve("search.yml.pre-v34-backup")));
    }

    @Test
    void ownerTargetTextResolvesBeforeCurrentPlayerTextIsInserted() {
        String target = FoOrdersDialogInputService.renderConfiguredValue(
            "{theme}Zoek {current}", Map.of("current", "{theme} %target%"),
            raw -> raw.replace("{theme}", "GREEN "));
        assertEquals("GREEN Zoek {theme} %target%", target);
    }

    @Test
    void onlyTheRenderedSearchClearKeywordClearsTheQuery() {
        assertEquals("", FoOrdersDialogInputService.normalizeSearchClearInput(
            SignInputType.ITEM_SEARCH, "wissen", "WISSEN"));
        assertEquals("", FoOrdersDialogInputService.normalizeSearchClearInput(
            SignInputType.MAIN_SEARCH, "wissen", "wissen"));
        assertEquals("", FoOrdersDialogInputService.normalizeSearchClearInput(
            SignInputType.ITEM_SEARCH, "wissen", " &cWISSEN "));
        assertEquals("wissen", FoOrdersDialogInputService.normalizeSearchClearInput(
            SignInputType.AMOUNT, "wissen", "wissen"));
        assertEquals("wissen", FoOrdersDialogInputService.normalizeSearchClearInput(
            SignInputType.ITEM_SEARCH, "", "wissen"));
    }

    @Test
    void onlyExactOldClearHintMigratesWithBackupAndSecondPassIsNoOp() throws Exception {
        Path search = folder.resolve("search.yml");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("clear-keyword", "wissen");
        owner.set("fallback.lines", List.of("Eigen tekst",
            "#a7b8b0Type search text. Leave empty to clear.",
            "Andere eigen tekst"));
        owner.save(search.toFile());
        byte[] before = Files.readAllBytes(search);
        List<String> warnings = new ArrayList<>();

        assertTrue(FoOrdersDialogInputService.migrateSearchClearHintFile(search.toFile(), warnings::add));
        assertTrue(warnings.isEmpty());
        assertArrayEquals(before, Files.readAllBytes(folder.resolve("search.yml.pre-v31-backup")));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(search.toFile());
        assertEquals("wissen", migrated.getString("clear-keyword"));
        assertEquals(List.of("Eigen tekst",
            "#a7b8b0Type search text. Type #ff5d73{clear_keyword} #a7b8b0to clear.",
            "Andere eigen tekst"), migrated.getStringList("fallback.lines"));

        byte[] migratedBytes = Files.readAllBytes(search);
        assertTrue(FoOrdersDialogInputService.migrateSearchClearHintFile(search.toFile(), warnings::add));
        assertArrayEquals(migratedBytes, Files.readAllBytes(search));

        migrated.set("fallback.lines", List.of("Eigen tekst"));
        assertFalse(FoOrdersDialogInputService.replaceLegacySearchClearHint(migrated));
        assertEquals(List.of("Eigen tekst"), migrated.getStringList("fallback.lines"));
    }

    @Test
    void legacyClearHintMigrationRetainsInvalidSiblingForVersionedRepair() {
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("fallback.lines", List.of(
            "#a7b8b0Type search text. Leave empty to clear.", 42, "Eigen tekst ✓"));

        assertTrue(FoOrdersDialogInputService.replaceLegacySearchClearHint(owner));

        assertEquals(3, owner.getList("fallback.lines").size());
        assertEquals(42, owner.getList("fallback.lines").get(1));
        assertEquals("Eigen tekst ✓", owner.getList("fallback.lines").get(2));
    }

    @Test
    void targetTemplateMigrationRetainsInvalidSiblingForVersionedRepair() throws Exception {
        Path dialogs = Files.createDirectory(folder.resolve("dialogs"));
        YamlConfiguration amount = new YamlConfiguration();
        amount.set("target-label", "Menge ✓");
        amount.set("fallback.lines", List.of("{theme}Amount", 42, "Eigen tekst ✓"));
        amount.save(dialogs.resolve("amount.yml").toFile());
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("field-label", "{theme}{target}");
        bundled.set("fallback.lines", List.of("{theme}{target}", "Bundled second line"));
        Map<String, FileConfiguration> defaults = Map.of(
            "amount", bundled, "money", bundled, "search", bundled);

        assertTrue(FoOrdersDialogInputService.migrateTargetTemplateUsage(
            folder.toFile(), defaults, ignored -> { }));

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(dialogs.resolve("amount.yml").toFile());
        assertEquals("{theme}{target}", migrated.getList("fallback.lines").get(0));
        assertEquals(42, migrated.getList("fallback.lines").get(1));
        assertEquals("Eigen tekst ✓", migrated.getList("fallback.lines").get(2));
    }
}
