package art.arcane.iris.generation.cache;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ReleasableThreadLocalTest {
    private static final long THREAD_WAIT_SECONDS = 5L;

    @Test
    public void keepsOneValuePerThreadUntilReleased() {
        ReleasableThreadLocal<Object> local = new ReleasableThreadLocal<>(Object::new);

        Object first = local.get();

        assertSame(first, local.get());
    }

    @Test
    public void releaseAllDropsValuesHeldByOtherLiveThreads() throws Exception {
        ReleasableThreadLocal<Object> local = new ReleasableThreadLocal<>(Object::new);
        AtomicReference<Object> beforeRelease = new AtomicReference<>();
        AtomicReference<Object> afterRelease = new AtomicReference<>();
        CountDownLatch valueCreated = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        Thread pooledWorker = new Thread(() -> {
            beforeRelease.set(local.get());
            valueCreated.countDown();
            awaitQuietly(released);
            afterRelease.set(local.get());
        });

        pooledWorker.start();
        assertTrue(valueCreated.await(THREAD_WAIT_SECONDS, TimeUnit.SECONDS));
        local.releaseAll();
        released.countDown();
        pooledWorker.join(TimeUnit.SECONDS.toMillis(THREAD_WAIT_SECONDS));

        assertNotSame(beforeRelease.get(), afterRelease.get());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(THREAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
