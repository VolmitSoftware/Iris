package art.arcane.iris.world.history;

import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.spi.IrisPlatforms;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GenerationPackRepository {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final int MAXIMUM_COPY_WORKERS = 4;

    private final Path dimensionRoot;
    private final Path generationRoot;
    private final Path epochsRoot;

    public GenerationPackRepository(Path dimensionRoot) {
        Path root = Objects.requireNonNull(dimensionRoot, "dimensionRoot")
                .toAbsolutePath()
                .normalize();
        this.dimensionRoot = root;
        generationRoot = root.resolve("iris").resolve("generation");
        epochsRoot = generationRoot.resolve("epochs");
    }

    public Path generationRoot() {
        return generationRoot;
    }

    public Path epochRoot(String epochId) {
        return epochsRoot.resolve(requireDigest(epochId, "epochId"));
    }

    public Path packRoot(String epochId) {
        return epochRoot(epochId).resolve("pack");
    }

    public synchronized Path publish(
            String epochId,
            String packFingerprint,
            int packFingerprintVersion,
            Path source
    ) throws IOException {
        String requiredEpochId = requireDigest(epochId, "epochId");
        String requiredFingerprint = requireDigest(packFingerprint, "packFingerprint");
        ensureEpochsRoot();
        try (GenerationPublicationLock ignored = GenerationPublicationLock.acquire(
                epochsRoot,
                ".epoch-" + requiredEpochId + ".lock"
        )) {
            ensureEpochsRoot();
            return publishLocked(requiredEpochId, requiredFingerprint, packFingerprintVersion, source);
        }
    }

    private Path publishLocked(
            String epochId,
            String packFingerprint,
            int packFingerprintVersion,
            Path source
    ) throws IOException {
        Path epochDirectory = ensureChildDirectory(epochsRoot, epochId);
        Path target = epochDirectory.resolve("pack");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return requireExactPack(epochId, packFingerprint, packFingerprintVersion);
        }

        Path stage = Files.createTempDirectory(epochDirectory, ".pack-");
        try {
            Files.delete(stage);
            copyPackTree(source, stage);
            requireFingerprint(stage, packFingerprint, packFingerprintVersion);
            try (AtomicDirectoryPublisher.Publication publication =
                         AtomicDirectoryPublisher.publishAbsent(stage, target)) {
                publication.commit();
            } catch (FileAlreadyExistsException race) {
                return requireExactPack(epochId, packFingerprint, packFingerprintVersion);
            }
            forceDirectory(epochDirectory);
            validateExistingAncestors(epochId);
            requireSafeDirectory(target, "Generation epoch pack is missing or unsafe");
            return target;
        } finally {
            if (Files.exists(stage, LinkOption.NOFOLLOW_LINKS)) {
                AtomicDirectoryPublisher.deleteTree(stage);
            }
        }
    }

    public Path requireExactPack(
            String epochId,
            String packFingerprint,
            int packFingerprintVersion
    ) throws IOException {
        Path pack = requireSafePackRoot(epochId);
        requireFingerprint(
                pack,
                requireDigest(packFingerprint, "packFingerprint"),
                packFingerprintVersion
        );
        return pack;
    }

    Path requireSafePackRoot(String epochId) throws IOException {
        String requiredEpochId = requireDigest(epochId, "epochId");
        validateExistingAncestors(requiredEpochId);
        Path epoch = epochRoot(requiredEpochId);
        requireSafeDirectory(epoch, "Generation epoch path is not a safe directory");
        Path pack = epoch.resolve("pack");
        requireSafeDirectory(pack, "Generation epoch pack is missing or unsafe");
        return pack;
    }

    private void ensureEpochsRoot() throws IOException {
        requireSafeDirectory(dimensionRoot, "Dimension root is not a safe directory");
        Path irisRoot = ensureChildDirectory(dimensionRoot, "iris");
        Path ensuredGenerationRoot = ensureChildDirectory(irisRoot, "generation");
        Path ensuredEpochsRoot = ensureChildDirectory(ensuredGenerationRoot, "epochs");
        if (!ensuredEpochsRoot.equals(epochsRoot)) {
            throw new IOException("Generation epoch repository resolved outside its storage path: " + ensuredEpochsRoot);
        }
    }

    private void validateExistingAncestors(String epochId) throws IOException {
        requireSafeDirectory(dimensionRoot, "Dimension root is not a safe directory");
        Path irisRoot = dimensionRoot.resolve("iris");
        requireSafeDirectory(irisRoot, "Generation epoch ancestor is not a safe directory");
        requireSafeDirectory(generationRoot, "Generation epoch ancestor is not a safe directory");
        requireSafeDirectory(epochsRoot, "Generation epoch ancestor is not a safe directory");
        Path epoch = epochsRoot.resolve(epochId);
        requireSafeDirectory(epoch, "Generation epoch path is not a safe directory");
    }

    private static Path ensureChildDirectory(Path parent, String name) throws IOException {
        Path child = parent.resolve(name);
        if (Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
            requireSafeDirectory(child, "Generation epoch ancestor is not a safe directory");
            forceDirectory(parent);
            return child;
        }
        try {
            Files.createDirectory(child);
        } catch (FileAlreadyExistsException race) {
            requireSafeDirectory(child, "Generation epoch ancestor is not a safe directory");
            forceDirectory(parent);
            return child;
        }
        forceDirectory(parent);
        requireSafeDirectory(child, "Generation epoch ancestor is not a safe directory");
        return child;
    }

    private static void requireSafeDirectory(Path directory, String message) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(message + ": " + directory);
        }
        BasicFileAttributes attributes = Files.readAttributes(
                directory,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException(message + ": " + directory);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        if (File.separatorChar == '\\') {
            return;
        }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException exception) {
            throw new IOException("Generation epoch directory cannot be durability-synced: " + directory, exception);
        }
    }

    public static void copyPackTree(Path source, Path target) throws IOException {
        Executor executor = IrisPlatforms.isBound() ? MultiBurst.ioBurst : ForkJoinPool.commonPool();
        int workers = Math.max(1, Math.min(MAXIMUM_COPY_WORKERS, Runtime.getRuntime().availableProcessors()));
        copyPackTree(source, target, executor, workers);
    }

    static void copyPackTree(Path source, Path target, Executor executor, int workers) throws IOException {
        Objects.requireNonNull(executor, "executor");
        if (workers < 1 || workers > MAXIMUM_COPY_WORKERS) {
            throw new IllegalArgumentException("Pack copy workers must be between 1 and " + MAXIMUM_COPY_WORKERS + ".");
        }
        Path sourcePath = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(sourcePath)) {
            throw new IOException("Pack source is a symbolic link: " + sourcePath);
        }
        Path normalizedSource = sourcePath.toRealPath();
        if (!Files.isDirectory(normalizedSource, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Pack source is not a directory: " + normalizedSource);
        }
        Path normalizedTarget = Objects.requireNonNull(target, "target").toAbsolutePath().normalize();
        if (Files.exists(normalizedTarget, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalizedTarget)) {
            throw new IOException("Pack publication stage already exists: " + normalizedTarget);
        }
        if (normalizedTarget.startsWith(normalizedSource) || normalizedSource.startsWith(normalizedTarget)) {
            throw new IOException("Pack source and publication stage overlap: "
                    + normalizedSource + " and " + normalizedTarget);
        }

        List<Path> sourceFiles = new ArrayList<>();
        Files.walkFileTree(normalizedSource, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(directory)) {
                    throw new IOException("Pack contains a symbolic link: " + directory);
                }
                if (!directory.equals(normalizedSource)
                        && normalizedSource.relativize(directory).getNameCount() == 1
                        && PackDirectoryResolver.isHiddenName(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(copyDestination(normalizedSource, normalizedTarget, directory));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (attributes.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    throw new IOException("Pack contains a symbolic link: " + file);
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("Pack contains an unsupported entry: " + file);
                }
                Path relative = normalizedSource.relativize(file);
                String fileName = file.getFileName().toString();
                if ((relative.getNameCount() == 1 && PackDirectoryResolver.isHiddenName(fileName))
                        || fileName.endsWith(".code-workspace")) {
                    return FileVisitResult.CONTINUE;
                }
                sourceFiles.add(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                throw new IOException("Unable to copy pack entry: " + file, failure);
            }
        });
        executeFilePhase(sourceFiles, executor, workers, sourceFile ->
                Files.copy(sourceFile, copyDestination(normalizedSource, normalizedTarget, sourceFile),
                        StandardCopyOption.COPY_ATTRIBUTES));
        executeFilePhase(sourceFiles, executor, workers, sourceFile ->
                forceFile(copyDestination(normalizedSource, normalizedTarget, sourceFile)));
    }

    static void forceDirectoryTree(Path root, FileAction force) throws IOException {
        Executor executor = IrisPlatforms.isBound() ? MultiBurst.ioBurst : ForkJoinPool.commonPool();
        int workers = Math.max(1, Math.min(MAXIMUM_COPY_WORKERS, Runtime.getRuntime().availableProcessors()));
        forceDirectoryTree(root, executor, workers, force);
    }

    static void forceDirectoryTree(Path root, Executor executor, int workers, FileAction force) throws IOException {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(force, "force");
        if (workers < 1 || workers > MAXIMUM_COPY_WORKERS) {
            throw new IllegalArgumentException("Directory sync workers must be between 1 and " + MAXIMUM_COPY_WORKERS + ".");
        }
        List<Path> directories;
        try (Stream<Path> entries = Files.walk(root)) {
            directories = entries
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .toList();
        }
        int start = 0;
        while (start < directories.size()) {
            int end = start + 1;
            int depth = directories.get(start).getNameCount();
            while (end < directories.size() && directories.get(end).getNameCount() == depth) {
                end++;
            }
            executeFilePhase(directories.subList(start, end), executor, workers, force);
            start = end;
        }
    }

    private static void executeFilePhase(
            List<Path> files, Executor executor, int workers, FileAction action
    ) throws IOException {
        FileWork work = new FileWork(files, action, new AtomicInteger(), new AtomicBoolean());
        int workerCount = Math.min(workers, files.size());
        List<CompletableFuture<Void>> copies = new ArrayList<>(workerCount);
        for (int worker = 0; worker < workerCount; worker++) {
            FileTask task = new FileTask(work);
            copies.add(task.finished);
            try {
                executor.execute(task);
            } catch (RuntimeException | Error failure) {
                task.reject(failure);
                break;
            }
        }
        awaitFileTasks(copies);
    }

    private static void processFiles(FileWork work) throws IOException {
        int index;
        while (!work.failed().get() && (index = work.next().getAndIncrement()) < work.files().size()) {
            work.action().run(work.files().get(index));
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void awaitFileTasks(List<CompletableFuture<Void>> copies) throws IOException {
        CompletableFuture.allOf(copies.toArray(CompletableFuture[]::new))
                .handle((ignored, failure) -> null).join();
        Throwable failure = null;
        for (CompletableFuture<Void> copy : copies) {
            try {
                copy.join();
            } catch (CompletionException | CancellationException exception) {
                Throwable cause = exception instanceof CompletionException ? exception.getCause() : exception;
                if (failure == null) {
                    failure = cause;
                } else if (failure != cause) {
                    failure.addSuppressed(cause);
                }
            }
        }
        if (failure instanceof IOException exception) {
            throw exception;
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IOException("Generation pack file operation failed.", failure);
        }
    }

    private static Path copyDestination(Path source, Path target, Path entry) throws IOException {
        Path destination = target.resolve(source.relativize(entry)).normalize();
        if (!destination.startsWith(target)) {
            throw new IOException("Pack entry escapes its publication stage: " + entry);
        }
        return destination;
    }

    private static void requireFingerprint(
            Path pack,
            String expected,
            int packFingerprintVersion
    ) throws IOException {
        String actual = GenerationPackFingerprint.compute(pack, packFingerprintVersion);
        if (!expected.equals(actual)) {
            throw new IOException("Generation epoch pack fingerprint mismatch at " + pack
                    + ": expected " + expected + " but found " + actual);
        }
    }

    private static String requireDigest(String value, String field) {
        String requiredValue = Objects.requireNonNull(value, field);
        if (!SHA_256.matcher(requiredValue).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return requiredValue;
    }

    @FunctionalInterface
    interface FileAction {
        void run(Path file) throws IOException;
    }

    private record FileWork(List<Path> files, FileAction action, AtomicInteger next, AtomicBoolean failed) {
    }

    private static final class FileTask extends RecursiveAction implements Runnable {
        private final FileWork work;
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private boolean started;
        private Throwable failure;

        private FileTask(FileWork work) {
            this.work = work;
        }

        @Override
        public void run() {
            quietlyInvoke();
        }

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (finished.isDone() || !super.cancel(mayInterruptIfRunning)) {
                return false;
            }
            reject(new CancellationException("Generation pack file worker was cancelled."));
            return true;
        }

        @Override
        protected void compute() {
            synchronized (this) {
                if (started || failure != null) {
                    return;
                }
                started = true;
            }
            Throwable fileFailure = null;
            try {
                processFiles(work);
            } catch (Throwable caught) {
                fileFailure = caught;
            } finally {
                finish(fileFailure);
            }
        }

        private synchronized void reject(Throwable failure) {
            work.failed().set(true);
            if (this.failure == null) {
                this.failure = failure;
            } else if (this.failure != failure) {
                this.failure.addSuppressed(failure);
            }
            if (!started) {
                finished.completeExceptionally(this.failure);
            }
        }

        private synchronized void finish(Throwable fileFailure) {
            if (fileFailure != null) {
                reject(fileFailure);
            }
            if (failure == null) {
                finished.complete(null);
            } else {
                super.completeExceptionally(failure);
                finished.completeExceptionally(failure);
            }
        }
    }
}
