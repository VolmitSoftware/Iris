package com.volmit.generation;

import com.volmit.ModuleLog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/** Tells failures of the engine, its executors or the JVM from failures of one piece of content. Code that places a
 * list of objects or decorations may skip a piece that fails, because every rebuild skips it the same way. An engine
 * failure is transient, so skipping it would leave a half-built chunk that a healthy engine never produces: it must
 * end the chunk instead.
 */
public final class GenerationFailures {
    private static final int MAXIMUM_CAUSE_DEPTH = 32;
    private static final int MAXIMUM_SKIP_KEYS = 512;
    private static final String OVERFLOW_SKIP_KEY = "overflow";
    private static final String PACKAGE_PREFIX = "art.arcane.";
    static final long SKIP_WARNING_INTERVAL_NANOS = 60_000_000_000L;
    private static final ConcurrentHashMap<String, SkipWarning> SKIP_WARNINGS = new ConcurrentHashMap<>();

    private GenerationFailures() {
    }

    /** Whether {@code failure} or one of its causes is an {@link Error}, an {@link EngineFailure}, an I/O or timeout
     * failure, or a stopping executor or thread; any failure on an interrupted thread counts too.
     */
    public static boolean isEngineFailure(Throwable failure) {
        return Thread.currentThread().isInterrupted() || hasCause(failure, cause -> cause instanceof Error
                || cause instanceof EngineFailure
                || cause instanceof IOException
                || cause instanceof UncheckedIOException
                || cause instanceof TimeoutException
                || cause instanceof InterruptedException
                || cause instanceof RejectedExecutionException
                || cause instanceof CancellationException);
    }

    /** Throws {@code failure} when it is an engine failure, so a catch block can call it first and then handle the
     * content failures that remain. A checked failure is wrapped in an {@link IllegalStateException}.
     */
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

    /** Ends one failed placement: an engine failure leaves through {@link #rethrowEngineFailure}, anything else is one
     * warning on {@code log} per minute for each {@code kind}, {@code subject} and failure type, counting the ones
     * skipped meanwhile, and the caller moves on. The limit only decides what is logged, never what is placed.
     *
     * @param kind    what was being placed, such as {@code decoration}
     * @param subject the pack content that failed, such as a block or an object name; it keys the warning, so it must
     *                not hold a coordinate
     * @param where   where it failed, such as {@code at 1,2,3}; it is only printed
     */
    public static void skipPlacement(ModuleLog log, Throwable failure, String kind, String subject, String where) {
        rethrowEngineFailure(failure);
        long suppressed = claimSkipWarning(kind + '|' + subject + '|' + failure.getClass().getName(), System.nanoTime());
        if (suppressed >= 0L) {
            log.warn(skipWarning(kind, subject, where, failure, suppressed));
        }
    }

    static long claimSkipWarning(String key, long now) {
        SkipWarning warning = SKIP_WARNINGS.get(key);
        if (warning == null) {
            String resolved = SKIP_WARNINGS.size() >= MAXIMUM_SKIP_KEYS ? OVERFLOW_SKIP_KEY : key;
            warning = SKIP_WARNINGS.computeIfAbsent(resolved, ignored -> new SkipWarning(now - SKIP_WARNING_INTERVAL_NANOS));
        }
        return warning.claim(now);
    }

    static String skipWarning(String kind, String subject, String where, Throwable failure, long suppressed) {
        String message = failure.getMessage() == null ? "" : ": " + failure.getMessage().replaceAll("\\s+", " ");
        StringBuilder line = new StringBuilder("Skipped ").append(kind).append(' ').append(subject)
                .append(' ').append(where).append(": ").append(failure.getClass().getSimpleName()).append(message);
        StackTraceElement origin = origin(failure);
        if (origin != null) {
            String className = origin.getClassName();
            line.append(" (").append(className.substring(className.lastIndexOf('.') + 1)).append('.')
                    .append(origin.getMethodName()).append(')');
        }
        if (suppressed > 0L) {
            line.append(", ").append(suppressed).append(" more");
        }
        return line.toString();
    }

    private static StackTraceElement origin(Throwable failure) {
        StackTraceElement[] frames = failure.getStackTrace();
        for (StackTraceElement frame : frames) {
            if (frame.getClassName().startsWith(PACKAGE_PREFIX)) {
                return frame;
            }
        }
        return frames.length == 0 ? null : frames[0];
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

    private static final class SkipWarning {
        private final AtomicLong last;
        private final AtomicLong suppressed = new AtomicLong();

        private SkipWarning(long start) {
            last = new AtomicLong(start);
        }

        private long claim(long now) {
            long previous = last.get();
            if (now - previous >= SKIP_WARNING_INTERVAL_NANOS && last.compareAndSet(previous, now)) {
                return suppressed.getAndSet(0L);
            }
            suppressed.incrementAndGet();
            return -1L;
        }
    }
}
