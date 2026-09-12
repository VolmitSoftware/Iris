package art.arcane.iris.platform.bukkit.terrain;

import java.util.concurrent.atomic.AtomicLong;

public final class IrisApiFaultGuard {
    private static final long NEVER = Long.MIN_VALUE;

    private final long reportIntervalMillis;
    private final AtomicLong faults = new AtomicLong();
    private final AtomicLong lastReportedAt = new AtomicLong(NEVER);

    public IrisApiFaultGuard(long reportIntervalMillis) {
        if (reportIntervalMillis < 0L) {
            throw new IllegalArgumentException("reportIntervalMillis must not be negative");
        }
        this.reportIntervalMillis = reportIntervalMillis;
    }

    public long faults() {
        return faults.get();
    }

    public boolean record(long nowMillis) {
        faults.incrementAndGet();
        long last = lastReportedAt.get();
        if (last != NEVER && nowMillis - last < reportIntervalMillis) {
            return false;
        }
        return lastReportedAt.compareAndSet(last, nowMillis);
    }
}
