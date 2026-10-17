package me.foesio.foOrders;

import me.foesio.foOrders.config.GuiConfigManager;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

final class OrdersPublicGuiUpgradeTest {
    @TempDir Path install;

    @Test
    void historicalOmissionIsNotMistakenForCurrentSchemaRemovalOnRetry() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/4.1/guis/main.yml");
        YamlConfiguration historical = resource("guis/main.yml");
        YamlConfiguration mistakenCurrent = resource("guis/main.yml");
        List<YamlConfiguration> defaults = List.of(
            resource("migrations/legacy-public-defaults/current/guis/main.yml"),
            resource("migrations/legacy-public-defaults/4.1/guis/main.yml"));

        OrdersPublicGuiUpgrade.convertScreen("main", old, defaults, historical,
            resource("public-text.yml"), 4, ignored -> {});
        OrdersPublicGuiUpgrade.convertScreen("main", old, defaults, mistakenCurrent,
            resource("public-text.yml"), 37, ignored -> {});

        assertTrue(historical.getBoolean("buttons.sort.enabled"));
        assertFalse(mistakenCurrent.getBoolean("buttons.sort.enabled"));
    }

    @Test
    void deliveryConfirmKeepsCustomizedConfirmWithoutCreatingTaxStates() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/4.6/guis/delivery-confirm.yml");
        old.set("items.confirm.name", "#3ecf8e&lCONFIRM DELIVERY MIGRATION TEST");
        old.set("items.confirm.item-flags", "all");
        old.set("items.confirm.enabled", true);
        old.set("items.confirm.amount", 1);
        old.set("size", 27);
        YamlConfiguration newest = resource("guis/delivery-confirm.yml");

        OrdersPublicGuiUpgrade.convertScreen("delivery-confirm", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/delivery-confirm.yml"),
            resource("migrations/legacy-public-defaults/4.6/guis/delivery-confirm.yml")),
            newest, resource("public-text.yml"), 37, ignored -> {});

        assertEquals("#3ecf8e&lCONFIRM DELIVERY MIGRATION TEST",
            newest.getString("buttons.confirm.name"));
        assertFalse(newest.isConfigurationSection("buttons.confirm.states"));
    }

    @Test
    void newOrderStillCopiesCustomizedConfirmIntoItsTaxStates() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/4.6/guis/new-order.yml");
        old.set("items.confirm.name", "&aOWNER CONFIRM");
        YamlConfiguration newest = resource("guis/new-order.yml");

        OrdersPublicGuiUpgrade.convertScreen("new-order", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/new-order.yml"),
            resource("migrations/legacy-public-defaults/4.6/guis/new-order.yml")),
            newest, resource("public-text.yml"), 37, ignored -> {});

        assertEquals("&aOWNER CONFIRM", newest.getString("buttons.confirm.name"));
        assertEquals("&aOWNER CONFIRM", newest.getString("buttons.confirm.states.taxed.name"));
        assertEquals("&aOWNER CONFIRM", newest.getString("buttons.confirm.states.untaxed.name"));
    }

    @Test
    void removedLegacyPaginationLoreSurvivesConversionAndPendingRetry() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/4.6/guis/main.yml");
        old.set("buttons.previous-page.lore", null);
        YamlConfiguration newest = resource("guis/main.yml");

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/main.yml"),
            resource("migrations/legacy-public-defaults/4.6/guis/main.yml")),
            newest, resource("public-text.yml"), 37, ignored -> {});

        assertFalse(newest.isSet("entries.previous-page.item.lore"));
        assertFalse(OrdersPublicGuiUpgrade.repairPartialCoreScreen("main", newest,
            resource("guis/main.yml"), ignored -> {}));
        assertFalse(newest.isSet("entries.previous-page.item.lore"));
    }

    @Test
    void partialCoreScreenRepairsInvalidFieldAndKeepsValidOwnerTitle() throws Exception {
        YamlConfiguration owner = resource("guis/main.yml");
        YamlConfiguration bundled = resource("guis/main.yml");
        owner.set("title", "&8Bestellungen ✓");
        owner.set("buttons.sort.material", "NOT_A_MATERIAL");
        List<String> warnings = new ArrayList<>();

        assertTrue(OrdersPublicGuiUpgrade.repairPartialCoreScreen("main", owner, bundled,
            warnings::add));

        assertEquals("&8Bestellungen ✓", owner.getString("title"));
        assertEquals(bundled.getString("buttons.sort.material"), owner.getString("buttons.sort.material"));
        assertFalse(OrdersPublicGuiUpgrade.repairPartialCoreScreen("main", owner, bundled,
            warnings::add));

        owner.set("decorations.frame.slots", List.of(999, 1));
        assertTrue(OrdersPublicGuiUpgrade.repairPartialCoreScreen("main", owner, bundled,
            warnings::add));
        assertEquals(List.of(0, 1), owner.getIntegerList("decorations.frame.slots"));
    }

    @Test
    void partialCoreScreenRepairsMalformedControlSectionWithoutLosingTitle() throws Exception {
        YamlConfiguration owner = resource("guis/main.yml");
        YamlConfiguration bundled = resource("guis/main.yml");
        owner.set("title", "&8Bestellungen ✓");
        owner.set("buttons.sort", "broken section");

        assertTrue(OrdersPublicGuiUpgrade.repairPartialCoreScreen("main", owner, bundled,
            ignored -> {}));

        assertEquals("&8Bestellungen ✓", owner.getString("title"));
        assertEquals(bundled.getString("buttons.sort.material"),
            owner.getString("buttons.sort.material"));
    }

    @Test
    void partialCoreScreenRestoresMissingGameplayStatesWithoutReplacingOwnerText() throws Exception {
        YamlConfiguration owner = resource("guis/new-order.yml");
        YamlConfiguration bundled = resource("guis/new-order.yml");
        owner.set("title", "&8Neue Bestellung ✓");
        owner.set("buttons.confirm.name", "&aBestätigen ✓");
        owner.set("buttons.confirm.states.taxed", null);
        owner.set("buttons.enchants.states.hidden", null);

        assertTrue(OrdersPublicGuiUpgrade.repairPartialCoreScreen("new-order", owner, bundled,
            ignored -> {}));

        assertEquals("&8Neue Bestellung ✓", owner.getString("title"));
        assertEquals("&aBestätigen ✓", owner.getString("buttons.confirm.name"));
        assertTrue(owner.isConfigurationSection("buttons.confirm.states.taxed"));
        assertTrue(owner.isConfigurationSection("buttons.enchants.states.hidden"));
        assertFalse(OrdersPublicGuiUpgrade.repairPartialCoreScreen("new-order", owner, bundled,
            ignored -> {}));

        YamlConfiguration history = resource("guis/history.yml");
        history.set("entries.entry.source-item.lore-states.deliver", null);
        history.set("buttons.order-tab.states.unselected", null);
        assertTrue(OrdersPublicGuiUpgrade.repairPartialCoreScreen("history", history,
            resource("guis/history.yml"), ignored -> {}));
        assertTrue(history.isConfigurationSection("entries.entry.source-item.lore-states.deliver"));
        assertTrue(history.isConfigurationSection("buttons.order-tab.states.unselected"));
    }

    @Test
    void retryDoesNotBackUpAFileCreatedDuringTheFirstUpgradeAttempt() throws Exception {
        Path gui = install.resolve("guis/main.yml");
        Path textFile = install.resolve("public-text.yml");
        Files.createDirectories(gui.getParent());
        Files.writeString(gui, "title: Original\n", StandardCharsets.UTF_8);
        me.foesio.foOrders.util.OwnerUpgradeBackups.begin(install.toFile(), 4);
        me.foesio.foOrders.util.OwnerUpgradeBackups.retainPublicGuiOrigin(install.toFile(), 4);
        Files.writeString(textFile, "labels: {}\n", StandardCharsets.UTF_8);

        me.foesio.foOrders.util.OwnerUpgradeBackups.begin(install.toFile(), 37);
        byte[] oldGui = Files.readAllBytes(gui);
        byte[] oldText = Files.readAllBytes(textFile);
        OrdersPublicGuiUpgrade.installConverted(Map.of(gui, oldGui, textFile, oldText),
            Map.of(gui, "title: Converted\n".getBytes(StandardCharsets.UTF_8),
                textFile, "labels:\n  owner: true\n".getBytes(StandardCharsets.UTF_8)),
            me.foesio.foOrders.util.OwnerUpgradeBackups.originalVersion(install.toFile(), 37), () -> true);

        assertTrue(Files.exists(gui.resolveSibling("main.yml.gui-v4-backup")));
        assertFalse(Files.exists(textFile.resolveSibling("public-text.yml.gui-v4-backup")));
    }



    @Test
    void malformedAuxiliaryOwnerFileStaysUntouchedWhileOtherChangesCanBeStaged() throws Exception {
        Path malformed = install.resolve("sounds.yml");
        byte[] original = "sounds: [broken\n".getBytes(StandardCharsets.UTF_8);
        Files.write(malformed, original);
        Path valid = install.resolve("dialogs/amount.yml");
        Files.createDirectories(valid.getParent());
        Files.writeString(valid, "title: 'Owner title ✓'\n", StandardCharsets.UTF_8);
        Map<Path, byte[]> before = new java.util.LinkedHashMap<>();
        Map<Path, byte[]> proposed = new java.util.LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        assertEquals(OrdersPublicGuiUpgrade.AuxiliaryResult.PENDING,
            OrdersPublicGuiUpgrade.stageAuxiliary(malformed, before, proposed,
                ignored -> true, warnings::add));
        assertEquals(OrdersPublicGuiUpgrade.AuxiliaryResult.CHANGED,
            OrdersPublicGuiUpgrade.stageAuxiliary(valid, before, proposed,
                owner -> { owner.set("columns", 2); return true; }, warnings::add));

        org.junit.jupiter.api.Assertions.assertArrayEquals(original, Files.readAllBytes(malformed));
        assertFalse(before.containsKey(malformed));
        assertEquals("Owner title ✓", YamlConfiguration.loadConfiguration(valid.toFile()).getString("title"));
        assertTrue(new String(proposed.get(valid), StandardCharsets.UTF_8).contains("columns: 2"));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("sounds.yml")));
    }

    @Test
    void invalidCustomSoundLeavesOwnerFileUnchangedAndMigrationPending() throws Exception {
        Path soundFile = install.resolve("sounds.yml");
        byte[] original = "custom:\n  alert:\n    sound: owner:alert\n    volume: loud\n"
            .getBytes(StandardCharsets.UTF_8);
        Files.write(soundFile, original);
        YamlConfiguration defaults = resource("sounds.yml");
        Map<Path, byte[]> before = new java.util.LinkedHashMap<>();
        Map<Path, byte[]> proposed = new java.util.LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        assertEquals(OrdersPublicGuiUpgrade.AuxiliaryResult.PENDING,
            OrdersPublicGuiUpgrade.stageAuxiliary(soundFile, before, proposed,
                owner -> me.foesio.foOrders.config.PublicSoundFiles.repairLegacyInvalidFields(
                    owner, defaults, warnings::add), warnings::add));

        org.junit.jupiter.api.Assertions.assertArrayEquals(original, Files.readAllBytes(soundFile));
        assertFalse(before.containsKey(soundFile));
        assertFalse(proposed.containsKey(soundFile));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("custom.alert.volume")));
    }

    @Test
    void allReleasedShapeScreensConvertToStrictCoreSchema() throws Exception {
        for (String screen : OrdersPublicGuiUpgrade.SCREENS) {
            YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/" + screen + ".yml");
            YamlConfiguration newest = resource("guis/" + screen + ".yml");
            OrdersPublicGuiUpgrade.convertScreen(screen, old, List.of(old), newest,
                resource("public-text.yml"), 37, ignored -> {});
            PublicGuiSchema.parse("orders." + screen, "guis/" + screen + ".yml",
                newest, OrdersPublicGuis.contractFor(screen));
            assertFalse(OrdersPublicGuiUpgrade.repairPartialCoreScreen(screen, newest,
                resource("guis/" + screen + ".yml"), ignored -> {}));
            if (screen.equals("main")) {
                for (String page : List.of("previous-page", "next-page")) {
                    String path = "entries." + page + ".empty-item";
                    assertEquals("GRAY_STAINED_GLASS_PANE", newest.getString(path + ".material"));
                    assertEquals(" ", newest.getString(path + ".name"));
                    assertEquals(List.of(), newest.getStringList(path + ".lore"));
                }
            }
            if (screen.equals("claim-order")) {
                assertFalse(newest.isSet("buttons.back"));
                assertFalse(newest.isSet("entries.back"));
            }
        }
    }

    @Test
    void customOwnerTextAndIndependentInvalidFieldKeepTheirOwnResults() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration baseline = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        YamlConfiguration text = resource("public-text.yml");
        old.set("title", "&8Bestellungen ✓ {page}");
        old.set("items.sort.name", "&aSortieren ✓");
        old.set("items.sort.material", "NOT_A_MATERIAL");
        old.set("labels.search-empty", "Keine Suche ✓");
        List<String> warnings = new ArrayList<>();

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(baseline), newest, text, 37,
            warnings::add);

        assertEquals("&8Bestellungen ✓ {page}", newest.getString("title"));
        assertEquals("&aSortieren ✓", newest.getString("buttons.sort.name"), warnings.toString());
        assertEquals("CAULDRON", newest.getString("buttons.sort.material"));
        assertEquals("Keine Suche ✓", text.getString("labels.main.search-empty"));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("buttons.sort.material")));
    }

    @Test
    void legacyPaneNamesUseNewBlankDefaultsUnlessOwnerCustomizedThem() throws Exception {
        YamlConfiguration baseline = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        old.set("items.gray-pane.name", "&aCustom frame");
        YamlConfiguration newest = resource("guis/main.yml");

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(baseline), newest,
            resource("public-text.yml"), 37, ignored -> {});

        assertEquals("&aCustom frame", newest.getString("decorations.frame.name"));
        assertEquals(" ", newest.getString("entries.order-entry.empty-item.name"));
        assertEquals(List.of(), newest.getStringList("entries.order-entry.empty-item.lore"));
    }

    @Test
    void currentOwnerRemovalsSurviveTheLegacyToCoreConversion() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration baseline = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        YamlConfiguration text = resource("public-text.yml");
        old.set("items.order-entry.lore.price", null);
        old.set("buttons.search.lore", List.of());
        old.set("items.history.enabled", false);
        old.set("labels.search-empty", null);

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(baseline), newest, text, 37,
            ignored -> {});

        assertFalse(newest.getStringList("entries.order-entry.source-item.lore-states.normal.lines")
            .stream().anyMatch(line -> line.contains("{price}")));
        assertEquals(List.of(), newest.getStringList("buttons.search.lore"));
        assertFalse(newest.getBoolean("entries.history.enabled"));
        assertFalse(text.isSet("labels.main.search-empty"));
    }

    @Test
    void movedControlSharedDisableAndInvalidSiblingConvertIndependently() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration baseline = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        old.set("items.previous-page.slot", 46);
        List<Integer> frame = new ArrayList<>(old.getIntegerList("layout.frame-slots"));
        frame.remove(Integer.valueOf(46));
        old.set("layout.frame-slots", frame);
        old.set("buttons.search.enabled", false);
        old.set("items.sort.name", "&aSortieren ✓");
        old.set("items.sort.amount", 1000);
        List<String> warnings = new ArrayList<>();

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(baseline), newest,
            resource("public-text.yml"), 37, warnings::add);

        assertEquals(List.of(46), newest.getIntegerList("entries.previous-page.slots"), warnings.toString());
        assertFalse(newest.getIntegerList("decorations.frame.slots").contains(46));
        assertFalse(newest.getBoolean("buttons.search.enabled"));
        assertEquals("&aSortieren ✓", newest.getString("buttons.sort.name"));
        assertEquals(1, newest.getInt("buttons.sort.amount"));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("buttons.sort.amount")));
    }

    @Test
    void invalidLegacyFrameSlotDoesNotDiscardCustomSiblingSlots() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        old.set("layout.frame-slots", List.of(0, "invalid", 44));
        String before = newest.saveToString();

        assertThrows(java.io.IOException.class, () -> OrdersPublicGuiUpgrade.convertScreen(
            "main", old, List.of(resource("migrations/legacy-public-defaults/current/guis/main.yml")),
            newest, resource("public-text.yml"), 37, ignored -> { }));

        assertEquals(before, newest.saveToString());
    }

    @Test
    void invalidLegacyLoreEntryKeepsTranslatedSiblingLines() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        YamlConfiguration bundled = resource("guis/main.yml");
        old.set("items.sort.lore", List.of("Erste Zeile ✓", 42, "Letzte Zeile ✓"));
        List<String> warnings = new ArrayList<>();

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/main.yml")),
            newest, resource("public-text.yml"), 37, warnings::add);

        assertEquals(List.of("Erste Zeile ✓", bundled.getStringList("buttons.sort.lore").get(1),
            "Letzte Zeile ✓"), newest.getStringList("buttons.sort.lore"));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("buttons.sort.lore[1]")));
    }

    @Test
    void splitPublicTextKeepsValidOptionLabelsAroundInvalidEntry() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        YamlConfiguration publicText = resource("public-text.yml");
        old.set("labels.main-sort-options", List.of("Meist bezahlt ✓", 42, "Zuletzt gelistet ✓"));

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/main.yml")),
            newest, publicText, 37, ignored -> { });
        GuiConfigManager.repairLegacyPublicTextCandidate(publicText, resource("public-text.yml"),
            ignored -> { });

        assertEquals(List.of("Meist bezahlt ✓", "Most Delivered", "Zuletzt gelistet ✓"),
            publicText.getStringList("labels.main.main-sort-options"));
    }

    @Test
    void partialCoreScreenWithExtraInvalidListEntryStaysPending() throws Exception {
        YamlConfiguration owner = resource("guis/main.yml");
        YamlConfiguration bundled = resource("guis/main.yml");
        List<Object> slots = new ArrayList<>(owner.getIntegerList("decorations.frame.slots"));
        slots.add(999);
        owner.set("decorations.frame.slots", slots);
        owner.set("title", "Eigen menu ✓");

        assertThrows(java.io.IOException.class, () -> OrdersPublicGuiUpgrade.repairPartialCoreScreen(
            "main", owner, bundled, ignored -> { }));

        assertEquals("Eigen menu ✓", owner.getString("title"));
        assertEquals(slots, owner.getList("decorations.frame.slots"));
    }

    @Test
    void conflictingOldSlotFallsBackWithoutDroppingTranslatedSibling() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        old.set("items.sort.slot", 48);
        old.set("items.sort.name", "&aSortieren ✓");
        List<String> warnings = new ArrayList<>();

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/main.yml")), newest,
            resource("public-text.yml"), 37, warnings::add);

        assertEquals(47, newest.getInt("buttons.sort.slot"));
        assertEquals("&aSortieren ✓", newest.getString("buttons.sort.name"), warnings.toString());
        assertTrue(warnings.stream().anyMatch(value -> value.contains("buttons.sort.slot")));
    }

    @Test
    void unrelatedSchemaErrorDoesNotDiscardAnArbitraryOwnerCustomization() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        old.set("title", "&8Bestellungen ✓");
        newest.set("filler.enabled", "invalid");

        java.io.IOException failure = org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
            () -> OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(
                resource("migrations/legacy-public-defaults/current/guis/main.yml")), newest,
                resource("public-text.yml"), 37, ignored -> {}));

        assertTrue(failure.getMessage().contains("Cannot isolate invalid migrated GUI field"));
        assertEquals("&8Bestellungen ✓", newest.getString("title"));
    }

    @Test
    void malformedLegacyLoreLayoutUsesNewBundledLoreWithoutLosingTheItemName() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        List<String> defaultLore = newest.getStringList("entries.order-entry.source-item.lore-states.normal.lines");
        old.set("items.order-entry.name", "&aAuftrag ✓");
        old.set("items.order-entry.lore-layout", "invalid list");
        List<String> warnings = new ArrayList<>();

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/main.yml")), newest,
            resource("public-text.yml"), 37, warnings::add);

        assertEquals("&aAuftrag ✓", newest.getString("entries.order-entry.source-item.default-name"));
        assertEquals(defaultLore, newest.getStringList("entries.order-entry.source-item.lore-states.normal.lines"));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("items.order-entry.lore-layout")));
    }

    @Test
    void malformedLegacyLoreLeafUsesOnlyItsBundledLine() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/main.yml");
        YamlConfiguration newest = resource("guis/main.yml");
        String defaultPrice = newest.getStringList("entries.order-entry.source-item.lore-states.normal.lines")
            .get(1);
        old.set("items.order-entry.lore.amount", "&aMenge ✓ {amount}");
        old.set("items.order-entry.lore.price", List.of("invalid scalar"));
        List<String> warnings = new ArrayList<>();

        OrdersPublicGuiUpgrade.convertScreen("main", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/main.yml")), newest,
            resource("public-text.yml"), 37, warnings::add);

        List<String> result = newest.getStringList("entries.order-entry.source-item.lore-states.normal.lines");
        assertEquals("&aMenge ✓ {amount}", result.get(0));
        assertEquals(defaultPrice, result.get(1));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("items.order-entry.lore.price")));
    }

    @Test
    void ownerMaterialNameFromOldPickerSurvivesTheTextSplit() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/item-select.yml");
        old.set("labels.material-names.DIAMOND", "Diamant ✓");
        YamlConfiguration publicText = resource("public-text.yml");

        OrdersPublicGuiUpgrade.convertScreen("item-select", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/item-select.yml")),
            resource("guis/item-select.yml"), publicText, 37, ignored -> {});

        assertEquals("Diamant ✓", publicText.getString("labels.item-select.material-names.DIAMOND"));
    }

    @Test
    void invalidOldTimestampPatternDoesNotDiscardTranslatedHistoryText() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/history.yml");
        old.set("labels.timestamp-pattern", "invalid");
        old.set("labels.unknown-time", "Unbekannt ✓");
        YamlConfiguration publicText = resource("public-text.yml");
        String newestPattern = publicText.getString("labels.history.timestamp-pattern");
        List<String> warnings = new ArrayList<>();

        OrdersPublicGuiUpgrade.convertScreen("history", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/history.yml")),
            resource("guis/history.yml"), publicText, 37, warnings::add);

        assertEquals(newestPattern, publicText.getString("labels.history.timestamp-pattern"));
        assertEquals("Unbekannt ✓", publicText.getString("labels.history.unknown-time"));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("timestamp-pattern")));
    }

    @Test
    void invalidHistoryTabColorCannotBecomeAnEmptyValidName() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/history.yml");
        old.set("items.order-tab.selected-color", 42);
        old.set("items.order-tab.selected-name", "{state_color}Eigen geschiedenis ✓");
        YamlConfiguration newest = resource("guis/history.yml");
        String bundledName = newest.getString("buttons.order-tab.states.selected.name");

        assertThrows(java.io.IOException.class, () -> OrdersPublicGuiUpgrade.convertScreen(
            "history", old, List.of(resource("migrations/legacy-public-defaults/current/guis/history.yml")),
            newest, resource("public-text.yml"), 37, ignored -> { }));

        assertEquals(bundledName, newest.getString("buttons.order-tab.states.selected.name"));
    }

    @Test
    void removedHistoryTabStateNameStaysEmpty() throws Exception {
        YamlConfiguration old = resource("migrations/legacy-public-defaults/current/guis/history.yml");
        old.set("items.order-tab.selected-name", null);
        old.set("items.order-tab.selected-color", "#aabbcc");
        YamlConfiguration newest = resource("guis/history.yml");

        OrdersPublicGuiUpgrade.convertScreen("history", old, List.of(
            resource("migrations/legacy-public-defaults/current/guis/history.yml")),
            newest, resource("public-text.yml"), 37, ignored -> { });

        assertEquals("", newest.getString("buttons.order-tab.states.selected.name"));
    }

    @Test
    void invalidExistingSplitPublicTextIsRepairedInStagedUpgrade() throws Exception {
        YamlConfiguration bundled = resource("public-text.yml");
        YamlConfiguration candidate = resource("public-text.yml");
        candidate.set("labels.main.item-display.max-enchants", -4);
        candidate.set("labels.history.unknown-time", "Unbekannt ✓");
        List<String> warnings = new ArrayList<>();

        GuiConfigManager.repairLegacyPublicTextCandidate(candidate, bundled, warnings::add);

        assertEquals(2, candidate.getInt("labels.main.item-display.max-enchants"));
        assertEquals("Unbekannt ✓", candidate.getString("labels.history.unknown-time"));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("max-enchants")
            && value.contains("staged upgrade")));
    }

    @Test
    void malformedSplitPublicTextSectionKeepsIndependentTranslation() throws Exception {
        YamlConfiguration bundled = resource("public-text.yml");
        YamlConfiguration candidate = resource("public-text.yml");
        candidate.set("labels.main.item-display", "invalid section");
        candidate.set("labels.history.unknown-time", "Unbekannt ✓");

        GuiConfigManager.repairLegacyPublicTextCandidate(candidate, bundled, ignored -> {});

        assertEquals(bundled.getInt("labels.main.item-display.max-enchants"),
            candidate.getInt("labels.main.item-display.max-enchants"));
        assertEquals("Unbekannt ✓", candidate.getString("labels.history.unknown-time"));
    }

    @Test
    void transactionKeepsExactOldBackupAndRollsBackFailedVersionSave() throws Exception {
        Path gui = install.resolve("guis/main.yml");
        Path text = install.resolve("public-text.yml");
        Files.createDirectories(gui.getParent());
        byte[] original = "title: 'Übersicht ✓'\nsize: 54\n".getBytes(StandardCharsets.UTF_8);
        Files.write(gui, original);
        me.foesio.foOrders.util.OwnerUpgradeBackups.begin(install.toFile(), 37);
        Map<Path, byte[]> before = new java.util.LinkedHashMap<>();
        before.put(gui, original);
        before.put(text, null);
        Map<Path, byte[]> proposed = Map.of(gui, "title: Neu\nrows: 6\n".getBytes(StandardCharsets.UTF_8),
            text, "labels: {}\n".getBytes(StandardCharsets.UTF_8));

        OrdersPublicGuiUpgrade.installConverted(before, proposed, 37, () -> true);
        assertEquals("title: Neu\nrows: 6\n", Files.readString(gui));
        assertTrue(Files.isRegularFile(text));
        assertEquals(new String(original, StandardCharsets.UTF_8), Files.readString(
            gui.resolveSibling("main.yml.gui-v37-backup")));
        assertFalse(Files.exists(text.resolveSibling("public-text.yml.gui-v37-backup")));

        Map<Path, byte[]> secondBefore = Map.of(gui, Files.readAllBytes(gui),
            text, Files.readAllBytes(text));
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class, () ->
            OrdersPublicGuiUpgrade.installConverted(secondBefore, Map.of(
                gui, "title: Broken\n".getBytes(StandardCharsets.UTF_8),
                text, "bad: true\n".getBytes(StandardCharsets.UTF_8)), 37, () -> false));
        assertEquals("title: Neu\nrows: 6\n", Files.readString(gui));
        assertEquals("labels: {}\n", Files.readString(text));
        assertEquals(new String(original, StandardCharsets.UTF_8), Files.readString(
            gui.resolveSibling("main.yml.gui-v37-backup")));
    }

    @Test
    void partialUpgradeKeepsBadScreenAndRetriesWithoutRewritingConvertedScreen() throws Exception {
        Path main = install.resolve("guis/main.yml");
        Path history = install.resolve("guis/history.yml");
        Path text = install.resolve("public-text.yml");
        Files.createDirectories(main.getParent());
        byte[] mainOld = "title: Alte Aufträge ✓\nsize: 54\n".getBytes(StandardCharsets.UTF_8);
        byte[] historyBad = "title: [unparseable\n".getBytes(StandardCharsets.UTF_8);
        Files.write(main, mainOld);
        Files.write(history, historyBad);
        me.foesio.foOrders.util.OwnerUpgradeBackups.begin(install.toFile(), 37);
        java.util.concurrent.atomic.AtomicBoolean advanced = new java.util.concurrent.atomic.AtomicBoolean();

        OrdersPublicGuiUpgrade.installConverted(new java.util.LinkedHashMap<>(Map.of(
                main, mainOld)), new java.util.LinkedHashMap<>(Map.of(
                main, "title: Neue Aufträge ✓\nrows: 6\n".getBytes(StandardCharsets.UTF_8))),
            37, () -> {
                if (!Files.exists(history)) advanced.set(true);
                return true;
            });
        assertFalse(advanced.get());
        assertEquals("title: Neue Aufträge ✓\nrows: 6\n", Files.readString(main));
        org.junit.jupiter.api.Assertions.assertArrayEquals(historyBad, Files.readAllBytes(history));
        assertFalse(Files.exists(history.resolveSibling("history.yml.gui-v37-backup")));

        OrdersPublicGuiUpgrade.installConverted(Map.of(history, historyBad),
            Map.of(history, "title: Verlauf ✓\nrows: 6\n".getBytes(StandardCharsets.UTF_8)),
            37, () -> { advanced.set(true); return true; });
        assertTrue(advanced.get());
        assertEquals("title: Neue Aufträge ✓\nrows: 6\n", Files.readString(main));
        org.junit.jupiter.api.Assertions.assertArrayEquals(historyBad, Files.readAllBytes(
            history.resolveSibling("history.yml.gui-v37-backup")));
        assertFalse(Files.exists(text));
    }

    private static YamlConfiguration resource(String path) throws Exception {
        try (InputStream stream = OrdersPublicGuiUpgradeTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalArgumentException("Missing " + path);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
