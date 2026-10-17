package me.foesio.foOrders.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnerYamlTest {
    @TempDir
    Path folder;

    @Test
    void malformedOwnerFileFailsWithoutBeingReplacedByAnEmptyConfig() throws Exception {
        Path file = folder.resolve("messages.yml");
        String original = "prefix: [broken\n";
        Files.writeString(file, original);

        assertThrows(IllegalStateException.class, () -> OwnerYaml.load(file.toFile()));
        assertEquals(original, Files.readString(file));
    }

    @Test
    void translatedOwnerTextLoadsExactly() throws Exception {
        Path file = folder.resolve("messages.yml");
        Files.writeString(file, "prefix: 'Beställningar → '\n");

        assertEquals("Beställningar → ", OwnerYaml.load(file.toFile()).getString("prefix"));
    }
}
