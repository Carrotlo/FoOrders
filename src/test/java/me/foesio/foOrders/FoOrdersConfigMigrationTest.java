package me.foesio.foOrders;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoOrdersConfigMigrationTest {
    @Test
    void currentSchemaDoesNotBackfillRemovedOwnerSettings() {
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("config-version", 30);
        owner.set("native-dialogs.enabled", false);

        assertFalse(FoOrders.migrateLegacyConfigFields(owner));
        assertFalse(owner.isSet("native-dialogs.item-selection"));
        assertFalse(owner.isSet("native-dialogs.warn-on-fallback"));
        assertFalse(owner.isSet("history.enabled"));
        assertFalse(owner.isSet("discord-webhook.username"));
    }

    @Test
    void oldSchemaAddsOnlyTheIntroducedPickerSettingWhenItsFamilyExists() {
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("config-version", 17);
        owner.set("native-dialogs.enabled", false);

        assertTrue(FoOrders.migrateLegacyConfigFields(owner));
        assertTrue(owner.getBoolean("native-dialogs.item-selection"));
        assertFalse(owner.getBoolean("native-dialogs.enabled"));
        assertFalse(owner.isSet("native-dialogs.warn-on-fallback"));
        assertFalse(owner.isSet("history.enabled"));
        assertFalse(FoOrders.migrateLegacyConfigFields(owner));

        YamlConfiguration removedFamily = new YamlConfiguration();
        removedFamily.set("config-version", 17);
        YamlConfiguration bundledDefaults = new YamlConfiguration();
        bundledDefaults.set("native-dialogs.enabled", true);
        bundledDefaults.set("native-dialogs.item-selection", true);
        removedFamily.setDefaults(bundledDefaults);
        assertFalse(FoOrders.migrateLegacyConfigFields(removedFamily));
        assertFalse(removedFamily.isSet("native-dialogs"));
        assertFalse(removedFamily.saveToString().contains("native-dialogs:"));
    }
}
