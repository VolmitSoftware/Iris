package art.arcane.iris.core.pregenerator.methods;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.INMSBinding;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KSet;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AsyncPregenServerSaveOwnershipTest {
    @Test
    public void serverStopRetainsChunksForNativeSaveWithoutSchedulingEviction() throws Exception {
        Fixture fixture = new Fixture();
        try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
             MockedStatic<J> scheduling = mockStatic(J.class)) {
            toolbelt.when(IrisToolbelt::isServerStopping).thenReturn(true);
            CompletableFuture<?> eviction = (CompletableFuture<?>) invoke(fixture.method, "evictRegion", 0L);
            assertTrue(eviction.isDone());
            assertFalse(eviction.isCompletedExceptionally());
            assertSame(fixture.chunk, fixture.chunks.get(0L).peek());
            scheduling.verifyNoInteractions();
            verifyNoInteractions(fixture.world, fixture.binding);
        }
    }

    @Test
    public void serverStopDoesNotWaitForAnUnresolvedPluginEviction() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> flushed = executor.submit(() -> {
                try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
                     MockedStatic<J> scheduling = mockStatic(J.class)) {
                    toolbelt.when(IrisToolbelt::isServerStopping).thenReturn(true);
                    scheduling.when(() -> J.sfut(any(Runnable.class)))
                            .thenReturn(CompletableFuture.completedFuture(null));
                    invoke(fixture.method, "flushAllRemainingChunks");
                    scheduling.verifyNoInteractions();
                }
                return null;
            });
            flushed.get(1L, TimeUnit.SECONDS);
            assertFalse(fixture.pending.isDone());
            assertSame(fixture.chunk, fixture.chunks.get(0L).peek());
            verifyNoInteractions(fixture.world, fixture.binding);
        } finally {
            fixture.pending.complete(null);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void serverStopDuringFlushWaitReturnsWithoutSchedulingALateFlush() throws Exception {
        Fixture fixture = new Fixture();
        AtomicBoolean stopping = new AtomicBoolean();
        CountDownLatch firstFlush = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstFlush.countDown();
            return null;
        }).when(fixture.binding).flushChunkIO(fixture.world);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SchedulingContext owner = new SchedulingContext(fixture)) {
            owner.toolbelt.when(IrisToolbelt::isServerStopping).thenAnswer(invocation -> stopping.get());
            Future<?> flushed = executor.submit(() -> {
                try (SchedulingContext context = new SchedulingContext(fixture)) {
                    context.toolbelt.when(IrisToolbelt::isServerStopping).thenAnswer(invocation -> stopping.get());
                    invoke(fixture.method, "flushAllRemainingChunks");
                }
                return null;
            });
            assertTrue(firstFlush.await(5L, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> flushed.get(30L, TimeUnit.MILLISECONDS));
            stopping.set(true);
            flushed.get(1L, TimeUnit.SECONDS);
            assertFalse(fixture.pending.isDone());
            verify(fixture.binding).flushChunkIO(fixture.world);

            fixture.pending.complete(null);
            owner.scheduling.verifyNoInteractions();
            verify(fixture.binding).flushChunkIO(fixture.world);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void ordinaryFlushWaitsForExistingEvictionsAndFlushesNewChunks() throws Exception {
        Fixture fixture = new Fixture();
        CountDownLatch firstFlush = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstFlush.countDown();
            return null;
        }).when(fixture.binding).flushChunkIO(fixture.world);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SchedulingContext ignored = new SchedulingContext(fixture)) {
            Future<?> flushed = executor.submit(() -> {
                try (SchedulingContext context = new SchedulingContext(fixture)) {
                    invoke(fixture.method, "flushAllRemainingChunks");
                }
                return null;
            });
            assertTrue(firstFlush.await(5L, TimeUnit.SECONDS));
            assertFalse(flushed.isDone());
            assertFalse(fixture.pending.isDone());
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
            verify(fixture.binding).flushChunkIO(fixture.world);
            fixture.pending.complete(null);
            flushed.get(5L, TimeUnit.SECONDS);
            verify(fixture.binding, times(2)).flushChunkIO(fixture.world);
            assertTrue(fixture.chunks.isEmpty());
        } finally {
            fixture.pending.complete(null);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    private static Object invoke(AsyncPregenMethod target, String name, Object... arguments) throws Exception {
        Method method = arguments.length == 0
                ? AsyncPregenMethod.class.getDeclaredMethod(name)
                : AsyncPregenMethod.class.getDeclaredMethod(name, long.class);
        method.setAccessible(true);
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException(failure.getCause());
        }
    }

    private static final class Fixture {
        private final AsyncPregenMethod method = mock(AsyncPregenMethod.class, CALLS_REAL_METHODS);
        private final World world = mock(World.class);
        private final Chunk chunk = mock(Chunk.class);
        private final INMSBinding binding = mock(INMSBinding.class);
        private final ConcurrentHashMap<Long, Queue<Chunk>> chunks = new ConcurrentHashMap<>();
        private final CompletableFuture<Void> pending = new CompletableFuture<>();

        private Fixture() throws Exception {
            Queue<Chunk> region = new ConcurrentLinkedQueue<>();
            region.add(chunk);
            chunks.put(0L, region);
            Queue<CompletableFuture<Void>> evictions = new ConcurrentLinkedQueue<>();
            evictions.add(pending);
            set("world", world);
            set("regionChunks", chunks);
            set("regionPending", new ConcurrentHashMap<>());
            set("pendingEvictions", evictions);
            set("evictedRegions", new KSet<>());
            when(chunk.getX()).thenReturn(2);
            when(chunk.getZ()).thenReturn(3);
            when(binding.saveAndUnloadChunk(world, 2, 3)).thenReturn(true);
        }

        private void set(String name, Object value) throws Exception {
            Field field = AsyncPregenMethod.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(method, value);
        }
    }

    private static final class SchedulingContext implements AutoCloseable {
        private final MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
        private final MockedStatic<J> scheduling = mockStatic(J.class);
        private final MockedStatic<INMS> nativeAccess = mockStatic(INMS.class);
        private final MockedStatic<BukkitPlatform> platform = mockStatic(BukkitPlatform.class);

        private SchedulingContext(Fixture fixture) {
            nativeAccess.when(INMS::get).thenReturn(fixture.binding);
            platform.when(BukkitPlatform::plugin).thenReturn(mock(Plugin.class));
            scheduling.when(() -> J.sfut(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return CompletableFuture.completedFuture(null);
            });
        }

        @Override
        public void close() {
            platform.close();
            nativeAccess.close();
            scheduling.close();
            toolbelt.close();
        }
    }
}
