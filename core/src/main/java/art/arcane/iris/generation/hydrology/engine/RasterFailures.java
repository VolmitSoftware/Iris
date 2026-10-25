package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.spi.IrisLogging;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class RasterFailures {
    static final long BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(30L);
    static final int TRACE_BUDGET = 16;
    private static final int CAPACITY = 4096;

    private final LongSupplier ticker;
    private final Map<Long, Long> failedAt = new HashMap<>();
    private long windowStart;
    private int traces;
    private int suppressed;

    RasterFailures(LongSupplier ticker) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.windowStart = ticker.getAsLong();
    }

    boolean backingOff(long chunk) {
        boolean backingOff;
        int overdue;
        synchronized (this) {
            Long failed = failedAt.get(chunk);
            if (failed == null && suppressed == 0) {
                return false;
            }
            long now = ticker.getAsLong();
            overdue = closedWindowSummary(now);
            backingOff = failed != null && now - failed < BACKOFF_NANOS;
            if (failed != null && !backingOff) {
                failedAt.remove(chunk);
            }
        }
        summarize(overdue);
        return backingOff;
    }

    void failed(long chunk) {
        int overdue;
        synchronized (this) {
            long now = ticker.getAsLong();
            overdue = closedWindowSummary(now);
            mark(chunk, now);
        }
        summarize(overdue);
    }

    void report(long chunk, String message, Throwable failure) {
        int overdue;
        boolean trace;
        synchronized (this) {
            long now = ticker.getAsLong();
            overdue = closedWindowSummary(now);
            Long previous = mark(chunk, now);
            trace = (previous == null || now - previous >= BACKOFF_NANOS) && admitTrace(now);
        }
        summarize(overdue);
        if (trace) {
            IrisLogging.reportError(message, failure);
        }
    }

    private int closedWindowSummary(long now) {
        if (suppressed == 0 || now - windowStart < BACKOFF_NANOS) {
            return 0;
        }
        int overdue = suppressed;
        suppressed = 0;
        return overdue;
    }

    private boolean admitTrace(long now) {
        if (now - windowStart >= BACKOFF_NANOS) {
            windowStart = now;
            traces = 0;
        }
        if (traces < TRACE_BUDGET) {
            traces++;
            return true;
        }
        suppressed++;
        return false;
    }

    private static void summarize(int overdue) {
        if (overdue > 0) {
            IrisLogging.warn("Hydrology water rasters failed for %d more chunks in the last 30 s; their traces were not logged.", overdue);
        }
    }

    private Long mark(long chunk, long now) {
        Long previous = failedAt.put(chunk, now);
        if (failedAt.size() > CAPACITY) {
            prune(now);
        }
        return previous;
    }

    private void prune(long now) {
        Iterator<Map.Entry<Long, Long>> entries = failedAt.entrySet().iterator();
        while (entries.hasNext()) {
            if (now - entries.next().getValue() >= BACKOFF_NANOS) {
                entries.remove();
            }
        }
        if (failedAt.size() > CAPACITY) {
            failedAt.clear();
        }
    }
}
