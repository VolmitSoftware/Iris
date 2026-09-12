package art.arcane.iris.generation.context;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.bukkit.block.data.BlockData;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ChunkContextPrefillPlanTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Test
    public void noCavePrefillSkipsCaveCacheFill() {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        ChunkContext context = createContext(
                ChunkContext.PrefillPlan.NO_CAVE,
                caveCalls,
                heightCalls,
                biomeCalls,
                rockCalls,
                fluidCalls,
                regionCalls
        );

        assertEquals(256, heightCalls.get());
        assertEquals(256, biomeCalls.get());
        assertEquals(256, rockCalls.get());
        assertEquals(256, fluidCalls.get());
        assertEquals(256, regionCalls.get());
        assertEquals(0, caveCalls.get());

        assertEquals(34051D, context.getHeight().getDouble(2, 3), 0D);
        context.getCave().get(2, 3);
        context.getCave().get(2, 3);
        assertEquals(1, caveCalls.get());
    }

    @Test
    public void allPrefillIncludesCaveCacheFill() {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        ChunkContext context = createContext(
                ChunkContext.PrefillPlan.ALL,
                caveCalls,
                heightCalls,
                biomeCalls,
                rockCalls,
                fluidCalls,
                regionCalls
        );

        assertEquals(256, heightCalls.get());
        assertEquals(256, biomeCalls.get());
        assertEquals(256, rockCalls.get());
        assertEquals(256, fluidCalls.get());
        assertEquals(256, regionCalls.get());
        assertEquals(256, caveCalls.get());

        context.getCave().get(1, 1);
        assertEquals(256, caveCalls.get());
    }

    @Test
    public void singleOrNoFillTaskPrefillsInline() {
        assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NO_CAVE, 0));
        assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NO_CAVE, 1));
    }

    @Test
    public void naturalTerrainPrefillsAllRequiredCachesWithoutConsultingSharedExecutor() {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class)) {
            platforms.when(IrisPlatforms::isBound).thenReturn(true);
            assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NATURAL_TERRAIN, 5));
            ChunkContext context = createContext(ChunkContext.PrefillPlan.NATURAL_TERRAIN,
                    caveCalls, heightCalls, biomeCalls, rockCalls, fluidCalls, regionCalls);

            assertEquals(256, heightCalls.get());
            assertEquals(256, biomeCalls.get());
            assertEquals(256, rockCalls.get());
            assertEquals(256, fluidCalls.get());
            assertEquals(256, regionCalls.get());
            assertEquals(0, caveCalls.get());
            assertEquals(34051D, context.getHeight().getDouble(2, 3), 0D);
            assertEquals(34051, context.getRoundedHeight(2, 3));
            context.getBiome().get(2, 3);
            context.getRegion().get(2, 3);
            assertEquals(256, heightCalls.get());
            assertEquals(256, biomeCalls.get());
            assertEquals(256, regionCalls.get());
            platforms.verifyNoInteractions();
        }
    }

    @Test
    public void hydrologyPreparationCompletesBeforeDependentPrefills() throws Exception {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        CountDownLatch preparationStarted = new CountDownLatch(1);
        CountDownLatch preparationRelease = new CountDownLatch(1);
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);
        doAnswer(invocation -> {
            preparationStarted.countDown();
            assertTrue(preparationRelease.await(10L, TimeUnit.SECONDS));
            return null;
        }).when(runtime).prepareChunkColumns(32, 48);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ChunkContext> future = executor.submit(() -> createContext(
                    ChunkContext.PrefillPlan.NO_CAVE,
                    caveCalls,
                    heightCalls,
                    biomeCalls,
                    rockCalls,
                    fluidCalls,
                    regionCalls,
                    runtime
            ));
            assertTrue(preparationStarted.await(10L, TimeUnit.SECONDS));
            assertEquals(0, heightCalls.get());
            assertEquals(0, biomeCalls.get());
            preparationRelease.countDown();
            future.get(10L, TimeUnit.SECONDS);
        } finally {
            preparationRelease.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));
        }
        assertEquals(256, heightCalls.get());
        assertEquals(256, biomeCalls.get());
        verify(runtime).prepareChunkColumns(32, 48);
    }

    @Test
    public void hydrologyPreparationIsSkippedWithoutDependentPrefills() {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        IrisHydrologyRuntime runtime = mock(IrisHydrologyRuntime.class);

        createContext(
                ChunkContext.PrefillPlan.NONE,
                caveCalls,
                heightCalls,
                biomeCalls,
                rockCalls,
                fluidCalls,
                regionCalls,
                runtime
        );

        verify(runtime, never()).prepareChunkColumns(32, 48);
    }

    private ChunkContext createContext(
            ChunkContext.PrefillPlan prefillPlan,
            AtomicInteger caveCalls,
            AtomicInteger heightCalls,
            AtomicInteger biomeCalls,
            AtomicInteger rockCalls,
            AtomicInteger fluidCalls,
            AtomicInteger regionCalls
    ) {
        return createContext(
                prefillPlan,
                caveCalls,
                heightCalls,
                biomeCalls,
                rockCalls,
                fluidCalls,
                regionCalls,
                null
        );
    }

    private ChunkContext createContext(
            ChunkContext.PrefillPlan prefillPlan,
            AtomicInteger caveCalls,
            AtomicInteger heightCalls,
            AtomicInteger biomeCalls,
            AtomicInteger rockCalls,
            AtomicInteger fluidCalls,
            AtomicInteger regionCalls,
            IrisHydrologyRuntime runtime
    ) {
        IrisComplex complex = mock(IrisComplex.class);

        @SuppressWarnings("unchecked")
        ProceduralStream<Double> heightStream = mock(ProceduralStream.class);
        doAnswer(invocation -> {
            heightCalls.incrementAndGet();
            double worldX = invocation.getArgument(0);
            double worldZ = invocation.getArgument(1);
            return (worldX * 1000D) + worldZ;
        }).when(heightStream).getDouble(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> biomeStream = mock(ProceduralStream.class);
        IrisBiome biome = mock(IrisBiome.class);
        doAnswer(invocation -> {
            biomeCalls.incrementAndGet();
            return biome;
        }).when(biomeStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<IrisBiome> caveStream = mock(ProceduralStream.class);
        IrisBiome caveBiome = mock(IrisBiome.class);
        doAnswer(invocation -> {
            caveCalls.incrementAndGet();
            return caveBiome;
        }).when(caveStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<BlockData> rockStream = mock(ProceduralStream.class);
        BlockData rock = mock(BlockData.class);
        doReturn("minecraft:stone").when(rock).getAsString();
        doAnswer(invocation -> {
            rockCalls.incrementAndGet();
            return rock;
        }).when(rockStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<BlockData> fluidStream = mock(ProceduralStream.class);
        BlockData fluid = mock(BlockData.class);
        doReturn("minecraft:water").when(fluid).getAsString();
        doAnswer(invocation -> {
            fluidCalls.incrementAndGet();
            return fluid;
        }).when(fluidStream).get(anyDouble(), anyDouble());

        @SuppressWarnings("unchecked")
        ProceduralStream<IrisRegion> regionStream = mock(ProceduralStream.class);
        IrisRegion region = mock(IrisRegion.class);
        doAnswer(invocation -> {
            regionCalls.incrementAndGet();
            return region;
        }).when(regionStream).get(anyDouble(), anyDouble());

        doReturn(heightStream).when(complex).getRawHeightStream();
        doReturn(biomeStream).when(complex).getTrueBiomeStream();
        doReturn(caveStream).when(complex).getCaveBiomeStream();
        doReturn(rockStream).when(complex).getRockStream();
        doReturn(fluidStream).when(complex).getFluidStream();
        doReturn(regionStream).when(complex).getRegionStream();
        doReturn(runtime).when(complex).getHydrologyRuntime();

        return new ChunkContext(32, 48, complex, true, prefillPlan, null);
    }
}
