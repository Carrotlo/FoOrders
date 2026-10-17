package me.foesio.foOrders.dialog;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogTextPresentationTest {
    @Test
    void allBundledTextInputsHaveNativeAndChatPresentation() throws Exception {
        for (String id : List.of("amount", "money", "search")) {
            DialogTextPresentation presentation = DialogTextPresentation.load(bundled(id));
            assertTrue(presentation.nativeReady(), id);
            assertTrue(presentation.fallbackReady(), id);
            assertEquals(2, presentation.columns(), id);
        }
    }

    @Test
    void changingTargetLabelReachesBundledNativeFieldAndChatPrompt() throws Exception {
        for (String id : List.of("amount", "money", "search")) {
            DialogTextPresentation loaded = DialogTextPresentation.load(bundled(id));
            DialogTextPresentation rendered = loaded.render(Map.of("target", "Ziel-" + id));
            assertEquals("#03fc88Ziel-" + id, rendered.fieldLabel());
            assertEquals("{theme}Ziel-" + id, rendered.fallbackLines().get(0));
        }
    }

    @Test
    void ownerMutationAndRemovalNeverRegenerateButtonTextOrIcons() throws Exception {
        YamlConfiguration config = bundled("search");
        config.set("title", "Buscar {target}");
        config.set("title-icon", "");
        config.set("confirm-button.label", "Enviar");
        config.set("confirm-button.icon", "");
        config.set("confirm-button.tooltip", null);
        config.set("placeholder", "voorbeeld");
        config.set("body", "Voorbeeld: {placeholder}");
        config.set("fallback.cancel-keyword", "salir");
        config.set("fallback.lines", List.of("Texto {current}", "Cancelar: {cancel_keyword}"));
        DialogTextPresentation rendered = DialogTextPresentation.load(config).render(Map.of(
            "target", "pedidos", "current", "diamante", "cancel_keyword", "salir",
            "placeholder", config.getString("placeholder"), "theme", ""));

        assertTrue(rendered.nativeReady());
        assertEquals("Buscar pedidos", rendered.title());
        assertEquals("", rendered.titleIcon());
        assertEquals("Enviar", rendered.confirm().label());
        assertEquals("", rendered.confirm().icon());
        assertEquals("", rendered.confirm().tooltip());
        assertEquals(List.of("Voorbeeld: voorbeeld"), rendered.body());
        assertEquals(List.of("Texto diamante", "Cancelar: salir"), rendered.fallbackLines());

        config.set("confirm-button.label", null);
        assertFalse(DialogTextPresentation.load(config).nativeReady());
    }

    @Test
    void malformedNativeGeometryOrFlagsCannotSilentlyChangePresentation() throws Exception {
        YamlConfiguration config = bundled("amount");
        config.set("label-visible", "not-a-boolean");
        assertFalse(DialogTextPresentation.load(config).nativeReady());

        config = bundled("amount");
        config.set("input-width", "300");
        assertFalse(DialogTextPresentation.load(config).nativeReady());

        config = bundled("amount");
        config.set("columns", 1);
        assertTrue(DialogTextPresentation.load(config).nativeReady());
        assertEquals(1, DialogTextPresentation.load(config).columns());
        config.set("columns", null);
        assertFalse(DialogTextPresentation.load(config).nativeReady());
        config.set("columns", 3);
        assertFalse(DialogTextPresentation.load(config).nativeReady());
    }

    @Test
    void ownerMayRemoveCancelWhenEscapeStillProvidesAnExit() throws Exception {
        YamlConfiguration config = bundled("search");
        config.set("button-order", List.of("confirm"));
        config.set("cancel-button", null);
        DialogTextPresentation noCancel = DialogTextPresentation.load(config);
        assertTrue(noCancel.nativeReady());
        assertEquals(List.of("confirm"), noCancel.buttonOrder());
        assertEquals("", noCancel.cancel().label());

        config.set("can-close-with-escape", false);
        assertFalse(DialogTextPresentation.load(config).nativeReady());
        config.set("can-close-with-escape", true);
        config.set("button-order", List.of("cancel"));
        assertFalse(DialogTextPresentation.load(config).nativeReady());
        config.set("button-order", List.of("confirm", "confirm"));
        assertFalse(DialogTextPresentation.load(config).nativeReady());
        config.set("button-order", List.of("confirm", "unknown"));
        assertFalse(DialogTextPresentation.load(config).nativeReady());

        config = bundled("search");
        config.set("title", "");
        config.set("field-label", "");
        config.set("confirm-button.label", "");
        assertTrue(DialogTextPresentation.load(config).nativeReady());
        config.set("confirm-button.label", null);
        assertFalse(DialogTextPresentation.load(config).nativeReady());
    }

    @Test
    void blankRenderedChatCancelKeywordCannotBecomeCoreEnglishDefault() throws Exception {
        YamlConfiguration config = bundled("search");
        config.set("fallback.cancel-keyword", "{current}");
        DialogTextPresentation loaded = DialogTextPresentation.load(config);
        assertTrue(loaded.fallbackReady());

        DialogTextPresentation blank = loaded.render(Map.of("current", ""));
        assertEquals("", blank.fallbackCancelKeyword());
        assertFalse(blank.fallbackReady());

        DialogTextPresentation translated = loaded.render(Map.of("current", "annuleren"));
        assertTrue(translated.fallbackReady());
        assertEquals("annuleren", translated.fallbackCancelKeyword());
    }

    @Test
    void searchFallbackShowsTheActualClearKeywordAndHidesTheHintWhenRemoved() throws Exception {
        YamlConfiguration config = bundled("search");
        config.set("clear-keyword", "wissen");
        DialogTextPresentation translated = DialogTextPresentation.load(config).render(Map.of());
        assertTrue(translated.fallbackLines().stream().anyMatch(line -> line.contains("wissen")));
        assertEquals("wissen", translated.clearKeyword());

        config.set("clear-keyword", "");
        DialogTextPresentation noClear = DialogTextPresentation.load(config).render(Map.of());
        assertFalse(noClear.fallbackLines().stream().anyMatch(line -> line.contains("to clear")));
        assertEquals("", noClear.clearKeyword());

        config.set("clear-keyword", "&c Wissen ");
        config.set("fallback.cancel-keyword", "wissen");
        DialogTextPresentation conflict = DialogTextPresentation.load(config).render(Map.of());
        assertEquals("Wissen", conflict.clearKeyword());
        assertEquals("wissen", conflict.fallbackCancelKeyword());
        assertFalse(conflict.fallbackReady());

        config.set("fallback.cancel-keyword", "&cannuleren");
        DialogTextPresentation distinct = DialogTextPresentation.load(config).render(Map.of());
        assertEquals("annuleren", distinct.fallbackCancelKeyword());
        assertTrue(distinct.fallbackReady());
    }

    @Test
    void playerSearchTextIsNotReprocessedAsAnOwnerPlaceholder() throws Exception {
        YamlConfiguration config = bundled("search");
        config.set("title", "Current {current} / target {target}");
        config.set("fallback.cancel-keyword", "stop-{target}");
        config.set("fallback.lines", List.of("Query {current}", "Cancel {cancel_keyword}"));

        DialogTextPresentation rendered = DialogTextPresentation.load(config).render(Map.of(
            "current", "{target} %theme%", "target", "Zoeken", "theme", "GREEN"));

        assertEquals("Current {target} %theme% / target Zoeken", rendered.title());
        assertEquals("{target} %theme%", rendered.initialValue());
        assertEquals(List.of("Query {target} %theme%", "Cancel stop-Zoeken"), rendered.fallbackLines());
        assertEquals("stop-Zoeken", rendered.fallbackCancelKeyword());
    }

    private YamlConfiguration bundled(String id) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/dialogs/" + id + ".yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
