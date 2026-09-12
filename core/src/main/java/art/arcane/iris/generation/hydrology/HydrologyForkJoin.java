package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs independent planning tasks and returns results in request order, using the current pool or fallback.
 * A worker claims its own queued tasks before waiting, and never helps unrelated work while holding
 * an owner draft. Every started task finishes before the batch returns or throws.
 */
public final class HydrologyForkJoin {
    private HydrologyForkJoin() {
    }

    public static <T> List<T> invokeAll(List<Callable<T>> tasks, Executor fallback) {
        Objects.requireNonNull(tasks, "tasks");
        boolean callerRuns = Thread.currentThread() instanceof ForkJoinWorkerThread;
        if (tasks.size() < 2 || fallback == null && !callerRuns) {
            ArrayList<T> results = new ArrayList<>(tasks.size());
            for (Callable<T> task : tasks) {
                results.add(call(task));
            }
            return results;
        }
        Executor executor = callerRuns ? ((ForkJoinWorkerThread) Thread.currentThread()).getPool() : fallback;
        ArrayList<Task<T>> pending = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            pending.add(new Task<>(task));
        }
        Throwable failure = null;
        int submitted = callerRuns ? 1 : 0;
        for (int index = callerRuns ? 1 : 0; index < pending.size(); index++) {
            try {
                executor.execute(pending.get(index));
                submitted = index + 1;
            } catch (RuntimeException | Error rejected) {
                failure = rejected;
                break;
            }
        }
        ArrayList<T> results = new ArrayList<>(tasks.size());
        int drainCount = callerRuns ? pending.size() : submitted;
        for (int index = 0; index < drainCount; index++) {
            try {
                results.add(pending.get(index).await(callerRuns));
            } catch (RuntimeException | Error failed) {
                if (failure == null) {
                    failure = failed;
                } else if (failure != failed) {
                    failure.addSuppressed(failed);
                }
            }
        }
        if (failure != null) {
            throw unwrap(failure);
        }
        return results;
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("Hydrology planning task failed.", failure);
        }
    }

    private static RuntimeException unwrap(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("Hydrology planning task failed.", cause);
    }

    static final class Task<T> implements Runnable, ForkJoinPool.ManagedBlocker {
        private final Callable<T> work;
        private final AtomicBoolean claimed = new AtomicBoolean();
        private final CountDownLatch complete = new CountDownLatch(1);
        private T result;
        private Throwable failure;

        Task(Callable<T> work) {
            this.work = Objects.requireNonNull(work);
        }

        T await() {
            return await(true);
        }

        private T await(boolean claimPending) {
            if (claimPending) {
                run();
            }
            boolean interrupted = false;
            boolean managed = true;
            Throwable blockingFailure = null;
            while (!isReleasable()) {
                try {
                    if (managed) {
                        ForkJoinPool.managedBlock(this);
                    } else {
                        complete.await();
                    }
                } catch (InterruptedException interruption) {
                    interrupted = true;
                    managed = false;
                } catch (RejectedExecutionException unavailable) {
                    managed = false;
                } catch (RuntimeException | Error failed) {
                    blockingFailure = failed;
                    managed = false;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            if (blockingFailure != null) {
                if (failure != null && failure != blockingFailure) {
                    blockingFailure.addSuppressed(failure);
                }
                throw unwrap(blockingFailure);
            }
            if (failure != null) {
                throw unwrap(failure);
            }
            return result;
        }

        @Override
        public boolean isReleasable() {
            return complete.getCount() == 0;
        }

        @Override
        public boolean block() throws InterruptedException {
            complete.await();
            return true;
        }

        @Override
        public void run() {
            if (!claimed.compareAndSet(false, true)) {
                return;
            }
            try {
                result = call(work);
            } catch (RuntimeException | Error failed) {
                failure = failed;
            } finally {
                complete.countDown();
            }
        }
    }
}
