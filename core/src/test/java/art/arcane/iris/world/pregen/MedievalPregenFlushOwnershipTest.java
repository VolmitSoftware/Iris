package art.arcane.iris.world.pregen;

import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.hydrology.engine.HydrologyPregeneration;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class MedievalPregenFlushOwnershipTest {
    @Test
    public void mantlePrefetchHandsTheChunkContextBlockCoordinates() throws Exception {
        MedievalPregenMethod method = mock(MedievalPregenMethod.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.isUseMantle()).thenReturn(true);
        when(engine.getDimension()).thenReturn(dimension);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getRawHeightStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 64D));
        when(complex.getTrueBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> null, Interpolated.of(value -> 0D, value -> null)));
        when(complex.getCaveBiomeStream()).thenReturn(ProceduralStream.of((x, z) -> null, Interpolated.of(value -> 0D, value -> null)));
        when(complex.getRockStream()).thenReturn(ProceduralStream.of((x, z) -> null, Interpolated.of(value -> 0D, value -> null)));
        when(complex.getFluidStream()).thenReturn(ProceduralStream.of((x, z) -> null, Interpolated.of(value -> 0D, value -> null)));
        when(complex.getRegionStream()).thenReturn(ProceduralStream.of((x, z) -> null, Interpolated.of(value -> 0D, value -> null)));
        HydrologyRuntime hydrology = FakeHydrology.disabled();
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
        when(engine.getComplex()).thenReturn(complex);
        AtomicReference<ChunkContext> context = new AtomicReference<>();
        doAnswer(invocation -> {
            context.set(invocation.getArgument(3));
            return null;
        }).when(engine).generateMatter(eq(3), eq(-2), eq(true), any());
        Method prefetch = MedievalPregenMethod.class.getDeclaredMethod("prefetchMantle", Engine.class, int.class, int.class);
        prefetch.setAccessible(true);

        prefetch.invoke(method, engine, 3, -2);

        assertEquals(48, context.get().getX());
        assertEquals(-32, context.get().getZ());
    }

    @Test
    public void closeWithdrawsQueuedHydrologyPrefetchAndRefusesLaterAnnouncements() throws Exception {
        MedievalPregenMethod method = mock(MedievalPregenMethod.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        HydrologyPregeneration epoch = mock(HydrologyPregeneration.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(runtime.openPregeneration()).thenReturn(epoch);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        set(method, "world", world);
        set(method, "cachedEngine", engine);
        set(method, "closing", new AtomicBoolean());
        set(method, "hydrology", new PregenHydrology(() -> engine));
        set(method, "futures", new KList<>());
        set(method, "lastUse", new ConcurrentHashMap<>());
        set(method, "chunkFlush", new PregenSerialWorker("Test Chunk Flush", "world"));
        List<long[]> ahead = List.of(new long[]{0L, 0L}, new long[]{1L, 0L});

        method.onRegionsAhead(ahead);
        try (MockedStatic<J> scheduling = mockStatic(J.class)) {
            scheduling.when(J::isFolia).thenReturn(true);
            method.close();
        }
        method.onRegionsAhead(ahead);

        InOrder order = inOrder(epoch, engine);
        order.verify(epoch).prepare(ahead);
        order.verify(epoch).close();
        order.verify(engine).syncGenerationHistory();
        verify(epoch, times(1)).prepare(anyList());
    }

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
