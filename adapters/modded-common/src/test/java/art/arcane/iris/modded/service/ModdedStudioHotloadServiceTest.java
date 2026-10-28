package art.arcane.iris.modded.service;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedStudioHotloadServiceTest {
    @Test
    public void manualHotloadRunsWithoutAFileChangeOnTheWorker() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        Engine engine = studioEngine();
        Thread caller = Thread.currentThread();
        doAnswer(invocation -> {
            assertNotSame(caller, Thread.currentThread());
            return null;
        }).when(engine).hotloadSilently();
        service.onEnable();
        try {
            assertTrue(service.requestHotload("irisworldgen:studio_test", engine).get(5L, TimeUnit.SECONDS));
            verify(engine).hotloadSilently();
        } finally {
            service.onDisable();
        }
    }

    @Test
    public void overlappingManualHotloadIsRejected() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        Engine engine = studioEngine();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5L, TimeUnit.SECONDS));
            return null;
        }).when(engine).hotloadSilently();
        service.onEnable();
        try {
            CompletableFuture<Boolean> first = service.requestHotload("irisworldgen:studio_test", engine);
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            assertFalse(service.requestHotload("irisworldgen:studio_test", engine).get(5L, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(first.get(5L, TimeUnit.SECONDS));
            verify(engine).hotloadSilently();
        } finally {
            release.countDown();
            service.onDisable();
        }
    }

    @Test
    public void closingStudioIsNotHotloadedAfterWaitingInTheQueue() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        Engine engine = studioEngine();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch release = new CountDownLatch(1);
        executor.submit(() -> {
            try {
                assertTrue(release.await(5L, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
        setExecutor(service, executor);
        try {
            CompletableFuture<Boolean> requested = service.requestHotload("irisworldgen:studio_test", engine);
            when(engine.isClosing()).thenReturn(true);
            release.countDown();
            assertThrows(ExecutionException.class, () -> requested.get(5L, TimeUnit.SECONDS));
            verify(engine, never()).hotloadSilently();
        } finally {
            release.countDown();
            service.onDisable();
        }
    }

    @Test
    public void manualHotloadRejectsProductionWorlds() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        Engine engine = studioEngine();
        service.onEnable();
        try {
            assertThrows(ExecutionException.class, () -> service.requestHotload("irisworldgen:production", engine)
                    .get(5L, TimeUnit.SECONDS));
            when(engine.isStudio()).thenReturn(false);
            assertThrows(ExecutionException.class, () -> service.requestHotload("irisworldgen:studio_test", engine)
                    .get(5L, TimeUnit.SECONDS));
            verify(engine, never()).hotloadSilently();
        } finally {
            service.onDisable();
        }
    }

    @Test
    public void failedManualHotloadReleasesTheWatchForRetry() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        Engine engine = studioEngine();
        doThrow(new IllegalStateException("Invalid pack")).doNothing().when(engine).hotloadSilently();
        service.onEnable();
        try {
            assertThrows(ExecutionException.class, () -> service.requestHotload("irisworldgen:studio_test", engine)
                    .get(5L, TimeUnit.SECONDS));
            assertTrue(service.requestHotload("irisworldgen:studio_test", engine).get(5L, TimeUnit.SECONDS));
        } finally {
            service.onDisable();
        }
    }

    @Test
    public void shutdownDrainsWorkerBeforeAllowingRestart() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        ExecutorService executor = mock(ExecutorService.class);
        setExecutor(service, executor);
        when(executor.awaitTermination(30L, TimeUnit.SECONDS)).thenReturn(false);
        when(executor.awaitTermination(5L, TimeUnit.SECONDS)).thenReturn(true);

        service.onDisable();

        InOrder order = inOrder(executor);
        order.verify(executor).shutdown();
        order.verify(executor).awaitTermination(30L, TimeUnit.SECONDS);
        order.verify(executor).shutdownNow();
        order.verify(executor).awaitTermination(5L, TimeUnit.SECONDS);
        service.onEnable();
        service.onDisable();
    }

    @Test
    public void undrainedWorkerPreventsRestart() throws Exception {
        ModdedStudioHotloadService service = new ModdedStudioHotloadService();
        ExecutorService executor = mock(ExecutorService.class);
        setExecutor(service, executor);
        when(executor.isShutdown()).thenReturn(true);
        try {
            service.onDisable();
            fail("Hotload shutdown must report a worker that did not terminate");
        } catch (IllegalStateException expected) {
            verify(executor).shutdownNow();
        }
        try {
            service.onEnable();
            fail("Hotload must not start another worker before the previous worker terminates");
        } catch (IllegalStateException expected) {
            assertFalse(executor.isTerminated());
        }
        when(executor.isTerminated()).thenReturn(true);
        service.onEnable();
        service.onDisable();
    }

    @Test
    public void detectsDatapackImportsAcrossLoadedDimensions() {
        IrisDimension empty = new IrisDimension();
        IrisDimension imported = new IrisDimension();
        imported.setDatapackImports(new KList<String>().qadd("https://modrinth.com/datapack/example"));

        assertTrue(ModdedStudioHotloadService.hasDatapackImports(List.of(empty, imported)));
    }

    @Test
    public void rejectsMissingOrEmptyDatapackImports() {
        IrisDimension empty = new IrisDimension();

        assertFalse(ModdedStudioHotloadService.hasDatapackImports(null));
        assertFalse(ModdedStudioHotloadService.hasDatapackImports(List.of(empty)));
    }

    @Test
    public void reportsTheCapturedServerThread() throws InterruptedException {
        ModdedScheduler scheduler = new ModdedScheduler();
        scheduler.reset();
        try {
            ModdedStudioHotloadService service = new ModdedStudioHotloadService();
            assertTrue(service.isMainThread());
            AtomicBoolean offThread = new AtomicBoolean(true);
            Thread worker = new Thread(() -> offThread.set(service.isMainThread()));
            worker.start();
            worker.join();
            assertFalse(offThread.get());
        } finally {
            scheduler.shutdown();
        }
    }
    private static void setExecutor(ModdedStudioHotloadService service, ExecutorService executor) throws Exception {
        Field field = ModdedStudioHotloadService.class.getDeclaredField("executor");
        field.setAccessible(true);
        field.set(service, executor);
    }

    private static Engine studioEngine() {
        Engine engine = mock(Engine.class);
        when(engine.isStudio()).thenReturn(true);
        when(engine.getDimension()).thenReturn(new IrisDimension());
        return engine;
    }

}
