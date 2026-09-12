package art.arcane.iris.pack;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

public final class AtomicDirectoryPublisher {
    private AtomicDirectoryPublisher() {
    }

    public static Publication publish(Path stagedDirectory, Path targetDirectory) throws IOException {
        Path staged = Objects.requireNonNull(stagedDirectory, "stagedDirectory").toAbsolutePath().normalize();
        Path target = Objects.requireNonNull(targetDirectory, "targetDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(staged) || Files.isSymbolicLink(staged)) {
            throw new IOException("Staged directory is missing or unsafe: " + staged);
        }
        if (!Objects.equals(staged.getParent(), target.getParent())) {
            throw new IOException("Staged and target directories must have the same parent.");
        }

        Path backup = null;
        if (Files.exists(target) || Files.isSymbolicLink(target)) {
            backup = target.resolveSibling("." + target.getFileName() + ".backup-" + UUID.randomUUID());
            move(target, backup);
        }
        try {
            move(staged, target);
            return new Publication(target, backup);
        } catch (IOException failure) {
            if (backup != null && (Files.exists(backup) || Files.isSymbolicLink(backup))) {
                try {
                    move(backup, target);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    public static Publication publishAbsent(Path stagedDirectory, Path targetDirectory) throws IOException {
        Path staged = Objects.requireNonNull(stagedDirectory, "stagedDirectory").toAbsolutePath().normalize();
        Path target = Objects.requireNonNull(targetDirectory, "targetDirectory").toAbsolutePath().normalize();
        if (!Files.isDirectory(staged) || Files.isSymbolicLink(staged)) {
            throw new IOException("Staged directory is missing or unsafe: " + staged);
        }
        if (!Objects.equals(staged.getParent(), target.getParent())) {
            throw new IOException("Staged and target directories must have the same parent.");
        }
        if (Files.exists(target) || Files.isSymbolicLink(target)) {
            throw new FileAlreadyExistsException("Publication target already exists: " + target);
        }
        move(staged, target);
        return new Publication(target, null);
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    public static final class Publication implements AutoCloseable {
        private final Path target;
        private final Path backup;
        private boolean committed;
        private boolean closed;

        private Publication(Path target, Path backup) {
            this.target = target;
            this.backup = backup;
        }

        public synchronized void commit() {
            if (closed) {
                throw new IllegalStateException("Directory publication is already closed.");
            }
            committed = true;
        }

        public synchronized void cleanupBackup() throws IOException {
            if (!committed) {
                throw new IllegalStateException("Directory publication is not committed.");
            }
            if (backup != null) {
                deleteTree(backup);
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (committed) {
                return;
            }

            deleteTree(target);
            if (backup != null && (Files.exists(backup) || Files.isSymbolicLink(backup))) {
                move(backup, target);
            }
        }
    }

    public static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path) && !Files.isSymbolicLink(path)) {
            return;
        }
        if (Files.isSymbolicLink(path) || !Files.isDirectory(path)) {
            Files.delete(path);
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }
}
