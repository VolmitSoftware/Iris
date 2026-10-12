package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisWorldManager;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.ReactiveFolder;
import art.arcane.volmlib.util.scheduling.Looper;
import org.bukkit.generator.BlockPopulator;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitChunkGeneratorCloseTest {
    @Test
    public void nativeDropFlushCompletesBeforeExclusiveCloseIsDispatched() throws Exception {
        CloseFixture fixture = new CloseFixture();
        IrisWorldManager manager = mock(IrisWorldManager.class);
        CompletableFuture<Void> flushed = new CompletableFuture<>();
        when(fixture.engine.getWorldManager()).thenReturn(manager);
        when(manager.flushPendingBlockDropsAsync()).thenReturn(flushed);
        CompletableFuture<Void> operation = fixture.queueOperation();

        CompletableFuture<Void> closed = fixture.generator.closeAsync();

        assertFalse(closed.isDone());
        verify(fixture.generator, never()).withExclusiveControlFuture(any(Runnable.class));
        verify(fixture.engine, never()).close();
        flushed.complete(null);
        verify(fixture.generator).withExclusiveControlFuture(any(Runnable.class));
        verify(fixture.engine, never()).close();
        fixture.operation.get().run();
        operation.complete(null);
        closed.join();
        verify(fixture.engine).close();
    }

    @Test
    public void failedNativeDropFlushLeavesTheInitialCloseRetryable() throws Exception {
        CloseFixture fixture = new CloseFixture();
        IrisWorldManager manager = mock(IrisWorldManager.class);
        when(fixture.engine.getWorldManager()).thenReturn(manager);
        when(manager.flushPendingBlockDropsAsync()).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("Drop scheduling unavailable")),
                CompletableFuture.completedFuture(null));

        CompletableFuture<Void> failed = fixture.generator.closeAsync();

        assertTrue(failed.isCompletedExceptionally());
        assertFalse(fixture.generator.isClosing());
        verify(fixture.generator, never()).withExclusiveControlFuture(any(Runnable.class));
        verify(fixture.engine, never()).close();
        CompletableFuture<Void> operation = fixture.queueOperation();
        CompletableFuture<Void> retried = fixture.generator.closeAsync();
        assertNotSame(failed, retried);
        fixture.operation.get().run();
        operation.complete(null);
        retried.join();
        assertTrue(fixture.generator.isClosing());
        verify(fixture.engine).close();
    }

    @Test
    public void failedCloseReleasesInitialEntryDeferral() throws Exception {
        CloseFixture fixture = new CloseFixture();
        fixture.beginInitialEntry();
        doThrow(new IllegalStateException("Planner remains active")).when(fixture.engine).close();
        CompletableFuture<Void> operation = fixture.queueOperation();
        CompletableFuture<Void> closed = fixture.generator.closeAsync();

        operation.completeExceptionally(assertThrows(IllegalStateException.class, fixture.operation.get()::run));

        assertTrue(closed.isCompletedExceptionally());
        verify(fixture.hydrology).setNeighbourPrefetchEnabled(false);
        verify(fixture.hydrology).setNeighbourPrefetchEnabled(true);
        fixture.generator.completeInitialEntry();
        verify(fixture.hydrology, times(1)).setNeighbourPrefetchEnabled(true);
    }

    @Test
    public void rejectedCloseDispatchReleasesInitialEntryDeferral() throws Exception {
        CloseFixture fixture = new CloseFixture();
        fixture.beginInitialEntry();
        doThrow(new IllegalStateException("Scheduler unavailable")).when(fixture.generator)
                .withExclusiveControlFuture(any(Runnable.class));

        assertTrue(fixture.generator.closeAsync().isCompletedExceptionally());

        assertFalse(fixture.generator.isClosing());
        verify(fixture.hydrology).setNeighbourPrefetchEnabled(true);
    }

    @Test
    public void asynchronousCloseFailureCanRetryWithoutReopeningGeneration() throws Exception {
        CloseFixture fixture = new CloseFixture();
        IllegalStateException failure = new IllegalStateException("Planner remains active");
        doThrow(failure).doNothing().when(fixture.engine).close();
        CompletableFuture<Void> firstOperation = fixture.queueOperation();
        CompletableFuture<Void> firstClose = fixture.generator.closeAsync();
        assertSame(firstClose, fixture.generator.closeAsync());
        firstOperation.completeExceptionally(assertThrows(IllegalStateException.class, fixture.operation.get()::run));
        assertTrue(firstClose.isCompletedExceptionally());
        assertTrue(fixture.generator.isClosing());

        CompletableFuture<Void> secondOperation = fixture.queueOperation();
        CompletableFuture<Void> secondClose = fixture.generator.closeAsync();
        assertNotSame(firstClose, secondClose);
        fixture.operation.get().run();
        secondOperation.complete(null);
        secondClose.join();
        assertTrue(fixture.generator.isClosing());
        assertSame(secondClose, fixture.generator.closeAsync());
        verify(fixture.engine, times(2)).close();
    }

    @Test
    public void failedRetryDispatchKeepsPartiallyClosedGenerationSealed() throws Exception {
        CloseFixture fixture = new CloseFixture();
        CompletableFuture<Void> operation = fixture.queueOperation();
        CompletableFuture<Void> firstClose = fixture.generator.closeAsync();
        operation.completeExceptionally(new IllegalStateException("Planner remains active"));
        assertTrue(firstClose.isCompletedExceptionally());
        doThrow(new IllegalStateException("Scheduler unavailable")).when(fixture.generator)
                .withExclusiveControlFuture(any(Runnable.class));

        CompletableFuture<Void> retry = fixture.generator.closeAsync();
        assertNotSame(firstClose, retry);
        assertTrue(retry.isCompletedExceptionally());
        assertTrue(fixture.generator.isClosing());
    }

    @Test
    public void initialDispatchFailureLeavesTheUnchangedGeneratorAvailable() throws Exception {
        CloseFixture fixture = new CloseFixture();
        doThrow(new IllegalStateException("Scheduler unavailable")).when(fixture.generator)
                .withExclusiveControlFuture(any(Runnable.class));

        CompletableFuture<Void> failedClose = fixture.generator.closeAsync();
        assertTrue(failedClose.isCompletedExceptionally());
        assertFalse(fixture.generator.isClosing());
    }

    @Test
    public void closeCancelsPendingWatcherBeforeQueuingExclusiveCleanup() throws Exception {
        CloseFixture fixture = new CloseFixture();
        CompletableFuture<Void> pending = new CompletableFuture<>();
        fixture.watcherHotload.set(pending);
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        doAnswer(invocation -> {
            assertTrue(fixture.generator.isClosing());
            assertTrue(pending.isCancelled());
            assertNull(fixture.watcherHotload.get());
            return cleanup;
        }).when(fixture.generator).withExclusiveControlFuture(any(Runnable.class));

        CompletableFuture<Void> closed = fixture.generator.closeAsync();

        assertFalse(closed.isDone());
        cleanup.complete(null);
        closed.join();
    }

    @Test
    public void studioQuiesceCancelsWatcherAndDrainsExclusiveControlWithoutClosingEngine() throws Exception {
        CloseFixture fixture = new CloseFixture();
        CompletableFuture<Void> pending = new CompletableFuture<>();
        fixture.watcherHotload.set(pending);
        Looper watcher = mock(Looper.class);
        setField(fixture.generator, "hotloader", watcher);
        CompletableFuture<Void> drained = fixture.queueOperation();

        CompletableFuture<Void> quiesced = fixture.generator.quiesceStudioUpdatesAsync();

        assertTrue(pending.isCancelled());
        assertNull(fixture.watcherHotload.get());
        assertFalse(quiesced.isDone());
        assertFalse(fixture.generator.isClosing());
        verify(watcher).interrupt();
        fixture.generator.hotload();
        verify(fixture.engine, times(0)).hotload();
        verify(fixture.engine, times(0)).close();

        fixture.operation.get().run();
        drained.complete(null);
        quiesced.join();
        verify(fixture.engine, times(0)).close();
    }

    @Test
    public void shutdownQuiesceCancelsPendingWatcherWithoutClosingGeneration() throws Exception {
        CloseFixture fixture = new CloseFixture();
        CompletableFuture<Void> pending = new CompletableFuture<>();
        fixture.watcherHotload.set(pending);
        Looper watcher = mock(Looper.class);
        setField(fixture.generator, "hotloader", watcher);

        fixture.generator.quiesceForServerShutdown();

        assertTrue(pending.isCancelled());
        assertNull(fixture.watcherHotload.get());
        assertFalse(fixture.generator.isClosing());
        verify(watcher).interrupt();
    }

    private static void setField(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class CloseFixture {
        private final BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        private final IrisEngine engine = mock(IrisEngine.class);
        private final IrisComplex complex = mock(IrisComplex.class);
        private final IrisHydrologyRuntime hydrology = mock(IrisHydrologyRuntime.class);
        private final AtomicReference<Runnable> operation = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<Void>> watcherHotload = new AtomicReference<>();
        private final ConcurrentLinkedQueue<CompletableFuture<Void>> operations = new ConcurrentLinkedQueue<>();

        private CloseFixture() throws Exception {
            when(engine.getComplex()).thenReturn(complex);
            when(complex.getHydrologyRuntime()).thenReturn(hydrology);
            setField(generator, "closeFuture", new AtomicReference<CompletableFuture<Void>>());
            setField(generator, "watcherHotload", watcherHotload);
            setField(generator, "startupReady", CompletableFuture.completedFuture(null));
            setField(generator, "engine", engine);
            setField(generator, "folder", mock(ReactiveFolder.class));
            setField(generator, "populators", new KList<BlockPopulator>());
            doAnswer(invocation -> {
                operation.set(invocation.getArgument(0, Runnable.class));
                return operations.remove();
            }).when(generator).withExclusiveControlFuture(any(Runnable.class));
        }

        private CompletableFuture<Void> queueOperation() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            operations.add(future);
            return future;
        }

        private void beginInitialEntry() {
            generator.setEngine(null);
            generator.beginInitialEntry(false);
            generator.publishInitializedEngine(engine);
        }
    }
}
