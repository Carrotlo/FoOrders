package me.foesio.foOrders.config;

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
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiConfigManagerTest {
    @TempDir
    Path dataFolder;

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
    void overlappingControlSlotsKeepBothControlsReachable() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.filter.slot", 48);
        defaults.set("items.refresh.slot", 49);
        YamlConfiguration active = new YamlConfiguration();
        active.set("items.filter.slot", 49);
        List<String> warnings = new ArrayList<>();

        Map<String, Integer> resolved = GuiConfigManager.resolveItemSlots("main", active, defaults, warnings::add);

        assertEquals(49, resolved.get("main.refresh"));
        assertEquals(48, resolved.get("main.filter"));
        assertFalse(warnings.isEmpty());
    }

    @Test
    void contentSlotCannotHideItemSelectionFilter() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("items.filter.slot", 49);
        YamlConfiguration active = new YamlConfiguration();
        active.set("items.filter.slot", 10);

        Map<String, Integer> resolved = GuiConfigManager.resolveItemSlots("item-select", active, defaults, ignored -> {});

        assertEquals(49, resolved.get("item-select.filter"));
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
        List<String> configured = List.of("Own heading", "{options}", "Own footer");
        assertEquals(List.of("Own heading", "First", "Second", "Own footer"),
            GuiConfigManager.expandLore(configured, Map.of("{options}", List.of("First", "Second"))));
        assertEquals(List.of("Own heading", "Own footer"),
            GuiConfigManager.expandLore(configured, Map.of("{options}", List.of())));
        assertEquals(configured, GuiConfigManager.expandLore(configured, Map.of()));
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

    private File saveOldFile(String name, String contents) throws IOException {
        Path path = dataFolder.resolve("guis").resolve(name + ".yml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
        return path.toFile();
    }

    private YamlConfiguration bundledGui(String name) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/guis/" + name + ".yml")) {
            if (stream == null) {
                throw new IOException("Missing bundled GUI " + name);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
