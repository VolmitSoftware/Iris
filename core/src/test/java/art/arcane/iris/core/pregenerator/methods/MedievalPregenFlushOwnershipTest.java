package art.arcane.iris.core.pregenerator.methods;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.INMSBinding;
import art.arcane.iris.util.common.scheduling.J;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class MedievalPregenFlushOwnershipTest {
    @Test
    public void closeWaitsForOffOwnerIoAfterTheOwnedUnloadReturns() throws Exception {
        MedievalPregenMethod method = mock(MedievalPregenMethod.class, CALLS_REAL_METHODS);
        World world = mock(World.class);
        Chunk chunk = mock(Chunk.class);
        INMSBinding binding = mock(INMSBinding.class);
        Map<Chunk, Long> lastUse = new ConcurrentHashMap<>();
        lastUse.put(chunk, 0L);
        ExecutorService io = Executors.newSingleThreadExecutor();
        ExecutorService closing = Executors.newSingleThreadExecutor();
        set(method, "world", world);
        set(method, "lastUse", lastUse);
        set(method, "chunkIoExecutor", io);
        CountDownLatch scheduled = new CountDownLatch(1);
        CountDownLatch draining = new CountDownLatch(1);
        CountDownLatch finishDrain = new CountDownLatch(1);
        AtomicReference<Runnable> ownerAction = new AtomicReference<>();
        CompletableFuture<Void> unloaded = new CompletableFuture<>();
        Thread owner = Thread.currentThread();
        doAnswer(invocation -> {
            assertSame(owner, Thread.currentThread());
            return true;
        }).when(chunk).unload(true);
        doAnswer(invocation -> {
            assertNotSame(owner, Thread.currentThread());
            verify(chunk).unload(true);
            draining.countDown();
            assertTrue(finishDrain.await(5L, TimeUnit.SECONDS));
            return null;
        }).when(binding).flushChunkIO(world);
        try {
            Future<?> closed = closing.submit(() -> {
                try (MockedStatic<J> scheduling = mockStatic(J.class);
                     MockedStatic<INMS> nativeAccess = mockStatic(INMS.class)) {
                    nativeAccess.when(INMS::get).thenReturn(binding);
                    scheduling.when(() -> J.sfut(any(Runnable.class))).thenAnswer(invocation -> {
                        ownerAction.set(invocation.getArgument(0));
                        scheduled.countDown();
                        return unloaded;
                    });
                    Method flush = MedievalPregenMethod.class.getDeclaredMethod("unloadAndSaveAllChunks", boolean.class);
                    flush.setAccessible(true);
                    flush.invoke(method, true);
                }
                return null;
            });
            assertTrue(scheduled.await(5L, TimeUnit.SECONDS));
            verifyNoInteractions(binding);
            ownerAction.get().run();
            unloaded.complete(null);
            assertTrue(draining.await(5L, TimeUnit.SECONDS));
            assertFalse(closed.isDone());
            assertTrue(lastUse.isEmpty());
            finishDrain.countDown();
            closed.get(5L, TimeUnit.SECONDS);
        } finally {
            unloaded.complete(null);
            finishDrain.countDown();
            io.shutdownNow();
            closing.shutdownNow();
            assertTrue(io.awaitTermination(5L, TimeUnit.SECONDS));
            assertTrue(closing.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    private static void set(MedievalPregenMethod method, String name, Object value) throws Exception {
        Field field = MedievalPregenMethod.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(method, value);
    }
}
