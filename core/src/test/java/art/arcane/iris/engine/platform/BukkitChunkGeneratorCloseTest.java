package art.arcane.iris.engine.platform;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.ReactiveFolder;
import org.bukkit.generator.BlockPopulator;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class BukkitChunkGeneratorCloseTest {
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

    private static void setField(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class CloseFixture {
        private final BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        private final Engine engine = mock(Engine.class);
        private final AtomicReference<Runnable> operation = new AtomicReference<>();
        private final ConcurrentLinkedQueue<CompletableFuture<Void>> operations = new ConcurrentLinkedQueue<>();

        private CloseFixture() throws Exception {
            setField(generator, "closeFuture", new AtomicReference<CompletableFuture<Void>>());
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
    }
}
