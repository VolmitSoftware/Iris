package art.arcane.iris.generation.context;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.bukkit.block.data.BlockData;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

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
    public void generationWorkersPrefillInlineWithoutConsultingSharedExecutor() {
        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class)) {
            platforms.when(IrisPlatforms::isBound).thenReturn(true);
            assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NO_CAVE, false));
            assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.ALL, false));
            platforms.verifyNoInteractions();
        }
    }

    @Test
    public void platformMainThreadKeepsBlockingPrefillOffTheTickThread() {
        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class)) {
            platforms.when(IrisPlatforms::isBound).thenReturn(true);
            assertTrue(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NO_CAVE, true));
            assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NONE, true));
            assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NATURAL_TERRAIN, true));
        }
    }

    @Test
    public void inlinePrefillFinishesHeightsBeforeTheBiomePassStarts() {
        AtomicInteger caveCalls = new AtomicInteger();
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        AtomicInteger rockCalls = new AtomicInteger();
        AtomicInteger fluidCalls = new AtomicInteger();
        AtomicInteger regionCalls = new AtomicInteger();
        AtomicInteger heightsSeenByFirstBiome = new AtomicInteger(-1);
        try (MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class)) {
            platforms.when(IrisPlatforms::isBound).thenReturn(true);
            createContext(ChunkContext.PrefillPlan.NO_CAVE, caveCalls, heightCalls, biomeCalls, rockCalls,
                    fluidCalls, regionCalls, FakeHydrology.disabled(), () -> heightsSeenByFirstBiome.compareAndSet(-1, heightCalls.get()));
        }

        assertEquals(256, heightsSeenByFirstBiome.get());
        assertEquals(256, biomeCalls.get());
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
            assertFalse(ChunkContext.shouldPrefillAsync(ChunkContext.PrefillPlan.NATURAL_TERRAIN, true));
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

    @Test(timeout = 120_000L)
    public void waterRasterResolvesOnceBeforeDependentPrefills() throws Exception {
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger biomeCalls = new AtomicInteger();
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 64, 40, 70)) {
            hydrology.hold();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<ChunkContext> future = executor.submit(() -> createContext(ChunkContext.PrefillPlan.NO_CAVE, new AtomicInteger(),
                        heightCalls, biomeCalls, new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), hydrology.runtime));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
                while (hydrology.windowCalls.get() == 0) {
                    assertTrue("the water raster was never requested", System.nanoTime() < deadline);
                    Thread.sleep(10L);
                }
                assertEquals(0, heightCalls.get());
                assertEquals(0, biomeCalls.get());
                hydrology.release();
                ChunkContext context = future.get(30L, TimeUnit.SECONDS);
                ChunkWaterRaster raster = context.waterRaster();
                assertFalse(raster.provisional());
                assertSame(hydrology.runtime.raster(2, 3), raster);
                assertSame(raster, context.waterRaster());
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));
            }
        }
        assertEquals(256, heightCalls.get());
        assertEquals(256, biomeCalls.get());
    }

    @Test(timeout = 120_000L)
    public void waterRasterWaitsForFirstUseWithoutDependentPrefills() {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 64, 40, 70)) {
            ChunkContext context = createContext(ChunkContext.PrefillPlan.NONE, new AtomicInteger(), new AtomicInteger(),
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), hydrology.runtime);
            assertEquals(0, hydrology.windowCalls.get());
            ChunkWaterRaster raster = context.waterRaster();
            assertFalse(raster.provisional());
            assertSame(raster, context.waterRaster());
        }
    }

    @Test(timeout = 120_000L)
    public void provisionalWaterRasterIsNeverKept() throws Exception {
        try (FakeHydrology hydrology = new FakeHydrology(() -> true, (x, z) -> 64, 40, 70)) {
            hydrology.hold();
            AtomicInteger heightCalls = new AtomicInteger();
            ChunkContext context = createContext(ChunkContext.PrefillPlan.NO_CAVE, new AtomicInteger(), heightCalls,
                    new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), hydrology.runtime);
            assertEquals(256, heightCalls.get());
            assertSame(ChunkWaterRaster.PROVISIONAL, context.waterRaster());
            hydrology.release();
            ChunkWaterRaster landed = hydrology.awaitSnapshot(2, 3);
            assertSame(landed, context.waterRaster());
            hydrology.runtime.close();
            assertSame(ChunkWaterRaster.PROVISIONAL, hydrology.runtime.raster(2, 3));
            assertSame(landed, context.waterRaster());
        }
    }

    @Test(timeout = 120_000L)
    public void failedWaterRasterFailsTheChunkInsteadOfDryingIt() {
        try (FakeHydrology hydrology = new FakeHydrology(() -> false, (x, z) -> 64, 40, 70)) {
            hydrology.failing.set(true);
            AtomicInteger heightCalls = new AtomicInteger();
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> createContext(ChunkContext.PrefillPlan.NO_CAVE,
                    new AtomicInteger(), heightCalls, new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger(),
                    hydrology.runtime));
            StringBuilder causes = new StringBuilder();
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                causes.append(cause.getMessage()).append(" | ");
            }
            assertTrue(causes.toString(), causes.toString().contains(FakeHydrology.FAILURE));
            assertEquals(0, heightCalls.get());
        }
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
                FakeHydrology.disabled()
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
            HydrologyRuntime runtime
    ) {
        return createContext(prefillPlan, caveCalls, heightCalls, biomeCalls, rockCalls, fluidCalls, regionCalls,
                runtime, () -> {
                });
    }

    private ChunkContext createContext(
            ChunkContext.PrefillPlan prefillPlan,
            AtomicInteger caveCalls,
            AtomicInteger heightCalls,
            AtomicInteger biomeCalls,
            AtomicInteger rockCalls,
            AtomicInteger fluidCalls,
            AtomicInteger regionCalls,
            HydrologyRuntime runtime,
            Runnable biomeObserver
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
            biomeObserver.run();
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
