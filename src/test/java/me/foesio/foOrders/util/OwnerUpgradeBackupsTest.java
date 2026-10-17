package me.foesio.foOrders.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OwnerUpgradeBackupsTest {
    @TempDir Path data;

    @Test
    void oneOriginalVersionLabelIsReusedAndNewFilesGetNoUpgradeBackup() throws Exception {
        Path original = data.resolve("guis/main.yml");
        Files.createDirectories(original.getParent());
        Files.writeString(original, "owner text ✓\n");
        OwnerUpgradeBackups.begin(data.toFile(), 22);
        Path introduced = data.resolve("guis/new-menu.yml");
        Files.writeString(introduced, "new\n");

        assertTrue(OwnerUpgradeBackups.wasOriginallyPresent(original.toFile()));
        assertFalse(OwnerUpgradeBackups.wasOriginallyPresent(introduced.toFile()));
        assertEquals(OwnerUpgradeBackups.forFile(original.toFile(), ".pre-v23-backup"),
            OwnerUpgradeBackups.forFile(original.toFile(), ".pre-v35-backup"));
        assertEquals("main.yml.gui-v22-backup",
            OwnerUpgradeBackups.forFile(original.toFile(), ".pre-v23-backup").getName());
    }

    @Test
    void retriedLegacySplitKeepsTheFirstStartupBackupVersion() throws Exception {
        Path gui = data.resolve("guis/orders.yml");
        Files.createDirectories(gui.getParent());
        Files.writeString(gui, "title: Owner\n");
        Files.writeString(data.resolve("guis/.legacy-aggregate-pending"), "original-version: 2\n");

        OwnerUpgradeBackups.begin(data.toFile(), 37);

        assertEquals(2, OwnerUpgradeBackups.originalVersion(data.toFile(), 37));
        assertEquals("orders.yml.gui-v2-backup",
            OwnerUpgradeBackups.forFile(gui.toFile(), ".old-backup").getName());
    }

    @Test
    void partialCoreConversionRetainsOriginalSchemaAcrossRestart() throws Exception {
        Path gui = data.resolve("guis/main.yml");
        Path introducedText = data.resolve("public-text.yml");
        Files.createDirectories(gui.getParent());
        Files.writeString(gui, "title: Owner\n");
        OwnerUpgradeBackups.begin(data.toFile(), 4);
        OwnerUpgradeBackups.retainPublicGuiOrigin(data.toFile(), 4);
        Files.writeString(introducedText, "labels: {}\n");
        assertFalse(OwnerUpgradeBackups.wasOriginallyPresent(introducedText.toFile()));

        OwnerUpgradeBackups.begin(data.toFile(), 37);
        assertEquals(4, OwnerUpgradeBackups.originalVersion(data.toFile(), 37));
        assertFalse(OwnerUpgradeBackups.wasOriginallyPresent(introducedText.toFile()));
        assertTrue(OwnerUpgradeBackups.wasOriginallyPresent(gui.toFile()));
        assertEquals("main.yml.gui-v4-backup",
            OwnerUpgradeBackups.forFile(gui.toFile(), ".old-backup").getName());

        OwnerUpgradeBackups.clearPublicGuiOrigin(data.toFile());
        OwnerUpgradeBackups.begin(data.toFile(), 38);
        assertEquals(38, OwnerUpgradeBackups.originalVersion(data.toFile(), 38));
    }
}
