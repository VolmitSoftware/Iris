package art.arcane.iris.generation.hydrology;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

final class HydrologyPlanningAdmission {
    private static final long BYTES_PER_ROOT = 2L * 1024L * 1024L * 1024L;
    private static final int MAXIMUM_ROOTS = rootLimit(
            Runtime.getRuntime().maxMemory(), Runtime.getRuntime().availableProcessors());
    private static final int MAXIMUM_TRIALS = trialLimit(Runtime.getRuntime().maxMemory(), MAXIMUM_ROOTS);
    private static final HydrologyPlanningAdmission ROOTS = new HydrologyPlanningAdmission(MAXIMUM_ROOTS);

    private final Semaphore permits;

    HydrologyPlanningAdmission(int maximumRoots) {
        if (maximumRoots < 1) {
            throw new IllegalArgumentException("Maximum hydrology roots must be positive.");
        }
        permits = new Semaphore(maximumRoots, true);
    }

    static Permit acquireRoot(BooleanSupplier cancelled) {
        return ROOTS.acquire(cancelled);
    }

    static int maximumRoots() {
        return MAXIMUM_ROOTS;
    }

    static int maximumTrials() {
        return MAXIMUM_TRIALS;
    }

    static int effectiveParallelism(int poolParallelism) {
        return effectiveParallelism(poolParallelism, Runtime.getRuntime().availableProcessors());
    }

    static int effectiveParallelism(int poolParallelism, int availableProcessors) {
        return Math.max(1, Math.min(poolParallelism, availableProcessors));
    }

    static int trialLimit(long maximumHeap, int maximumRoots) {
        if (maximumRoots < 1) {
            throw new IllegalArgumentException("Maximum hydrology roots must be positive.");
        }
        return (int) Math.clamp(maximumHeap / maximumRoots / (1024L * 1024L * 1024L), 1L, 16L);
    }

    static int rootLimit(long maximumHeap, int processors) {
        return (int) Math.max(1L, Math.min(Math.min(8L, processors), maximumHeap / BYTES_PER_ROOT));
    }

    Permit acquire(BooleanSupplier cancelled) {
        AdmissionWait wait = new AdmissionWait(Objects.requireNonNull(cancelled));
        try {
            ForkJoinPool.managedBlock(wait);
            wait.requireActive();
            return new Permit(permits);
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            if (wait.acquired) {
                permits.release();
            }
            throw new CancellationException("Hydrology planning admission interrupted.");
        } catch (RuntimeException | Error failure) {
            if (wait.acquired) {
                permits.release();
            }
            throw failure;
        }
    }

    static final class Permit implements AutoCloseable {
        private final Semaphore permits;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(Semaphore permits) {
            this.permits = permits;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                permits.release();
            }
        }
    }

    private final class AdmissionWait implements ForkJoinPool.ManagedBlocker {
        private final BooleanSupplier cancelled;
        private boolean acquired;

        private AdmissionWait(BooleanSupplier cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public boolean isReleasable() {
            requireActive();
            if (!acquired) {
                try {
                    acquired = permits.tryAcquire(0L, TimeUnit.NANOSECONDS);
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Hydrology planning admission interrupted.");
                }
            }
            return acquired;
        }

        @Override
        public boolean block() throws InterruptedException {
            requireActive();
            if (!acquired) {
                acquired = permits.tryAcquire(100L, TimeUnit.MILLISECONDS);
            }
            return acquired;
        }

        private void requireActive() {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Hydrology planning admission interrupted.");
            }
            if (cancelled.getAsBoolean()) {
                throw new CancellationException("Hydrology tile cache is closed.");
            }
        }
    }
}
