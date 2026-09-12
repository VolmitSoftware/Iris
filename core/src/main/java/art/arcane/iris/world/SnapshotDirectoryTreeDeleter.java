package art.arcane.iris.world;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SnapshotDirectoryTreeDeleter {
    private SnapshotDirectoryTreeDeleter() {
    }

    public static void delete(Path target) throws IOException {
        Path root = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        if (root.getParent() == null) {
            throw new IOException("Refusing to delete a filesystem root: " + root);
        }
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        requireDirectory(root);
        deleteDirectory(root);
    }

    private static void deleteDirectory(Path directory) throws IOException {
        requireDirectory(directory);
        List<SnapshotEntry> entries = snapshot(directory);
        for (SnapshotEntry entry : entries) {
            BasicFileAttributes current = requireSafeEntry(entry.path());
            if (entry.directory() != current.isDirectory()
                    || !sameFile(entry.fileKey(), current.fileKey())) {
                throw new IOException("Directory entry changed during deletion: " + entry.path());
            }
            if (entry.directory()) {
                deleteDirectory(entry.path());
            } else {
                Files.delete(entry.path());
            }
        }
        Files.delete(directory);
    }

    private static List<SnapshotEntry> snapshot(Path directory) throws IOException {
        ArrayList<SnapshotEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                Path normalized = child.toAbsolutePath().normalize();
                if (!Objects.equals(normalized.getParent(), directory)) {
                    throw new IOException("Directory entry escapes its parent: " + child);
                }
                BasicFileAttributes attributes = requireSafeEntry(normalized);
                entries.add(new SnapshotEntry(normalized, attributes.isDirectory(), attributes.fileKey()));
            }
        }
        return List.copyOf(entries);
    }

    private static BasicFileAttributes requireDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = requireSafeEntry(directory);
        if (!attributes.isDirectory()) {
            throw new IOException("Deletion target is not a directory: " + directory);
        }
        return attributes;
    }

    private static BasicFileAttributes requireSafeEntry(Path entry) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                entry,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (attributes.isSymbolicLink()) {
            throw new IOException("Deletion target contains a symbolic link: " + entry);
        }
        if (!attributes.isDirectory() && !attributes.isRegularFile()) {
            throw new IOException("Deletion target contains an unsafe filesystem entry: " + entry);
        }
        return attributes;
    }

    private static boolean sameFile(Object expected, Object actual) {
        return expected == null || actual == null || expected.equals(actual);
    }

    private record SnapshotEntry(Path path, boolean directory, Object fileKey) {
    }
}
