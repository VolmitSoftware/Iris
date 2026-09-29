package art.arcane.iris.world.storage;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Group commit for append-only stores: writers record which files and directory entries they
 * changed, and one background pass forces them shortly afterwards, outside every store lock.
 * {@link #sync()} is the durability barrier for saves, shutdown and pregeneration completion.
 */
public final class DeferredDurability {
    public static final long DEFAULT_FLUSH_DELAY_MILLIS = 2_000L;
    private static final ScheduledThreadPoolExecutor FLUSHER = createFlusher();

    private final Set<Path> files = ConcurrentHashMap.newKeySet();
    private final Set<Path> directories = ConcurrentHashMap.newKeySet();
    private final ReentrantLock flushLock = new ReentrantLock();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final long flushDelayMillis;
    private final Consumer<IOException> backgroundFailure;

    public DeferredDurability(long flushDelayMillis, Consumer<IOException> backgroundFailure) {
        if (flushDelayMillis < 0L) {
            throw new IllegalArgumentException("Flush delay must not be negative.");
        }
        this.flushDelayMillis = flushDelayMillis;
        this.backgroundFailure = Objects.requireNonNull(backgroundFailure, "backgroundFailure");
    }

    public void written(Path file) {
        if (!Durability.enabled()) {
            return;
        }
        files.add(file);
        schedule();
    }

    public void linked(Path directory) {
        if (!Durability.enabled() || File.separatorChar == '\\') {
            return;
        }
        directories.add(directory);
        schedule();
    }

    public void sync() throws IOException {
        flushLock.lock();
        try {
            IOException failure = null;
            for (Path file : files) {
                files.remove(file);
                try {
                    forceFile(file);
                } catch (IOException fileFailure) {
                    failure = append(failure, fileFailure);
                }
            }
            for (Path directory : directories) {
                directories.remove(directory);
                try {
                    forceDirectory(directory);
                } catch (IOException directoryFailure) {
                    failure = append(failure, directoryFailure);
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            flushLock.unlock();
        }
    }

    private void schedule() {
        if (scheduled.compareAndSet(false, true)) {
            FLUSHER.schedule(this::flushInBackground, flushDelayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void flushInBackground() {
        scheduled.set(false);
        try {
            sync();
        } catch (IOException failure) {
            backgroundFailure.accept(failure);
        } catch (RuntimeException failure) {
            backgroundFailure.accept(new IOException("Deferred durability sync failed unexpectedly.", failure));
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            Durability.force(channel);
        } catch (NoSuchFileException replaced) {
            // Compaction or rewrite already published this file's contents durably under another name.
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            Durability.force(channel);
        } catch (UnsupportedOperationException failure) {
            throw new IOException("Directory cannot be durability-synced: " + directory, failure);
        }
    }

    private static IOException append(IOException failure, IOException additional) {
        if (failure == null) {
            return additional;
        }
        failure.addSuppressed(additional);
        return failure;
    }

    private static ScheduledThreadPoolExecutor createFlusher() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Iris Durability Flush");
            thread.setDaemon(true);
            return thread;
        });
        executor.setKeepAliveTime(30L, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }
}
