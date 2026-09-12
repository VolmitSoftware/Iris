package art.arcane.iris.platform.bukkit.nms;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class ServerShutdownBoundary {
    private static final long MAX_JOIN_SLICE_MILLIS = 1000L;

    private final BooleanSupplier boundaryReached;
    private final Thread serverThread;

    public ServerShutdownBoundary(BooleanSupplier boundaryReached, Thread serverThread) {
        this.boundaryReached = Objects.requireNonNull(boundaryReached, "Server shutdown boundary");
        this.serverThread = Objects.requireNonNull(serverThread, "Server thread");
        this.boundaryReached.getAsBoolean();
    }

    public boolean await(long timeout, TimeUnit unit) {
        TimeUnit activeUnit = Objects.requireNonNull(unit, "Server shutdown timeout unit");
        if (boundaryReached.getAsBoolean()) {
            return true;
        }
        if (serverThread == Thread.currentThread()) {
            return false;
        }

        long timeoutNanos = Math.max(0L, activeUnit.toNanos(timeout));
        long started = System.nanoTime();
        boolean interrupted = false;
        while (!boundaryReached.getAsBoolean()) {
            long remaining = timeoutNanos - (System.nanoTime() - started);
            if (remaining <= 0L || !serverThread.isAlive()) {
                restoreInterrupt(interrupted);
                return boundaryReached.getAsBoolean();
            }

            long joinMillis = Math.max(
                    1L,
                    Math.min(MAX_JOIN_SLICE_MILLIS, TimeUnit.NANOSECONDS.toMillis(remaining))
            );
            try {
                serverThread.join(joinMillis);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        restoreInterrupt(interrupted);
        return true;
    }

    private static void restoreInterrupt(boolean interrupted) {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
