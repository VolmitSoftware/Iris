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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class GenerationPackFingerprint {
    public static final int CURRENT_VERSION = 2;
    private static final int BUFFER_BYTES = 64 * 1_024;
    private static final int PREFETCH_FILE_BYTES = 256 * 1_024;
    private static final int PREFETCH_BATCH_FILES = 64;
    private static final int MAXIMUM_PREFETCH_WORKERS = 4;

    private GenerationPackFingerprint() {
    }

    public static String compute(Path packRoot, int version) throws IOException {
        return computeWithInput(packRoot, version, GenerationPackFingerprint::openInput);
    }

    static String computeWithInput(Path packRoot, int version, InputSource inputSource) throws IOException {
        requireSupported(version);
        return computeTree(packRoot, version == 2, Objects.requireNonNull(inputSource, "Fingerprint input source"));
    }

    public static void requireSupported(int version) throws IOException {
        if (version != 1 && version != 2) {
            throw new IOException("Unsupported Iris generation pack fingerprint version " + version + ".");
        }
    }

    private static String computeTree(Path packRoot, boolean ignoreFinderMetadata, InputSource inputSource) throws IOException {
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
        int workers = Math.min(MAXIMUM_PREFETCH_WORKERS, Runtime.getRuntime().availableProcessors());
        if (workers <= 1 || !hasPrefetchBatch(entries)) {
            for (FingerprintEntry entry : entries) {
                updateEntry(digest, entry.relativePath(), entry.size());
                streamEntry(digest, entry, buffer, inputSource);
            }
        } else {
            try (ExecutorService readers = Executors.newFixedThreadPool(workers,
                    Thread.ofPlatform().name("Iris Pack Fingerprint-", 0L).factory())) {
                for (int start = 0; start < entries.size(); start += PREFETCH_BATCH_FILES) {
                    List<FingerprintEntry> batch = entries.subList(start,
                            Math.min(entries.size(), start + PREFETCH_BATCH_FILES));
                    PrefetchedBatch prefetched = prefetchBatch(batch, inputSource, readers, workers, root);
                    for (int index = 0; index < batch.size(); index++) {
                        FingerprintEntry entry = batch.get(index);
                        try {
                            updateEntry(digest, entry.relativePath(), entry.size());
                            requireSuccessfulRead(prefetched.failures()[index]);
                            byte[] bytes = prefetched.contents()[index];
                            if (bytes == null) {
                                streamEntry(digest, entry, buffer, inputSource);
                            } else {
                                digest.update(bytes);
                            }
                        } catch (IOException | RuntimeException | Error failure) {
                            suppressReadFailures(failure, prefetched.failures());
                            throw failure;
                        }
                    }
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean hasPrefetchBatch(List<FingerprintEntry> entries) {
        int smallFiles = 0;
        for (FingerprintEntry entry : entries) {
            if (entry.size() <= PREFETCH_FILE_BYTES && ++smallFiles >= PREFETCH_BATCH_FILES) {
                return true;
            }
        }
        return false;
    }

    private static PrefetchedBatch prefetchBatch(List<FingerprintEntry> entries, InputSource inputSource,
                                                ExecutorService readers, int workers, Path root) throws IOException {
        PrefetchedBatch batch = new PrefetchedBatch(new byte[entries.size()][], new Throwable[entries.size()]);
        List<Callable<Void>> tasks = new ArrayList<>(workers);
        for (int worker = 0; worker < workers; worker++) {
            int first = worker;
            tasks.add(() -> {
                readBatch(entries, batch, inputSource, first, workers);
                return null;
            });
        }
        try {
            for (Future<Void> task : readers.invokeAll(tasks)) {
                task.get();
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fingerprinting " + root, failure);
        } catch (ExecutionException failure) {
            throw new IOException("Unable to prefetch generation pack " + root, failure.getCause());
        }
        return batch;
    }

    private static void readBatch(List<FingerprintEntry> entries, PrefetchedBatch batch,
                                  InputSource inputSource, int first, int workers) {
        for (int index = first; index < entries.size(); index += workers) {
            FingerprintEntry entry = entries.get(index);
            if (entry.size() > PREFETCH_FILE_BYTES) {
                continue;
            }
            try {
                batch.contents()[index] = readSmall(entry, inputSource);
            } catch (Throwable failure) {
                batch.failures()[index] = failure;
            }
        }
    }

    private static byte[] readSmall(FingerprintEntry entry, InputSource inputSource) throws IOException {
        byte[] bytes = new byte[(int) entry.size()];
        int offset = 0;
        try (InputStream input = inputSource.open(entry.source())) {
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    throw changed(entry);
                }
                offset += read;
            }
            if (input.read() != -1) {
                throw changed(entry);
            }
        }
        return bytes;
    }

    private static void streamEntry(MessageDigest digest, FingerprintEntry entry, byte[] buffer,
                                    InputSource inputSource) throws IOException {
        long readBytes = 0L;
        try (InputStream input = inputSource.open(entry.source())) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                    readBytes += read;
                }
            }
        }
        if (readBytes != entry.size()) {
            throw changed(entry);
        }
    }

    static InputStream openInput(Path source) throws IOException {
        return Files.newInputStream(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    private static void requireSuccessfulRead(Throwable failure) throws IOException {
        if (failure == null) {
            return;
        }
        if (failure instanceof IOException ioFailure) {
            throw ioFailure;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IOException("Unable to read generation pack entry", failure);
    }

    private static void suppressReadFailures(Throwable primary, Throwable[] failures) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(primary);
        Collections.addAll(seen, primary.getSuppressed());
        for (Throwable failure : failures) {
            if (failure != null && seen.add(failure)) {
                primary.addSuppressed(failure);
            }
        }
    }

    private static IOException changed(FingerprintEntry entry) {
        return new IOException("Iris generation pack changed while fingerprinting: " + entry.source());
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

    @FunctionalInterface
    interface InputSource {
        InputStream open(Path source) throws IOException;
    }

    private record PrefetchedBatch(byte[][] contents, Throwable[] failures) {
    }

    private record FingerprintEntry(Path source, String relativePath, long size) {
    }
}
