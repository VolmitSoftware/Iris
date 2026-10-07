package art.arcane.iris.pack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class PackFingerprints {
    private static final String CODE_WORKSPACE_SUFFIX = ".code-workspace";
    private static final int FINGERPRINT_BUFFER_BYTES = 64 * 1024;

    private PackFingerprints() {
    }

    public static String computePackMetadataDigest(File packsDir) {
        Path root = resolveFingerprintRoot(packsDir);
        if (root == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<FingerprintEntry> entries = collectFingerprintEntries(root.toRealPath());
            entries.sort(Comparator.comparing(FingerprintEntry::relativePath));
            for (FingerprintEntry entry : entries) {
                byte[] relativePath = entry.relativePath().getBytes(StandardCharsets.UTF_8);
                updateDigestInt(digest, relativePath.length);
                digest.update(relativePath);
                updateDigestLong(digest, entry.size());
                updateDigestLong(digest, entry.lastModifiedMillis());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to fingerprint Iris packs at " + root, exception);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String computePackFingerprint(File packsDir) {
        return computePackContentSnapshot(packsDir).content();
    }

    public static PackContentSnapshot computePackContentSnapshot(File packsDir) {
        Path root = resolveFingerprintRoot(packsDir);
        if (root == null) {
            return new PackContentSnapshot("", Map.of());
        }
        try {
            Path resolvedRoot = root.toRealPath();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            Map<String, MessageDigest> packDigests = new LinkedHashMap<>();
            List<FingerprintEntry> entries = collectFingerprintEntries(resolvedRoot);
            entries.sort(Comparator.comparing(FingerprintEntry::relativePath));
            byte[] buffer = new byte[FINGERPRINT_BUFFER_BYTES];
            for (FingerprintEntry entry : entries) {
                MessageDigest packDigest = entry.packName() == null
                        ? null
                        : packDigests.computeIfAbsent(entry.packName(), ignored -> newSha256Digest());
                updateFingerprintEntry(digest, entry.relativePath(), entry.size());
                if (packDigest != null) {
                    updateFingerprintEntry(packDigest, entry.packRelativePath(), entry.size());
                }
                long readBytes = 0L;
                try (InputStream input = Files.newInputStream(
                        entry.source(),
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                            if (packDigest != null) {
                                packDigest.update(buffer, 0, read);
                            }
                            readBytes += read;
                        }
                    }
                }
                if (readBytes != entry.size()) {
                    throw new IOException("Iris pack changed while fingerprinting: " + entry.source());
                }
            }
            Map<String, String> packContents = new LinkedHashMap<>();
            for (Map.Entry<String, MessageDigest> entry : packDigests.entrySet()) {
                packContents.put(entry.getKey(), HexFormat.of().formatHex(entry.getValue().digest()));
            }
            return new PackContentSnapshot(
                    HexFormat.of().formatHex(digest.digest()),
                    Map.copyOf(packContents));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to fingerprint Iris packs at " + root, exception);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String computePackTreeFingerprint(File packDir) {
        Path root = resolveFingerprintRoot(packDir);
        if (root == null) {
            return "";
        }
        try {
            List<FingerprintEntry> entries = new ArrayList<>();
            collectFingerprintTree(root.toRealPath(), "", null, entries);
            entries.sort(Comparator.comparing(FingerprintEntry::relativePath));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[FINGERPRINT_BUFFER_BYTES];
            for (FingerprintEntry entry : entries) {
                updateFingerprintEntry(digest, entry.relativePath(), entry.size());
                long readBytes = 0L;
                try (InputStream input = Files.newInputStream(
                        entry.source(),
                        StandardOpenOption.READ,
                        LinkOption.NOFOLLOW_LINKS)) {
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read > 0) {
                            digest.update(buffer, 0, read);
                            readBytes += read;
                        }
                    }
                }
                if (readBytes != entry.size()) {
                    throw new IOException("Iris pack changed while fingerprinting: " + entry.source());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to fingerprint Iris pack at " + root, exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static void updateFingerprintEntry(MessageDigest digest, String relativePath, long size) {
        byte[] relativeBytes = relativePath.getBytes(StandardCharsets.UTF_8);
        updateDigestInt(digest, relativeBytes.length);
        digest.update(relativeBytes);
        updateDigestLong(digest, size);
    }

    private static Path resolveFingerprintRoot(File packsDir) {
        if (packsDir == null) {
            return null;
        }
        Path root = packsDir.toPath().toAbsolutePath().normalize();
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isDirectory(root)) {
            if (Files.isSymbolicLink(root)) {
                throw new IllegalArgumentException("Iris packs root target is missing or unsafe: " + root);
            }
            return null;
        }
        return root;
    }

    private static List<FingerprintEntry> collectFingerprintEntries(Path root) throws IOException {
        List<FingerprintEntry> entries = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            for (Path child : children.toList()) {
                String childName = child.getFileName().toString();
                if (PackDirectoryResolver.isHiddenName(childName) || isNonContentPackFile(childName)) {
                    continue;
                }
                if (Files.isSymbolicLink(child)) {
                    if (!Files.isDirectory(child)) {
                        throw new IOException("Iris pack fingerprint rejected symbolic link: " + child);
                    }
                    PackDirectoryResolver.requireSafePackTree(child.toFile());
                    collectFingerprintTree(child.toRealPath(), childName, childName, entries);
                } else if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    collectFingerprintTree(child, childName, childName, entries);
                } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                    BasicFileAttributes attributes = Files.readAttributes(
                            child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    entries.add(new FingerprintEntry(
                            child,
                            childName,
                            null,
                            null,
                            attributes.size(),
                            attributes.lastModifiedTime().toMillis()));
                } else {
                    throw new IOException("Iris pack fingerprint rejected unsupported entry: " + child);
                }
            }
        }
        return entries;
    }

    private static void collectFingerprintTree(
            Path treeRoot,
            String logicalRoot,
            String packName,
            List<FingerprintEntry> entries
    ) throws IOException {
        Files.walkFileTree(treeRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
            ) {
                if (!directory.equals(treeRoot)
                        && treeRoot.relativize(directory).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                String fileName = file.getFileName().toString();
                if ((treeRoot.relativize(file).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(fileName))
                        || isNonContentPackFile(fileName)) {
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    throw new IOException("Iris pack fingerprint rejected symbolic link: " + file);
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("Iris pack fingerprint rejected unsupported entry: " + file);
                }
                String relative = treeRoot.relativize(file).toString().replace(File.separatorChar, '/');
                String logicalRelative = logicalRoot.isEmpty() ? relative : logicalRoot + "/" + relative;
                entries.add(new FingerprintEntry(
                        file,
                        logicalRelative,
                        packName,
                        packName == null ? null : relative,
                        attributes.size(),
                        attributes.lastModifiedTime().toMillis()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to inspect Iris pack entry: " + file, failure);
            }
        });
    }

    private static boolean isNonContentPackFile(String name) {
        return name != null && (name.equals(".DS_Store") || name.endsWith(CODE_WORKSPACE_SUFFIX));
    }

    private record FingerprintEntry(
            Path source,
            String relativePath,
            String packName,
            String packRelativePath,
            long size,
            long lastModifiedMillis
    ) {
    }

    private static void updateDigestInt(MessageDigest digest, int value) {
        for (int shift = Integer.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static void updateDigestLong(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    public record PackContentSnapshot(String content, Map<String, String> packContents) {
        public PackContentSnapshot {
            content = Objects.requireNonNullElse(content, "");
            packContents = Map.copyOf(Objects.requireNonNullElse(packContents, Map.of()));
        }
    }

}
