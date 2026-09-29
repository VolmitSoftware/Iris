package art.arcane.iris.world.pregen;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AsyncPregenServerSaveOwnershipTest {
    @Test
    public void ownerReturnsAfterUnloadWhileEvictionTracksTheOffOwnerIoDrain() throws Exception {
        Fixture fixture = new Fixture();
        ExecutorService io = Executors.newSingleThreadExecutor();
        fixture.set("chunkIoExecutor", io);
        Thread ownerThread = Thread.currentThread();
        CountDownLatch draining = new CountDownLatch(1);
        CountDownLatch finishDrain = new CountDownLatch(1);
        AtomicReference<Thread> ioThread = new AtomicReference<>();
        doAnswer(invocation -> {
            assertSame(ownerThread, Thread.currentThread());
            return true;
        }).when(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
        doAnswer(invocation -> {
            ioThread.set(Thread.currentThread());
            draining.countDown();
            assertTrue(finishDrain.await(5L, TimeUnit.SECONDS));
            return null;
        }).when(fixture.binding).flushChunkIO(fixture.world);
        try (SchedulingContext context = new SchedulingContext(fixture)) {
            CompletableFuture<?> eviction = (CompletableFuture<?>) invoke(fixture.method, "evictRegion", 0L);
            assertTrue(draining.await(5L, TimeUnit.SECONDS));
            assertNotSame(ownerThread, ioThread.get());
            assertFalse(eviction.isDone());
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
            finishDrain.countDown();
            eviction.get(5L, TimeUnit.SECONDS);
        } finally {
            finishDrain.countDown();
            io.shutdownNow();
            assertTrue(io.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void foliaFlushWaitsForEveryOwningRegionAndRunsOffOwner() throws Exception {
        Fixture fixture = new Fixture();
        fixture.set("foliaRuntime", true);
        ExecutorService io = Executors.newSingleThreadExecutor();
        fixture.set("chunkIoExecutor", io);
        Chunk second = mock(Chunk.class);
        when(second.getX()).thenReturn(17);
        when(second.getZ()).thenReturn(3);
        fixture.chunks.get(0L).add(second);
        when(fixture.binding.saveAndUnloadChunk(fixture.world, 17, 3)).thenReturn(true);
        List<OwnedTask> owners = new ArrayList<>();
        Thread ownerThread = Thread.currentThread();
        CountDownLatch draining = new CountDownLatch(1);
        CountDownLatch finishDrain = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertNotSame(ownerThread, Thread.currentThread());
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 17, 3);
            draining.countDown();
            assertTrue(finishDrain.await(5L, TimeUnit.SECONDS));
            return null;
        }).when(fixture.binding).flushChunkIO(fixture.world);
        try (SchedulingContext context = new SchedulingContext(fixture)) {
            context.scheduling.when(() -> J.runRegionFuture(eq(fixture.world), anyInt(), anyInt(), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        CompletableFuture<Void> completion = new CompletableFuture<>();
                        owners.add(new OwnedTask(invocation.getArgument(1), invocation.getArgument(2),
                                invocation.getArgument(3), completion));
                        return completion;
                    });
            CompletableFuture<?> eviction = (CompletableFuture<?>) invoke(fixture.method, "evictRegion", 0L);
            assertEquals(2, owners.size());
            assertEquals(2, owners.get(0).chunkX());
            assertEquals(17, owners.get(1).chunkX());
            owners.get(0).run();
            assertEquals(1L, draining.getCount());
            assertFalse(eviction.isDone());
            owners.get(1).run();
            assertTrue(draining.await(5L, TimeUnit.SECONDS));
            assertFalse(eviction.isDone());
            finishDrain.countDown();
            eviction.get(5L, TimeUnit.SECONDS);
        } finally {
            finishDrain.countDown();
            io.shutdownNow();
            assertTrue(io.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedIoDrainFailsTheEvictionReceiptAndReportsTheFailure() throws Exception {
        Fixture fixture = new Fixture();
        IllegalStateException failure = new IllegalStateException("native IO failure");
        doThrow(failure).when(fixture.binding).flushChunkIO(fixture.world);
        try (SchedulingContext context = new SchedulingContext(fixture);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            CompletableFuture<?> eviction = (CompletableFuture<?>) invoke(fixture.method, "evictRegion", 0L);
            assertTrue(eviction.isCompletedExceptionally());
            assertSame(failure, assertThrows(CompletionException.class, eviction::join).getCause());
            logging.verify(() -> IrisLogging.reportError(any(Throwable.class)));
        }
    }

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
                    fixture.method.reclaimMemory();
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

    @Test
    public void emptyPublicReclaimDoesNotScheduleWorldIo() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunks.clear();
        fixture.set("pendingEvictions", new ConcurrentLinkedQueue<CompletableFuture<Void>>());
        try (SchedulingContext context = new SchedulingContext(fixture)) {
            fixture.method.reclaimMemory();
            context.scheduling.verifyNoInteractions();
            verifyNoInteractions(fixture.binding);
        }
    }

    @Test
    public void publicReclaimFlushesCompletedChunksWithoutPriorEvictions() throws Exception {
        Fixture fixture = new Fixture();
        fixture.set("pendingEvictions", new ConcurrentLinkedQueue<CompletableFuture<Void>>());
        try (SchedulingContext context = new SchedulingContext(fixture)) {
            fixture.method.reclaimMemory();
            assertTrue(fixture.chunks.isEmpty());
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
            verify(fixture.binding, times(2)).flushChunkIO(fixture.world);
        }
    }

    @Test
    public void publicReclaimWaitsForEvictionsWithoutNewChunks() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunks.clear();
        CountDownLatch started = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (SchedulingContext owner = new SchedulingContext(fixture)) {
            Future<?> reclaimed = executor.submit(() -> {
                try (SchedulingContext context = new SchedulingContext(fixture)) {
                    started.countDown();
                    fixture.method.reclaimMemory();
                }
            });
            assertTrue(started.await(5L, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> reclaimed.get(30L, TimeUnit.MILLISECONDS));
            verifyNoInteractions(fixture.binding);
            fixture.pending.complete(null);
            reclaimed.get(5L, TimeUnit.SECONDS);
            owner.scheduling.verifyNoInteractions();
            verify(fixture.binding).flushChunkIO(fixture.world);
        } finally {
            fixture.pending.complete(null);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void nativeFlushRunsOnThePregenFlushWorkerNotTheSharedIoPool() throws Exception {
        Fixture fixture = new Fixture();
        PregenSerialWorker flush = new PregenSerialWorker("Iris Pregen Chunk Flush", "world");
        fixture.set("chunkIoExecutor", flush.executor());
        AtomicReference<Thread> ioThread = new AtomicReference<>();
        doAnswer(invocation -> {
            ioThread.set(Thread.currentThread());
            return null;
        }).when(fixture.binding).flushChunkIO(fixture.world);
        try (SchedulingContext context = new SchedulingContext(fixture)) {
            CompletableFuture<?> eviction = (CompletableFuture<?>) invoke(fixture.method, "evictRegion", 0L);
            eviction.get(5L, TimeUnit.SECONDS);
            assertTrue(ioThread.get().getName().startsWith("Iris Pregen Chunk Flush world"));
            assertFalse(ioThread.get().getName().startsWith("Iris IO"));
        } finally {
            assertTrue(flush.close(5L, TimeUnit.SECONDS));
        }
    }

    @Test
    public void completedRegionsUnloadAtDrainWhileRetentionKeepsOnlyTheBordersUndrainedNeighboursNeed() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunks.clear();
        Set<Long> retained = new HashSet<>();
        PregenChunkRetention retention = new PregenChunkRetention(new PregenChunkRetention.Tickets() {
            @Override
            public void retain(int chunkX, int chunkZ) {
                assertTrue(retained.add(PregenChunkRetention.regionKey(chunkX, chunkZ)));
            }

            @Override
            public void release(int chunkX, int chunkZ) {
                assertTrue(retained.remove(PregenChunkRetention.regionKey(chunkX, chunkZ)));
            }
        }, 10, 1_000_000, 0, 0, 127, 0);
        fixture.set("retention", retention);
        try (SchedulingContext context = new SchedulingContext(fixture)) {
            for (int region = 0; region < 128; region++) {
                long key = (long) region << 32;
                retention.retainAround(region << 5, 0);
                retention.retainAround((region << 5) + 31, 0);
                fixture.pendingRegions.put(key, new AtomicInteger(2));
                invoke(fixture.method, "onChunkCompleted", region << 5, 0, fixture.chunk);
                assertEquals(1, fixture.pendingRegions.get(key).get());
                fixture.method.onRegionSubmitted(region, 0);
                assertTrue(fixture.pendingRegions.isEmpty());
                assertTrue(fixture.chunks.isEmpty());
                verify(fixture.binding, times(region + 1)).saveAndUnloadChunk(fixture.world, 2, 3);
                for (long position : retained) {
                    assertTrue((int) (position >> 32) >= ((region + 1) << 5) - 10);
                }
            }
            assertTrue(retained.isEmpty());
        }
    }

    @Test
    public void partialRegionEvictionKeepsPendingSubmissionsAndAcceptsLaterCompletions() throws Exception {
        Fixture fixture = new Fixture();
        AtomicInteger pendingChunks = new AtomicInteger(2);
        fixture.pendingRegions.put(0L, pendingChunks);
        try (SchedulingContext context = new SchedulingContext(fixture)) {
            CompletableFuture<?> eviction = (CompletableFuture<?>) invoke(fixture.method, "evictRegion", 0L);
            eviction.get(5L, TimeUnit.SECONDS);
            assertSame(pendingChunks, fixture.pendingRegions.get(0L));
            assertEquals(2, pendingChunks.get());
            assertTrue(fixture.chunks.isEmpty());

            invoke(fixture.method, "onChunkCompleted", 2, 3, fixture.chunk);
            assertEquals(1, pendingChunks.get());
            assertSame(fixture.chunk, fixture.chunks.get(0L).peek());
            fixture.method.onRegionSubmitted(0, 0);
            assertTrue(fixture.pendingRegions.isEmpty());
            assertTrue(fixture.chunks.isEmpty());
            verify(fixture.binding, times(2)).saveAndUnloadChunk(fixture.world, 2, 3);
        }
    }

    @Test
    public void completionDuringHeapWaitEvictsWithoutWaitingForRegionSubmission() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunks.clear();
        AtomicInteger pendingChunks = new AtomicInteger(2);
        fixture.pendingRegions.put(0L, pendingChunks);
        try (SchedulingContext context = new SchedulingContext(fixture);
             MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            pressure.when(MantleHeapPressure::overHighWater).thenReturn(true);
            invoke(fixture.method, "onChunkCompleted", 2, 3, fixture.chunk);
            assertEquals(1, pendingChunks.get());
            assertSame(pendingChunks, fixture.pendingRegions.get(0L));
            assertTrue(fixture.chunks.isEmpty());
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
            fixture.method.onRegionSubmitted(0, 0);
            assertTrue(fixture.pendingRegions.isEmpty());
        }
    }

    @Test
    public void heapPressureReleasesEveryRetainedChunkBeforeEvicting() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunks.clear();
        Set<Long> retained = new HashSet<>();
        PregenChunkRetention retention = new PregenChunkRetention(new PregenChunkRetention.Tickets() {
            @Override
            public void retain(int chunkX, int chunkZ) {
                retained.add(PregenChunkRetention.regionKey(chunkX, chunkZ));
            }

            @Override
            public void release(int chunkX, int chunkZ) {
                retained.remove(PregenChunkRetention.regionKey(chunkX, chunkZ));
            }
        }, 10, 1_000_000, 0, 0, 0, 0);
        retention.retainAround(2, 3);
        fixture.set("retention", retention);
        fixture.pendingRegions.put(0L, new AtomicInteger(2));
        try (SchedulingContext context = new SchedulingContext(fixture);
             MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            pressure.when(MantleHeapPressure::overHighWater).thenReturn(true);
            invoke(fixture.method, "onChunkCompleted", 2, 3, fixture.chunk);
            assertTrue(retained.isEmpty());
            assertEquals(0, retention.size());
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
        }
    }

    @Test
    public void heapPressureStartingAfterInitialCheckStillReclaimsCompletedChunks() throws Exception {
        Fixture fixture = new Fixture();
        fixture.pending.complete(null);
        fixture.set("closing", new AtomicBoolean(true));
        PregenMantleBackpressure backpressure = mock(PregenMantleBackpressure.class);
        fixture.set("backpressure", backpressure);
        doAnswer(invocation -> {
            BooleanSupplier pressure = invocation.getArgument(0);
            Runnable reclaim = invocation.getArgument(1);
            assertTrue(pressure.getAsBoolean());
            reclaim.run();
            return null;
        }).when(backpressure).awaitHeapHeadroom(any(BooleanSupplier.class), any(Runnable.class));
        try (SchedulingContext context = new SchedulingContext(fixture);
             MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            pressure.when(MantleHeapPressure::overHighWater).thenReturn(false, true);
            fixture.method.generateChunk(2, 3, mock(PregenListener.class));
            assertTrue(fixture.chunks.isEmpty());
            verify(fixture.binding).saveAndUnloadChunk(fixture.world, 2, 3);
            pressure.verify(MantleHeapPressure::requestPanicReclaim);
        }
    }

    @Test
    public void emptyPressurePollDoesNotScheduleWorldIo() throws Exception {
        Fixture fixture = new Fixture();
        fixture.chunks.clear();
        fixture.set("pendingEvictions", new ConcurrentLinkedQueue<CompletableFuture<Void>>());
        try (SchedulingContext context = new SchedulingContext(fixture);
             MockedStatic<MantleHeapPressure> pressure = mockStatic(MantleHeapPressure.class)) {
            invoke(fixture.method, "reclaimHeapPressure");
            verifyNoInteractions(fixture.binding);
            context.scheduling.verifyNoInteractions();
            pressure.verify(MantleHeapPressure::requestPanicReclaim);
        }
    }

    private static Object invoke(AsyncPregenMethod target, String name, Object... arguments) throws Exception {
        Class<?>[] parameterTypes = new Class<?>[arguments.length];
        for (int index = 0; index < arguments.length; index++) {
            parameterTypes[index] = arguments[index] instanceof Integer ? int.class
                    : arguments[index] instanceof Long ? long.class : Chunk.class;
        }
        Method method = AsyncPregenMethod.class.getDeclaredMethod(name, parameterTypes);
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
        private final ConcurrentHashMap<Long, AtomicInteger> pendingRegions = new ConcurrentHashMap<>();

        private Fixture() throws Exception {
            Queue<Chunk> region = new ConcurrentLinkedQueue<>();
            region.add(chunk);
            chunks.put(0L, region);
            Queue<CompletableFuture<Void>> evictions = new ConcurrentLinkedQueue<>();
            evictions.add(pending);
            set("world", world);
            set("regionChunks", chunks);
            set("regionPending", pendingRegions);
            set("pendingEvictions", evictions);
            set("chunkIoExecutor", (Executor) Runnable::run);
            set("generationFailure", new AtomicReference<>());
            set("inFlightRequests", new PregenInFlightRequests());
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

    private record OwnedTask(int chunkX, int chunkZ, Runnable action, CompletableFuture<Void> completion) {
        private void run() {
            action.run();
            completion.complete(null);
        }
    }
}
