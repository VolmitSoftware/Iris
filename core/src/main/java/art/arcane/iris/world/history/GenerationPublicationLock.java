package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

final class GenerationPublicationLock implements AutoCloseable {
    private static final ConcurrentHashMap<Path, ReentrantLock> PROCESS_LOCKS = new ConcurrentHashMap<>();

    private final ReentrantLock processLock;
    private final FileChannel channel;
    private final FileLock fileLock;

    private GenerationPublicationLock(ReentrantLock processLock, FileChannel channel, FileLock fileLock) {
        this.processLock = processLock;
        this.channel = channel;
        this.fileLock = fileLock;
    }

    static GenerationPublicationLock acquire(Path directory, String fileName) throws IOException {
        Path requiredDirectory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
        Path lockPath = requiredDirectory.resolve(Objects.requireNonNull(fileName, "fileName")).normalize();
        if (!Objects.equals(lockPath.getParent(), requiredDirectory)) {
            throw new IOException("Generation publication lock escapes its storage directory: " + lockPath);
        }
        requireSafeDirectory(requiredDirectory);
        ReentrantLock processLock = PROCESS_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
        processLock.lock();
        FileChannel channel = null;
        FileLock fileLock = null;
        try {
            channel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS
            );
            requireSafeRegularFile(lockPath);
            fileLock = channel.lock();
            return new GenerationPublicationLock(processLock, channel, fileLock);
        } catch (IOException | RuntimeException error) {
            if (fileLock != null) {
                fileLock.close();
            }
            if (channel != null) {
                channel.close();
            }
            processLock.unlock();
            throw error;
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        try {
            fileLock.close();
        } catch (IOException error) {
            failure = error;
        }
        try {
            channel.close();
        } catch (IOException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        } finally {
            processLock.unlock();
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static void requireSafeDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                directory,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("Generation publication lock directory is unsafe: " + directory);
        }
    }

    private static void requireSafeRegularFile(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException("Generation publication lock file is unsafe: " + file);
        }
    }
}
