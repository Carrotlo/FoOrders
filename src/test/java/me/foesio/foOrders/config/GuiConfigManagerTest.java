package me.foesio.foOrders.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private File saveOldFile(String name, String contents) throws IOException {
        Path path = dataFolder.resolve("guis").resolve(name + ".yml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
        return path.toFile();
    }
}
