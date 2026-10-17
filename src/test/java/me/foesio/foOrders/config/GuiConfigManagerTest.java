package me.foesio.foOrders.config;

import me.foesio.foOrders.util.OwnerYaml;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiConfigManagerTest {
    @TempDir
    Path dataFolder;

    @Test
    void publicTextStartupFallbackIsInMemoryAndBadReloadKeepsValidOwnerSiblings() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("labels.history.timestamp-pattern", "yyyy-MM-dd HH:mm");
        bundled.set("labels.main.search-empty", "None");
        bundled.set("labels.main.main-sort-options", List.of("A-Z"));
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("labels.history.timestamp-pattern", "invalid");
        owner.set("labels.main.search-empty", "Keine Suche ✓");
        owner.set("labels.main.main-sort-options", List.of());
        List<String> warnings = new ArrayList<>();

        GuiConfigManager.validatePublicTextCandidate(owner, bundled, false, warnings::add);

        assertEquals("yyyy-MM-dd HH:mm", owner.getString("labels.history.timestamp-pattern"));
        assertEquals("Keine Suche ✓", owner.getString("labels.main.search-empty"));
        assertEquals(List.of(), owner.getStringList("labels.main.main-sort-options"));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("timestamp-pattern")));

        owner.set("labels.history.timestamp-pattern", "invalid");
        assertThrows(IllegalStateException.class, () ->
            GuiConfigManager.validatePublicTextCandidate(owner, bundled, true, warnings::add));
        assertEquals("invalid", owner.getString("labels.history.timestamp-pattern"));
        assertEquals("Keine Suche ✓", owner.getString("labels.main.search-empty"));
    }

    @Test
    void legacyPublicTextRepairsOneOptionWithoutReplacingTranslations() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("labels.main.main-sort-options", List.of("Most Paid", "Most Delivered", "Recently Listed"));
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("labels.main.main-sort-options", List.of("Meist bezahlt ✓", 42, "Zuletzt gelistet ✓"));
        List<String> warnings = new ArrayList<>();

        GuiConfigManager.repairLegacyPublicTextCandidate(owner, bundled, warnings::add);

        assertEquals(List.of("Meist bezahlt ✓", "Most Delivered", "Zuletzt gelistet ✓"),
            owner.getStringList("labels.main.main-sort-options"));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("main-sort-options[1]")));
    }

    @Test
    void publicTextListWithoutMatchingDefaultStaysUntouchedForRetry() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("labels.main.main-sort-options", List.of("Most Paid", "Most Delivered"));
        YamlConfiguration owner = new YamlConfiguration();
        List<Object> original = List.of("Meist bezahlt ✓", "Meist geliefert ✓", 42);
        owner.set("labels.main.main-sort-options", original);

        assertThrows(IllegalStateException.class, () -> GuiConfigManager.repairLegacyPublicTextCandidate(
            owner, bundled, ignored -> { }));

        assertEquals(original, owner.getList("labels.main.main-sort-options"));
    }

    @Test
    void invalidCustomMaterialNameWithoutDefaultStaysUntouchedForRetry() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.createSection("labels.item-select.material-names");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("labels.item-select.material-names.OWNER_ITEM", 42);

        assertThrows(IllegalStateException.class, () -> GuiConfigManager.repairLegacyPublicTextCandidate(
            owner, bundled, ignored -> { }));

        assertEquals(42, owner.get("labels.item-select.material-names.OWNER_ITEM"));
    }

    @Test
    void malformedCurrentPublicTextSectionIsRejectedOnReload() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("labels.main.search-empty", "None");
        bundled.set("labels.history.unknown-time", "Unknown");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("labels.main", "invalid section");
        owner.set("labels.history.unknown-time", "Onbekend ✓");

        assertThrows(IllegalStateException.class, () ->
            GuiConfigManager.validatePublicTextCandidate(owner, bundled, true, ignored -> {}));

        assertEquals("invalid section", owner.getString("labels.main"));
        assertEquals("Onbekend ✓", owner.getString("labels.history.unknown-time"));
    }

    @Test
    void malformedLegacyAggregateRetainsOwnerBytesAndRetryMarkerUntilRepaired() throws IOException {
        Path old = dataFolder.resolve("guis/orders.yml");
        Path marker = dataFolder.resolve("guis/.legacy-aggregate-pending");
        Files.createDirectories(old.getParent());
        byte[] malformed = "titles: [broken\n".getBytes(StandardCharsets.UTF_8);
        Files.write(old, malformed);
        GuiConfigManager.retainLegacyPendingMarker(marker, 2);
        List<String> warnings = new ArrayList<>();

        assertFalse(GuiConfigManager.retryPendingLegacyAggregate(marker, () -> {
            OwnerYaml.load(old.toFile());
            return true;
        }, warnings::add));
        assertTrue(Files.isRegularFile(marker));
        org.junit.jupiter.api.Assertions.assertArrayEquals(malformed, Files.readAllBytes(old));
        assertTrue(warnings.stream().anyMatch(value -> value.contains("retry marker")));

        Files.writeString(old, "titles: {main: 'Aufträge ✓'}\n", StandardCharsets.UTF_8);
        assertTrue(GuiConfigManager.retryPendingLegacyAggregate(marker, () -> {
            OwnerYaml.load(old.toFile());
            return true;
        }, warnings::add));
        assertFalse(Files.exists(marker));
    }

    @Test
    void filterLabelsStayScopedToTheirOwnMenu() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("labels.filter-options", List.of("Main owner label"));
        YamlConfiguration itemSelect = new YamlConfiguration();
        itemSelect.set("labels.filter-options", List.of("Item owner label"));
        YamlConfiguration merged = new YamlConfiguration();

        GuiConfigManager.mergeActiveGui(merged, "main", main);
        GuiConfigManager.mergeActiveGui(merged, "item-select", itemSelect);

        assertEquals(List.of("Main owner label"), merged.getStringList("labels.main.filter-options"));
        assertEquals(List.of("Item owner label"), merged.getStringList("labels.item-select.filter-options"));
    }

    @Test
    void publicButtonTemplatesStayScopedAndOwnerRemovalIsNotMergedFromDefaults() {
        YamlConfiguration main = new YamlConfiguration();
        main.set("buttons.search.material", "NAME_TAG");
        main.set("buttons.search.name", "Owner search");
        main.set("buttons.search.lore", List.of());
        YamlConfiguration itemSelect = new YamlConfiguration();
        itemSelect.set("buttons.search.material", "PAPER");
        itemSelect.set("buttons.search.name", "Translated search");
        YamlConfiguration merged = new YamlConfiguration();

        GuiConfigManager.mergeActiveGui(merged, "main", main);
        GuiConfigManager.mergeActiveGui(merged, "item-select", itemSelect);

        assertEquals("Owner search", merged.getString("buttons.main.search.name"));
        assertEquals(List.of(), merged.getStringList("buttons.main.search.lore"));
        assertEquals("Translated search", merged.getString("buttons.item-select.search.name"));
        assertFalse(merged.isSet("buttons.item-select.search.lore"));
        assertFalse(merged.isSet("buttons.main.back"));
    }

    @Test
    void disabledHistoryStatesHaveNeitherRenderedMaterialNorClickTarget() throws IOException {
        YamlConfiguration active = new YamlConfiguration();
        GuiConfigManager.mergeActiveGui(active, "history", bundledGui("history"));

        assertEquals(Material.WRITABLE_BOOK,
            GuiConfigManager.stateMaterial(active, Set.of(), "history.order-tab", "selected", ignored -> true, ignored -> {}));
        assertEquals(Material.CHEST_MINECART,
            GuiConfigManager.stateMaterial(active, Set.of(), "history.deliver-tab", "unselected", ignored -> true, ignored -> {}));
        assertEquals(Material.WRITABLE_BOOK,
            GuiConfigManager.stateMaterial(active, Set.of(), "history.entry", "order", ignored -> true, ignored -> {}));

        active.set("items.history.order-tab.enabled", false);
        active.set("items.history.deliver-tab.enabled", false);
        active.set("items.history.entry.enabled", false);
        for (String path : List.of("history.order-tab", "history.deliver-tab", "history.entry")) {
            assertNull(GuiConfigManager.stateMaterial(active, Set.of(), path,
                path.endsWith("entry") ? "order" : "selected", ignored -> true, ignored -> {}));
        }
        assertNull(GuiConfigManager.stateMaterial(active, Set.of(), "history.entry", "deliver", ignored -> true, ignored -> {}));

        active.set("items.history.order-tab.enabled", null);
        assertNull(GuiConfigManager.stateMaterial(active, Set.of(), "history.order-tab", "unselected", ignored -> true, ignored -> {}));
    }

    @Test
    void disabledOrIncompleteEnchantEntryHasNoCompleteStatePair() throws IOException {
        YamlConfiguration active = new YamlConfiguration();
        GuiConfigManager.mergeActiveGui(active, "enchant-select", bundledGui("enchant-select"));
        assertTrue(hasBothEnchantEntryStates(active));

        active.set("items.enchant-select.entry.enabled", false);
        assertFalse(hasBothEnchantEntryStates(active));
        active.set("items.enchant-select.entry.enabled", true);
        active.set("items.enchant-select.entry.selected-material", null);
        assertFalse(hasBothEnchantEntryStates(active));
    }

    private boolean hasBothEnchantEntryStates(YamlConfiguration active) {
        return GuiConfigManager.stateMaterial(active, Set.of(), "enchant-select.entry", "selected",
            ignored -> true, ignored -> {}) != null
            && GuiConfigManager.stateMaterial(active, Set.of(), "enchant-select.entry", "unselected",
                ignored -> true, ignored -> {}) != null;
    }

    @Test
    void materialTranslationsStayScopedAndDynamicTemplatesExistInBundledMenus() throws IOException {
        YamlConfiguration main = bundledGui("main");
        YamlConfiguration itemSelect = bundledGui("item-select");
        YamlConfiguration enchantSelect = bundledGui("enchant-select");
        YamlConfiguration manageOrder = bundledGui("manage-order");
        itemSelect.set("labels.material-names.DIAMOND_SWORD", "Translated sword");
        YamlConfiguration merged = new YamlConfiguration();
        GuiConfigManager.mergeActiveGui(merged, "main", main);
        GuiConfigManager.mergeActiveGui(merged, "item-select", itemSelect);
        assertEquals("Translated sword", merged.getString("labels.item-select.material-names.DIAMOND_SWORD"));
        assertFalse(merged.isSet("labels.main.material-names.DIAMOND_SWORD"));
        for (YamlConfiguration gui : List.of(main, itemSelect)) {
            assertTrue(gui.getStringList("items.sort.lore").contains("{options}"));
            assertTrue(gui.getStringList("items.filter.lore").contains("{options}"));
        }
        assertTrue(itemSelect.isList("items.entry.lore"));
        assertTrue(itemSelect.isList("items.custom-entry.lore"));
        assertTrue(enchantSelect.isList("items.entry.lore"));
        assertTrue(manageOrder.getStringList("items.claim.lore").contains("{claim_stacks}"));
    }

    @Test
    void overlappingControlSlotsAreRejectedInsteadOfSilentlyMovingAButton() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.filter.slot", 48);
        defaults.set("items.refresh.slot", 49);
        YamlConfiguration active = new YamlConfiguration();
        active.set("items.filter.slot", 49);
        active.set("items.refresh.slot", 49);
        List<String> warnings = new ArrayList<>();

        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.resolveItemSlots("main", active, defaults, warnings::add));
    }

    @Test
    void movedFilterCanUseFormerContentSlotWhenContentRegionIsAdjusted() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.filter.slot", 49);
        YamlConfiguration active = new YamlConfiguration();
        active.set("items.filter.slot", 10);

        Map<String, Integer> resolved = GuiConfigManager.resolveItemSlots("item-select", active, defaults, ignored -> {});

        assertEquals(10, resolved.get("item-select.filter"));
    }

    @Test
    void currentSchemaRemovedEnabledControlSlotIsRejectedRatherThanRestored() throws IOException {
        YamlConfiguration defaults = bundledGui("main");
        YamlConfiguration active = bundledGui("main");
        active.set("items.filter.slot", null);

        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.requireControlSlots("main", active, defaults, true));

        active.set("items.filter.enabled", false);
        GuiConfigManager.requireControlSlots("main", active, defaults, true);
        assertFalse(GuiConfigManager.resolveItemSlots("main", active, defaults, ignored -> {}, true)
            .containsKey("main.filter"));
    }

    @Test
    void aggregateUpgradeKeepsSupportedOwnerValuesButNotRemovedOptions() {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("items.main.filter.name", "Owner's filter");
        legacy.set("items.main.black-pane.name", "Old unused pane");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.filter.name", "Bundled filter");
        YamlConfiguration target = new YamlConfiguration();
        target.set("items.filter.name", "Bundled filter");

        GuiConfigManager.copySectionIfPresent(
            legacy.getConfigurationSection("items.main"), target, defaults, "items");

        assertEquals("Owner's filter", target.getString("items.filter.name"));
        assertFalse(target.isSet("items.black-pane"));

        target.set("items.filter.name", "Newer owner value");
        GuiConfigManager.copySectionIfPresent(
            legacy.getConfigurationSection("items.main"), target, defaults, "items");
        assertEquals("Newer owner value", target.getString("items.filter.name"));
    }

    @Test
    void aggregateUpgradeDoesNotRestoreDeletedOldPresentation() throws IOException {
        Set<String> oldFields;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
            "migrations/orders-v2.8-present-fields.txt")) {
            assertTrue(stream != null);
            oldFields = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(java.util.stream.Collectors.toSet());
        }
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("items.main.filter.material", "HOPPER");
        legacy.set("items.main.refresh.name", "Owner refresh");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("title", "Default title");
        defaults.set("labels.filter-options", List.of("All", "Blocks"));
        defaults.set("layout.order-slots", List.of(10, 11));
        defaults.set("items.filter.material", "HOPPER");
        defaults.set("items.filter.name", "Default filter");
        defaults.set("items.refresh.lore", List.of("Refresh"));
        defaults.set("items.refresh.enabled", true);
        defaults.set("items.order-entry.enabled", true);
        defaults.set("items.sort.lore", List.of("Newly configurable sort lore"));
        defaults.set("buttons.search.lore", List.of("Search"));
        YamlConfiguration target = new YamlConfiguration();
        for (String path : defaults.getKeys(true)) {
            if (!defaults.isConfigurationSection(path)) {
                target.set(path, defaults.get(path));
            }
        }

        GuiConfigManager.preserveLegacyDeletions(legacy, null, target, defaults, "main", oldFields);

        assertFalse(target.isSet("title"));
        assertFalse(target.isSet("labels.filter-options"));
        assertFalse(target.isSet("layout.order-slots"));
        assertFalse(target.isSet("items.filter.name"));
        assertFalse(target.isSet("items.refresh.lore"));
        assertTrue(target.getBoolean("items.refresh.enabled"));
        assertFalse(target.getBoolean("items.order-entry.enabled"));
        assertFalse(target.isSet("buttons.search.lore"));
        assertEquals("HOPPER", target.getString("items.filter.material"));
        assertEquals(List.of("Newly configurable sort lore"), target.getStringList("items.sort.lore"));
    }

    @Test
    void published29AggregateKeepsDeletedSlotsAndGlobalButtonTextAbsent() throws IOException {
        Set<String> oldest = legacyFieldInventory("orders-v2.8-present-fields.txt");
        Set<String> later = legacyFieldInventory("orders-v2.9-present-fields.txt");
        YamlConfiguration legacy;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/2.9/guis/orders.yml")) {
            assertTrue(stream != null);
            legacy = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        legacy.set("items.new-order.cancel.slot", null);
        legacy.set("buttons.search.name", null);
        assertEquals(later, GuiConfigManager.chooseLegacyKnownFields(legacy, oldest, later));
        YamlConfiguration oldStyle = new YamlConfiguration();
        oldStyle.set("items.main.filter.name", "Owner filter");
        assertEquals(oldest, GuiConfigManager.chooseLegacyKnownFields(oldStyle, oldest, later));

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.cancel.slot", 0);
        YamlConfiguration target = new YamlConfiguration();
        target.set("items.cancel.slot", 0);
        GuiConfigManager.preserveLegacyDeletions(legacy, legacy.getConfigurationSection("buttons"),
            target, defaults, "new-order", later);
        assertFalse(target.isSet("items.cancel.slot"));

        defaults.set("buttons.search.name", "Bundled English search");
        target.set("buttons.search.name", "Bundled English search");
        GuiConfigManager.preserveLegacyDeletions(legacy, legacy.getConfigurationSection("buttons"),
            target, defaults, "main", later);
        assertFalse(target.isSet("buttons.search.name"));
    }

    @Test
    void publicTextMigrationDoesNotRestoreDeletedOldAdminHint() throws IOException {
        File main = saveOldFile("main", "items:\n  order-entry:\n    lore:\n"
            + "      amount: 'Owner amount'\n");
        saveOldFile("orders", "items:\n  main:\n    order-entry:\n      lore:\n"
            + "        amount: 'Owner amount'\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.order-entry.lore.admin-cancel", "Old English hint");
        defaults.set("items.sort.lore", List.of("New sort lore"));

        assertTrue(GuiConfigManager.migratePublicTextTemplates(dataFolder.toFile(),
            Map.of("main", defaults), ignored -> {}));

        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(main);
        assertFalse(migrated.isSet("items.order-entry.lore.admin-cancel"));
        assertEquals(List.of("New sort lore"), migrated.getStringList("items.sort.lore"));
    }

    @Test
    void published45MainGetsGenuinelyNewAdminHint() throws IOException {
        File main;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.5/guis/main.yml")) {
            assertTrue(stream != null);
            Path path = dataFolder.resolve("guis/main.yml");
            Files.createDirectories(path.getParent());
            Files.copy(stream, path);
            main = path.toFile();
        }
        assertFalse(YamlConfiguration.loadConfiguration(main).isSet("items.order-entry.lore.admin-cancel"));
        assertTrue(GuiConfigManager.migratePublicTextTemplates(dataFolder.toFile(),
            Map.of("main", bundledGui("main")), ignored -> {}));
        assertEquals(bundledGui("main").getString("items.order-entry.lore.admin-cancel"),
            YamlConfiguration.loadConfiguration(main).getString("items.order-entry.lore.admin-cancel"));
    }

    @Test
    void aggregateUpgradeMovesOnlyCustomizedLegacyButtonFields() {
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("items.main.previous-page.material", "DIAMOND");
        legacy.set("items.main.previous-page.name", "#03fc88ʙᴀᴄᴋ");
        legacy.set("items.main.search.lore", List.of("Find an order", "Current: {search_status}"));
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("buttons.previous-page.material", "ARROW");
        defaults.set("buttons.previous-page.name", "New page name");
        defaults.set("buttons.search.lore", List.of("Current: {current}"));
        YamlConfiguration target = new YamlConfiguration();
        target.set("buttons.previous-page.material", "ARROW");
        target.set("buttons.previous-page.name", "New page name");
        target.set("buttons.search.lore", List.of("Current: {current}"));

        GuiConfigManager.copyLegacyButtonOverrides(legacy, "main", target, defaults);

        assertEquals("DIAMOND", target.getString("buttons.previous-page.material"));
        assertEquals("New page name", target.getString("buttons.previous-page.name"));
        assertEquals(List.of("Find an order", "Current: {current}"), target.getStringList("buttons.search.lore"));

        target.set("buttons.previous-page.material", "EMERALD");
        GuiConfigManager.copyLegacyButtonOverrides(legacy, "main", target, defaults);
        assertEquals("EMERALD", target.getString("buttons.previous-page.material"));
    }

    @Test
    void oldCustomizedGuiFilesGetOnlyNewBackSlotsAndMigrationIsIdempotent() throws IOException {
        File itemSelect = saveOldFile("item-select", "items:\n  filter:\n    slot: 51\nbuttons:\n  back:\n    name: 'Translated back'\n");
        File enchantSelect = saveOldFile("enchant-select", "items:\n  done:\n    slot: 47\n");
        File claimOrder = saveOldFile("claim-order", "title: 'Custom claim title'\nitems:\n  drop-page:\n    slot: 52\n");
        List<String> warnings = new ArrayList<>();

        assertTrue(GuiConfigManager.migrateNewBackSlots(dataFolder.toFile(), warnings::add));
        YamlConfiguration item = YamlConfiguration.loadConfiguration(itemSelect);
        YamlConfiguration enchant = YamlConfiguration.loadConfiguration(enchantSelect);
        YamlConfiguration claim = YamlConfiguration.loadConfiguration(claimOrder);
        assertEquals(46, item.getInt("items.back.slot"));
        assertEquals(46, enchant.getInt("items.back.slot"));
        assertEquals(49, claim.getInt("items.back.slot"));
        assertEquals(51, item.getInt("items.filter.slot"));
        assertEquals("Translated back", item.getString("buttons.back.name"));
        assertEquals("Custom claim title", claim.getString("title"));
        assertFalse(enchant.isSet("buttons.search"));

        byte[] before = Files.readAllBytes(itemSelect.toPath());
        assertTrue(GuiConfigManager.migrateNewBackSlots(dataFolder.toFile(), warnings::add));
        assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(itemSelect.toPath())));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void dynamicLoreExpansionKeepsOwnerOrderAndSupportsRemovingOptions() {
        List<String> configured = List.of("Own heading", "{options}", " ", "Own footer");
        assertEquals(List.of("Own heading", "First", "Second", " ", "Own footer"),
            GuiConfigManager.expandLore(configured, Map.of("{options}", List.of("First", "Second"))));
        assertEquals(List.of("» First2", "» Second2"),
            GuiConfigManager.expandLore(List.of("» {options}2"),
                Map.of("{options}", List.of("First", "Second"))));
        assertEquals(List.of("Own heading", " ", "Own footer"),
            GuiConfigManager.expandLore(configured, Map.of("{options}", List.of())));
        assertEquals(List.of(), GuiConfigManager.expandLore(List.of("» {options}2"),
            Map.of("{options}", List.of())));
        assertEquals(configured, GuiConfigManager.expandLore(configured, Map.of()));
    }

    @Test
    void inlineLoreExpansionFormatsOnlyOwnerTextAndNeverReprocessesInsertedData() {
        List<String> actual = GuiConfigManager.renderLoreLines(
            List.of("{theme}» {options}2"),
            Map.of("{options}", List.of("Player {theme} &a", "Second")),
            ignored -> true,
            line -> me.foesio.foOrders.util.TextFormat.applyPlaceholders(line, Map.of("theme", "OWNER ")));
        assertEquals(List.of("OWNER » Player {theme} &a2", "OWNER » Second2"), actual);
        assertEquals(List.of("Lore: First2", "Lore: Second2"),
            GuiConfigManager.expandLore(List.of("Lore: {template_lore}2"),
                Map.of("{template_lore}", List.of("First", "Second"))));
        assertEquals(List.of("Enchant: Sharpness2", "Enchant: Fortune2"),
            GuiConfigManager.expandLore(List.of("Enchant: {enchantments}2"),
                Map.of("{enchantments}", List.of("Sharpness", "Fortune"))));
    }

    @Test
    void publicTextMigrationBackfillsOnlyNewFieldsAndPreservesTranslations() throws IOException {
        File main = saveOldFile("main", "items:\n  sort:\n    name: 'Translated sort'\n"
            + "    lore:\n      - 'Owner lore'\n  filter:\n    name: 'Translated filter'\n"
            + "  order-entry:\n    lore:\n      amount: 'Custom amount {item}'\n");
        File itemSelect = saveOldFile("item-select", "items:\n  entry:\n    name: 'Owner entry {item}'\n"
            + "  sort:\n    name: 'Owner sort'\nlabels:\n  filter-options:\n    - 'Translated all'\n");
        saveOldFile("orders", "items:\n  main:\n    order-entry:\n      lore:\n"
            + "        admin-cancel: 'Owner moderation hint'\n");
        YamlConfiguration mainDefaults = new YamlConfiguration();
        mainDefaults.set("items.sort.lore", List.of("Default header", "{options}"));
        mainDefaults.set("items.sort.selected-option", "{option}");
        mainDefaults.set("items.filter.lore", List.of("Default filter", "{options}"));
        mainDefaults.set("items.order-entry.lore.admin-cancel", "Cancel hint");
        YamlConfiguration itemDefaults = new YamlConfiguration();
        itemDefaults.set("items.entry.name", "Default entry {item}");
        itemDefaults.set("items.entry.lore", List.of("Default item lore"));
        itemDefaults.set("items.sort.lore", List.of("Default sort lore"));
        List<String> warnings = new ArrayList<>();

        assertTrue(GuiConfigManager.migratePublicTextTemplates(dataFolder.toFile(),
            Map.of("main", mainDefaults, "item-select", itemDefaults), warnings::add));
        YamlConfiguration migratedMain = YamlConfiguration.loadConfiguration(main);
        YamlConfiguration migratedItems = YamlConfiguration.loadConfiguration(itemSelect);
        assertEquals("Translated sort", migratedMain.getString("items.sort.name"));
        assertEquals(List.of("Owner lore"), migratedMain.getStringList("items.sort.lore"));
        assertEquals("Custom amount {item}", migratedMain.getString("items.order-entry.lore.amount"));
        assertEquals(List.of("Default filter", "{options}"), migratedMain.getStringList("items.filter.lore"));
        assertEquals("Owner moderation hint", migratedMain.getString("items.order-entry.lore.admin-cancel"));
        assertEquals("Owner entry {item}", migratedItems.getString("items.entry.name"));
        assertEquals(List.of("Default item lore"), migratedItems.getStringList("items.entry.lore"));
        assertEquals(List.of("Translated all"), migratedItems.getStringList("labels.filter-options"));
        assertTrue(migratedItems.isSet("labels.material-names"));
        assertTrue(new File(main.getParentFile(), main.getName() + ".pre-v13-backup").isFile());
        byte[] once = Files.readAllBytes(main.toPath());
        assertTrue(GuiConfigManager.migratePublicTextTemplates(dataFolder.toFile(),
            Map.of("main", mainDefaults, "item-select", itemDefaults), warnings::add));
        assertTrue(Arrays.equals(once, Files.readAllBytes(main.toPath())));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void publicTextMigrationAddsEnchantSummaryOnlyToUntouchedDefaultLore() throws IOException {
        String oldLore = "items:\n  enchants:\n    lore:\n      - '&8ʙᴜᴛᴛᴏɴ'\n      - ' '\n"
            + "      - '&eⓘ Information ↓'\n      - '&7&l | &fConfigure item enchantments.'\n"
            + "      - ' '\n      - '{good}→ Click to Edit Enchants ←'\n";
        File newOrder = saveOldFile("new-order", oldLore);
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.enchants.lore", List.of("Bundled heading", "{enchantments}", "Bundled hint"));
        defaults.set("items.enchants.summary-line", "{enchant}: {level}");

        assertTrue(GuiConfigManager.migratePublicTextTemplates(dataFolder.toFile(),
            Map.of("new-order", defaults), ignored -> {}));
        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(newOrder);
        assertEquals(List.of("Bundled heading", "{enchantments}", "Bundled hint"),
            upgraded.getStringList("items.enchants.lore"));
        assertEquals("{enchant}: {level}", upgraded.getString("items.enchants.summary-line"));

        upgraded.set("items.enchants.lore", List.of("Owner translated lore"));
        upgraded.save(newOrder);
        assertTrue(GuiConfigManager.migratePublicTextTemplates(dataFolder.toFile(),
            Map.of("new-order", defaults), ignored -> {}));
        assertEquals(List.of("Owner translated lore"),
            YamlConfiguration.loadConfiguration(newOrder).getStringList("items.enchants.lore"));
    }

    @Test
    void loreLayoutUpgradePreservesExistingTranslationsAndLaterRemoval() throws IOException {
        File main = saveOldFile("main", "items:\n  order-entry:\n    lore:\n"
            + "      amount: 'Translated amount {amount}'\n  search:\n    lore: []\n"
            + "labels:\n  filter-options: ['Translated all']\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.order-entry.lore-layout", List.of("{amount_line}", "{price_line}"));
        defaults.set("labels.search-empty", "None");
        List<String> warnings = new ArrayList<>();

        assertTrue(GuiConfigManager.migrateLoreLayouts(dataFolder.toFile(),
            Map.of("main", defaults), warnings::add));
        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(main);
        assertEquals("Translated amount {amount}", upgraded.getString("items.order-entry.lore.amount"));
        assertEquals(List.of(), upgraded.getStringList("items.search.lore"));
        assertEquals(List.of("Translated all"), upgraded.getStringList("labels.filter-options"));
        assertEquals(List.of("{amount_line}", "{price_line}"),
            upgraded.getStringList("items.order-entry.lore-layout"));
        assertTrue(new File(main.getParentFile(), main.getName() + ".pre-v14-backup").isFile());

        byte[] migrated = Files.readAllBytes(main.toPath());
        assertTrue(GuiConfigManager.migrateLoreLayouts(dataFolder.toFile(),
            Map.of("main", defaults), warnings::add));
        assertTrue(Arrays.equals(migrated, Files.readAllBytes(main.toPath())));

        upgraded.set("items.order-entry.lore-layout", null);
        upgraded.save(main);
        assertFalse(YamlConfiguration.loadConfiguration(main).isSet("items.order-entry.lore-layout"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void layoutUpgradeAddsOnlyNewFieldsAndKeepsOwnerControlSlots() throws IOException {
        File itemSelect = saveOldFile("item-select", "title: 'Translated item picker'\n"
            + "items:\n  filter:\n    slot: 10\n  search:\n    slot: 47\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("size", 54);
        defaults.set("layout.content-slots", List.of(0, 1, 2, 3));
        List<String> warnings = new ArrayList<>();

        assertTrue(GuiConfigManager.migrateLayouts(dataFolder.toFile(),
            Map.of("item-select", defaults), warnings::add));
        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(itemSelect);
        assertEquals("Translated item picker", upgraded.getString("title"));
        assertEquals(10, upgraded.getInt("items.filter.slot"));
        assertEquals(47, upgraded.getInt("items.search.slot"));
        assertEquals(54, upgraded.getInt("size"));
        assertEquals(List.of(0, 1, 2, 3), upgraded.getIntegerList("layout.content-slots"));
        assertTrue(new File(itemSelect.getParentFile(), itemSelect.getName() + ".pre-v15-backup").isFile());
        byte[] once = Files.readAllBytes(itemSelect.toPath());
        assertTrue(GuiConfigManager.migrateLayouts(dataFolder.toFile(),
            Map.of("item-select", defaults), warnings::add));
        assertTrue(Arrays.equals(once, Files.readAllBytes(itemSelect.toPath())));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void itemFlagUpgradePreservesOwnerOverrideAndAddsOnlyIntroducedFields() throws IOException {
        File main = saveOldFile("main", "items:\n  sort:\n    name: 'Ordre'\n"
            + "    item-flags: [HIDE_ATTRIBUTES]\n  filter:\n    name: 'Filtro'\n"
            + "buttons:\n  search:\n    name: 'Buscar'\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.sort.item-flags", "all");
        defaults.set("items.filter.item-flags", "all");
        defaults.set("items.filter.amount", 1);
        defaults.set("buttons.search.item-flags", "all");
        defaults.set("buttons.search.amount", 1);
        List<String> warnings = new ArrayList<>();

        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(),
            Map.of("main", defaults), warnings::add));
        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(main);
        assertEquals(List.of("HIDE_ATTRIBUTES"), upgraded.getStringList("items.sort.item-flags"));
        assertEquals("all", upgraded.getString("items.filter.item-flags"));
        assertEquals(1, upgraded.getInt("items.filter.amount"));
        assertEquals("all", upgraded.getString("buttons.search.item-flags"));
        assertEquals(1, upgraded.getInt("buttons.search.amount"));
        assertTrue(new File(main.getParentFile(), main.getName() + ".pre-v19-backup").isFile());
        byte[] once = Files.readAllBytes(main.toPath());
        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(),
            Map.of("main", defaults), warnings::add));
        assertTrue(Arrays.equals(once, Files.readAllBytes(main.toPath())));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void eventAndEnchantmentDefaultsUpgradeWithoutReplacingTranslations() throws IOException {
        File history = saveOldFile("history", "events:\n  order-created:\n    action: 'Pedido creado'\n"
            + "items:\n  entry:\n    lore: []\n");
        File enchant = saveOldFile("enchant-select", "labels:\n  enchantment-names:\n"
            + "    SHARPNESS: 'Filo'\n");
        YamlConfiguration historyDefaults = new YamlConfiguration();
        historyDefaults.set("events.order-created.action", "Order Created");
        historyDefaults.set("events.order-created.details", "{amount}x {item}");
        historyDefaults.set("labels.timestamp-pattern", "yyyy-MM-dd HH:mm");
        YamlConfiguration enchantDefaults = new YamlConfiguration();
        enchantDefaults.set("labels.enchantment-names.SHARPNESS", "Sharpness");
        enchantDefaults.set("labels.enchantment-names.PROTECTION", "Protection");
        List<String> warnings = new ArrayList<>();

        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(),
            Map.of("history", historyDefaults, "enchant-select", enchantDefaults), warnings::add));
        YamlConfiguration upgradedHistory = YamlConfiguration.loadConfiguration(history);
        YamlConfiguration upgradedEnchant = YamlConfiguration.loadConfiguration(enchant);
        assertEquals("Pedido creado", upgradedHistory.getString("events.order-created.action"));
        assertEquals("{amount}x {item}", upgradedHistory.getString("events.order-created.details"));
        assertEquals(List.of(), upgradedHistory.getStringList("items.entry.lore"));
        assertEquals("Filo", upgradedEnchant.getString("labels.enchantment-names.SHARPNESS"));
        assertEquals("Protection", upgradedEnchant.getString("labels.enchantment-names.PROTECTION"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void visualUpgradeDoesNotResurrectOwnerRemovedControlOrLore() throws IOException {
        File main = saveOldFile("main", "items:\n  sort:\n    name: 'Ordre'\n"
            + "  order-entry:\n    lore:\n      price: 'Prijs {price}'\n");
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.sort.enabled", true);
        defaults.set("items.sort.amount", 1);
        defaults.set("items.sort.item-flags", "all");
        defaults.set("items.order-entry.lore.amount", "Amount {amount}");
        defaults.set("buttons.search.enabled", true);
        defaults.set("buttons.search.amount", 1);
        defaults.set("buttons.search.item-flags", "all");

        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(),
            Map.of("main", defaults), ignored -> {}));
        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(main);
        assertEquals(1, upgraded.getInt("items.sort.amount"));
        assertFalse(upgraded.isSet("buttons.search"));
        assertFalse(upgraded.isSet("items.order-entry.lore.amount"));
        assertEquals("Prijs {price}", upgraded.getString("items.order-entry.lore.price"));
    }

    @Test
    void historyStateMigrationReplacesOnlyExactOldBundledStyle() throws IOException {
        YamlConfiguration old = new YamlConfiguration();
        old.set("items.order-tab.name", "{theme}&lORDER HISTORY");
        old.set("items.order-tab.lore", List.of("&8ʙᴜᴛᴛᴏɴ", " ", "&eⓘ Information ↓",
            "&7&l | &fView order history entries.", " ", "{good}→ Click to View Orders ←"));
        old.set("items.deliver-tab.name", "Ver entregas");
        old.set("items.deliver-tab.lore", List.of("Mi texto"));
        File file = new File(dataFolder.toFile(), "guis/history.yml");
        assertTrue(file.getParentFile().mkdirs());
        old.save(file);

        YamlConfiguration defaults = bundledGui("history");
        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(),
            Map.of("history", defaults), ignored -> {}));
        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(file);
        assertEquals("{state_color}&lORDER HISTORY", upgraded.getString("items.order-tab.name"));
        assertEquals(List.of("{state_lore}"), upgraded.getStringList("items.order-tab.lore"));
        assertEquals("Ver entregas", upgraded.getString("items.deliver-tab.name"));
        assertEquals(List.of("Mi texto"), upgraded.getStringList("items.deliver-tab.lore"));
        assertTrue(upgraded.isList("items.order-tab.selected-lore"));
        byte[] once = Files.readAllBytes(file.toPath());
        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(),
            Map.of("history", defaults), ignored -> {}));
        assertTrue(Arrays.equals(once, Files.readAllBytes(file.toPath())));
    }

    @Test
    void historyTabStateLoreExpansionAndRemovalUseOwnerLinesOnly() throws IOException {
        YamlConfiguration history = bundledGui("history");
        List<String> layout = history.getStringList("items.order-tab.lore");
        List<String> selected = history.getStringList("items.order-tab.selected-lore");
        List<String> unselected = history.getStringList("items.order-tab.unselected-lore");
        assertEquals(selected, GuiConfigManager.expandLore(layout, Map.of("{state_lore}", selected)));
        assertEquals(unselected, GuiConfigManager.expandLore(layout, Map.of("{state_lore}", unselected)));
        assertEquals(selected.stream().map(line -> line + "2").toList(),
            GuiConfigManager.expandLore(List.of("{state_lore}2"), Map.of("{state_lore}", selected)));
        assertEquals(List.of(), GuiConfigManager.expandLore(layout, Map.of("{state_lore}", List.of())));
        assertFalse(selected.equals(unselected));
    }

    @Test
    void claimEntryLoreKeepsTemplateLinesOnlyWhenOwnerIncludesTheirPlaceholder() throws IOException {
        YamlConfiguration claim = bundledGui("claim-order");
        assertEquals("{item}", claim.getString("items.entry.name"));
        List<String> configuredLore = claim.getStringList("items.entry.lore");
        assertTrue(configuredLore.contains("{template_lore}"));
        assertTrue(configuredLore.stream().anyMatch(line -> line.contains("{amount}")));

        List<String> templateLore = List.of("§bPersoonlijke tekst", "§7Tweede regel");
        List<String> expanded = GuiConfigManager.expandLore(configuredLore,
            Map.of("{template_lore}", templateLore));
        assertEquals(templateLore, expanded.subList(0, templateLore.size()));
        assertFalse(GuiConfigManager.expandLore(configuredLore,
            Map.of("{template_lore}", List.of())).contains("{template_lore}"));

        claim.set("items.entry.name", "Mijn pakket: {item}");
        claim.set("items.entry.lore", List.of("Neem {amount}"));
        assertEquals("Mijn pakket: {item}", claim.getString("items.entry.name"));
        assertEquals(List.of("Neem {amount}"), GuiConfigManager.expandLore(
            claim.getStringList("items.entry.lore"), Map.of("{template_lore}", templateLore)));
        claim.set("items.entry.lore", List.of());
        assertEquals(List.of(), GuiConfigManager.expandLore(
            claim.getStringList("items.entry.lore"), Map.of("{template_lore}", templateLore)));
        claim.set("items.entry.enabled", false);
        assertFalse(GuiConfigManager.itemEnabled(claim, Set.of(), "entry", Material::isItem));
        claim.set("items.entry.enabled", null);
        assertFalse(GuiConfigManager.itemEnabled(claim, Set.of(), "entry", Material::isItem));
    }

    @Test
    void customItemPickerLoreOnlyIncludesTemplateLinesAtOwnerChosenPosition() throws IOException {
        YamlConfiguration itemSelect = bundledGui("item-select");
        List<String> templateLore = List.of("§bEigen beschrijving", "§7Tweede regel");
        List<String> bundledLore = itemSelect.getStringList("items.custom-entry.lore");
        assertEquals("{template_lore}", bundledLore.get(0));
        assertEquals(templateLore, GuiConfigManager.expandLore(
            List.of("{template_lore}"), Map.of("{template_lore}", templateLore)));

        itemSelect.set("items.custom-entry.lore", List.of("Mijn label", "{template_lore}"));
        assertEquals(List.of("Mijn label", "§bEigen beschrijving", "§7Tweede regel"),
            GuiConfigManager.expandLore(itemSelect.getStringList("items.custom-entry.lore"),
                Map.of("{template_lore}", templateLore)));
        itemSelect.set("items.custom-entry.lore", List.of("Alleen mijn tekst"));
        assertEquals(List.of("Alleen mijn tekst"), GuiConfigManager.expandLore(
            itemSelect.getStringList("items.custom-entry.lore"), Map.of("{template_lore}", templateLore)));
        itemSelect.set("items.custom-entry.lore", List.of());
        assertEquals(List.of(), GuiConfigManager.expandLore(
            itemSelect.getStringList("items.custom-entry.lore"), Map.of("{template_lore}", templateLore)));

        assertEquals(List.of("formatted:owner line", "§bLiteral {theme} and &a"),
            GuiConfigManager.renderLoreLines(List.of("owner line", "{template_lore}"),
                Map.of("{template_lore}", List.of("§bLiteral {theme} and &a")),
                ignored -> true, line -> "formatted:" + line));
    }

    @Test
    void orderEntryLoreExpansionFormatsOwnerLayoutButNotInsertedPlayerData() {
        List<String> rendered = GuiConfigManager.renderLoreLines(
            List.of("{amount_line}", "Owner {owner}", "{price_line}"),
            Map.of("{amount_line}", List.of("Amount for {theme} &a"), "{price_line}", List.of()),
            ignored -> true,
            line -> me.foesio.foOrders.util.TextFormat.applyPlaceholders(line,
                Map.of("theme", "translated color", "owner", "Speler")));
        assertEquals(List.of("Amount for {theme} &a", "Owner Speler"), rendered);
    }

    @Test
    void customItemPickerLoreUpgradePreservesPriorOutputAndOwnerRemoval() throws IOException {
        assertFalse(GuiConfigManager.migrateCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));

        File file = saveOldFile("item-select", "size: 54\nitems:\n  custom-entry:\n"
            + "    name: 'Aangepast'\n    lore:\n      - 'Mijn tekst'\n");
        assertTrue(GuiConfigManager.migrateCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals("Aangepast", migrated.getString("items.custom-entry.name"));
        assertEquals(List.of("{template_lore}", "Mijn tekst"),
            migrated.getStringList("items.custom-entry.lore"));
        assertTrue(new File(file.getParentFile(), file.getName() + ".pre-v30-backup").isFile());
        byte[] first = Files.readAllBytes(file.toPath());
        assertTrue(GuiConfigManager.migrateCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertTrue(Arrays.equals(first, Files.readAllBytes(file.toPath())));

        File emptyLore = saveOldFile("item-select", "size: 54\nitems:\n  custom-entry:\n"
            + "    lore: []\n");
        assertTrue(GuiConfigManager.migrateCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertEquals(List.of(), YamlConfiguration.loadConfiguration(emptyLore)
            .getStringList("items.custom-entry.lore"));
        File missingLore = saveOldFile("item-select", "size: 54\nitems:\n  custom-entry:\n"
            + "    name: 'No lore key'\n");
        assertTrue(GuiConfigManager.migrateCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(missingLore).isSet("items.custom-entry.lore"));

        File removedControl = saveOldFile("item-select", "size: 54\nitems:\n  entry:\n"
            + "    name: 'Other control'\n");
        assertTrue(GuiConfigManager.migrateCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(removedControl).isSet("items.custom-entry"));

        File malformed = saveOldFile("item-select", "size: 54\nitems:\n  custom-entry:\n"
            + "    lore: invalid\n");
        byte[] malformedBytes = Files.readAllBytes(malformed.toPath());
        assertFalse(GuiConfigManager.migrateCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertTrue(Arrays.equals(malformedBytes, Files.readAllBytes(malformed.toPath())));
    }

    @Test
    void laterUpgradeReversesOnlyTemplateLoreGeneratedFromOldEmptyOwnerLore() throws IOException {
        File file = saveOldFile("item-select", "items:\n  custom-entry:\n    lore:\n      - '{template_lore}'\n");
        File oldBackup = new File(file.getParentFile(), file.getName() + ".pre-v30-backup");
        YamlConfiguration old = new YamlConfiguration();
        old.set("items.custom-entry.lore", List.of());
        old.save(oldBackup);
        byte[] before = Files.readAllBytes(file.toPath());

        assertTrue(GuiConfigManager.restoreRemovedCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertEquals(List.of(), YamlConfiguration.loadConfiguration(file).getStringList("items.custom-entry.lore"));
        assertTrue(YamlConfiguration.loadConfiguration(file).isList("items.custom-entry.lore"));
        assertTrue(Arrays.equals(before, Files.readAllBytes(new File(file.getParentFile(),
            file.getName() + ".pre-v35-backup").toPath())));
        byte[] repaired = Files.readAllBytes(file.toPath());
        assertTrue(GuiConfigManager.restoreRemovedCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertTrue(Arrays.equals(repaired, Files.readAllBytes(file.toPath())));

        old.set("items.custom-entry.lore", null);
        old.save(oldBackup);
        YamlConfiguration active = new YamlConfiguration();
        active.set("items.custom-entry.lore", List.of("{template_lore}"));
        active.save(file);
        assertTrue(GuiConfigManager.restoreRemovedCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(file).isSet("items.custom-entry.lore"));

        active.set("items.custom-entry.lore", List.of("{template_lore}", "Owner added this"));
        active.save(file);
        byte[] edited = Files.readAllBytes(file.toPath());
        assertTrue(GuiConfigManager.restoreRemovedCustomItemPickerLore(dataFolder.toFile(), ignored -> {}));
        assertTrue(Arrays.equals(edited, Files.readAllBytes(file.toPath())));
    }

    @Test
    void claimEntryUpgradeAddsOnlyNewFieldsAndPreservesRemovedFamily() throws IOException {
        File file = saveOldFile("claim-order", "title: 'Mijn claim'\nsize: 54\nitems:\n"
            + "  drop-page:\n    name: 'Laat vallen'\n  entry:\n    name: 'Eigen naam'\n");
        Map<String, YamlConfiguration> defaults = Map.of("claim-order", bundledGui("claim-order"));
        assertTrue(GuiConfigManager.migrateClaimEntryPresentation(
            dataFolder.toFile(), defaults, ignored -> {}));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals("Mijn claim", migrated.getString("title"));
        assertEquals("Laat vallen", migrated.getString("items.drop-page.name"));
        assertEquals("Eigen naam", migrated.getString("items.entry.name"));
        assertTrue(migrated.getBoolean("items.entry.enabled"));
        assertEquals(defaults.get("claim-order").getStringList("items.entry.lore"),
            migrated.getStringList("items.entry.lore"));
        assertTrue(new File(file.getParentFile(), file.getName() + ".pre-v28-backup").isFile());
        byte[] first = Files.readAllBytes(file.toPath());
        assertTrue(GuiConfigManager.migrateClaimEntryPresentation(
            dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(Arrays.equals(first, Files.readAllBytes(file.toPath())));

        File removedFamily = saveOldFile("claim-order", "title: 'Mijn claim'\nsize: 54\n");
        assertTrue(GuiConfigManager.migrateClaimEntryPresentation(
            dataFolder.toFile(), defaults, ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(removedFamily).isSet("items"));
    }

    @Test
    void previewLimitUpgradePreservesOwnerTextAndCurrentSchemaRemoval() throws IOException {
        File mainFile = saveOldFile("main", "title: 'Mijn orders'\nsize: 54\nlabels:\n"
            + "  item-display:\n    enchant-summary: '{enchants} voor {item}'\n");
        File manageFile = saveOldFile("manage-order", "title: 'Mijn bestelling'\nsize: 27\nitems:\n"
            + "  claim:\n    lore: []\n    stack-line: '{item}: {amount}'\n");
        Map<String, YamlConfiguration> defaults = Map.of("main", bundledGui("main"),
            "manage-order", bundledGui("manage-order"));

        assertTrue(GuiConfigManager.migratePreviewLimits(dataFolder.toFile(), defaults, ignored -> {}));
        YamlConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
        YamlConfiguration manage = YamlConfiguration.loadConfiguration(manageFile);
        assertEquals(2, main.getInt("labels.item-display.max-enchants"));
        assertEquals(5, manage.getInt("items.claim.max-stack-lines"));
        assertEquals("{enchants} voor {item}", main.getString("labels.item-display.enchant-summary"));
        assertEquals(List.of(), manage.getStringList("items.claim.lore"));
        assertEquals("{item}: {amount}", manage.getString("items.claim.stack-line"));
        assertTrue(new File(mainFile.getParentFile(), "main.yml.pre-v32-backup").isFile());
        assertTrue(new File(manageFile.getParentFile(), "manage-order.yml.pre-v32-backup").isFile());
        byte[] mainFirst = Files.readAllBytes(mainFile.toPath());
        byte[] manageFirst = Files.readAllBytes(manageFile.toPath());
        assertTrue(GuiConfigManager.migratePreviewLimits(dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(Arrays.equals(mainFirst, Files.readAllBytes(mainFile.toPath())));
        assertTrue(Arrays.equals(manageFirst, Files.readAllBytes(manageFile.toPath())));

        main.set("labels.item-display.max-enchants", null);
        manage.set("items.claim.max-stack-lines", null);
        assertEquals(0, GuiConfigManager.previewLimit(main, "labels.item-display.max-enchants"));
        assertEquals(0, GuiConfigManager.previewLimit(manage, "items.claim.max-stack-lines"));
        main.set("labels.item-display.max-enchants", -1);
        assertThrows(IllegalStateException.class, () -> GuiConfigManager.requirePreviewLimits("main", main));
        manage.set("items.claim.max-stack-lines", "five");
        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.requirePreviewLimits("manage-order", manage));

        saveOldFile("main", "title: 'Mijn orders'\nsize: 54\nlabels:\n"
            + "  item-display:\n    enchant-level: '{enchant} {level}'\n");
        saveOldFile("manage-order", "title: 'Mijn bestelling'\nsize: 27\nitems:\n"
            + "  claim:\n    stack-line: '{amount} voorwerpen'\n");
        assertTrue(GuiConfigManager.migratePreviewLimits(dataFolder.toFile(), defaults, ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(mainFile).isSet("labels.item-display.enchant-summary"));
        assertFalse(YamlConfiguration.loadConfiguration(manageFile).isSet("items.claim.more-line"));

        saveOldFile("main", "title: 'Mijn orders'\nsize: 54\n");
        saveOldFile("manage-order", "title: 'Mijn bestelling'\nsize: 27\n");
        assertTrue(GuiConfigManager.migratePreviewLimits(dataFolder.toFile(), defaults, ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(mainFile).isSet("labels"));
        assertFalse(YamlConfiguration.loadConfiguration(manageFile).isSet("items"));
    }

    @Test
    void historyUnknownDetailsUpgradeKeepsTranslationsAndRemovedLabelFamily() throws IOException {
        File file = saveOldFile("history", "title: 'Mijn geschiedenis'\nsize: 54\nlabels:\n"
            + "  unknown-event: 'Onbekende actie'\n  unknown-time: 'Onbekende tijd'\n");
        Map<String, YamlConfiguration> defaults = Map.of("history", bundledGui("history"));
        assertTrue(GuiConfigManager.migrateHistoryUnknownDetails(
            dataFolder.toFile(), defaults, ignored -> {}));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals("Onbekende actie", migrated.getString("labels.unknown-event"));
        assertEquals("Onbekende tijd", migrated.getString("labels.unknown-time"));
        assertEquals("No details", migrated.getString("labels.unknown-details"));
        assertTrue(new File(file.getParentFile(), file.getName() + ".pre-v29-backup").isFile());
        byte[] first = Files.readAllBytes(file.toPath());
        assertTrue(GuiConfigManager.migrateHistoryUnknownDetails(
            dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(Arrays.equals(first, Files.readAllBytes(file.toPath())));

        File removedLabels = saveOldFile("history", "title: 'Mijn geschiedenis'\nsize: 54\n");
        assertTrue(GuiConfigManager.migrateHistoryUnknownDetails(
            dataFolder.toFile(), defaults, ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(removedLabels).isSet("labels"));
    }

    @Test
    void historyTabNameUpgradeKeepsCustomizedNamesAndAddsIndependentStates() throws IOException {
        File history = saveOldFile("history", "items:\n"
            + "  order-tab:\n    name: '{state_color}&lORDER HISTORY'\n"
            + "  deliver-tab:\n    name: 'Mijn leveringen'\n");
        YamlConfiguration defaults = bundledGui("history");
        assertTrue(GuiConfigManager.migrateHistoryTabNames(dataFolder.toFile(),
            Map.of("history", defaults), ignored -> {}));
        YamlConfiguration upgraded = YamlConfiguration.loadConfiguration(history);
        assertEquals("{state_name}", upgraded.getString("items.order-tab.name"));
        assertEquals("Mijn leveringen", upgraded.getString("items.deliver-tab.name"));
        assertEquals("{state_color}&lORDER HISTORY", upgraded.getString("items.order-tab.selected-name"));
        assertEquals("{state_color}&lORDER HISTORY", upgraded.getString("items.order-tab.unselected-name"));
        assertTrue(new File(history.getParentFile(), history.getName() + ".pre-v23-backup").isFile());
        byte[] once = Files.readAllBytes(history.toPath());
        assertTrue(GuiConfigManager.migrateHistoryTabNames(dataFolder.toFile(),
            Map.of("history", defaults), ignored -> {}));
        assertTrue(Arrays.equals(once, Files.readAllBytes(history.toPath())));

        upgraded.set("items.order-tab.selected-name", null);
        upgraded.save(history);
        assertFalse(YamlConfiguration.loadConfiguration(history).isSet("items.order-tab.selected-name"));
    }

    @Test
    void searchAndHistoryPresentationUpgradePreservesTranslationsAndRemovedEntry() throws IOException {
        File main = saveOldFile("main", "labels:\n  search-empty: 'Geen'\n"
            + "  search-current: 'Zoek: {search}'\n");
        File itemSelect = saveOldFile("item-select", "labels:\n  search-empty: 'Niets'\n");
        File history = saveOldFile("history", "items:\n  entry:\n"
            + "    name: '{action}'\n    order-material: DIAMOND\n");
        Map<String, YamlConfiguration> defaults = Map.of(
            "main", bundledGui("main"),
            "item-select", bundledGui("item-select"),
            "history", bundledGui("history"));

        assertTrue(GuiConfigManager.migrateSearchAndHistoryPresentation(
            dataFolder.toFile(), defaults, ignored -> {}));
        assertEquals("Zoek: {search}", YamlConfiguration.loadConfiguration(main)
            .getString("labels.search-current"));
        assertEquals("{theme}{search}", YamlConfiguration.loadConfiguration(itemSelect)
            .getString("labels.search-current"));
        YamlConfiguration upgradedHistory = YamlConfiguration.loadConfiguration(history);
        assertEquals("DIAMOND", upgradedHistory.getString("items.entry.order-material"));
        assertEquals("CHEST_MINECART", upgradedHistory.getString("items.entry.deliver-material"));
        byte[] once = Files.readAllBytes(history.toPath());
        assertTrue(GuiConfigManager.migrateSearchAndHistoryPresentation(
            dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(Arrays.equals(once, Files.readAllBytes(history.toPath())));

        upgradedHistory.set("items.entry.deliver-material", null);
        upgradedHistory.save(history);
        assertFalse(YamlConfiguration.loadConfiguration(history)
            .isSet("items.entry.deliver-material"));

        File removedEntry = saveOldFile("history", "items:\n  info:\n    name: 'Informasjon'\n");
        assertTrue(GuiConfigManager.migrateSearchAndHistoryPresentation(
            dataFolder.toFile(), defaults, ignored -> {}));
        assertFalse(YamlConfiguration.loadConfiguration(removedEntry).isSet("items.entry"));
    }

    @Test
    void itemFlagParserHonorsAllExplicitNoneAndSelectedFlags() {
        assertEquals(ItemFlag.values().length, GuiConfigManager.itemFlags("all").size());
        assertEquals(List.of(), GuiConfigManager.itemFlags(List.of()));
        assertEquals(List.of(ItemFlag.HIDE_ATTRIBUTES),
            GuiConfigManager.itemFlags(List.of("hide_attributes")));
        assertEquals(List.of(), GuiConfigManager.itemFlags(null));
        assertThrows(IllegalArgumentException.class,
            () -> GuiConfigManager.itemFlags(List.of("NOT_A_FLAG")));
    }

    @Test
    void removedRequiredAmountFailsValidationWithoutInsertingDefault() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("items.sort.name", "Sort");
        bundled.set("items.sort.amount", 1);
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("items.sort.name", "Orden");
        owner.set("items.sort.enabled", true);

        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.requireItemAmounts("guis/main.yml", owner, bundled));
        assertFalse(owner.isSet("items.sort.amount"));
        owner.set("items.sort.amount", 3);
        GuiConfigManager.requireItemAmounts("guis/main.yml", owner, bundled);
    }

    @Test
    void invalidVisualMetadataIsRejectedWithoutReplacingOwnerValues() {
        YamlConfiguration active = new YamlConfiguration();
        active.set("items.entry.material", "NOT_AN_ITEM");
        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.requireVisualMetadata("guis/item-select.yml", active, ignored -> true));
        assertEquals("NOT_AN_ITEM", active.getString("items.entry.material"));

        active.set("items.entry.material", "DIAMOND");
        active.set("items.entry.custom-model-data", 2.5);
        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.requireVisualMetadata("guis/item-select.yml", active, ignored -> true));
        active.set("items.entry.custom-model-data", "");
        GuiConfigManager.requireVisualMetadata("guis/item-select.yml", active, ignored -> true);
    }

    @Test
    void malformedOrCollidingContentSlotsFailBeforeTheMenuOpens() {
        YamlConfiguration active = new YamlConfiguration();
        active.set("size", 27);
        active.set("layout.content-slots", List.of(1, 1));
        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.requireLayoutSlots("guis/item-select.yml", active,
                Map.of("item-select.filter", 10)));
        active.set("layout.content-slots", List.of(1, 10));
        assertThrows(IllegalStateException.class,
            () -> GuiConfigManager.requireLayoutSlots("guis/item-select.yml", active,
                Map.of("item-select.filter", 10)));
        active.set("layout.content-slots", List.of(1, 2));
        GuiConfigManager.requireLayoutSlots("guis/item-select.yml", active,
            Map.of("item-select.filter", 10));
        active.set("layout.content-slots", List.of(1, 10));
        GuiConfigManager.requireLayoutSlots("guis/item-select.yml", active, Map.of());
        active.set("layout.content-slots", List.of());
        GuiConfigManager.requireLayoutSlots("guis/item-select.yml", active,
            Map.of("item-select.filter", 10));
    }

    @Test
    void quotedValidatedLayoutSlotsKeepTheirRenderedPositions() {
        YamlConfiguration active = new YamlConfiguration();
        active.set("size", 27);
        active.set("layout.content-slots", List.of(" 1 ", "2"));
        GuiConfigManager.requireLayoutSlots("guis/item-select.yml", active, Map.of());
        assertEquals(List.of(1, 2), GuiConfigManager.parseConfiguredSlots(
            active.getList("layout.content-slots", List.of())));
    }

    @Test
    void disabledOrMateriallessStaticItemCannotRenderOrKeepAction() {
        YamlConfiguration active = new YamlConfiguration();
        active.set("items.main.sort.enabled", true);
        active.set("items.main.sort.material", "CAULDRON");
        Set<String> staticMaterials = Set.of("items.main.sort.material");
        assertTrue(GuiConfigManager.itemEnabled(active, staticMaterials, "main.sort", ignored -> true));

        active.set("items.main.sort.material", null);
        assertFalse(GuiConfigManager.itemEnabled(active, staticMaterials, "main.sort", ignored -> true));
        active.set("items.main.sort.material", "CAULDRON");
        active.set("items.main.sort.enabled", null);
        assertFalse(GuiConfigManager.itemEnabled(active, staticMaterials, "main.sort", ignored -> true));
        active.set("items.main.sort.enabled", false);
        assertFalse(GuiConfigManager.itemEnabled(active, staticMaterials, "main.sort", ignored -> true));
    }

    @Test
    void disabledControlReleasesItsSlotForAnEnabledControl() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.left.name", "Left");
        defaults.set("items.left.slot", 10);
        defaults.set("items.right.name", "Right");
        defaults.set("items.right.slot", 11);
        YamlConfiguration active = new YamlConfiguration();
        active.set("size", 27);
        active.set("items.left.enabled", false);
        active.set("items.left.slot", 10);
        active.set("items.right.enabled", true);
        active.set("items.right.slot", 10);

        Map<String, Integer> slots = GuiConfigManager.resolveItemSlots(
            "new-order", active, defaults, ignored -> {}, true);
        assertFalse(slots.containsKey("new-order.left"));
        assertEquals(-1, GuiConfigManager.activeControlSlot(slots, "new-order.left"));
        assertEquals(10, slots.get("new-order.right"));
        assertEquals(10, GuiConfigManager.activeControlSlot(slots, "new-order.right"));
    }

    @Test
    void materialNameTranslationsAndCurrentSchemaDeletionAreFinal() throws IOException {
        YamlConfiguration bundled = bundledGui("material-names");
        YamlConfiguration owner = bundledGui("material-names");
        YamlConfiguration gui = new YamlConfiguration();
        assertEquals("Diamond", GuiConfigManager.rawMaterialName(gui, owner, bundled, Material.DIAMOND));

        owner.set("materials.DIAMOND", "Diamante");
        assertEquals("Diamante", GuiConfigManager.rawMaterialName(gui, owner, bundled, Material.DIAMOND));
        owner.set("materials.DIAMOND", null);
        assertEquals("", GuiConfigManager.rawMaterialName(gui, owner, bundled, Material.DIAMOND));

        gui.set("labels.item-select.material-names.DIAMOND", "Piedra preciosa");
        assertEquals("Piedra preciosa", GuiConfigManager.rawMaterialName(gui, owner, bundled, Material.DIAMOND));
    }

    @Test
    void everyKnownBundledMaterialNameHonorsTranslationAndRemoval() throws IOException {
        YamlConfiguration bundled = bundledGui("material-names");
        ConfigurationSection names = bundled.getConfigurationSection("materials");
        assertTrue(names != null && names.getKeys(false).size() > 1000);
        YamlConfiguration owner = bundledGui("material-names");
        YamlConfiguration gui = new YamlConfiguration();
        int checked = 0;
        for (String key : names.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                continue;
            }
            String path = "materials." + key;
            String translated = "ÖVERSATT_" + key;
            owner.set(path, translated);
            assertEquals(translated, GuiConfigManager.rawMaterialName(gui, owner, bundled, material), key);
            owner.set(path, null);
            assertEquals("", GuiConfigManager.rawMaterialName(gui, owner, bundled, material), key);
            owner.set(path, bundled.get(path));
            checked++;
        }
        assertTrue(checked > 1000, "Expected to exercise the bundled names known to this Bukkit API");
    }

    @Test
    void resizedMenuUsesConfiguredControlSlotInsteadOfOldHardcodedRow() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.filter.slot", 49);
        YamlConfiguration active = new YamlConfiguration();
        active.set("size", 27);
        active.set("items.filter.slot", 11);

        Map<String, Integer> resolved = GuiConfigManager.resolveItemSlots(
            "item-select", active, defaults, ignored -> {});

        assertEquals(11, resolved.get("item-select.filter"));
    }

    @Test
    void everyBundledPublicControlCanMoveWithoutKeepingItsOldActionSlot() throws IOException {
        int checked = 0;
        for (String menu : List.of("main", "your-orders", "new-order", "item-select",
            "enchant-select", "manage-order", "claim-order", "delivery-confirm", "history")) {
            YamlConfiguration bundled = bundledGui(menu);
            Map<String, Integer> initial = GuiConfigManager.resolveItemSlots(
                menu, bundled, bundled, ignored -> {}, true);
            int size = bundled.getInt("size");
            Set<Integer> content = new java.util.HashSet<>();
            content.addAll(bundled.getIntegerList("layout.content-slots"));
            content.addAll(bundled.getIntegerList("layout.order-slots"));
            for (Map.Entry<String, Integer> control : initial.entrySet()) {
                int movedSlot = -1;
                for (int candidate = 0; candidate < size; candidate++) {
                    if (!content.contains(candidate) && !initial.containsValue(candidate)) {
                        movedSlot = candidate;
                        break;
                    }
                }
                YamlConfiguration owner = bundledGui(menu);
                String itemKey = control.getKey().substring(menu.length() + 1);
                if (movedSlot < 0) {
                    for (int candidate : content) {
                        if (!initial.containsValue(candidate)) {
                            movedSlot = candidate;
                            break;
                        }
                    }
                    assertTrue(movedSlot >= 0, "No movable test slot in " + menu);
                    owner.set("items." + itemKey + ".slot", movedSlot);
                    Map<String, Integer> colliding = GuiConfigManager.resolveItemSlots(
                        menu, owner, bundled, ignored -> {}, true);
                    assertThrows(IllegalStateException.class,
                        () -> GuiConfigManager.requireLayoutSlots(menu + ".yml", owner, colliding));
                    String layoutPath = owner.getIntegerList("layout.order-slots").contains(movedSlot)
                        ? "layout.order-slots" : "layout.content-slots";
                    List<Integer> reducedContent = new ArrayList<>(owner.getIntegerList(layoutPath));
                    reducedContent.remove(Integer.valueOf(movedSlot));
                    owner.set(layoutPath, reducedContent);
                }
                owner.set("items." + itemKey + ".slot", movedSlot);
                Map<String, Integer> moved = GuiConfigManager.resolveItemSlots(
                    menu, owner, bundled, ignored -> {}, true);
                GuiConfigManager.requireLayoutSlots(menu + ".yml", owner, moved);
                assertEquals(movedSlot, GuiConfigManager.activeControlSlot(moved, control.getKey()),
                    control.getKey());
                assertFalse(control.getValue().equals(GuiConfigManager.activeControlSlot(moved, control.getKey())),
                    control.getKey() + " retained its old action slot");

                YamlConfiguration disabled = bundledGui(menu);
                String buttonKey = itemKey.equals("back-to-orders") ? "back" : itemKey;
                String enabledRoot = bundled.isSet("items." + itemKey + ".enabled")
                    ? "items." + itemKey : "buttons." + buttonKey;
                disabled.set(enabledRoot + ".enabled", false);
                Map<String, Integer> withoutControl = GuiConfigManager.resolveItemSlots(
                    menu, disabled, bundled, ignored -> {}, true);
                assertEquals(-1, GuiConfigManager.activeControlSlot(withoutControl, control.getKey()),
                    control.getKey() + " retained a disabled action slot");
                checked++;
            }
        }
        assertTrue(checked > 20, "Expected broad public control coverage");
    }

    @Test
    void everyBundledPublicMenuHasValidSizeAndNonConflictingControls() throws IOException {
        for (String name : List.of("main", "your-orders", "new-order", "item-select",
            "enchant-select", "manage-order", "claim-order", "deliver",
            "delivery-confirm", "history")) {
            YamlConfiguration bundled = bundledGui(name);
            int size = bundled.getInt("size", 0);
            assertTrue(size >= 9 && size <= 54 && size % 9 == 0, name);
            GuiConfigManager.resolveItemSlots(name, bundled, bundled, ignored -> {});
        }
    }

    @Test
    void bundledPublicControlsHaveNamesAndLoreEvenWhenOptional() throws IOException {
        for (String menu : List.of("main", "your-orders", "new-order", "item-select",
            "enchant-select", "manage-order", "claim-order", "deliver",
            "delivery-confirm", "history")) {
            YamlConfiguration bundled = bundledGui(menu);
            for (String family : List.of("items", "buttons")) {
                ConfigurationSection controls = bundled.getConfigurationSection(family);
                if (controls == null) {
                    continue;
                }
                for (String key : controls.getKeys(false)) {
                    String root = family + "." + key;
                    if (!bundled.isSet(root + ".enabled")
                        || menu.equals("main") && family.equals("items")
                            && (key.equals("gray-pane") || key.equals("empty-order-slot"))) {
                        continue;
                    }
                    assertTrue(bundled.isString(root + ".name")
                        && !bundled.getString(root + ".name", "").isBlank(), menu + ":" + root + ".name");
                    boolean hasLore = bundled.isList(root + ".lore")
                        && bundled.getStringList(root + ".lore").stream().anyMatch(line -> !line.isBlank());
                    ConfigurationSection loreParts = bundled.getConfigurationSection(root + ".lore");
                    hasLore |= loreParts != null && !loreParts.getKeys(true).isEmpty();
                    assertTrue(hasLore, menu + ":" + root + ".lore");
                }
            }
        }
    }

    @Test
    void optionalBackButtonsDoNotChangeTheReleasedInventoryLayout() throws IOException {
        for (String name : List.of("item-select", "enchant-select", "claim-order")) {
            YamlConfiguration bundled = bundledGui(name);
            assertFalse(bundled.getBoolean("buttons.back.enabled"), name);
            assertFalse(GuiConfigManager.resolveItemSlots(name, bundled, bundled,
                ignored -> {}, true).containsKey(name + ".back"), name);
        }
    }

    @Test
    void released46GuiFilesUpgradeWithoutRestoringDeletedOwnerPresentation() throws IOException {
        List<String> menus = List.of("main", "your-orders", "new-order", "item-select",
            "enchant-select", "manage-order", "claim-order", "deliver", "delivery-confirm", "history");
        Map<String, YamlConfiguration> defaults = new java.util.LinkedHashMap<>();
        for (String menu : menus) {
            try (InputStream stream = getClass().getResourceAsStream(
                "/fixtures/4.6/guis/" + menu + ".yml")) {
                assertTrue(stream != null, menu);
                Path path = dataFolder.resolve("guis").resolve(menu + ".yml");
                Files.createDirectories(path.getParent());
                Files.copy(stream, path);
            }
            defaults.put(menu, bundledGui(menu));
        }
        File mainFile = dataFolder.resolve("guis/main.yml").toFile();
        YamlConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
        main.set("title", "&8Pedidos");
        main.set("items.filter.name", "Filtro");
        main.set("items.filter.lore", List.of());
        main.set("items.refresh.material", null);
        main.set("buttons.search.name", "Buscar");
        main.save(mainFile);
        File historyFile = dataFolder.resolve("guis/history.yml").toFile();
        YamlConfiguration history = YamlConfiguration.loadConfiguration(historyFile);
        history.set("items.info.lore", List.of());
        history.set("items.entry", null);
        history.save(historyFile);
        File newOrderFile = dataFolder.resolve("guis/new-order.yml").toFile();
        YamlConfiguration newOrder = YamlConfiguration.loadConfiguration(newOrderFile);
        newOrder.set("items.confirm.lore", List.of());
        newOrder.save(newOrderFile);
        List<String> warnings = new ArrayList<>();

        assertTrue(GuiConfigManager.migrateLoreLayouts(dataFolder.toFile(), defaults, warnings::add));
        assertTrue(GuiConfigManager.migrateLayouts(dataFolder.toFile(), defaults, warnings::add));
        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(), defaults, warnings::add));
        assertTrue(GuiConfigManager.migrateSearchAndHistoryPresentation(dataFolder.toFile(),
            defaults, warnings::add));
        assertTrue(GuiConfigManager.migrateHistoryTabNames(dataFolder.toFile(), defaults, warnings::add));

        main = YamlConfiguration.loadConfiguration(mainFile);
        history = YamlConfiguration.loadConfiguration(historyFile);
        newOrder = YamlConfiguration.loadConfiguration(newOrderFile);
        assertEquals("&8Pedidos", main.getString("title"));
        assertEquals("Filtro", main.getString("items.filter.name"));
        assertEquals(List.of(), main.getStringList("items.filter.lore"));
        assertFalse(main.isSet("items.refresh.material"));
        assertEquals("Buscar", main.getString("buttons.search.name"));
        assertEquals(List.of(), history.getStringList("items.info.lore"));
        assertFalse(history.isSet("items.entry"));
        assertEquals(List.of(), newOrder.getStringList("items.confirm.lore"));
        assertTrue(main.isList("items.order-entry.lore-layout"));
        assertTrue(main.isList("layout.frame-slots"));
        assertTrue(main.isSet("items.filter.enabled"));
        assertTrue(history.isSet("items.order-tab.selected-name"));
        assertTrue(warnings.isEmpty());

        byte[] once = Files.readAllBytes(mainFile.toPath());
        assertTrue(GuiConfigManager.migrateLoreLayouts(dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(GuiConfigManager.migrateLayouts(dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(GuiConfigManager.migrateItemFlags(dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(GuiConfigManager.migrateSearchAndHistoryPresentation(dataFolder.toFile(),
            defaults, ignored -> {}));
        assertTrue(GuiConfigManager.migrateHistoryTabNames(dataFolder.toFile(), defaults, ignored -> {}));
        assertTrue(Arrays.equals(once, Files.readAllBytes(mainFile.toPath())));
    }

    @Test
    void released41To46GuiSchemasPreserveOwnerTextAndAbsenceAcrossEveryStep() throws IOException {
        List<String> menus = List.of("main", "your-orders", "new-order", "item-select",
            "enchant-select", "manage-order", "claim-order", "deliver", "delivery-confirm", "history");
        Map<String, YamlConfiguration> defaults = new java.util.LinkedHashMap<>();
        for (String menu : menus) {
            defaults.put(menu, bundledGui(menu));
        }
        for (String sourceVersion : List.of("4.1", "4.4", "4.5", "4.6")) {
            Path install = dataFolder.resolve("from-" + sourceVersion);
            for (String menu : menus) {
                try (InputStream stream = getClass().getResourceAsStream(
                    "/fixtures/" + sourceVersion + "/guis/" + menu + ".yml")) {
                    assertTrue(stream != null, sourceVersion + "/" + menu);
                    Path path = install.resolve("guis").resolve(menu + ".yml");
                    Files.createDirectories(path.getParent());
                    Files.copy(stream, path);
                }
            }
            File mainFile = install.resolve("guis/main.yml").toFile();
            YamlConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
            main.set("items.filter.name", "Filtro " + sourceVersion);
            main.set("items.refresh.lore", List.of());
            main.set("buttons.search.name", "Buscar");
            main.set("items.history.material", null);
            main.save(mainFile);
            File manageFile = install.resolve("guis/manage-order.yml").toFile();
            YamlConfiguration manage = YamlConfiguration.loadConfiguration(manageFile);
            manage.set("items.claim.lore", List.of());
            manage.set("items.claim.stack-line", "{amount} voorwerpen");
            manage.save(manageFile);
            File historyFile = install.resolve("guis/history.yml").toFile();
            YamlConfiguration history = YamlConfiguration.loadConfiguration(historyFile);
            history.set("items.info.lore", List.of());
            history.save(historyFile);
            List<String> warnings = new ArrayList<>();

            if (!sourceVersion.equals("4.5") && !sourceVersion.equals("4.6")) {
                assertTrue(GuiConfigManager.migrateNewBackSlots(install.toFile(), warnings::add));
            }
            assertTrue(GuiConfigManager.migratePublicTextTemplates(install.toFile(), defaults, warnings::add));
            assertTrue(GuiConfigManager.migrateLoreLayouts(install.toFile(), defaults, warnings::add));
            assertTrue(GuiConfigManager.migrateLayouts(install.toFile(), defaults, warnings::add));
            assertTrue(GuiConfigManager.migrateItemFlags(install.toFile(), defaults, warnings::add));
            assertTrue(GuiConfigManager.migrateSearchAndHistoryPresentation(install.toFile(),
                defaults, warnings::add));
            assertTrue(GuiConfigManager.migrateHistoryTabNames(install.toFile(), defaults, warnings::add));
            assertTrue(GuiConfigManager.migratePreviewLimits(install.toFile(), defaults, warnings::add));

            main = YamlConfiguration.loadConfiguration(mainFile);
            history = YamlConfiguration.loadConfiguration(historyFile);
            manage = YamlConfiguration.loadConfiguration(manageFile);
            assertEquals("Filtro " + sourceVersion, main.getString("items.filter.name"));
            assertEquals(List.of(), main.getStringList("items.refresh.lore"));
            assertEquals("Buscar", main.getString("buttons.search.name"));
            assertFalse(main.isSet("items.history.material"));
            assertEquals(List.of(), history.getStringList("items.info.lore"));
            assertTrue(main.isSet("items.order-entry.lore.admin-cancel"));
            assertTrue(main.isList("items.order-entry.lore-layout"));
            assertTrue(main.isSet("items.filter.enabled"));
            assertTrue(history.isSet("items.order-tab.selected-name"));
            assertEquals(2, main.getInt("labels.item-display.max-enchants"));
            assertEquals(5, manage.getInt("items.claim.max-stack-lines"));
            assertEquals(List.of(), manage.getStringList("items.claim.lore"));
            assertEquals("{amount} voorwerpen", manage.getString("items.claim.stack-line"));
            assertTrue(warnings.isEmpty(), sourceVersion + ": " + warnings);
        }
    }

    private File saveOldFile(String name, String contents) throws IOException {
        Path path = dataFolder.resolve("guis").resolve(name + ".yml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
        return path.toFile();
    }

    private YamlConfiguration bundledGui(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(
            "/migrations/legacy-public-defaults/current/guis/" + name + ".yml")) {
            if (stream == null) {
                throw new IOException("Missing bundled GUI " + name);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private Set<String> legacyFieldInventory(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/migrations/" + name)) {
            assertTrue(stream != null);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(java.util.stream.Collectors.toSet());
        }
    }
}
