package me.foesio.foOrders;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicNumberFormatTest {
    @TempDir Path install;

    @Test
    void bundledValuesPreservePriorPresentation() throws Exception {
        PublicNumberFormat format = PublicNumberFormat.load(bundled());
        assertEquals("1", format.plain(1));
        assertEquals("1.25", format.plain(1.25));
        assertEquals("1.00K", format.compact(1000));
        assertEquals("-1.50M", format.compact(-1_500_000));
    }

    @Test
    void translatedLocaleAndSuffixesReachFinalNumber() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("numbers.locale", "nl-NL");
        owner.set("numbers.plain-pattern", "#,##0.##");
        owner.set("numbers.compact-suffixes", List.of("duizend", "miljoen"));
        PublicNumberFormat format = PublicNumberFormat.load(owner);

        assertEquals("1,25", format.plain(1.25));
        assertEquals("1.234.567,5", format.plain(1_234_567.5));
        assertEquals("1,50miljoen", format.compact(1_500_000));
        owner.set("numbers.compact-pattern", "0.00;(0.00)");
        assertEquals("(1,50)miljoen", PublicNumberFormat.load(owner).compact(-1_500_000));
        owner.set("numbers.compact-pattern", "0");
        assertEquals("1miljoen", PublicNumberFormat.load(owner).compact(999_600));
        owner.set("numbers.compact-suffixes", null);
        assertNull(PublicNumberFormat.load(owner));
    }

    @Test
    void invalidStartupFieldsUseBundledDefaultsInMemoryButReloadKeepsWorkingNumbers() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("numbers.locale", "nl-NL");
        owner.set("orders.cancelled", "Bestelling geannuleerd ✓");
        owner.set("numbers.compact-pattern", "");
        owner.set("numbers.compact-suffixes", List.of("duizend", 42));
        Path file = install.resolve("messages.yml");
        owner.save(file.toFile());
        byte[] installedBytes = Files.readAllBytes(file);
        YamlConfiguration startup = YamlConfiguration.loadConfiguration(file.toFile());

        assertTrue(PluginMessages.requiresStartupNumberFallback(startup, true));
        assertTrue(PluginMessages.repairInvalidNumberFields(startup, bundled(), ignored -> {}));
        assertEquals("nl-NL", startup.getString("numbers.locale"));
        assertEquals("Bestelling geannuleerd ✓", startup.getString("orders.cancelled"));
        assertEquals("duizend", startup.getStringList("numbers.compact-suffixes").get(0));
        assertEquals("M", startup.getStringList("numbers.compact-suffixes").get(1));
        assertEquals("1,50duizend", PublicNumberFormat.load(startup).compact(1500));
        org.junit.jupiter.api.Assertions.assertArrayEquals(installedBytes, Files.readAllBytes(file));

        YamlConfiguration secondStartupLoad = YamlConfiguration.loadConfiguration(file.toFile());
        assertTrue(PluginMessages.requiresStartupNumberFallback(secondStartupLoad, true));
        assertTrue(PluginMessages.repairInvalidNumberFields(secondStartupLoad, bundled(), ignored -> {}));
        assertEquals("1,50duizend", PublicNumberFormat.load(secondStartupLoad).compact(1500));
        org.junit.jupiter.api.Assertions.assertArrayEquals(installedBytes, Files.readAllBytes(file));

        PublicNumberFormat working = PublicNumberFormat.load(startup);
        YamlConfiguration badReload = bundled();
        badReload.set("numbers.compact-suffixes", List.of("K", 42));
        assertThrows(IllegalStateException.class,
            () -> PluginMessages.requiresStartupNumberFallback(badReload, false));
        assertEquals("1,50duizend", working.compact(1500));
        assertFalse(PluginMessages.requiresStartupNumberFallback(startup, false));
    }

    @Test
    void malformedNumberSectionUsesSmallestSafeBundledSection() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("orders.cancelled", "Einde ✓");
        owner.set("numbers", "broken");

        assertEquals("numbers", PublicNumberFormat.firstInvalidPath(owner));
        assertTrue(PluginMessages.repairInvalidNumberFields(owner, bundled(), ignored -> {}));
        assertEquals("Einde ✓", owner.getString("orders.cancelled"));
        assertEquals("1.00K", PublicNumberFormat.load(owner).compact(1000));
    }

    @Test
    void invalidSuffixWithoutMatchingDefaultDoesNotEraseValidOwnerSuffixes() throws Exception {
        YamlConfiguration owner = bundled();
        List<Object> suffixes = new java.util.ArrayList<>(owner.getStringList("numbers.compact-suffixes"));
        suffixes.set(0, "duizend");
        suffixes.add(42);
        owner.set("numbers.compact-suffixes", suffixes);

        assertThrows(IllegalStateException.class,
            () -> PluginMessages.repairInvalidNumberFields(owner, bundled(), ignored -> {}));
        assertEquals("duizend", owner.getList("numbers.compact-suffixes").get(0));
        assertEquals(42, owner.getList("numbers.compact-suffixes").get(suffixes.size() - 1));
    }

    @Test
    void laterVersionedUpgradeRepairsPresentBadNumbersWithoutRestoringRemovedFields() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("numbers.locale", "bad tag!");
        owner.set("numbers.plain-pattern", null);
        owner.set("numbers.compact-suffixes", List.of("duizend", 42));
        owner.set("orders.cancelled", "Bestelling geannuleerd ✓");

        assertTrue(PluginMessages.repairPresentInvalidNumberFields(owner, bundled(), ignored -> { }));
        assertEquals("en-US", owner.getString("numbers.locale"));
        assertFalse(owner.isSet("numbers.plain-pattern"));
        assertEquals("duizend", owner.getList("numbers.compact-suffixes").get(0));
        assertEquals("M", owner.getList("numbers.compact-suffixes").get(1));
        assertEquals("Bestelling geannuleerd ✓", owner.getString("orders.cancelled"));
    }

    @Test
    void laterVersionedUpgradeKeepsRemovedNumberSectionAbsent() throws Exception {
        YamlConfiguration owner = bundled();
        owner.set("numbers", null);

        assertFalse(PluginMessages.repairPresentInvalidNumberFields(owner, bundled(), ignored -> { }));
        assertFalse(owner.isSet("numbers"));
    }

    private YamlConfiguration bundled() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/messages.yml")) {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
