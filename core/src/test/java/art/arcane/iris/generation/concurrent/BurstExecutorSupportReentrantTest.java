package art.arcane.iris.generation.concurrent;

import art.arcane.volmlib.util.parallel.BurstExecutorSupport;
import org.junit.Test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

public class BurstExecutorSupportReentrantTest {
    @Test
    public void multiBurstRecognizesOwnedWorkerThread() throws Exception {
        MultiBurst burst = new MultiBurst("Iris Test", Thread.NORM_PRIORITY, () -> 1);

        try {
            Future<Boolean> future = burst.submit(burst::ownsCurrentThread);
            assertTrue(future.get(5, TimeUnit.SECONDS));
        } finally {
            burst.close();
        }
    }

    @Test
    public void runsNestedBurstInlineOnSameForkJoinPoolWorker() throws Exception {
        ForkJoinPool pool = new ForkJoinPool(1);
        AtomicBoolean nestedExecuted = new AtomicBoolean(false);

        try {
            Future<?> future = pool.submit(() -> {
                BurstExecutorSupport burst = new BurstExecutorSupport(pool, 1);
                burst.queue(() -> {
                    BurstExecutorSupport nested = new BurstExecutorSupport(pool, 1);
                    nested.queue(() -> nestedExecuted.set(true));
                    nested.complete();
                });
                burst.complete();
            });

            future.get(5, TimeUnit.SECONDS);
            assertTrue(nestedExecuted.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
