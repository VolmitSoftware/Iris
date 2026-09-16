package art.arcane.iris.generation.concurrent;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HydrologyBurstTest {
    @Test(timeout = 15000)
    public void blockedOwnersLeaveTheFullRunnableTargetAvailableForLeafTasks() throws Exception {
        int parallelism = 4;
        MultiBurst.HydrologyBurst burst = new MultiBurst.HydrologyBurst();
        ForkJoinPool pool = burst.createPool(parallelism, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null);
        CountDownLatch ownersReady = new CountDownLatch(parallelism);
        CountDownLatch startOwners = new CountDownLatch(1);
        CountDownLatch ownersBlocked = new CountDownLatch(parallelism);
        CountDownLatch releaseOwners = new CountDownLatch(1);
        CountDownLatch leavesStarted = new CountDownLatch(parallelism);
        CountDownLatch releaseLeaves = new CountDownLatch(1);
        List<Future<?>> owners = new ArrayList<>();
        try {
            for (int index = 0; index < parallelism; index++) {
                owners.add(pool.submit(() -> {
                    ownersReady.countDown();
                    await(startOwners);
                    pool.execute(() -> {
                        leavesStarted.countDown();
                        await(releaseLeaves);
                    });
                    ForkJoinPool.managedBlock(new LatchBlocker(ownersBlocked, releaseOwners));
                    return null;
                }));
            }
            assertTrue(ownersReady.await(5, TimeUnit.SECONDS));
            startOwners.countDown();
            assertTrue(ownersBlocked.await(5, TimeUnit.SECONDS));
            assertTrue("Managed waits reduced the runnable leaf capacity", leavesStarted.await(5, TimeUnit.SECONDS));
            releaseLeaves.countDown();
            releaseOwners.countDown();
            for (Future<?> owner : owners) {
                owner.get(5, TimeUnit.SECONDS);
            }
        } finally {
            startOwners.countDown();
            releaseLeaves.countDown();
            releaseOwners.countDown();
            MultiBurst.close(pool);
        }
        assertTrue(pool.isTerminated());
    }

    @Test
    public void closedHydrologyWorkersTerminateAndReopenCreatesANewPool() throws Exception {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        MultiBurst.HydrologyBurst burst = new MultiBurst.HydrologyBurst();
        Callable<ForkJoinPool> currentPool = ForkJoinTask::getPool;
        try {
            ForkJoinPool first = burst.submit(currentPool).get(5, TimeUnit.SECONDS);
            burst.close();
            assertTrue(first.isTerminated());
            assertTrue(burst.isTerminated());
            assertSame(Thread.currentThread(), burst.submit(Thread::currentThread).get(5, TimeUnit.SECONDS));

            burst.reopen();
            ForkJoinPool reopened = burst.submit(currentPool).get(5, TimeUnit.SECONDS);
            assertNotSame(first, reopened);
            assertTrue(reopened.getParallelism() > 0);
            burst.close();
            assertTrue(reopened.isTerminated());
        } finally {
            burst.close();
            IrisSettings.settings = previousSettings;
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for pool work");
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    private record LatchBlocker(CountDownLatch blocked, CountDownLatch release) implements ForkJoinPool.ManagedBlocker {
        @Override
        public boolean block() throws InterruptedException {
            blocked.countDown();
            release.await();
            return true;
        }

        @Override
        public boolean isReleasable() {
            return release.getCount() == 0;
        }
    }
}
