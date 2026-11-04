package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.volmlib.util.mantle.MantleClosedException;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Separates failures of the engine, its mantle, generation history, executors or the JVM from failures of one
 * piece of content. Content loops (objects, decorators, layers) may skip a failed item because every rebuild
 * skips it the same way. An engine failure is transient, so skipping would cache and persist terrain that a
 * healthy engine never produces: it must fail the whole chunk instead.
 */
public final class GenerationFailures {
    private static final int MAXIMUM_CAUSE_DEPTH = 32;
    private static final int MAXIMUM_SKIP_KEYS = 512;
    private static final String OVERFLOW_SKIP_KEY = "overflow";
    static final long SKIP_WARNING_NANOS = 60_000_000_000L;
    private static final ConcurrentHashMap<String, SkipWarning> SKIP_WARNINGS = new ConcurrentHashMap<>();

    private GenerationFailures() {
    }

    public static boolean isEngineFailure(Throwable failure) {
        return isShutdownFailure(failure) || hasCause(failure, (Throwable cause) -> cause instanceof Error
                || cause instanceof WrongEngineBroException
                || cause instanceof ProvisionalAnswerException
                || cause instanceof SavedBiomeUnavailableException unavailable && unavailable.isLoading()
                || cause instanceof GenerationSessionException
                || cause instanceof IOException
                || cause instanceof UncheckedIOException
                || cause instanceof TimeoutException);
    }

    /**
     * The engine failures that only mean a runtime, pool or thread is stopping. They say nothing about the world, so
     * nothing may remember them against it once the next runtime is up.
     */
    public static boolean isShutdownFailure(Throwable failure) {
        return Thread.currentThread().isInterrupted() || hasCause(failure, (Throwable cause) ->
                cause instanceof GenerationClosedException
                        || cause instanceof MantleClosedException
                        || cause instanceof GenerationSessionException session && session.isExpectedTeardown()
                        || cause instanceof InterruptedException
                        || cause instanceof InterruptedIOException
                        || cause instanceof ClosedChannelException
                        || cause instanceof RejectedExecutionException
                        || cause instanceof CancellationException);
    }

    private static boolean hasCause(Throwable failure, Predicate<Throwable> match) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAXIMUM_CAUSE_DEPTH; depth++, current = current.getCause()) {
            if (match.test(current)) {
                return true;
            }
        }
        return false;
    }

    public static void rethrowEngineFailure(Throwable failure) {
        if (!isEngineFailure(failure)) {
            return;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("Iris generation stopped: " + failure, failure);
    }

    /**
     * Ends one failed placement: an engine failure leaves through {@link #rethrowEngineFailure}, anything else is one
     * rate-limited warning and the caller moves on. The warning state only decides what is logged, never what is
     * placed, so a skip gives the same blocks in any chunk order.
     */
    public static void skipPlacement(Throwable failure, String kind, String block, int x, int y, int z, String biome) {
        rethrowEngineFailure(failure);
        String key = kind + '|' + block + '|' + failure.getClass().getName();
        long suppressed = claimSkipWarning(key, System.nanoTime());
        if (suppressed >= 0L) {
            IrisLogging.warn(skipWarning(kind, block, x, y, z, biome, failure, suppressed));
        }
    }

    static long claimSkipWarning(String key, long now) {
        SkipWarning warning = SKIP_WARNINGS.get(key);
        if (warning == null) {
            String resolved = SKIP_WARNINGS.size() >= MAXIMUM_SKIP_KEYS ? OVERFLOW_SKIP_KEY : key;
            warning = SKIP_WARNINGS.computeIfAbsent(resolved, ignored -> new SkipWarning(now - SKIP_WARNING_NANOS));
        }
        return warning.claim(now);
    }

    static String skipWarning(String kind, String block, int x, int y, int z, String biome, Throwable failure, long suppressed) {
        String message = failure.getMessage() == null ? "" : ": " + failure.getMessage().replaceAll("\\s+", " ");
        StringBuilder line = new StringBuilder("Skipped ").append(kind).append(' ').append(block)
                .append(" at ").append(x).append(',').append(y).append(',').append(z)
                .append(" biome ").append(biome).append(": ").append(failure.getClass().getSimpleName()).append(message);
        StackTraceElement[] frames = failure.getStackTrace();
        if (frames.length > 0) {
            String file = frames[0].getFileName() == null ? frames[0].getClassName() : frames[0].getFileName().replace(".java", "");
            line.append(" (").append(file).append(':').append(frames[0].getLineNumber()).append(')');
        }
        if (suppressed > 0L) {
            line.append(", ").append(suppressed).append(" more");
        }
        return line.toString();
    }

    private static final class SkipWarning {
        private final AtomicLong last;
        private final AtomicLong suppressed = new AtomicLong();

        private SkipWarning(long start) {
            last = new AtomicLong(start);
        }

        private long claim(long now) {
            long previous = last.get();
            if (now - previous >= SKIP_WARNING_NANOS && last.compareAndSet(previous, now)) {
                return suppressed.getAndSet(0L);
            }
            suppressed.incrementAndGet();
            return -1L;
        }
    }
}
