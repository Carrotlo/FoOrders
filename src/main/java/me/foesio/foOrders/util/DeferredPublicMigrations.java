package me.foesio.foOrders.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Keeps legacy public-file steps retryable after the global schema number advances. */
public final class DeferredPublicMigrations {
    private static final String FILE = ".public-interface-migrations-pending.yml";

    private DeferredPublicMigrations() { }

    public static boolean hasPending(File dataFolder) {
        return !steps(dataFolder).isEmpty();
    }

    public static boolean runOrDefer(File dataFolder, int step, List<File> required,
                                     List<File> optional, BooleanSupplier migration,
                                     Consumer<String> warn) {
        String problem = sourceProblem(required, optional);
        if (problem == null) return migration.getAsBoolean();
        return defer(dataFolder, step, problem, warn);
    }

    public static boolean defer(File dataFolder, int step, String problem, Consumer<String> warn) {
        try {
            mark(dataFolder, step);
        } catch (IOException failure) {
            warn.accept("Could not retain public migration " + step + " for retry: " + failure.getMessage());
            return false;
        }
        warn.accept("Public migration " + step + " deferred: " + problem
            + "; owner files are unchanged and this step will retry before schema 38.");
        return true;
    }

    public static boolean retry(File dataFolder, int step, List<File> required, List<File> optional,
                                BooleanSupplier migration, Consumer<String> warn) {
        if (!steps(dataFolder).contains(step)) return true;
        String problem = sourceProblem(required, optional);
        if (problem != null) {
            warn.accept("Public migration " + step + " remains pending: " + problem);
            return false;
        }
        try {
            if (!migration.getAsBoolean()) return false;
            List<Integer> remaining = new ArrayList<>(steps(dataFolder));
            remaining.remove(Integer.valueOf(step));
            save(dataFolder, remaining);
            return true;
        } catch (RuntimeException | IOException failure) {
            warn.accept("Public migration " + step + " retry failed: " + failure.getMessage());
            return false;
        }
    }

    private static String sourceProblem(List<File> required, List<File> optional) {
        for (File file : required) {
            if (!file.isFile()) return "missing " + file;
        }
        List<File> sources = new ArrayList<>(required);
        sources.addAll(optional);
        for (File file : sources) {
            if (!file.isFile()) continue;
            try {
                OwnerYaml.load(file);
            } catch (IllegalStateException malformed) {
                return malformed.getMessage();
            }
        }
        return null;
    }

    private static void mark(File dataFolder, int step) throws IOException {
        List<Integer> pending = new ArrayList<>(steps(dataFolder));
        if (!pending.contains(step)) pending.add(step);
        save(dataFolder, pending);
    }

    private static List<Integer> steps(File dataFolder) {
        File file = new File(dataFolder, FILE);
        if (!file.isFile()) return List.of();
        YamlConfiguration yaml = OwnerYaml.load(file);
        if (!yaml.isList("steps") || yaml.getList("steps").stream().anyMatch(value -> !(value instanceof Integer))) {
            throw new IllegalStateException("Invalid public migration retry marker " + file);
        }
        return yaml.getIntegerList("steps");
    }

    private static void save(File dataFolder, List<Integer> pending) throws IOException {
        Path target = new File(dataFolder, FILE).toPath();
        Files.createDirectories(target.getParent());
        if (pending.isEmpty()) {
            Files.deleteIfExists(target);
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("steps", pending);
        Path stage = Files.createTempFile(target.getParent(), ".public-interface-pending-", ".stage");
        try {
            Files.writeString(stage, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(stage, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(stage);
        }
    }
}
