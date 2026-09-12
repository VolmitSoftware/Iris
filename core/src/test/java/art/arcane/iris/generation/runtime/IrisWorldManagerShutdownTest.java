package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.util.scheduling.Looper;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisWorldManagerShutdownTest {
    @Test
    public void liveLooperPreventsSuccessfulCloseAndRemainsRetryable() throws Exception {
        IrisWorldManager manager = manager();
        HeldLooper looper = new HeldLooper();
        setField(manager, "looper", looper);
        looper.start();
        try {
            assertTrue(looper.started.await(5L, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, manager::close);
            assertFalse(manager.isLooperStopped());
            assertTrue(looper.isAlive());
            assertTrue(manager.isCleanupServiceStopped());
        } finally {
            looper.release.countDown();
            looper.join(5_000L);
            manager.close();
        }
        assertFalse(looper.isAlive());
        assertTrue(manager.isLooperStopped());
        manager.close();
    }

    @Test
    public void cleanupTimeoutRemainsRetryableAndSuccessfulCloseIsIdempotent() throws Exception {
        IrisWorldManager manager = manager();
        ScheduledThreadPoolExecutor executor = mock(ScheduledThreadPoolExecutor.class);
        manager.getCleanupService().shutdownNow();
        setField(manager, "cleanupService", executor);
        when(executor.awaitTermination(anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false, true);

        assertThrows(IllegalStateException.class, manager::close);
        assertFalse(manager.isCleanupServiceStopped());
        assertTrue(manager.isLooperStopped());

        manager.close();
        assertTrue(manager.isCleanupServiceStopped());
        manager.close();
        verify(executor, times(2)).shutdownNow();
        verify(executor, times(2)).awaitTermination(anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void interruptedCleanupWaitRestoresInterruptAndCanBeRetried() throws Exception {
        IrisWorldManager manager = manager();
        ScheduledThreadPoolExecutor executor = mock(ScheduledThreadPoolExecutor.class);
        manager.getCleanupService().shutdownNow();
        setField(manager, "cleanupService", executor);
        when(executor.awaitTermination(anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException("Close interrupted"))
                .thenReturn(true);
        try {
            assertThrows(IllegalStateException.class, manager::close);
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(manager.isCleanupServiceStopped());
        } finally {
            Thread.interrupted();
            manager.close();
        }
        assertTrue(manager.isCleanupServiceStopped());
        verify(executor, times(2)).awaitTermination(anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    private static IrisWorldManager manager() {
        Engine engine = mock(Engine.class);
        EngineTarget target = mock(EngineTarget.class);
        IrisWorld world = mock(IrisWorld.class);
        when(world.name()).thenReturn("shutdown-test");
        when(target.getWorld()).thenReturn(world);
        when(engine.getTarget()).thenReturn(target);
        return new IrisWorldManager(engine);
    }

    private static void setField(IrisWorldManager manager, String name, Object value) throws Exception {
        Field field = IrisWorldManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static final class HeldLooper extends Looper {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        protected void onStart() {
        }

        @Override
        protected void onStop() {
        }

        @Override
        protected long loop() {
            started.countDown();
            while (true) {
                try {
                    release.await();
                    return -1L;
                } catch (InterruptedException ignored) {
                }
            }
        }
    }
}
