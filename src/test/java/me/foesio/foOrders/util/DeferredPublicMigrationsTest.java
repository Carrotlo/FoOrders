package me.foesio.foOrders.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DeferredPublicMigrationsTest {
    @TempDir Path data;

    @Test
    void malformedOwnerStepIsRetainedUntilRepairAndSuccessfulReplay() throws IOException {
        Path sound = data.resolve("sounds.yml");
        byte[] bad = "gui: [broken\n".getBytes(StandardCharsets.UTF_8);
        Files.write(sound, bad);
        AtomicInteger calls = new AtomicInteger();
        List<String> warnings = new ArrayList<>();

        assertTrue(DeferredPublicMigrations.runOrDefer(data.toFile(), 16, List.of(),
            List.of(sound.toFile()), () -> { calls.incrementAndGet(); return true; }, warnings::add));
        assertTrue(DeferredPublicMigrations.hasPending(data.toFile()));
        assertArrayEquals(bad, Files.readAllBytes(sound));
        assertFalse(DeferredPublicMigrations.retry(data.toFile(), 16, List.of(), List.of(sound.toFile()),
            () -> { calls.incrementAndGet(); return true; }, warnings::add));
        assertFalse(DeferredPublicMigrations.retry(data.toFile(), 16, List.of(), List.of(sound.toFile()),
            () -> { calls.incrementAndGet(); return false; }, warnings::add));
        assertTrue(calls.get() == 0);

        Files.writeString(sound, "gui: {}\n", StandardCharsets.UTF_8);
        assertFalse(DeferredPublicMigrations.retry(data.toFile(), 16, List.of(), List.of(sound.toFile()),
            () -> { calls.incrementAndGet(); return false; }, warnings::add));
        assertTrue(DeferredPublicMigrations.hasPending(data.toFile()));
        assertTrue(DeferredPublicMigrations.retry(data.toFile(), 16, List.of(), List.of(sound.toFile()),
            () -> { calls.incrementAndGet(); return true; }, warnings::add));
        assertFalse(DeferredPublicMigrations.hasPending(data.toFile()));
        assertTrue(calls.get() == 2);
    }

    @Test
    void replayingOneStepKeepsOtherDeferredStepsPending() throws IOException {
        Path sound = data.resolve("sounds.yml");
        Path dialog = data.resolve("dialogs/amount.yml");
        Files.createDirectories(dialog.getParent());
        Files.writeString(sound, "gui: [broken\n");
        Files.writeString(dialog, "title: [broken\n");
        assertTrue(DeferredPublicMigrations.runOrDefer(data.toFile(), 16, List.of(),
            List.of(sound.toFile()), () -> true, ignored -> {}));
        assertTrue(DeferredPublicMigrations.runOrDefer(data.toFile(), 17,
            List.of(dialog.toFile()), List.of(), () -> true, ignored -> {}));

        Files.writeString(sound, "gui: {}\n");
        assertTrue(DeferredPublicMigrations.retry(data.toFile(), 16, List.of(),
            List.of(sound.toFile()), () -> true, ignored -> {}));

        assertTrue(DeferredPublicMigrations.hasPending(data.toFile()));
        assertFalse(DeferredPublicMigrations.retry(data.toFile(), 17,
            List.of(dialog.toFile()), List.of(), () -> true, ignored -> {}));
        assertTrue(Files.readString(data.resolve(".public-interface-migrations-pending.yml"))
            .contains("17"));
    }

    @Test
    void parseableButUnsafeOwnerFileKeepsSemanticMigrationPending() throws IOException {
        Path sound = data.resolve("sounds.yml");
        byte[] owner = "custom:\n  alert:\n    sound: owner:alert\n    volume: loud\n"
            .getBytes(StandardCharsets.UTF_8);
        Files.write(sound, owner);
        List<String> warnings = new ArrayList<>();

        assertTrue(DeferredPublicMigrations.runOrDefer(data.toFile(), 16, List.of(),
            List.of(sound.toFile()), () -> DeferredPublicMigrations.defer(data.toFile(), 16,
                "invalid custom sound field", warnings::add), warnings::add));
        assertArrayEquals(owner, Files.readAllBytes(sound));
        assertTrue(DeferredPublicMigrations.hasPending(data.toFile()));
        assertFalse(DeferredPublicMigrations.retry(data.toFile(), 16, List.of(),
            List.of(sound.toFile()), () -> false, warnings::add));

        Files.writeString(sound, "custom:\n  alert:\n    sound: owner:alert\n    volume: 0.4\n");
        assertTrue(DeferredPublicMigrations.retry(data.toFile(), 16, List.of(),
            List.of(sound.toFile()), () -> true, warnings::add));
        assertFalse(DeferredPublicMigrations.hasPending(data.toFile()));
    }
}
