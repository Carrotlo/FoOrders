package me.foesio.foOrders;

import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiFrame;
import me.foesio.core.gui.publicgui.PublicGuiPlanner;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiSchema;
import me.foesio.core.gui.publicgui.GuiSnapshot;
import me.foesio.core.gui.publicgui.ClickKind;
import me.foesio.core.gui.publicgui.PublicGuiText;
import me.foesio.foOrders.config.GuiConfigManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OrdersPublicGuisTest {
    private static final Pattern SPRITE = Pattern.compile(":([a-z0-9_]+):");
    private static final List<String> SCREENS = List.of("main", "your-orders", "new-order",
        "item-select", "enchant-select", "manage-order", "claim-order", "deliver",
        "delivery-confirm", "history");

    @Test
    void everyBundledPublicScreenParsesWithItsRuntimeContract() throws Exception {
        for (String screen : SCREENS) {
            String resource = "guis/" + screen + ".yml";
            try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
                PublicGuiDefinition definition = PublicGuiSchema.parse("orders." + screen,
                    resource, yaml, OrdersPublicGuis.contractFor(screen));
                assertTrue(definition.rows() > 0, screen);
                if (screen.equals("claim-order")) {
                    assertFalse(definition.buttons().containsKey("back"));
                    assertFalse(definition.entries().containsKey("back"));
                }
            }
        }
    }

    @Test
    void namedFixedDefaultItemsHaveOneVerifiedEditableSprite() throws Exception {
        for (String screen : SCREENS) {
            YamlConfiguration yaml = bundled(screen);
            assertFixedSprites(yaml, screen);
        }

        YamlConfiguration main = bundled("main");
        main.set("buttons.sort.name", "{theme}&lSORT");
        PublicGuiDefinition definition = PublicGuiSchema.parse("orders.main", "guis/main.yml",
            main, OrdersPublicGuis.contractFor("main"));
        assertEquals("{theme}&lSORT", definition.buttons().get("sort").item().name());
        assertEquals("{theme}&lSORT", mainFrame(definition, false, false)
            .visuals().get(47).fixed().name());
    }

    private static void assertFixedSprites(ConfigurationSection section, String path) {
        if (section.isString("material") && section.isString("name")) {
            String name = section.getString("name", "");
            if (!name.isBlank()) {
                Matcher matcher = SPRITE.matcher(name);
                assertTrue(matcher.find(), path + ": missing sprite in " + name);
                String sprite = matcher.group();
                Material spriteMaterial = Material.matchMaterial(matcher.group(1));
                assertNotNull(spriteMaterial, path + ": unknown sprite material " + sprite);
                assertEquals(sprite, DialogIcons.token(spriteMaterial), path + ": unsupported sprite " + sprite);
                assertFalse(matcher.find(), path + ": duplicate sprite in " + name);
            }
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                assertFixedSprites(child, path + "." + key);
            }
        }
    }

    @Test
    void ownerTitleLoreAndMovedControlResolveAtTheSameSlotAsItsAction() throws Exception {
        YamlConfiguration yaml = bundled("main");
        yaml.set("title", "&8Aufträge ✓ {page}");
        yaml.set("buttons.search.name", "&aSuchen ✓");
        yaml.set("buttons.search.lore", List.of("&7Suchen {current} ✓"));
        yaml.set("buttons.search.slot", 46);
        List<Integer> frameSlots = new java.util.ArrayList<>(yaml.getIntegerList("decorations.frame.slots"));
        frameSlots.remove(Integer.valueOf(46));
        yaml.set("decorations.frame.slots", frameSlots);
        PublicGuiDefinition definition = PublicGuiSchema.parse("orders.main", "guis/main.yml",
            yaml, OrdersPublicGuis.contractFor("main"));

        PublicGuiFrame frame = PublicGuiPlanner.plan(new GuiSnapshot<>(1,
            Map.of("orders.main", definition)), "orders.main",
            new PublicGuiRenderInput(Map.of("page", "3", "current", "Diamant"),
                Map.of(), Map.of("sort", new PublicGuiText.CycleSelection(List.of("A-Z"), 0),
                    "filter", new PublicGuiText.CycleSelection(List.of("All"), 0)), Map.of()));

        assertEquals("&8Aufträge ✓ 3", frame.title());
        assertEquals("&aSuchen ✓", frame.visuals().get(46).fixed().name());
        assertEquals(List.of("&7Suchen {current} ✓"), frame.visuals().get(46).fixed().lore());
        assertEquals("&7Suchen Diamant ✓", PublicGuiText.scalar(
            frame.visuals().get(46).fixed().lore().get(0),
            frame.visuals().get(46).placeholders(), "buttons.search.lore[0]"));
        assertEquals("orders.main.search", frame.bindings().get(46).actionFor(ClickKind.LEFT));
        assertFalse(frame.bindings().containsKey(50));
    }

    @Test
    void mainPageArrowsBecomeConfiguredInertPanesOnlyAtPageBoundaries() throws Exception {
        YamlConfiguration yaml = bundled("main");
        PublicGuiDefinition definition = PublicGuiSchema.parse("orders.main", "guis/main.yml",
            yaml, OrdersPublicGuis.contractFor("main"));

        PublicGuiFrame singlePage = mainFrame(definition, false, false);
        for (int slot : List.of(45, 53)) {
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, singlePage.visuals().get(slot).fixed().material());
            assertEquals(" ", singlePage.visuals().get(slot).fixed().name());
            assertEquals(List.of(), singlePage.visuals().get(slot).fixed().lore());
            assertFalse(singlePage.bindings().containsKey(slot));
        }

        PublicGuiFrame firstPage = mainFrame(definition, false, true);
        assertFalse(firstPage.bindings().containsKey(45));
        assertEquals(Material.ARROW, firstPage.visuals().get(53).fixed().material());
        assertEquals("orders.main.next-page", firstPage.bindings().get(53).actionFor(ClickKind.LEFT));

        PublicGuiFrame lastPage = mainFrame(definition, true, false);
        assertEquals(Material.ARROW, lastPage.visuals().get(45).fixed().material());
        assertEquals("orders.main.previous-page", lastPage.bindings().get(45).actionFor(ClickKind.LEFT));
        assertFalse(lastPage.bindings().containsKey(53));

        yaml.set("entries.previous-page.empty-item.material", "YELLOW_STAINED_GLASS_PANE");
        yaml.set("entries.previous-page.empty-item.name", "&eUnavailable");
        definition = PublicGuiSchema.parse("orders.main", "guis/main.yml",
            yaml, OrdersPublicGuis.contractFor("main"));
        PublicGuiFrame customized = mainFrame(definition, false, false);
        assertEquals(Material.YELLOW_STAINED_GLASS_PANE, customized.visuals().get(45).fixed().material());
        assertEquals("&eUnavailable", customized.visuals().get(45).fixed().name());
        assertFalse(customized.bindings().containsKey(45));
    }

    @Test
    void mainDecorationsAndEmptyOrderSlotsKeepEditableBlankNamesAndLore() throws Exception {
        YamlConfiguration yaml = bundled("main");
        PublicGuiDefinition definition = PublicGuiSchema.parse("orders.main", "guis/main.yml",
            yaml, OrdersPublicGuis.contractFor("main"));
        PublicGuiFrame frame = mainFrame(definition, false, false);

        for (int slot : List.of(0, 10)) {
            assertEquals(" ", frame.visuals().get(slot).fixed().name());
            assertEquals(List.of(), frame.visuals().get(slot).fixed().lore());
            assertFalse(frame.bindings().containsKey(slot));
        }

        yaml.set("decorations.frame.name", "&aFrame test");
        yaml.set("decorations.frame.lore", List.of("&7Frame lore"));
        yaml.set("entries.order-entry.empty-item.name", "&bEmpty test");
        yaml.set("entries.order-entry.empty-item.lore", List.of("&7Empty lore"));
        definition = PublicGuiSchema.parse("orders.main", "guis/main.yml",
            yaml, OrdersPublicGuis.contractFor("main"));
        frame = mainFrame(definition, false, false);

        assertEquals("&aFrame test", frame.visuals().get(0).fixed().name());
        assertEquals(List.of("&7Frame lore"), frame.visuals().get(0).fixed().lore());
        assertEquals("&bEmpty test", frame.visuals().get(10).fixed().name());
        assertEquals(List.of("&7Empty lore"), frame.visuals().get(10).fixed().lore());
    }

    private static PublicGuiFrame mainFrame(PublicGuiDefinition definition, boolean previous, boolean next) {
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = Map.of(
            "previous-page", previous ? Map.of(45,
                new PublicGuiRenderInput.EntryValue("previous", null, Map.of())) : Map.of(),
            "next-page", next ? Map.of(53,
                new PublicGuiRenderInput.EntryValue("next", null, Map.of())) : Map.of());
        return PublicGuiPlanner.plan(new GuiSnapshot<>(1, Map.of("orders.main", definition)),
            "orders.main", new PublicGuiRenderInput(Map.of("page", "1", "max_page", "2"),
                Map.of(), Map.of("sort", new PublicGuiText.CycleSelection(List.of("A-Z"), 0),
                    "filter", new PublicGuiText.CycleSelection(List.of("All"), 0)), entries));
    }

    @Test
    void sourceEntryVisibilityDoesNotRequireAnOptionalMaterialOverride() throws Exception {
        YamlConfiguration history = bundled("history");
        history.set("entries.entry.source-item.lore-states.order.material", null);
        PublicGuiDefinition definition = PublicGuiSchema.parse("orders.history", "guis/history.yml",
            history, OrdersPublicGuis.contractFor("history"));
        assertTrue(GuiConfigManager.sourceEntryStateEnabled(definition, "entry", "order"));
        assertNull(definition.entries().get("entry").sourceItem().forState("order").materialOverride());

        history.set("entries.entry.source-item.lore-states.order.enabled", false);
        definition = PublicGuiSchema.parse("orders.history", "guis/history.yml",
            history, OrdersPublicGuis.contractFor("history"));
        assertFalse(GuiConfigManager.sourceEntryStateEnabled(definition, "entry", "order"));
        assertTrue(GuiConfigManager.sourceEntryStateEnabled(definition, "entry", "deliver"));

        YamlConfiguration enchant = bundled("enchant-select");
        enchant.set("entries.entry.source-item.lore-states.selected.material", null);
        definition = PublicGuiSchema.parse("orders.enchant-select", "guis/enchant-select.yml",
            enchant, OrdersPublicGuis.contractFor("enchant-select"));
        assertTrue(GuiConfigManager.sourceEntryStateEnabled(definition, "entry", "selected"));

        YamlConfiguration itemSelect = bundled("item-select");
        itemSelect.set("entries.entry.source-item.lore-states.custom.enabled", false);
        definition = PublicGuiSchema.parse("orders.item-select", "guis/item-select.yml",
            itemSelect, OrdersPublicGuis.contractFor("item-select"));
        assertTrue(GuiConfigManager.sourceEntryStateEnabled(definition, "entry", "vanilla"));
        assertFalse(GuiConfigManager.sourceEntryStateEnabled(definition, "entry", "custom"));
    }

    private static YamlConfiguration bundled(String screen) throws Exception {
        String resource = "guis/" + screen + ".yml";
        try (InputStream stream = OrdersPublicGuisTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return yaml;
        }
    }
}
