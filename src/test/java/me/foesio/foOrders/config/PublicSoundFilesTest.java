package me.foesio.foOrders.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

class PublicSoundFilesTest {
    @TempDir
    Path folder;

    @Test
    void unparseableOwnerSoundsArePreservedAndAllDefinitionsSuppressed() throws IOException {
        Path owner = folder.resolve("sounds.yml");
        byte[] bytes = "gui: [broken\n".getBytes(StandardCharsets.UTF_8);
        Files.write(owner, bytes);
        YamlConfiguration defaults = bundledSounds();
        List<String> warnings = new ArrayList<>();

        PublicSoundFiles.Snapshot snapshot = PublicSoundFiles.capture(owner.toFile(), defaults,
            warnings::add);

        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, snapshot.original());
        org.junit.jupiter.api.Assertions.assertArrayEquals(bytes, Files.readAllBytes(owner));
        assertTrue(snapshot.suppress().contains("gui.sort"));
        assertTrue(snapshot.suppress().contains("orders.claimed"));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("Unparseable")));
    }

    @Test
    void invalidVariationOrScalarRejectsReloadBeforeChangingLiveSounds() throws IOException {
        YamlConfiguration defaults = bundledSounds();
        YamlConfiguration active = bundledSounds();
        active.set("gui.sort.pitch-variation", "wide");
        assertEquals("gui.sort.pitch-variation",
            PublicSoundFiles.firstInvalidReloadField(defaults, active));
        active.set("gui.sort.pitch-variation", 0.25D);
        active.set("gui.filter", 42);
        assertEquals("gui.filter", PublicSoundFiles.firstInvalidReloadField(defaults, active));
        active.set("gui.filter", null);
        assertEquals(null, PublicSoundFiles.firstInvalidReloadField(defaults, active));
    }

    @Test
    void customSoundWithInvalidCoreFieldKeepsUpgradePending() throws IOException {
        YamlConfiguration defaults = bundledSounds();
        YamlConfiguration active = bundledSounds();
        active.set("custom.alert.sound", "owner:alert");
        active.set("custom.alert.volume", "loud");

        assertEquals("custom.alert.volume", PublicSoundFiles.firstInvalidReloadField(defaults, active));
        assertTrue(assertThrows(IllegalStateException.class, () ->
            PublicSoundFiles.repairLegacyInvalidFields(active, defaults, ignored -> {}))
            .getMessage().contains("custom.alert.volume"));
        assertEquals("owner:alert", active.getString("custom.alert.sound"));

        active.set("custom.alert.volume", 0.4D);
        assertEquals(null, PublicSoundFiles.firstInvalidReloadField(defaults, active));
        assertFalse(PublicSoundFiles.repairLegacyInvalidFields(active, defaults, ignored -> {}));
    }

    @Test
    void invalidCustomSoundBlocksEarlierVariationWriteWithoutChangingOwnerValues() throws IOException {
        YamlConfiguration defaults = bundledSounds();
        YamlConfiguration old;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/sounds.yml")) {
            old = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        old.set("custom.alert.sound", "owner:alert");
        old.set("custom.alert.volume", "loud");
        String before = old.saveToString();

        assertTrue(PublicSoundFiles.variationUpgradeBlocker(old, defaults)
            .contains("custom.alert.volume"));
        assertEquals(before, old.saveToString());
        assertFalse(old.isSet("gui.sort.pitch-variation"));

        old.set("custom.alert.volume", 0.4D);
        assertEquals(null, PublicSoundFiles.variationUpgradeBlocker(old, defaults));
        assertTrue(PublicSoundFiles.migrateVariationFields(old, defaults));
        assertTrue(old.isSet("gui.sort.pitch-variation"));
    }

    @Test
    void released46AlreadyHadSoundDefinitionsSoMissingFileIsAmbiguous() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/sounds.yml")) {
            assertTrue(stream != null);
            YamlConfiguration released = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
            assertTrue(released.isSet("gui.sort.sound"));
            assertTrue(released.isSet("gui.filter.sound"));
        }
        Path soundFile = folder.resolve("sounds.yml");
        List<String> warnings = new ArrayList<>();
        assertTrue(PublicSoundFiles.preserveMissingSoundFile(soundFile.toFile(), warnings::add));
        assertFalse(Files.exists(soundFile));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("preserving")));
        Files.writeString(soundFile, "gui:\n  sort:\n    enabled: false\n");
        assertFalse(PublicSoundFiles.preserveMissingSoundFile(soundFile.toFile(), warnings::add));
    }

    @Test
    void removedOrIncompleteOwnerDefinitionIsNotEligibleForCoreDefaultPlayback() throws IOException {
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/sounds.yml")) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration active = new YamlConfiguration();
        active.set("gui.sort.enabled", false);
        active.set("gui.sort.sound", "OWNER_SENTINEL_SOUND");
        active.set("gui.sort.category", "MASTER");
        active.set("gui.sort.volume", 0.25);
        active.set("gui.sort.pitch", 0.9);

        assertFalse(PublicSoundFiles.missingDefinitions(defaults, active).contains("gui.sort"));
        assertTrue(PublicSoundFiles.missingDefinitions(defaults, active).contains("gui.filter"));
        active.set("gui.sort.pitch", null);
        assertTrue(PublicSoundFiles.missingDefinitions(defaults, active).contains("gui.sort"));
    }

    @Test
    void released46SoundUpgradeOnlyBackfillsCompleteOwnerDefinitions() throws IOException {
        YamlConfiguration owner;
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/4.6/sounds.yml")) {
            assertTrue(stream != null);
            owner = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        YamlConfiguration defaults;
        try (InputStream stream = getClass().getResourceAsStream("/sounds.yml")) {
            assertTrue(stream != null);
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        owner.set("gui.sort.sound", null);
        owner.set("gui.filter.pitch", null);
        owner.set("gui.cycle.pitch-variation", 0.42D);

        assertTrue(PublicSoundFiles.migrateVariationFields(owner, defaults));
        assertFalse(owner.isSet("gui.sort.sound"));
        assertFalse(owner.isSet("gui.sort.pitch-variation"));
        assertFalse(owner.isSet("gui.filter.pitch"));
        assertFalse(owner.isSet("gui.filter.pitch-variation"));
        assertTrue(owner.isSet("orders.claimed.pitch-variation"));
        assertTrue(Math.abs(owner.getDouble("gui.cycle.pitch-variation") - 0.42D) < 0.00001D);
        assertFalse(PublicSoundFiles.migrateVariationFields(owner, defaults));
    }

    @Test
    void malformedOwnerSoundFieldsAreSuppressedWithoutRewritingTheOwnerFile() throws IOException {
        YamlConfiguration defaults = bundledSounds();
        for (Map.Entry<String, Object> mutation : List.<Map.Entry<String, Object>>of(
            Map.entry("enabled", "false"), Map.entry("sound", " "),
            Map.entry("category", "not-a-category"), Map.entry("volume", "loud"),
            Map.entry("pitch", 4D), Map.entry("pitch-variation", "wide"))) {
            YamlConfiguration owner = bundledSounds();
            String path = "gui.cycle." + mutation.getKey();
            owner.set(path, mutation.getValue());
            String before = owner.saveToString();
            List<String> warnings = new ArrayList<>();

            assertTrue(PublicSoundFiles.suppressedDefinitions(defaults, owner, warnings::add)
                .contains("gui.cycle"), path);
            assertTrue(warnings.stream().anyMatch(line -> line.contains(path)), path);
            assertEquals(before, owner.saveToString(), path);
        }

        YamlConfiguration valid = bundledSounds();
        valid.set("gui.cycle.category", "records");
        valid.set("gui.cycle.volume", 0);
        valid.set("gui.cycle.pitch", 0.5D);
        valid.set("gui.cycle.pitch-variation", 0.42D);
        assertFalse(PublicSoundFiles.suppressedDefinitions(defaults, valid, ignored -> {})
            .contains("gui.cycle"));
    }

    @Test
    void versionedUpgradeRepairsOnlyInvalidSoundFields() throws IOException {
        YamlConfiguration defaults = bundledSounds();
        YamlConfiguration owner = bundledSounds();
        owner.set("gui.sort.category", "not-a-category");
        owner.set("gui.sort.pitch", 1.37D);
        owner.set("gui.sort.pitch-variation", "wide");
        owner.set("gui.filter.sound", null);
        owner.set("gui.filter.volume", 0.12D);
        owner.set("gui.refresh", 42);
        List<String> warnings = new ArrayList<>();

        assertTrue(PublicSoundFiles.repairLegacyInvalidFields(owner, defaults, warnings::add));

        assertEquals(defaults.getString("gui.sort.category"), owner.getString("gui.sort.category"));
        assertEquals(defaults.getDouble("gui.sort.pitch-variation"),
            owner.getDouble("gui.sort.pitch-variation"));
        assertEquals(1.37D, owner.getDouble("gui.sort.pitch"));
        assertFalse(owner.isSet("gui.filter.sound"));
        assertEquals(0.12D, owner.getDouble("gui.filter.volume"));
        assertEquals(defaults.getString("gui.refresh.sound"), owner.getString("gui.refresh.sound"));
        assertTrue(warnings.stream().anyMatch(line -> line.contains("gui.sort.category")));
        assertFalse(PublicSoundFiles.repairLegacyInvalidFields(owner, defaults, ignored -> {}));
    }

    private YamlConfiguration bundledSounds() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/sounds.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
