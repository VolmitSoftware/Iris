package art.arcane.iris.core.structure.authoring;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record StructureTransactionReadSet(
        Map<String, String> fileHashes,
        Set<String> absentPaths,
        Map<String, List<String>> directoryEntries
) {
    public static final int MAX_ENTRIES = 100_000;

    public StructureTransactionReadSet {
        Objects.requireNonNull(fileHashes, "fileHashes");
        Objects.requireNonNull(absentPaths, "absentPaths");
        Objects.requireNonNull(directoryEntries, "directoryEntries");
        TreeMap<String, String> orderedHashes = new TreeMap<>();
        for (Map.Entry<String, String> entry : fileHashes.entrySet()) {
            String path = validatePath(entry.getKey(), false);
            String hash = Objects.requireNonNull(entry.getValue(), "read-set file hash");
            if (!StructureHash.isSha256(hash)) {
                throw new IllegalArgumentException("Read-set file hash must be SHA-256 for " + path);
            }
            orderedHashes.put(path, hash);
        }
        TreeSet<String> orderedAbsent = new TreeSet<>();
        for (String path : absentPaths) {
            orderedAbsent.add(validatePath(path, false));
        }
        for (String path : orderedHashes.keySet()) {
            if (orderedAbsent.contains(path)) {
                throw new IllegalArgumentException("Read-set path cannot be both present and absent: " + path);
            }
        }
        TreeMap<String, List<String>> orderedDirectories = new TreeMap<>();
        TreeSet<String> uniqueEntries = new TreeSet<>(orderedHashes.keySet());
        uniqueEntries.addAll(orderedAbsent);
        for (Map.Entry<String, List<String>> entry : directoryEntries.entrySet()) {
            String directory = validatePath(entry.getKey(), true);
            uniqueEntries.add(directory);
            Objects.requireNonNull(entry.getValue(), "read-set directory entries");
            TreeSet<String> entries = new TreeSet<>();
            for (String child : entry.getValue()) {
                String childPath = validatePath(child, false);
                if (!childPath.startsWith(directory + "/")) {
                    throw new IllegalArgumentException(
                            "Read-set directory entry is outside " + directory + ": " + childPath);
                }
                entries.add(childPath);
            }
            uniqueEntries.addAll(entries);
            orderedDirectories.put(directory, List.copyOf(entries));
        }
        if (uniqueEntries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Read set exceeds " + MAX_ENTRIES + " entries");
        }
        fileHashes = Collections.unmodifiableMap(orderedHashes);
        absentPaths = Collections.unmodifiableSet(orderedAbsent);
        directoryEntries = Collections.unmodifiableMap(orderedDirectories);
    }

    public static StructureTransactionReadSet empty() {
        return new StructureTransactionReadSet(Map.of(), Set.of(), Map.of());
    }

    public boolean isEmpty() {
        return fileHashes.isEmpty() && absentPaths.isEmpty() && directoryEntries.isEmpty();
    }

    public List<String> paths() {
        TreeSet<String> paths = new TreeSet<>(fileHashes.keySet());
        paths.addAll(absentPaths);
        for (List<String> entries : directoryEntries.values()) {
            paths.addAll(entries);
        }
        return List.copyOf(paths);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String validatePath(String value, boolean directory) {
        Objects.requireNonNull(value, "read-set path");
        String normalized = value.trim();
        if (normalized.isEmpty() || !normalized.equals(value) || normalized.startsWith("/")
                || normalized.endsWith("/") || normalized.indexOf('\\') >= 0 || normalized.contains(":")) {
            throw new IllegalArgumentException("Invalid read-set path: " + value);
        }
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.getNameCount() == 0 || path.startsWith("..")
                || path.toString().equals(".")) {
            throw new IllegalArgumentException("Invalid read-set path: " + value);
        }
        String portable = path.toString().replace('\\', '/');
        if (!portable.equals(normalized)) {
            throw new IllegalArgumentException("Read-set path is not normalized: " + value);
        }
        if (!directory && normalized.endsWith("/.")) {
            throw new IllegalArgumentException("Invalid read-set file path: " + value);
        }
        return normalized;
    }

    public static final class Builder {
        private final Map<String, String> fileHashes = new TreeMap<>();
        private final Set<String> absentPaths = new TreeSet<>();
        private final Map<String, List<String>> directoryEntries = new TreeMap<>();

        public Builder file(String relativePath, String contentHash) {
            fileHashes.put(relativePath, contentHash);
            return this;
        }

        public Builder files(Map<String, String> hashes) {
            fileHashes.putAll(Objects.requireNonNull(hashes, "read-set files"));
            return this;
        }

        public Builder absent(String relativePath) {
            absentPaths.add(relativePath);
            return this;
        }

        public Builder absent(Collection<String> relativePaths) {
            absentPaths.addAll(Objects.requireNonNull(relativePaths, "absent read-set paths"));
            return this;
        }

        public Builder directory(String relativePath, Collection<String> entries) {
            directoryEntries.put(relativePath, new ArrayList<>(Objects.requireNonNull(entries, "directory entries")));
            return this;
        }

        public StructureTransactionReadSet build() {
            return new StructureTransactionReadSet(fileHashes, absentPaths, directoryEntries);
        }
    }
}
