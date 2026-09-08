package art.arcane.iris.testsupport;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public final class Await {
    private static final long POLL_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(10L);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5L);

    private Await() {
    }

    public static void until(String what, BooleanSupplier condition) {
        until(what, DEFAULT_TIMEOUT, condition);
    }

    public static void until(String what, Duration timeout, BooleanSupplier condition) {
        if (!reached(what, timeout, condition)) {
            throw new AssertionError("Timed out after " + timeout.toMillis() + "ms waiting for " + what);
        }
    }

    public static boolean reached(String what, Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (condition.getAsBoolean()) {
                return true;
            }
            if (System.nanoTime() - deadline >= 0L) {
                return false;
            }
            LockSupport.parkNanos(POLL_INTERVAL_NANOS);
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + what);
            }
        }
    }
}
