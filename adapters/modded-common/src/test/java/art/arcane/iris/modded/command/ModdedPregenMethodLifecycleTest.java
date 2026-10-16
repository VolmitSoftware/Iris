package art.arcane.iris.modded.command;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.ModdedGenPool;
import art.arcane.iris.world.pregen.MantleHeapPressure;
import art.arcane.iris.world.pregen.PregenListener;
import art.arcane.volmlib.nativelib.terrain.NativePregenRuntime;
import art.arcane.volmlib.nativelib.terrain.NativePregenRuntime.ChunkLoad;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedPregenMethodLifecycleTest {
    private MockedStatic<IrisSettings> settings;
    private NativePregenRuntime runtime;
    private Engine engine;
    private ModdedPregenMethod method;

    @Before
    public void prepare() {
        settings = mockStatic(IrisSettings.class);
        IrisSettings configured = new IrisSettings();
        settings.when(IrisSettings::get).thenReturn(configured);
        runtime = mock(NativePregenRuntime.class);
        when(runtime.worldIdentity()).thenReturn("example:terrain");
        engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        when(engine.getHeight()).thenReturn(384);
        method = new ModdedPregenMethod(new ModdedPregenMethod.Configuration(runtime, engine, true));
    }

    @After
    public void cleanup() {
        settings.close();
    }

    @Test
    public void periodicSaveRunsOnlyAfterTheServerExecutesIt() {
        method.save();
        verify(runtime, never()).save();
        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        verify(runtime).execute(queued.capture());
        when(runtime.isServerThread()).thenReturn(true);
        queued.getValue().run();
        verify(runtime).save();
    }

    @Test
    public void shutdownCanClaimTheFinalSaveWithoutQueuingWorkToItsOwnThread() {
        when(runtime.isServerThread()).thenReturn(true);
        assertTrue(method.deferFinalSaveToServerThread());
        method.close();
        assertTrue(method.hasPendingFinalSave());
        verify(runtime, never()).save();
        method.completeDeferredFinalSave();
        method.completeDeferredFinalSave();
        assertFalse(method.hasPendingFinalSave());
        verify(runtime, times(1)).save();
        verify(runtime, never()).execute(any());
    }

    @Test
    public void asynchronousCallerCannotClaimTheShutdownSave() {
        assertFalse(method.deferFinalSaveToServerThread());
        assertThrows(IllegalStateException.class, method::completeDeferredFinalSave);
        verify(runtime, never()).save();
    }

    @Test
    public void finalSaveWaitsForEveryCallbackBeyondFiveSeconds() throws Exception {
        CompletableFuture<ChunkLoad> load = new CompletableFuture<>();
        CompletableFuture<ChunkLoad> failedLoad = new CompletableFuture<>();
        PregenListener listener = mock(PregenListener.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        when(runtime.isRunning()).thenReturn(true);
        when(runtime.loadChunk(2, 3)).thenReturn(load);
        when(runtime.loadChunk(4, 5)).thenReturn(failedLoad);
        doAnswer(call -> {
            call.getArgument(0, Runnable.class).run();
            return null;
        }).when(runtime).execute(any());
        try (MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            pressure.when(MantleHeapPressure::memoryBudgetBytes).thenReturn(512L << 20);
            method = new ModdedPregenMethod(new ModdedPregenMethod.Configuration(runtime, engine, false));
            method.generateChunk(2, 3, listener);
            method.generateChunk(4, 5, listener);
            CountDownLatch started = new CountDownLatch(1);
            Future<?> closing = executor.submit(() -> {
                started.countDown();
                method.close();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> closing.get(6, TimeUnit.SECONDS));
            verify(runtime, never()).save();
            verify(engine, never()).syncGenerationHistory();
            load.complete(new ChunkLoad(true, null));
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            verify(runtime, never()).save();
            failedLoad.completeExceptionally(new IllegalStateException("chunk failed"));
            closing.get(5, TimeUnit.SECONDS);
            verify(listener).onChunkGenerated(2, 3);
            verify(listener).onChunkFailed(4, 5);
            verify(engine).syncGenerationHistory();
            verify(runtime).save();
        } finally {
            load.complete(new ChunkLoad(true, null));
            failedLoad.complete(new ChunkLoad(false, "chunk failed"));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void interruptedCloseDrainsPendingChunksAndWaitsForFinalSave() throws Exception {
        CompletableFuture<ChunkLoad> load = new CompletableFuture<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Thread> closingThread = new AtomicReference<>();
        AtomicReference<Runnable> queuedSave = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch saveQueued = new CountDownLatch(1);
        when(runtime.isRunning()).thenReturn(true);
        when(runtime.loadChunk(2, 3)).thenReturn(load);
        doAnswer(call -> {
            queuedSave.set(call.getArgument(0, Runnable.class));
            saveQueued.countDown();
            return null;
        }).when(runtime).execute(any());
        try (MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            pressure.when(MantleHeapPressure::memoryBudgetBytes).thenReturn(256L << 20);
            method = new ModdedPregenMethod(new ModdedPregenMethod.Configuration(runtime, engine, false));
            method.generateChunk(2, 3, mock(PregenListener.class));
            Future<Boolean> closing = executor.submit(() -> {
                closingThread.set(Thread.currentThread());
                started.countDown();
                method.close();
                return Thread.currentThread().isInterrupted();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            closingThread.get().interrupt();
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            verify(runtime, never()).save();
            verify(engine, never()).syncGenerationHistory();
            load.complete(new ChunkLoad(true, null));
            assertTrue(saveQueued.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            verify(runtime, never()).save();
            closingThread.get().interrupt();
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            queuedSave.get().run();
            assertTrue(closing.get(5, TimeUnit.SECONDS));
            verify(engine).syncGenerationHistory();
            verify(runtime).save();
        } finally {
            load.complete(new ChunkLoad(true, null));
            saveQueued.await(5, TimeUnit.SECONDS);
            Runnable pendingSave = queuedSave.get();
            if (pendingSave != null) {
                pendingSave.run();
            }
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void serverShutdownClaimsFinalSaveWithoutWaitingForPendingChunks() throws Exception {
        CompletableFuture<ChunkLoad> load = new CompletableFuture<>();
        PregenListener listener = mock(PregenListener.class);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Thread serverThread = Thread.currentThread();
        when(runtime.isServerThread()).thenAnswer(call -> Thread.currentThread() == serverThread);
        when(runtime.isRunning()).thenReturn(true);
        when(runtime.loadChunk(2, 3)).thenReturn(load);
        try (MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            pressure.when(MantleHeapPressure::memoryBudgetBytes).thenReturn(256L << 20);
            method = new ModdedPregenMethod(new ModdedPregenMethod.Configuration(runtime, engine, false));
            method.generateChunk(2, 3, listener);
            Future<?> closing = executor.submit(method::close);
            assertThrows(TimeoutException.class, () -> closing.get(100, TimeUnit.MILLISECONDS));
            assertTrue(method.deferFinalSaveToServerThread());
            closing.get(5, TimeUnit.SECONDS);
            verify(runtime, never()).save();
            assertTrue(method.hasPendingFinalSave());
            method.completeDeferredFinalSave();
            verify(runtime).save();
            verify(runtime, never()).execute(any());
        } finally {
            load.complete(new ChunkLoad(true, null));
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedFinalSaveStillRestoresTheEmptyServerPauseSetting() {
        AtomicInteger pause = new AtomicInteger(60);
        when(runtime.supportsPauseWhenEmpty()).thenReturn(true);
        when(runtime.pauseWhenEmptySeconds()).thenAnswer(call -> pause.get());
        doAnswer(call -> {
            pause.set(call.getArgument(0, Integer.class));
            return null;
        }).when(runtime).pauseWhenEmptySeconds(anyInt());
        when(runtime.isServerThread()).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("save failed");
        doThrow(failure).when(runtime).save();
        try (MockedStatic<ModdedGenPool> pool = mockStatic(ModdedGenPool.class)) {
            method.init();
            assertEquals(0, pause.get());
            assertSame(failure, assertThrows(IllegalStateException.class, method::close));
        }
        assertEquals(60, pause.get());
    }
}
