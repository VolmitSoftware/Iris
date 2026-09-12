package art.arcane.iris.pack;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class PackDirectoryResolver {
    private PackDirectoryResolver() {
    }

    public static File resolveExisting(File packsRoot, String packName) {
        if (packsRoot == null || packName == null || packName.isBlank()) {
            return null;
        }
        Path root = packsRoot.toPath().toAbsolutePath().normalize();
        Path candidate = root.resolve(packName).normalize();
        if (!root.equals(candidate.getParent())) {
            return null;
        }
        if (!isVisiblePackDirectory(candidate.toFile())) {
            return null;
        }
        return candidate.toFile();
    }

    public static List<File> listVisiblePackDirectories(File packsRoot) {
        try {
            return listVisiblePackDirectoriesOrThrow(packsRoot);
        } catch (IOException exception) {
            return List.of();
        }
    }

    public static List<File> listVisiblePackDirectoriesOrThrow(File packsRoot) throws IOException {
        if (packsRoot == null) {
            return List.of();
        }
        Path root = packsRoot.toPath().toAbsolutePath().normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (!Files.isDirectory(root)) {
            throw new IOException("Pack workspace is missing or unsafe: " + root);
        }
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .map(Path::toFile)
                    .filter(PackDirectoryResolver::isVisiblePackDirectory)
                    .sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(File::getName))
                    .toList();
        }
    }

    public static boolean isVisiblePackDirectory(File candidate) {
        if (candidate == null || isHiddenName(candidate.getName())) {
            return false;
        }
        Path path = candidate.toPath();
        return Files.isDirectory(path);
    }

    public static void requireSafePackTree(File candidate) throws IOException {
        if (!isVisiblePackDirectory(candidate)) {
            throw new IOException("Pack directory is missing or unsafe: " + candidate);
        }
        Path root = candidate.toPath().toAbsolutePath().normalize().toRealPath();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pack directory target is missing or unsafe: " + candidate);
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root) && isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    throw new IOException("Pack directory contains a symbolic link: " + file);
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("Pack directory contains an unsupported entry: " + file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to inspect pack entry: " + file, failure);
            }
        });
    }

    public static boolean containsHiddenPathSegment(Path root, Path candidate) {
        if (root == null || candidate == null) {
            return true;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            return true;
        }
        Path relative = normalizedRoot.relativize(normalizedCandidate);
        for (Path segment : relative) {
            if (isHiddenName(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHiddenName(String name) {
        return name != null && name.startsWith(".");
    }
}
