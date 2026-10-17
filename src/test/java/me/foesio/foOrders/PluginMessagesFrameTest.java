package me.foesio.foOrders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginMessagesFrameTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void animationFramesAreWholeOwnerTemplatesAndRemovalMeansSilence() {
        List<String> translated = List.of("Entregando·", "Entregando··", "Entregando···");
        assertEquals("Entregando·", PluginMessages.selectFrame(translated, 0));
        assertEquals("Entregando···", PluginMessages.selectFrame(translated, 2));
        assertEquals("Entregando·", PluginMessages.selectFrame(translated, 3));
        assertEquals("", PluginMessages.selectFrame(List.of(), 1));
    }

    @Test
    void oldSpriteUpgradeTouchesOnlyExactReleasedDefault() {
        YamlConfiguration active = new YamlConfiguration();
        active.set("orders.cancelled", "{prefix}§cBestelling geannuleerd.");
        assertFalse(PluginMessages.addSpriteIfOldDefault(active, "orders.cancelled", ":redstone:", "{prefix}"));
        assertEquals("{prefix}§cBestelling geannuleerd.", active.getString("orders.cancelled"));

        active.set("orders.cancelled", null);
        assertFalse(PluginMessages.addSpriteIfOldDefault(active, "orders.cancelled", ":redstone:", "{prefix}"));
        assertFalse(active.isSet("orders.cancelled"));

        active.set("orders.cancelled", "{prefix}#ff5d73Order was cancelled.");
        assertTrue(PluginMessages.addSpriteIfOldDefault(active, "orders.cancelled", ":redstone:", "{prefix}"));
        assertEquals("{prefix}:redstone: #ff5d73Order was cancelled.", active.getString("orders.cancelled"));
        assertFalse(PluginMessages.addSpriteIfOldDefault(active, "orders.cancelled", ":redstone:", "{prefix}"));

        active.set("prefix", "{theme}FoOrders &8» #a7b8b0");
        assertTrue(PluginMessages.addSpriteIfOldDefault(active, "prefix", ":chest_minecart:", null));
        assertEquals(":chest_minecart: {theme}FoOrders &8» #a7b8b0", active.getString("prefix"));
    }

    @Test
    void ownerStyleTokensKeepTranslationAndDoNotRestoreRemovedCoreDefaults() {
        YamlConfiguration active = new YamlConfiguration();
        active.set("tokens.theme", "#112233");
        active.set("tokens.good", "{theme}");
        assertEquals("#112233", PluginMessages.ownerTokens(active, "FoOrders").get("theme"));
        assertEquals("{theme}", PluginMessages.ownerTokens(active, "FoOrders").get("good"));
        assertEquals("", PluginMessages.ownerTokens(active, "FoOrders").get("bad"));
        assertEquals("", PluginMessages.ownerTokens(active, "FoOrders").get("muted"));
        assertEquals("#112233Caption", PluginMessages.resolveTokens("{good}Caption",
            PluginMessages.ownerTokens(active, "FoOrders")));
        active.set("tokens.good", null);
        assertEquals("", PluginMessages.ownerTokens(active, "FoOrders").get("good"));
        assertEquals("Caption", PluginMessages.resolveTokens("{good}Caption",
            PluginMessages.ownerTokens(active, "FoOrders")));
    }

    @Test
    void ownerTokensResolveButInsertedPlayerTextStaysLiteral() {
        Map<String, String> tokens = Map.of("prefix", "{theme} Orders » ", "theme", "GREEN");
        assertEquals("GREEN Orders » Search {theme} / {theme} / Sword",
            PluginMessages.renderOwnerTemplate("{prefix}Search {current} / %current% / {item}",
                Map.of("current", "{theme}", "item", "Sword"), tokens));
    }

    @Test
    void released46MessagesGainOnlyNewInputFailureFeedback() throws Exception {
        YamlConfiguration owner;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/messages.yml")) {
            assertTrue(stream != null);
            owner = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/messages.yml")) {
            assertTrue(stream != null);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        owner.set("orders.cancelled", "Bestelling geannuleerd");
        owner.set("orders.refunded", null);
        assertTrue(PluginMessages.addUnavailableInputFeedback(owner, defaults));
        assertEquals(defaults.getString("chat-input.unavailable"), owner.getString("chat-input.unavailable"));
        assertEquals("Bestelling geannuleerd", owner.getString("orders.cancelled"));
        assertFalse(owner.isSet("orders.refunded"));
        owner.set("chat-input.unavailable", null);
        assertFalse(owner.isSet("chat-input.unavailable"));
        // The version gate prevents this helper from running on subsequent reloads.
    }

    @Test
    void refreshCooldownUpgradeAddsOnlyItsNewFeedbackAndPreservesOwnerIntent() throws Exception {
        YamlConfiguration owner;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/messages.yml")) {
            assertTrue(stream != null);
            owner = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/messages.yml")) {
            assertTrue(stream != null);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        owner.set("orders.cancelled", "Bestelling geannuleerd");
        owner.set("actionbar.invalid-amount", null);
        assertTrue(PluginMessages.addRefreshCooldownFeedback(owner, defaults));
        assertEquals(defaults.getString("actionbar.refresh-cooldown"), owner.getString("actionbar.refresh-cooldown"));
        assertEquals("Bestelling geannuleerd", owner.getString("orders.cancelled"));
        assertFalse(owner.isSet("actionbar.invalid-amount"));
        assertFalse(PluginMessages.addRefreshCooldownFeedback(owner, defaults));

        owner.set("actionbar.refresh-cooldown", "");
        assertFalse(PluginMessages.addRefreshCooldownFeedback(owner, defaults));
        assertEquals("", owner.getString("actionbar.refresh-cooldown"));
    }

    @Test
    void actionFailureUpgradeAddsOnlyNewKeysAndKeepsOwnerRemovals() throws Exception {
        YamlConfiguration owner;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/messages.yml")) {
            assertTrue(stream != null);
            owner = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/messages.yml")) {
            assertTrue(stream != null);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        owner.set("orders.cancelled", "Bestelling geannuleerd");
        owner.set("actionbar.invalid-amount", null);
        owner.set("actionbar.inventory-full", "Inventaris vol");

        assertTrue(PluginMessages.addActionFailureFeedback(owner, defaults));
        assertEquals("Inventaris vol", owner.getString("actionbar.inventory-full"));
        assertEquals(defaults.getString("actionbar.action-unavailable"),
            owner.getString("actionbar.action-unavailable"));
        assertEquals(defaults.getString("actionbar.order-busy"), owner.getString("actionbar.order-busy"));
        assertEquals(defaults.getString("actionbar.no-items-accepted"),
            owner.getString("actionbar.no-items-accepted"));
        assertEquals("Bestelling geannuleerd", owner.getString("orders.cancelled"));
        assertFalse(owner.isSet("actionbar.invalid-amount"));
        assertFalse(PluginMessages.addActionFailureFeedback(owner, defaults));

        owner.set("actionbar.action-unavailable", null);
        assertFalse(owner.isSet("actionbar.action-unavailable"));
        // On a v27 installation the migration gate is already complete, so
        // this intentional deletion is never passed to the helper again.
    }

    @Test
    void feedbackUpgradesDoNotRecreateRemovedMessageFamilies() throws Exception {
        YamlConfiguration owner;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/messages.yml")) {
            assertTrue(stream != null);
            owner = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/messages.yml")) {
            assertTrue(stream != null);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        owner.set("actionbar", null);
        owner.set("chat-input", null);
        assertFalse(PluginMessages.addDeliveringFrames(owner));
        assertFalse(PluginMessages.addUnavailableInputFeedback(owner, defaults));
        assertFalse(PluginMessages.addRefreshCooldownFeedback(owner, defaults));
        assertFalse(PluginMessages.addActionFailureFeedback(owner, defaults));
        assertFalse(PluginMessages.addInvalidPriceFeedback(owner, defaults));
        assertFalse(owner.isSet("actionbar"));
        assertFalse(owner.isSet("chat-input"));
    }

    @Test
    void priceInputUpgradeAddsOnlyItsNewMessageAndPreservesOwnerRemoval() throws Exception {
        YamlConfiguration owner;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/messages.yml")) {
            assertTrue(stream != null);
            owner = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/messages.yml")) {
            assertTrue(stream != null);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        owner.set("actionbar.invalid-amount", null);
        owner.set("orders.cancelled", "Bestelling geannuleerd");
        assertTrue(PluginMessages.addInvalidPriceFeedback(owner, defaults));
        assertEquals(defaults.getString("actionbar.invalid-price"), owner.getString("actionbar.invalid-price"));
        assertEquals("Bestelling geannuleerd", owner.getString("orders.cancelled"));
        assertFalse(owner.isSet("actionbar.invalid-amount"));
        assertFalse(PluginMessages.addInvalidPriceFeedback(owner, defaults));

        owner.set("actionbar.invalid-price", "Ungültiger Preis");
        assertFalse(PluginMessages.addInvalidPriceFeedback(owner, defaults));
        assertEquals("Ungültiger Preis", owner.getString("actionbar.invalid-price"));
        // At schema 33, reload never runs this introduction helper again.
        owner.set("actionbar.invalid-price", null);
        assertFalse(owner.isSet("actionbar.invalid-price"));
    }

    @Test
    void priceInputFileUpgradeBacksUpPriorBytesAndIsIdempotentBeforeCurrentSchemaDeletion() throws Exception {
        File file = temporaryDirectory.resolve("messages.yml").toFile();
        String prior = "actionbar:\n  invalid-amount: '&cAmount'\norders:\n  cancelled: 'Geannuleerd'\n";
        Files.writeString(file.toPath(), prior, StandardCharsets.UTF_8);
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/messages.yml")) {
            assertTrue(stream != null);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        assertTrue(PluginMessages.migrateInvalidPriceFeedbackFile(file, defaults, ignored -> {}));
        File backup = temporaryDirectory.resolve("messages.yml.pre-v33-backup").toFile();
        assertEquals(prior, Files.readString(backup.toPath(), StandardCharsets.UTF_8));
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals(defaults.getString("actionbar.invalid-price"), migrated.getString("actionbar.invalid-price"));
        assertEquals("Geannuleerd", migrated.getString("orders.cancelled"));
        String upgraded = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(PluginMessages.migrateInvalidPriceFeedbackFile(file, defaults, ignored -> {}));
        assertEquals(upgraded, Files.readString(file.toPath(), StandardCharsets.UTF_8));

        migrated.set("actionbar.invalid-price", null);
        migrated.save(file);
        String removed = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        // A current-schema reload does not call this version-gated helper.
        assertFalse(YamlConfiguration.loadConfiguration(file).isSet("actionbar.invalid-price"));
        assertEquals(removed, Files.readString(file.toPath(), StandardCharsets.UTF_8));
        assertEquals(prior, Files.readString(backup.toPath(), StandardCharsets.UTF_8));
    }

    @Test
    void deliveringFramesKeepTranslatedBaseAndExistingRemoval() {
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("actionbar.delivering", "Bezorging");
        assertTrue(PluginMessages.addDeliveringFrames(owner));
        assertEquals(List.of("Bezorging.", "Bezorging..", "Bezorging..."),
            owner.getStringList("actionbar.delivering-frames"));
        assertFalse(PluginMessages.addDeliveringFrames(owner));
    }
}
