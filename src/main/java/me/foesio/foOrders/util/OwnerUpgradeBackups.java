package me.foesio.foOrders.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One retained original-byte backup name per installed file for one upgrade. */
public final class OwnerUpgradeBackups {
    private static final String PUBLIC_CORE_ORIGIN_MARKER = "guis/.public-core-migration-origin";
    private static final Map<Path, Integer> ORIGINAL_VERSIONS = new ConcurrentHashMap<>();
    private static final Map<Path, Set<Path>> ORIGINAL_FILES = new ConcurrentHashMap<>();

    private OwnerUpgradeBackups() { }

    public static void begin(File dataFolder, int originalVersion) {
        Path root = dataFolder.toPath().toAbsolutePath().normalize();
        Path pendingAggregate = root.resolve("guis/.legacy-aggregate-pending");
        if (Files.isRegularFile(pendingAggregate)) {
            try {
                String line = Files.readString(pendingAggregate).trim();
                if (line.startsWith("original-version: ")) {
                    int retained = Integer.parseInt(line.substring("original-version: ".length()).trim());
                    if (retained >= 0 && retained <= originalVersion) originalVersion = retained;
                }
            } catch (IOException | NumberFormatException ignored) {
                // A malformed marker is handled by the GUI retry path; preserve the active schema version.
            }
        }
        Set<Path> retainedFiles = null;
        Path originMarker = root.resolve(PUBLIC_CORE_ORIGIN_MARKER);
        if (Files.isRegularFile(originMarker)) {
            YamlConfiguration origin = OwnerYaml.load(originMarker.toFile());
            if (!origin.isInt("original-version")) {
                throw new IllegalStateException("Invalid public GUI origin marker " + originMarker);
            }
            int retained = origin.getInt("original-version");
            if (retained < 0 || retained > originalVersion) {
                throw new IllegalStateException("Conflicting public GUI origin version in " + originMarker);
            }
            originalVersion = retained;
            if (origin.isSet("original-files")) {
                Object raw = origin.get("original-files");
                if (!(raw instanceof List<?> names) || names.stream().anyMatch(name -> !(name instanceof String))) {
                    throw new IllegalStateException("Invalid public GUI origin file list in " + originMarker);
                }
                retainedFiles = names.stream().map(String.class::cast).map(name -> {
                    Path resolved = root.resolve(name).normalize();
                    if (name.isBlank() || !resolved.startsWith(root)) {
                        throw new IllegalStateException("Unsafe public GUI origin path in " + originMarker);
                    }
                    return resolved;
                }).collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
        }
        ORIGINAL_VERSIONS.put(root, originalVersion);
        if (retainedFiles != null) {
            ORIGINAL_FILES.put(root, retainedFiles);
            return;
        }
        if (!Files.isDirectory(root)) {
            ORIGINAL_FILES.put(root, Set.of());
            return;
        }
        try (var files = Files.walk(root)) {
            ORIGINAL_FILES.put(root, files.filter(Files::isRegularFile)
                .map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inventory original owner files", exception);
        }
    }

    public static boolean wasOriginallyPresent(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        for (Map.Entry<Path, Set<Path>> entry : ORIGINAL_FILES.entrySet()) {
            if (path.startsWith(entry.getKey())) return entry.getValue().contains(path);
        }
        return true; // Filesystem-only legacy migration tests have no plugin startup session.
    }

    public static File forFile(File file, String legacySuffix) {
        Path parent = file.getParentFile().toPath().toAbsolutePath().normalize();
        Integer original = ORIGINAL_VERSIONS.entrySet().stream()
            .filter(entry -> parent.startsWith(entry.getKey()))
            .map(Map.Entry::getValue).findFirst().orElse(null);
        return new File(file.getParentFile(), file.getName() + (original == null
            ? legacySuffix : ".gui-v" + original + "-backup"));
    }

    public static int originalVersion(File dataFolder, int fallback) {
        return ORIGINAL_VERSIONS.getOrDefault(dataFolder.toPath().toAbsolutePath().normalize(), fallback);
    }

    /** Persist the first installed GUI schema before any partial Core conversion. */
    public static void retainPublicGuiOrigin(File dataFolder, int originalVersion) throws IOException {
        Path marker = dataFolder.toPath().resolve(PUBLIC_CORE_ORIGIN_MARKER);
        Files.createDirectories(marker.getParent());
        if (Files.isRegularFile(marker)) {
            YamlConfiguration existing = OwnerYaml.load(marker.toFile());
            if (!existing.isInt("original-version") || existing.getInt("original-version") != originalVersion) {
                throw new IOException("Public GUI origin marker conflicts with retained schema version: " + marker);
            }
            return;
        }
        Path root = dataFolder.toPath().toAbsolutePath().normalize();
        Set<Path> originals = ORIGINAL_FILES.get(root);
        if (originals == null) {
            throw new IOException("Original owner file inventory is unavailable for " + dataFolder);
        }
        YamlConfiguration origin = new YamlConfiguration();
        origin.set("original-version", originalVersion);
        origin.set("original-files", originals.stream().filter(path -> isPublicUpgradeFile(root, path))
            .map(path -> root.relativize(path).toString().replace('\\', '/'))
            .sorted().toList());
        Path staged = Files.createTempFile(marker.getParent(), ".public-core-origin-", ".stage");
        try {
            Files.writeString(staged, origin.saveToString());
            try {
                Files.move(staged, marker, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(staged, marker);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    public static void clearPublicGuiOrigin(File dataFolder) throws IOException {
        Files.deleteIfExists(dataFolder.toPath().resolve(PUBLIC_CORE_ORIGIN_MARKER));
    }

    private static boolean isPublicUpgradeFile(Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return relative.equals("messages.yml") || relative.equals("sounds.yml")
            || relative.equals("public-text.yml")
            || relative.startsWith("guis/") && relative.endsWith(".yml")
            || relative.startsWith("dialogs/") && relative.endsWith(".yml");
    }
}
