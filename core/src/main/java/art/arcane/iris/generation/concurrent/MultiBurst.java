package art.arcane.iris.generation.concurrent;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.volmlib.util.hunk.HunkBurst;
import art.arcane.volmlib.util.parallel.MultiBurstSupport;
import art.arcane.volmlib.util.math.M;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

public class MultiBurst extends MultiBurstSupport {
    private static final long TIMEOUT = Long.getLong("iris.burst.timeout", 15000);
    public static final MultiBurst burst = new MultiBurst();
    public static final MultiBurst ioBurst = new MultiBurst("Iris IO", () -> IrisSettings.get().getConcurrency().getIoParallelism());
    /**
     * Long-running hydrology tile planning. Kept off {@link #burst} so a handful of twenty-second
     * plans never pin the workers that chunk generation's short stage and mantle tasks need.
     */
    public static final MultiBurst hydrology = new MultiBurst("Iris Hydrology", () -> Math.max(2, IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism())));
    private final AtomicInteger parallelismBaseline = new AtomicInteger();

    static {
        // Hunk's parallel section splits live in VolmLib and have no view of Iris' concurrency
        // settings, so point them at the same pool the generation stages use. Without this they
        // would spin up a second, untuned pool alongside it.
        HunkBurst.install(burst::burst);
    }

    public MultiBurst() {
        this("Iris");
    }

    public MultiBurst(String name) {
        this(name, Thread.MIN_PRIORITY, () -> IrisSettings.get().getConcurrency().getParallelism());
    }

    public MultiBurst(String name, IntSupplier parallelism) {
        this(name, Thread.MIN_PRIORITY, parallelism);
    }

    public MultiBurst(String name, int priority, IntSupplier parallelism) {
        super(name, priority, parallelism, IrisSettings::getThreadCount, M::ms, IrisLogging::reportError, IrisLogging::info, IrisLogging::warn, TIMEOUT);
    }

    /**
     * Raises the pool's parallelism to at least {@code target} workers. Pregeneration fans every
     * chunk's stages and mantle windows into this pool while some tasks block on unmanaged waits,
     * so a pregeneration box wants more workers than cores. {@link #restoreParallelism()} puts it back.
     */
    public boolean raiseParallelism(int target) {
        if (!(service() instanceof ForkJoinPool pool) || target <= pool.getParallelism()) {
            return false;
        }
        parallelismBaseline.compareAndSet(0, pool.getParallelism());
        pool.setParallelism(target);
        return true;
    }

    /**
     * Returns the pool to the parallelism it had before the first {@link #raiseParallelism(int)} that took
     * effect. A pool that was never raised, or that is not a {@link ForkJoinPool}, is left alone.
     */
    public boolean restoreParallelism() {
        int baseline = parallelismBaseline.getAndSet(0);
        if (baseline <= 0 || !(service() instanceof ForkJoinPool pool) || pool.getParallelism() <= baseline) {
            return false;
        }

        try {
            pool.setParallelism(baseline);
        } catch (RuntimeException e) {
            IrisLogging.reportError("Iris could not return the burst pool to its pre-pregeneration parallelism of "
                    + baseline + "; it stays at " + pool.getParallelism() + ".", e);
            return false;
        }

        return true;
    }

    public int parallelism() {
        return service() instanceof ForkJoinPool pool ? pool.getParallelism() : -1;
    }

    public boolean ownsCurrentThread() {
        Thread thread = Thread.currentThread();
        if (!(thread instanceof ForkJoinWorkerThread worker)) {
            return false;
        }

        ExecutorService service = service();
        if (!(service instanceof ForkJoinPool pool)) {
            return false;
        }

        return worker.getPool() == pool;
    }

    public <T> CompletableFuture<T> completeValueAsync(Callable<T> task) {
        CompletableFuture<T> completion = new CompletableFuture<>();
        Future<?> submitted;
        try {
            submitted = service().submit(() -> {
                try {
                    completion.complete(task.call());
                } catch (Throwable exception) {
                    completion.completeExceptionally(exception);
                }
            });
        } catch (Throwable exception) {
            completion.completeExceptionally(exception);
            return completion;
        }
        completion.whenComplete((value, exception) -> {
            if (completion.isCancelled()) {
                submitted.cancel(true);
            }
        });
        return completion;
    }

    @Override
    public BurstExecutor burst(int estimate) {
        return new BurstExecutor(this::service, estimate);
    }

    @Override
    public BurstExecutor burst() {
        return burst(16);
    }

    @Override
    public BurstExecutor burst(boolean multicore) {
        BurstExecutor b = burst();
        b.setMulticore(multicore);
        return b;
    }

    public static void close(ExecutorService service) {
        MultiBurstSupport.close(service, M::ms, IrisLogging::info, IrisLogging::warn, IrisLogging::reportError, TIMEOUT);
    }
}
