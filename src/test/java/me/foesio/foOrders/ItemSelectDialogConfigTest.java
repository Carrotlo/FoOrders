package me.foesio.foOrders;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemSelectDialogConfigTest {
    @Test
    void bundledPickerHasCompleteOwnerPresentation() throws Exception {
        ItemSelectDialogConfig config = ItemSelectDialogConfig.load(bundled());
        assertTrue(config.enabled());
        assertEquals(3, config.columns());
        assertEquals(45, config.pageSize());
        assertEquals(List.of("search", "sort", "filter", "previous", "choices", "next"), config.buttonOrder());
        assertEquals(64, config.searchInputMaxLength());
        assertEquals(":{material}: {color}{item} ({custom_id})", config.customChoiceLabel());
    }

    @Test
    void translatedAndRemovedFieldsDoNotRegenerateEnglishPicker() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("title", "Elegir objeto");
        owner.set("choice-button.label", "{item} {color}");
        owner.set("choice-button.current-tooltip", "Actual");
        owner.set("empty-message", "");
        owner.set("sort-button.label", "Orden: {option}");
        owner.set("button-order", List.of("choices", "search", "sort", "filter", "next", "previous"));
        ItemSelectDialogConfig translated = ItemSelectDialogConfig.load(owner);
        assertEquals("Elegir objeto", translated.title());
        assertEquals("{item} {color}", translated.choiceLabel());
        assertEquals("Actual", translated.currentChoiceTooltip());
        assertEquals("", translated.emptyMessage());
        assertEquals("Orden: {option}", translated.sortButton().label());
        assertEquals("choices", translated.buttonOrder().getFirst());

        owner.set("choice-button.current-tooltip", null);
        owner.set("empty-message", null);
        owner.set("sort-button.tooltip", null);
        assertTrue(ItemSelectDialogConfig.load(owner).enabled());
        assertEquals("", ItemSelectDialogConfig.load(owner).currentChoiceTooltip());
        assertEquals("", ItemSelectDialogConfig.load(owner).emptyMessage());
        assertEquals("", ItemSelectDialogConfig.load(owner).sortButton().tooltip());

        owner.set("button-order", List.of("choices", "previous", "next"));
        owner.set("search-input.label", null);
        owner.set("search-input.width", null);
        owner.set("search-button.label", null);
        owner.set("sort-button.label", null);
        owner.set("filter-button.label", null);
        assertTrue(ItemSelectDialogConfig.load(owner).enabled());
        assertEquals(List.of("choices", "previous", "next"), ItemSelectDialogConfig.load(owner).buttonOrder());

        owner.set("button-order", List.of("choices"));
        owner.set("previous-button.label", null);
        owner.set("next-button.label", null);
        ItemSelectDialogConfig noPages = ItemSelectDialogConfig.load(owner);
        assertTrue(noPages.enabled());
        assertTrue(noPages.canReachChoiceCount(45));
        assertFalse(noPages.canReachChoiceCount(46));

        owner.set("button-order", List.of("choices", "next"));
        assertFalse(ItemSelectDialogConfig.load(owner).enabled());
        owner.set("button-order", List.of("search", "choices"));
        assertFalse(ItemSelectDialogConfig.load(owner).enabled());
        owner.set("search-input.label", "Search");
        owner.set("search-input.width", 300);
        owner.set("search-button.label", "Search");
        assertTrue(ItemSelectDialogConfig.load(owner).enabled());

        owner.set("button-order", List.of("search", "sort", "filter", "previous", "choices", "next"));
        owner.set("sort-button.label", "Orden: {option}");
        owner.set("filter-button.label", "Filter: {option}");
        owner.set("previous-button.label", "Previous");
        owner.set("next-button.label", "Next");
        owner.set("choice-button.current-tooltip", "Actual");
        owner.set("search-input.width", null);
        assertFalse(ItemSelectDialogConfig.load(owner).enabled());
        owner.set("search-input.width", 300);
        owner.set("button-order", List.of("choices", "choices", "sort", "filter", "next", "previous"));
        assertFalse(ItemSelectDialogConfig.load(owner).enabled());
        owner.set("button-order", List.of("search", "sort", "filter", "previous", "choices", "next"));
        owner.set("preview.show-tooltip", "invalid");
        assertFalse(ItemSelectDialogConfig.load(owner).enabled());
    }

    @Test
    void omittedOptionalPickerDecorationAndControlsStayRemoved() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("empty-message", null);
        owner.set("search-button.tooltip", null);
        owner.set("choice-button.current-tooltip", null);
        owner.set("choice-button.other-tooltip", null);
        owner.set("button-order", List.of("choices", "previous", "next"));
        owner.set("search-input", null);
        owner.set("search-button", null);
        owner.set("sort-button", null);
        owner.set("filter-button", null);

        ItemSelectDialogConfig config = ItemSelectDialogConfig.load(owner);
        assertTrue(config.enabled());
        assertEquals(List.of("choices", "previous", "next"), config.buttonOrder());
        assertEquals("", config.emptyMessage());
        assertEquals("", config.currentChoiceTooltip());
        assertEquals("", config.otherChoiceTooltip());
        assertTrue(config.canReachChoiceCount(100));
    }

    @Test
    void pickerCannotShowAnUnclosableEmptyResult() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("can-close-with-escape", false);
        ItemSelectDialogConfig noEscape = ItemSelectDialogConfig.load(owner);
        assertTrue(noEscape.enabled());
        assertFalse(noEscape.canReachChoiceCount(0));
        assertTrue(noEscape.canReachChoiceCount(1));

        owner.set("button-order", List.of("choices"));
        ItemSelectDialogConfig noEscapeOrPages = ItemSelectDialogConfig.load(owner);
        assertTrue(noEscapeOrPages.canReachChoiceCount(45));
        assertFalse(noEscapeOrPages.canReachChoiceCount(46));
    }

    private YamlConfiguration bundled() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/dialogs/item-select.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
