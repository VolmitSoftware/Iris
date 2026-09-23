package art.arcane.iris.world.pregen;

import art.arcane.iris.world.lifecycle.CapabilitySnapshot;
import art.arcane.iris.world.lifecycle.WorldLifecycleService;
import art.arcane.volmlib.nativelib.terrain.NativeWorkerPool;
import art.arcane.volmlib.nativelib.terrain.NativeWorldRuntime;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class AsyncPregenWorkerBoostTest {
    @Test
    public void nestedTargetsEscalateAndRestoreOnlyAfterLastRelease() throws Exception {
        try (Fixture fixture = new Fixture(4)) {
            AsyncPregenMethod.acquireWorkerThreadBoost(16);
            AsyncPregenMethod.acquireWorkerThreadBoost(32);
            AsyncPregenMethod.acquireWorkerThreadBoost(16);
            assertEquals(32, fixture.threads.get());
            AsyncPregenMethod.releaseWorkerThreadBoost();
            AsyncPregenMethod.releaseWorkerThreadBoost();
            assertEquals(32, fixture.threads.get());
            AsyncPregenMethod.releaseWorkerThreadBoost();
            assertEquals(4, fixture.threads.get());
            AsyncPregenMethod.releaseWorkerThreadBoost();
            assertEquals(List.of(16, 32, 4), fixture.adjustments);
            AsyncPregenMethod.acquireWorkerThreadBoost(32);
            AsyncPregenMethod.releaseWorkerThreadBoost();
            assertEquals(List.of(16, 32, 4, 32, 4), fixture.adjustments);
        }
    }

    @Test
    public void explicitHigherNativeCountIsNeverReduced() throws Exception {
        try (Fixture fixture = new Fixture(48)) {
            AsyncPregenMethod.acquireWorkerThreadBoost(32);
            AsyncPregenMethod.acquireWorkerThreadBoost(16);
            AsyncPregenMethod.releaseWorkerThreadBoost();
            AsyncPregenMethod.releaseWorkerThreadBoost();
            assertEquals(48, fixture.threads.get());
            assertTrue(fixture.adjustments.isEmpty());
            assertThrows(IllegalArgumentException.class, () -> AsyncPregenMethod.acquireWorkerThreadBoost(0));
        }
    }

    @Test
    public void reacquisitionWaitsForPreviousRestorationAndKeepsItsOwnHold() throws Exception {
        try (Fixture fixture = new Fixture(4); ExecutorService executor = Executors.newFixedThreadPool(2)) {
            AsyncPregenMethod.acquireWorkerThreadBoost(16);
            CountDownLatch restoring = new CountDownLatch(1);
            CountDownLatch finishRestore = new CountDownLatch(1);
            CountDownLatch acquiring = new CountDownLatch(1);
            AtomicBoolean waitOnce = new AtomicBoolean(true);
            doAnswer(invocation -> {
                int count = invocation.getArgument(0);
                if (count == 4 && waitOnce.compareAndSet(true, false)) {
                    restoring.countDown();
                    assertTrue(finishRestore.await(5L, TimeUnit.SECONDS));
                }
                fixture.adjustments.add(count);
                fixture.threads.set(count);
                return null;
            }).when(fixture.pool).adjustThreadCount(anyInt());
            Future<?> released = executor.submit(() -> fixture.withBinding(AsyncPregenMethod::releaseWorkerThreadBoost));
            try {
                assertTrue(restoring.await(5L, TimeUnit.SECONDS));
                Future<?> acquired = executor.submit(() -> fixture.withBinding(() -> {
                    acquiring.countDown();
                    AsyncPregenMethod.acquireWorkerThreadBoost(32);
                }));
                assertTrue(acquiring.await(5L, TimeUnit.SECONDS));
                assertFalse(acquired.isDone());
                finishRestore.countDown();
                released.get(5L, TimeUnit.SECONDS);
                acquired.get(5L, TimeUnit.SECONDS);
                assertEquals(32, fixture.threads.get());
                AsyncPregenMethod.releaseWorkerThreadBoost();
                assertEquals(4, fixture.threads.get());
            } finally {
                finishRestore.countDown();
            }
        }
    }

    @Test
    public void closeRestoresWorkersWhenDrainFailsAndPreservesCancellationInterrupt() throws Exception {
        try (Fixture fixture = new Fixture(4)) {
            AsyncPregenMethod.acquireWorkerThreadBoost(32);
            AsyncPregenMethod method = mock(AsyncPregenMethod.class, CALLS_REAL_METHODS);
            PregenAdmissionGate admission = mock(PregenAdmissionGate.class);
            IllegalStateException failure = new IllegalStateException("drain failed");
            when(admission.awaitDrain(anyLong(), any(TimeUnit.class), any(Runnable.class))).thenThrow(failure);
            set(method, "closing", new AtomicBoolean());
            set(method, "holdsWorkerBoost", new AtomicBoolean(true));
            set(method, "admission", admission);
            Thread.currentThread().interrupt();
            try {
                assertSame(failure, assertThrows(IllegalStateException.class, method::close));
                assertEquals(4, fixture.threads.get());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertSame(failure, assertThrows(IllegalStateException.class, method::close));
            assertEquals(List.of(32, 4), fixture.adjustments);
        }
    }

    private static void set(AsyncPregenMethod method, String name, Object value) throws Exception {
        Field field = AsyncPregenMethod.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(method, value);
    }

    private static final class Fixture implements AutoCloseable {
        private final WorldLifecycleService service = mock(WorldLifecycleService.class);
        private final NativeWorkerPool pool = mock(NativeWorkerPool.class);
        private final AtomicInteger threads;
        private final List<Integer> adjustments = Collections.synchronizedList(new ArrayList<>());
        private final MockedStatic<WorldLifecycleService> lifecycle = mockStatic(WorldLifecycleService.class);

        private Fixture(int initialThreads) throws Exception {
            threads = new AtomicInteger(initialThreads);
            CapabilitySnapshot capabilities = mock(CapabilitySnapshot.class);
            NativeWorldRuntime runtime = mock(NativeWorldRuntime.class);
            when(service.capabilities()).thenReturn(capabilities);
            when(capabilities.nativeRuntime()).thenReturn(runtime);
            when(runtime.workers()).thenReturn(pool);
            when(pool.threadCount()).thenAnswer(invocation -> threads.get());
            doAnswer(invocation -> {
                int count = invocation.getArgument(0);
                adjustments.add(count);
                threads.set(count);
                return null;
            }).when(pool).adjustThreadCount(anyInt());
            lifecycle.when(WorldLifecycleService::get).thenReturn(service);
        }

        private void withBinding(Runnable action) {
            try (MockedStatic<WorldLifecycleService> binding = mockStatic(WorldLifecycleService.class)) {
                binding.when(WorldLifecycleService::get).thenReturn(service);
                action.run();
            }
        }

        @Override
        public void close() {
            for (int index = 0; index < 4; index++) {
                AsyncPregenMethod.releaseWorkerThreadBoost();
            }
            lifecycle.close();
        }
    }
}
