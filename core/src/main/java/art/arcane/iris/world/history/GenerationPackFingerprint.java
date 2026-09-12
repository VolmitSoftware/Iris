package art.arcane.iris.world.history;

import art.arcane.iris.pack.PackDirectoryResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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
import java.util.List;
import java.util.Objects;

public final class GenerationPackFingerprint {
    public static final int CURRENT_VERSION = 2;
    private static final int BUFFER_BYTES = 64 * 1_024;

    private GenerationPackFingerprint() {
    }

    public static String compute(Path packRoot, int version) throws IOException {
        requireSupported(version);
        return switch (version) {
            case 1 -> computeVersionOne(packRoot);
            case 2 -> computeTree(packRoot, true);
            default -> throw new IOException("Unsupported Iris generation pack fingerprint version " + version + ".");
        };
    }

    public static void requireSupported(int version) throws IOException {
        if (version != 1 && version != 2) {
            throw new IOException("Unsupported Iris generation pack fingerprint version " + version + ".");
        }
    }

    private static String computeVersionOne(Path packRoot) throws IOException {
        return computeTree(packRoot, false);
    }

    private static String computeTree(Path packRoot, boolean ignoreFinderMetadata) throws IOException {
        Path root = Objects.requireNonNull(packRoot, "packRoot").toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("Generation pack fingerprint root is missing or unsafe: " + root);
        }
        Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        List<FingerprintEntry> entries = new ArrayList<>();
        collectTree(realRoot, entries, ignoreFinderMetadata);
        entries.sort(Comparator.comparing(FingerprintEntry::relativePath));
        MessageDigest digest = sha256();
        byte[] buffer = new byte[BUFFER_BYTES];
        for (FingerprintEntry entry : entries) {
            updateEntry(digest, entry.relativePath(), entry.size());
            long readBytes = 0L;
            try (InputStream input = Files.newInputStream(
                    entry.source(),
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                        readBytes += read;
                    }
                }
            }
            if (readBytes != entry.size()) {
                throw new IOException("Iris generation pack changed while fingerprinting: "
                        + entry.source());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void collectTree(
            Path root,
            List<FingerprintEntry> entries,
            boolean ignoreFinderMetadata
    ) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(
                    Path directory,
                    BasicFileAttributes attributes
            ) throws IOException {
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                    throw new IOException("Generation pack fingerprint rejected symbolic link: " + directory);
                }
                if (!directory.equals(root)
                        && root.relativize(directory).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path relativePath = root.relativize(file);
                String fileName = file.getFileName().toString();
                if ((relativePath.getNameCount() == 1 && PackDirectoryResolver.isHiddenName(fileName))
                        || fileName.endsWith(".code-workspace")
                        || (ignoreFinderMetadata && fileName.equals(".DS_Store"))) {
                    return FileVisitResult.CONTINUE;
                }
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    throw new IOException("Generation pack fingerprint rejected symbolic link: " + file);
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("Generation pack fingerprint rejected unsupported entry: " + file);
                }
                entries.add(new FingerprintEntry(
                        file,
                        relativePath.toString().replace(File.separatorChar, '/'),
                        attributes.size()
                ));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to inspect Iris generation pack entry: " + file, failure);
            }
        });
    }

    private static void updateEntry(MessageDigest digest, String relativePath, long size) {
        byte[] relativeBytes = relativePath.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(relativeBytes.length).array());
        digest.update(relativeBytes);
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(size).array());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record FingerprintEntry(Path source, String relativePath, long size) {
    }
}
