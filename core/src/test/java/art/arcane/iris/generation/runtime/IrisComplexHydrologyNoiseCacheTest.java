package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.world.pregen.PregenPerformanceProfile;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.HydrologyPlanner;
import art.arcane.iris.generation.hydrology.HydrologyPlannerSettings;
import art.arcane.iris.generation.hydrology.HydrologyTile;
import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.iris.generation.hydrology.HydrologyTileKey;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.stream.CachedDoubleStream2D;
import art.arcane.iris.generation.stream.CachedStream2D;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisComplexHydrologyNoiseCacheTest {
    @Test
    public void pregenGrowsTheBackingHeightCacheWithoutReplacingItsThreadAwareStream() throws Exception {
        IrisSettings previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisSettings.get().getPerformance().setNoiseCacheSize(1_024);
        try (Fixture fixture = new Fixture(1_024)) {
            ProceduralStream<Double> wrapper = fixture.complex.getRawHeightStream();
            assertEquals(13D, fixture.height.getDouble(18, 5), 0D);
            assertEquals(13D, fixture.rawHeight.getDouble(18, 5), 0D);

            PregenPerformanceProfile.apply(fixture.engine);

            assertEquals(4_096L * 256L, fixture.height.getMaxSize());
            assertEquals(4_096L * 256L, fixture.rawHeight.getMaxSize());
            assertEquals(1_024L * 256L, fixture.biomes.getMaxSize());
            assertSame(wrapper, fixture.complex.getRawHeightStream());
            assertEquals(47D, wrapper.getDouble(18, 5), 0D);
            assertEquals(13D, fixture.height.getDouble(18, 5), 0D);
            assertEquals(13D, fixture.rawHeight.getDouble(18, 5), 0D);
        } finally {
            PregenPerformanceProfile.restore();
            IrisSettings.settings = previousSettings;
        }
    }

    @Test
    public void pregenPreservesActiveHydrologyGrants() throws Exception {
        try (Fixture fixture = new Fixture(1_024)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            fixture.complex.expandHydrologyNoiseCaches(budget);
            long reservedBytes = budget.reservedBytes();

            fixture.complex.ensureTerrainNoiseCacheSize(4_096);

            fixture.assertCapacities(32_768);
            assertEquals(4_096L * 256L, fixture.rawHeight.getMaxSize());
            assertEquals(reservedBytes, budget.reservedBytes());
            fixture.complex.close();
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test
    public void pregenPreservesLargerRuntimeCacheCapacities() throws Exception {
        try (Fixture fixture = new Fixture(65_536)) {
            fixture.complex.ensureTerrainNoiseCacheSize(4_096);

            fixture.assertCapacities(65_536);
            assertEquals(65_536L * 256L, fixture.rawHeight.getMaxSize());
        }
    }

    @Test
    public void idleRuntimeReservesOnlyOnDemandAndRestoresAfterHydrologyDrains() throws Exception {
        try (Fixture fixture = new Fixture(1_024)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            HydrologyTileCache cache = fixture.planningCache(budget);
            fixture.assertCapacities(1_024);
            assertEquals(0L, budget.reservedBytes());
            cache.get(new HydrologyTileKey(0, 0));
            cache.get(new HydrologyTileKey(0, 0));
            fixture.assertCapacities(32_768);
            assertEquals(4L * (32_768L - 1_024L) * 4_096L, budget.reservedBytes());
            doAnswer(invocation -> {
                fixture.assertCapacities(32_768);
                assertEquals(4L * (32_768L - 1_024L) * 4_096L, budget.reservedBytes());
                cache.close();
                return null;
            }).when(fixture.hydrology).close();
            fixture.complex.close();
            fixture.assertCapacities(1_024);
            assertEquals(0L, budget.reservedBytes());
            doNothing().when(fixture.hydrology).close();
            fixture.complex.close();
            fixture.complex.expandHydrologyNoiseCaches(budget);
            fixture.assertCapacities(1_024);
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test(timeout = 10_000L)
    public void concurrentCacheExpansionsShareOneReservation() throws Exception {
        try (Fixture fixture = new Fixture(1_024);
             ExecutorService callers = Executors.newFixedThreadPool(2)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            CountDownLatch entered = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            Runnable prepare = () -> {
                entered.countDown();
                await(release);
                fixture.complex.expandHydrologyNoiseCaches(budget);
            };
            Future<?> first = callers.submit(prepare);
            Future<?> second = callers.submit(prepare);
            assertTrue(entered.await(5L, TimeUnit.SECONDS));
            assertEquals(0L, budget.reservedBytes());
            release.countDown();
            first.get(5L, TimeUnit.SECONDS);
            second.get(5L, TimeUnit.SECONDS);
            fixture.assertCapacities(32_768);
            assertEquals(4L * (32_768L - 1_024L) * 4_096L, budget.reservedBytes());
            fixture.complex.close();
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test(timeout = 10_000L)
    public void closeDrainsAnAdmittedFirstDemandBeforeReleasingItsGrant() throws Exception {
        try (Fixture fixture = new Fixture(1_024);
             ExecutorService callers = Executors.newFixedThreadPool(2)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            HydrologyTileCache cache = fixture.planningCache(budget);
            CountDownLatch preparing = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch closing = new CountDownLatch(1);
            cache.setTerrainPreparation(() -> {
                preparing.countDown();
                await(release);
                fixture.complex.expandHydrologyNoiseCaches(budget);
            });
            doAnswer(invocation -> {
                closing.countDown();
                cache.close();
                return null;
            }).when(fixture.hydrology).close();
            Future<?> demand = callers.submit(() -> cache.get(new HydrologyTileKey(0, 0)));
            assertTrue(preparing.await(5L, TimeUnit.SECONDS));
            Future<?> close = callers.submit(fixture.complex::close);
            assertTrue(closing.await(5L, TimeUnit.SECONDS));
            try {
                assertThrows(TimeoutException.class, () -> close.get(100L, TimeUnit.MILLISECONDS));
                assertEquals(0L, budget.reservedBytes());
            } finally {
                release.countDown();
            }
            demand.get(5L, TimeUnit.SECONDS);
            close.get(5L, TimeUnit.SECONDS);
            fixture.assertCapacities(1_024);
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test
    public void volumetricNaturalHeightReservesItsDistinctFifthCache() throws Exception {
        try (Fixture fixture = new Fixture(1_024)) {
            CachedDoubleStream2D natural = fixture.heightCache(1_024);
            fixture.complex.setUnblendedNaturalHeightStream(natural);
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            fixture.complex.expandHydrologyNoiseCaches(budget);
            fixture.assertCapacities(32_768);
            assertEquals(32_768L * 256L, natural.getMaxSize());
            assertEquals(5L * (32_768L - 1_024L) * 4_096L, budget.reservedBytes());
            fixture.complex.close();
            assertEquals(1_024L * 256L, natural.getMaxSize());
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test
    public void partialGrantPreservesTheConfiguredFloorForEveryCache() throws Exception {
        try (Fixture fixture = new Fixture(1_024)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(4L * 80L * 4_096L + 4_095L);
            fixture.complex.expandHydrologyNoiseCaches(budget);
            fixture.assertCapacities(1_104);
            assertEquals(4L * 80L * 4_096L, budget.reservedBytes());
            fixture.complex.close();
            fixture.assertCapacities(1_024);
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test
    public void configuredCapacityAboveTargetRemainsUnchanged() throws Exception {
        try (Fixture fixture = new Fixture(65_536)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            fixture.complex.expandHydrologyNoiseCaches(budget);
            fixture.assertCapacities(65_536);
            assertEquals(0L, budget.reservedBytes());
            fixture.complex.close();
            fixture.assertCapacities(65_536);
        }
    }

    @Test
    public void detachedAndNonHydrologyRuntimesDoNotReserve() throws Exception {
        try (Fixture fixture = new Fixture(1_024)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            setField(fixture.complex, "detached", true);
            fixture.complex.expandHydrologyNoiseCaches(budget);
            fixture.assertCapacities(1_024);
            setField(fixture.complex, "detached", false);
            fixture.complex.setHydrologyRuntime(null);
            fixture.complex.expandHydrologyNoiseCaches(budget);
            fixture.assertCapacities(1_024);
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test
    public void failedHydrologyDrainRetainsTheReservationUntilRetry() throws Exception {
        try (Fixture fixture = new Fixture(1_024)) {
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            HydrologyTileCache cache = fixture.planningCache(budget);
            cache.get(new HydrologyTileKey(0, 0));
            doThrow(new IllegalStateException("Hydrology is still draining")).when(fixture.hydrology).close();
            assertThrows(IllegalStateException.class, fixture.complex::close);
            fixture.assertCapacities(32_768);
            assertEquals(4L * (32_768L - 1_024L) * 4_096L, budget.reservedBytes());
            doAnswer(invocation -> {
                cache.close();
                return null;
            }).when(fixture.hydrology).close();
            fixture.complex.close();
            fixture.assertCapacities(1_024);
            assertEquals(0L, budget.reservedBytes());
        }
    }

    @Test
    public void mappedStreamRetainsOnlyTheFinalCacheAtTheExpandedCapacity() throws Exception {
        try (Fixture fixture = new Fixture(1_024)) {
            CachedStream2D<IrisBiome> inner = fixture.biomes;
            CachedStream2D<IrisBiome> mapped = new CachedStream2D<>("mapped", fixture.engine, inner, 1_024);
            fixture.complex.setBaseBiomeStream(mapped);
            HydrologyNoiseCacheBudget budget = new HydrologyNoiseCacheBudget(Long.MAX_VALUE);
            fixture.complex.expandHydrologyNoiseCaches(budget);
            assertEquals(1_024L * 256L, inner.getMaxSize());
            assertEquals(32_768L * 256L, mapped.getMaxSize());
            fixture.complex.close();
            assertEquals(1_024L * 256L, mapped.getMaxSize());
        }
    }

    private static void setField(IrisComplex complex, String name, Object value) throws Exception {
        Field field = IrisComplex.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(complex, value);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5L, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final MockedStatic<IrisServices> services = mockStatic(IrisServices.class);
        private final Engine engine = mock(Engine.class);
        private final IrisComplex complex = mock(IrisComplex.class, CALLS_REAL_METHODS);
        private final IrisHydrologyRuntime hydrology = mock(IrisHydrologyRuntime.class);
        private final CachedStream2D<IrisBiome> biomes;
        private final CachedStream2D<IrisRegion> regions;
        private final CachedStream2D<InferredType> bridge;
        private final CachedDoubleStream2D height;
        private final CachedDoubleStream2D rawHeight;

        private Fixture(int capacity) throws Exception {
            services.when(() -> IrisServices.get(PreservationRegistry.class)).thenReturn(mock(PreservationRegistry.class));
            biomes = objectCache(capacity);
            regions = objectCache(capacity);
            bridge = objectCache(capacity);
            height = heightCache(capacity);
            rawHeight = heightCache(capacity);
            complex.setNaturalHeightStream(height);
            complex.setHeightStream(ProceduralStream.ofDouble((x, z) -> 47D));
            setField(complex, "cachedHeightStream", rawHeight);
            when(engine.getComplex()).thenReturn(complex);
            complex.setBaseBiomeStream(biomes);
            complex.setRegionStream(regions);
            complex.setBridgeStream(bridge);
            complex.setBaseTerrainHeightStream(height);
            complex.setUnblendedNaturalHeightStream(height);
            complex.setHydrologyRuntime(hydrology);
            setField(complex, "resolvedTerrain", mock(ResolvedTerrainProvider.class));
        }

        @Override
        public void close() {
            services.close();
        }

        private <T> CachedStream2D<T> objectCache(int capacity) {
            @SuppressWarnings("unchecked")
            ProceduralStream<T> source = mock(ProceduralStream.class);
            return new CachedStream2D<>("cache", engine, source, capacity);
        }

        private CachedDoubleStream2D heightCache(int capacity) {
            return new CachedDoubleStream2D("height", engine, ProceduralStream.ofDouble((x, z) -> x - z), capacity);
        }

        private HydrologyTileCache planningCache(HydrologyNoiseCacheBudget budget) {
            HydrologyPlanner planner = mock(HydrologyPlanner.class);
            when(planner.settings()).thenReturn(HydrologyPlannerSettings.defaults());
            when(planner.plan(any(HydrologyTileKey.class))).thenReturn(mock(HydrologyTile.class));
            HydrologyTileCache cache = new HydrologyTileCache(planner, 4);
            cache.setTerrainPreparation(() -> complex.expandHydrologyNoiseCaches(budget));
            doAnswer(invocation -> {
                cache.close();
                return null;
            }).when(hydrology).close();
            return cache;
        }

        private void assertCapacities(int chunks) {
            assertEquals(chunks * 256L, biomes.getMaxSize());
            assertEquals(chunks * 256L, regions.getMaxSize());
            assertEquals(chunks * 256L, bridge.getMaxSize());
            assertEquals(chunks * 256L, height.getMaxSize());
        }
    }
}
