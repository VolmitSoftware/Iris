package art.arcane.iris.modded;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModdedGenPoolTest {
    @Test
    public void executorLookupDoesNotRestartStoppedPool() throws Exception {
        ModdedGenPool.start();
        ExecutorService original = ModdedGenPool.pool();
        ModdedGenPool.shutdown();
        try {
            ModdedGenPool.pool();
            fail("Stopped generation pool must reject executor lookup");
        } catch (RejectedExecutionException expected) {
            assertTrue(original.isShutdown());
        }
        ModdedGenPool.start();
        try {
            assertNotSame(original, ModdedGenPool.pool());
        } finally {
            ModdedGenPool.shutdown();
        }
    }

    @Test
    public void restartWaitsForPreviousWorkersToTerminate() throws Exception {
        ModdedGenPool.start();
        ExecutorService original = ModdedGenPool.pool();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        original.execute(() -> {
            entered.countDown();
            boolean released = false;
            while (!released) {
                try {
                    release.await();
                    released = true;
                } catch (InterruptedException ignored) {
                    Thread.interrupted();
                }
            }
        });
        assertTrue(entered.await(5L, TimeUnit.SECONDS));
        try {
            Thread.currentThread().interrupt();
            ModdedGenPool.shutdown();
            assertTrue(Thread.interrupted());
            assertFalse(original.isTerminated());
            try {
                ModdedGenPool.start();
                fail("Generation pool must retain workers until they terminate");
            } catch (IllegalStateException expected) {
                assertFalse(original.isTerminated());
            }
        } finally {
            Thread.interrupted();
            release.countDown();
            assertTrue(original.awaitTermination(5L, TimeUnit.SECONDS));
            ModdedGenPool.start();
            assertNotSame(original, ModdedGenPool.pool());
            ExecutorService restarted = ModdedGenPool.pool();
            ModdedGenPool.start();
            assertSame(restarted, ModdedGenPool.pool());
            ModdedGenPool.shutdown();
        }
    }
}
