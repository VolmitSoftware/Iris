package art.arcane.iris.modded.command;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.pregen.MantleHeapPressure;
import art.arcane.iris.world.pregen.PregenListener;
import art.arcane.volmlib.nativelib.terrain.NativePregenRuntime;
import art.arcane.volmlib.nativelib.terrain.NativePregenRuntime.ChunkLoad;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedPregenMethodMemoryTest {
    @Test
    public void configuredLimitIsBoundedByMemoryWithoutRaisingSmallerRequests() {
        assertEquals(4, ModdedPregenMethod.memoryLimitedConcurrency(512, 1L << 30));
        assertEquals(2, ModdedPregenMethod.memoryLimitedConcurrency(2, 1L << 30));
        assertEquals(1, ModdedPregenMethod.memoryLimitedConcurrency(512, 128L << 20));
    }

    @Test
    public void smallMemoryBudgetAndRisingPressureBlockRequestsUntilHeadroomReturns() throws Exception {
        IrisSettings configured = new IrisSettings();
        configured.getPregen().setModdedPregenInFlight(512);
        NativePregenRuntime runtime = mock(NativePregenRuntime.class);
        Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        PregenListener listener = mock(PregenListener.class);
        CompletableFuture<ChunkLoad> first = new CompletableFuture<>();
        CompletableFuture<ChunkLoad> second = new CompletableFuture<>();
        when(runtime.isRunning()).thenReturn(true);
        when(runtime.loadChunk(0, 0)).thenReturn(first);
        when(runtime.loadChunk(1, 0)).thenReturn(second);
        when(engine.getHeight()).thenReturn(384);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (MockedStatic<IrisSettings> settings = mockStatic(IrisSettings.class);
             MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            settings.when(IrisSettings::get).thenReturn(configured);
            pressure.when(MantleHeapPressure::memoryBudgetBytes).thenReturn(256L << 20);
            ModdedPregenMethod method = new ModdedPregenMethod(new ModdedPregenMethod.Configuration(runtime, engine, false));
            method.generateChunk(0, 0, listener);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch pressureObserved = new CountDownLatch(1);
            AtomicBoolean pressured = new AtomicBoolean();
            Future<?> pending = executor.submit(() -> {
                try (MockedStatic<MantleHeapPressure> workerPressure = mockStatic(MantleHeapPressure.class)) {
                    workerPressure.when(MantleHeapPressure::overHighWater).thenAnswer(call -> {
                        if (pressured.get()) {
                            pressureObserved.countDown();
                            return true;
                        }
                        return false;
                    });
                    started.countDown();
                    method.generateChunk(1, 0, listener);
                }
            });
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> pending.get(100L, TimeUnit.MILLISECONDS));
            verify(runtime, never()).loadChunk(1, 0);
            pressured.set(true);
            first.complete(new ChunkLoad(true, null));
            assertTrue(pressureObserved.await(5L, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> pending.get(100L, TimeUnit.MILLISECONDS));
            verify(runtime, never()).loadChunk(1, 0);
            pressured.set(false);
            pending.get(5L, TimeUnit.SECONDS);
            verify(runtime).loadChunk(1, 0);
            second.complete(new ChunkLoad(true, null));
            verify(listener).onChunkGenerated(0, 0);
            verify(listener).onChunkGenerated(1, 0);
        } finally {
            first.complete(new ChunkLoad(true, null));
            second.complete(new ChunkLoad(true, null));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }
}
