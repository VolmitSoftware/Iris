package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.plan.HydrologyPlanCache;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.RegionKey;
import art.arcane.iris.generation.hydrology.plan.RegionPlan;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.query.HydrologyFeatureType;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.underground.SeaInlet;
import art.arcane.iris.generation.hydrology.underground.SeaInletRasterizer;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.PreparedHydrologyCacheIdentity;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.testsupport.FakeHydrology;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.IrisWorld;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class HydrologyRuntimeTest {
    private static final long TIMEOUT_SECONDS = 120L;
    private static final int PREVIEW_CAP = 64;
    private static final int NATURAL = 64;
    private static final long POND = 0x504f4e44L;
    private static final long FAR_POND = 0x464152L;
    private static final long LAKE = 0x4c414b45L;
    private static final long RIVER = 0x52495645L;
    private static final long INLET = 0x494e4c4554L;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final List<ExecutorService> pools = new ArrayList<>();
    private final List<HydrologyRuntime> runtimes = new ArrayList<>();

    @After
    public void shutDown() {
        for (HydrologyRuntime runtime : runtimes) {
            runtime.close();
        }
        for (ExecutorService pool : pools) {
            pool.shutdownNow();
        }
    }

    @Test(timeout = 180_000L)
    public void rasterSnapshotReturnsProvisionalWhenCold() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        CountDownLatch gate = new CountDownLatch(1);
        planners.gate = gate;
        AtomicBoolean main = new AtomicBoolean();
        HydrologyRuntime runtime = runtime(planners, main::get);

        assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(0, 0));
        main.set(true);
        assertSame(ChunkWaterRaster.PROVISIONAL, runtime.raster(0, 0));
        assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterAt(5, 9));
        main.set(false);
        assertEquals(0, planners.naturalSamples.get());

        gate.countDown();
        await(() -> !runtime.rasterSnapshot(0, 0).provisional(), "snapshot after the plans land");
        ChunkWaterRaster ready = runtime.rasterSnapshot(0, 0);
        assertFalse(ready.empty());
        assertEquals(WaterRole.POND, ready.role(8, 8));
        main.set(true);
        assertSame(ready, runtime.raster(0, 0));
        assertTrue(planners.plannerThreads().stream().noneMatch(name -> name.equals(Thread.currentThread().getName())));
    }

    @Test(timeout = 180_000L)
    public void rasterCachedPerChunk() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        HydrologyRuntime runtime = runtime(planners, () -> false);

        ChunkWaterRaster first = runtime.raster(0, 0);
        assertFalse(first.provisional());
        assertFalse(first.empty());
        assertEquals(70, first.waterHead(8, 8));
        int sampled = planners.naturalSamples.get();
        assertTrue(sampled > 0);
        assertSame(first, runtime.raster(0, 0));
        assertSame(first, runtime.rasterAt(5, 9));
        assertSame(first, runtime.rasterSnapshot(0, 0));
        AtomicReference<ChunkWaterRaster> other = new AtomicReference<>();
        Thread thread = new Thread(() -> other.set(runtime.raster(0, 0)));
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertSame(first, other.get());
        assertEquals(sampled, planners.naturalSamples.get());

        ChunkWaterRaster riverless = runtime.raster(25, 25);
        assertTrue(riverless.empty());
        assertFalse(riverless.provisional());
        assertSame(riverless, runtime.raster(25, 25));
        assertEquals(1, planners.windowCalls(new WindowKey(0, 0)));
    }

    @Test(timeout = 180_000L)
    public void nearestFeatureFindsPondAndRiver() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        HydrologyRuntime runtime = runtime(planners, () -> false);

        HydrologyFeatureRef pond = runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 2048).orElseThrow();
        assertEquals(new HydrologyFeatureRef(POND, HydrologyFeatureType.POND, 2, 70, 2), pond);
        assertEquals(0, planners.windowCalls(new WindowKey(3, 3)));
        assertEquals(new HydrologyFeatureRef(POND + 1, HydrologyFeatureType.POND, 300, 70, 300),
                runtime.nearestFeature(301, 299, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 64).orElseThrow());
        HydrologyFeatureRef river = runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.RIVER), 2048).orElseThrow();
        assertEquals(new HydrologyFeatureRef(RIVER, HydrologyFeatureType.RIVER, 1000, 80, 200), river);
        HydrologyFeatureRef fall = runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.WATERFALL), 2048).orElseThrow();
        assertEquals(new HydrologyFeatureRef(RIVER, HydrologyFeatureType.WATERFALL, 1050, 75, 200), fall);
        HydrologyFeatureQuery water = HydrologyFeatureQuery.of(HydrologyFeatureType.RIVER, HydrologyFeatureType.POND);
        assertEquals(pond, runtime.nearestFeature(0, 0, water, 2048).orElseThrow());
        assertEquals(new HydrologyFeatureRef(RIVER, HydrologyFeatureType.RIVER, 1100, 70, 200), runtime.nearestFeature(1100, 100, water, 2048)
                .orElseThrow());
        assertEquals(Optional.empty(), runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 2));
        assertEquals(Optional.empty(), runtime.nearestFeature(0, 0,
                HydrologyFeatureQuery.of(HydrologyFeatureType.STREAM, HydrologyFeatureType.MAJOR_RIVER), 2048));
        assertEquals(FAR_POND, runtime.nearestFeature(1700, 150, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 512).orElseThrow().id());
        HydrologyFeatureRef lake = runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.LAKE), 4096).orElseThrow();
        assertEquals(new HydrologyFeatureRef(LAKE, HydrologyFeatureType.LAKE, 2500, 90, 2500), lake);
    }

    @Test(timeout = 180_000L)
    public void seaInletsReachChunksPastTheirOwnerAndFailClosedOnTheMainThread() throws Exception {
        AtomicBoolean main = new AtomicBoolean(true);
        FakePlanners planners = new FakePlanners(settings());
        HydrologyRuntime runtime = runtime(planners, main::get);

        CountDownLatch gate = new CountDownLatch(1);
        planners.gate = gate;
        assertThrows(IllegalStateException.class, () -> runtime.inlets(68, 25));
        gate.countDown();
        main.set(false);
        List<SeaInlet> tail = runtime.inlets(68, 25);
        assertEquals(List.of(FakePlanners.inlet()), tail);
        assertEquals(new WindowKey(2, 0), WindowKey.containing(68 << 4, 25 << 4));
        assertEquals(tail, runtime.inlets(62, 25));
        assertEquals(List.of(), runtime.inlets(75, 25));
        assertEquals(List.of(), runtime.inlets(68, 30));
        assertEquals(List.of(), runtime.inlets(0, 0));
        main.set(true);
        assertEquals(tail, runtime.inlets(68, 25));
        main.set(false);

        Map<Long, HydrologyCaveCell> written = new ConcurrentHashMap<>();
        MantleWriter writer = mock(MantleWriter.class);
        doAnswer(call -> {
            HydrologyCaveCell cell = call.getArgument(3);
            int x = call.getArgument(0);
            assertEquals(68, x >> 4);
            written.put(((long) x << 40) ^ ((long) (int) call.getArgument(2) << 20) ^ (int) call.getArgument(1), cell);
            return null;
        }).when(writer).setData(anyInt(), anyInt(), anyInt(), any(HydrologyCaveCell.class));
        new SeaInletRasterizer().write(tail.getFirst(), 68, 25, writer, HydrologyCaveCell.SEA_FLUID, "", SeaInletRasterizer.SubterrainFluid.NOWHERE);
        assertTrue("the chunk past the owner window writes inlet water",
                written.values().stream().anyMatch(cell -> cell.action() == HydrologyCaveAction.WET_SOURCE));

        HydrologyFeatureRef nearest = runtime.nearestFeature(1050, 405, HydrologyFeatureQuery.of(HydrologyFeatureType.SEA_INLET), 512).orElseThrow();
        assertEquals(new HydrologyFeatureRef(INLET, HydrologyFeatureType.SEA_INLET, 1048, 40, 400), nearest);
        assertEquals(Optional.empty(), runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.SEA_INLET), 64));
        assertEquals(List.of(FakePlanners.inlet()), runtime.planPreview(900, 300, 1100, 500).inlets());
        assertEquals(List.of(), FakeHydrology.disabled().inlets(0, 0));
    }

    @Test(timeout = 180_000L)
    public void nearestFeatureSkipsIneligibleCandidatesForTheNextNearest() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        HydrologyRuntime runtime = runtime(planners, () -> false);

        HydrologyFeatureRef seeded = runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 2048,
                feature -> feature.id() != POND).orElseThrow();

        assertEquals(POND + 1, seeded.id());
        assertEquals(Optional.empty(), runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 2048,
                feature -> false));
    }

    @Test(timeout = 180_000L)
    public void classifiesChannelTiersAndStandingWater() throws Exception {
        HydrologySettings settings = settings();
        FakePlanners planners = new FakePlanners(settings);
        HydrologyRuntime runtime = runtime(planners, () -> false);

        assertEquals(HydrologyFeatureType.STREAM, runtime.channelType(settings.tiers().getFirst()));
        assertEquals(HydrologyFeatureType.RIVER, runtime.channelType(settings.tiers().get(1)));
        assertEquals(HydrologyFeatureType.MAJOR_RIVER, runtime.channelType(settings.majorTier()));
        assertEquals(Set.of(HydrologyFeatureType.POND), runtime.standingWaterTypes(0, 0, POND));
        assertEquals(Set.of(HydrologyFeatureType.LAKE), runtime.standingWaterTypes(156, 156, LAKE));
        IllegalStateException missing = assertThrows(IllegalStateException.class, () -> runtime.standingWaterTypes(0, 0, 42L));
        assertTrue(missing.getMessage().contains("none of its hydrology plans define"));
    }

    @Test(timeout = 180_000L)
    public void standingWaterRefusesTheMainThreadAndDisabledHydrology() {
        FakePlanners planners = new FakePlanners(settings());
        HydrologyRuntime runtime = runtime(planners, () -> true);

        assertThrows(IllegalStateException.class, () -> runtime.standingWaterTypes(0, 0, POND));
        assertThrows(IllegalStateException.class, () -> FakeHydrology.disabled().standingWaterTypes(0, 0, POND));
        assertThrows(IllegalStateException.class, () -> FakeHydrology.disabled().channelType(settings().tiers().getFirst()));
    }

    @Test(timeout = 180_000L)
    public void plannerFailurePropagatesFromRaster() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        planners.failing.set(true);
        HydrologyRuntime runtime = runtime(planners, () -> false);
        try {
            runtime.raster(0, 0);
            fail("A failed plan must fail the raster");
        } catch (IllegalStateException expected) {
            assertTrue(chain(expected), chain(expected).contains("window planner exploded"));
        }
        planners.failing.set(false);
        assertEquals(70, runtime.raster(0, 0).waterHead(8, 8));
        runtime.close();
        try {
            runtime.raster(0, 0);
            fail("A closed runtime must not answer");
        } catch (CancellationException expected) {
            assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(0, 0));
        }
    }

    @Test(timeout = 180_000L)
    public void chunksSharePrefetchAndRetryAfterEvictionOrFailure() {
        FakePlanners planners = new FakePlanners(settings());
        HydrologyPlanCache cache = spy(new HydrologyPlanCache(planners.settings, planners::region, planners::window,
                pool(planners, 2), () -> false));
        AtomicInteger prefetches = new AtomicInteger();
        doAnswer(invocation -> prefetches.incrementAndGet() == 2
                ? CompletableFuture.failedFuture(new IllegalStateException("prefetch failed")) : CompletableFuture.completedFuture(null))
                .when(cache).prepareWindowsAround(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        AtomicLong evictions = new AtomicLong();
        doAnswer(invocation -> evictions.get()).when(cache).windowEvictions();
        HydrologyRuntime runtime = new HydrologyRuntime(planners.settings, cache, planners::natural, () -> false, planners.ticker::get);
        runtimes.add(runtime);
        runtime.raster(0, 0);
        runtime.raster(1, 0);
        assertEquals(1, prefetches.get());
        evictions.incrementAndGet();
        runtime.raster(2, 0);
        assertEquals(2, prefetches.get());
        runtime.raster(3, 0);
        assertEquals(3, prefetches.get());
        runtime.raster(4, 0);
        assertEquals(3, prefetches.get());
    }

    @Test(timeout = 180_000L)
    public void preparePregenerationPlansRegionsInOrder() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        ExecutorService pool = pool(planners, 1);
        CountDownLatch blocker = block(pool);
        HydrologyRuntime runtime = runtime(planners, pool, () -> false);
        runtime.openPregeneration().prepare(List.of(new long[]{40L, 40L}, new long[]{-40L, -40L}));
        blocker.countDown();
        await(() -> planners.windowOrder().size() == 18, "both regions' windows planned");
        List<WindowKey> order = planners.windowOrder();
        for (int index = 0; index < 18; index++) {
            int expected = index < 9 ? 40 : -40;
            WindowKey key = order.get(index);
            assertTrue(index + " " + order, Math.abs(key.wx() - expected) <= 1 && Math.abs(key.wz() - expected) <= 1);
        }
        assertEquals(new WindowKey(40, 40), order.get(0));
    }

    @Test(timeout = 180_000L)
    public void closingAPregenerationWithdrawsItsQueuedWindows() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        ExecutorService pool = pool(planners, 1);
        CountDownLatch blocker = block(pool);
        HydrologyRuntime runtime = runtime(planners, pool, () -> false);
        HydrologyPregeneration pregeneration = runtime.openPregeneration();
        pregeneration.prepare(List.of(new long[]{40L, 40L}, new long[]{41L, 40L}));

        pregeneration.close();
        pregeneration.prepare(List.of(new long[]{-40L, -40L}));
        blocker.countDown();
        pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        runtime.prepareWindowsAround(0, 0, 0, 0, 0).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        assertEquals(List.of(new WindowKey(0, 0)), planners.windowOrder());
    }

    @Test(timeout = 180_000L)
    public void previewsNeverHoldMoreThanTheirCapOfPlanRequests() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        planners.gate = new CountDownLatch(1);
        HydrologyRuntime runtime = runtime(planners, pool(planners, 2), () -> false);
        for (int area = 0; area < 40; area++) {
            int x = area * 8 * WindowKey.SIZE;
            assertFalse(runtime.planPreview(x, 0, x + 255, 255).complete());
        }

        planners.gate.countDown();
        await(() -> !planners.windowOrder().isEmpty(), "capped preview windows planned");
        Thread.sleep(200L);
        int planned = planners.windowOrder().size();
        assertTrue("planned " + planned, planned > 0 && planned <= PREVIEW_CAP);
        HydrologyPlanPreview preview = runtime.planPreview(0, 0, 255, 255);
        for (int attempt = 0; attempt < 200 && !preview.complete(); attempt++) {
            Thread.sleep(25L);
            preview = runtime.planPreview(0, 0, 255, 255);
        }
        assertTrue("a released cap admits new previews", preview.complete());
        assertEquals(2, preview.ponds().size());
        assertEquals(1, preview.reaches().size());
    }

    @Test(timeout = 180_000L)
    public void aFailedRegionPreviewNeverBacksOffAWindowPreview() throws Exception {
        FakePlanners planners = new FakePlanners(settings(0, false));
        ExecutorService pool = pool(planners, 1);
        HydrologyRuntime runtime = runtime(planners, pool, () -> false);
        RegionKey failing = new RegionKey(5, 5);
        WindowKey collider = new WindowKey(-failing.rx() - 1, -failing.rz() - 1);
        planners.failingRegion = failing;
        int regionX = failing.rx() * RegionKey.SIZE + RegionKey.SIZE / 2;
        int regionZ = failing.rz() * RegionKey.SIZE + RegionKey.SIZE / 2;

        assertFalse(runtime.planPreview(regionX, regionZ, regionX + 255, regionZ + 255).complete());
        await(() -> planners.regionFailures.get() > 0, "the failing region preview planned");
        pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        int windowX = collider.wx() * WindowKey.SIZE;
        int windowZ = collider.wz() * WindowKey.SIZE;
        HydrologyPlanPreview preview = runtime.planPreview(windowX, windowZ, windowX + 255, windowZ + 255);
        for (int attempt = 0; attempt < 400 && !preview.complete(); attempt++) {
            Thread.sleep(25L);
            preview = runtime.planPreview(windowX, windowZ, windowX + 255, windowZ + 255);
        }

        assertTrue(preview.complete());
        assertEquals(1, planners.windowCalls(collider));
    }

    @Test(timeout = 180_000L)
    public void rasterJobsDroppedByARefusingPoolReleaseTheirPreviewSlots() throws Exception {
        FakePlanners planners = new FakePlanners(settings(0, false));
        ExecutorService delegate = pool(planners, 1);
        RefusingPool pool = new RefusingPool(delegate);
        HydrologyRuntime runtime = runtime(planners, pool, () -> false);
        runtime.raster(0, 0);
        CountDownLatch blocker = block(delegate);

        assertTrue(runtime.rasterPreview(1, 0).provisional());
        pool.refusing.set(true);
        assertTrue(runtime.rasterPreview(2, 0).provisional());
        pool.refusing.set(false);
        blocker.countDown();
        ChunkWaterRaster first = runtime.rasterPreview(1, 0);
        ChunkWaterRaster second = runtime.rasterPreview(2, 0);
        for (int attempt = 0; attempt < 400 && (first.provisional() || second.provisional()); attempt++) {
            Thread.sleep(25L);
            first = runtime.rasterPreview(1, 0);
            second = runtime.rasterPreview(2, 0);
        }

        assertFalse("the job queued behind the refused one is requeued", first.provisional());
        assertFalse(second.provisional());
    }

    @Test(timeout = 180_000L)
    public void rasterPreviewQueuesNoPrefetchRing() throws Exception {
        FakePlanners planners = new FakePlanners(settings(1, false));
        HydrologyRuntime runtime = runtime(planners, () -> false);
        ChunkWaterRaster raster = runtime.rasterPreview(0, 0);
        for (int attempt = 0; attempt < 400 && raster.provisional(); attempt++) {
            Thread.sleep(25L);
            raster = runtime.rasterPreview(0, 0);
        }

        assertFalse(raster.provisional());
        Set<WindowKey> planned = new HashSet<>(planners.windowOrder());
        for (WindowKey key : planned) {
            assertTrue(key + " is outside the raster neighbourhood", Math.abs(key.wx()) <= 1 && Math.abs(key.wz()) <= 1);
        }
    }

    @Test(timeout = 180_000L)
    public void mainThreadSnapshotQueuesOneWorkerRasterWithoutSampling() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        AtomicBoolean main = new AtomicBoolean();
        HydrologyRuntime runtime = runtime(planners, main::get);
        runtime.prepareWindowsAround(0, 0, 0, 0, 1).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        CountDownLatch rasterizing = new CountDownLatch(1);
        planners.naturalGate = rasterizing;

        main.set(true);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(0, 0));
            assertSame(ChunkWaterRaster.PROVISIONAL, runtime.raster(0, 0));
        }
        assertEquals(0, planners.naturalSamplesOn(Thread.currentThread()));
        await(() -> planners.naturalSamples.get() > 0, "the queued worker raster to start sampling");
        rasterizing.countDown();
        await(() -> !runtime.rasterSnapshot(0, 0).provisional(), "the queued worker raster to land");
        ChunkWaterRaster landed = runtime.rasterSnapshot(0, 0);
        assertEquals(WaterRole.POND, landed.role(8, 8));
        assertSame(landed, runtime.raster(0, 0));
        assertEquals(1, planners.originSamples.get());
        assertEquals(0, planners.naturalSamplesOn(Thread.currentThread()));
    }

    @Test(timeout = 180_000L)
    public void queuedSnapshotRasterYieldsToLaterDemandedRaster() throws Exception {
        FakePlanners planners = new FakePlanners(settings(0, false));
        ExecutorService pool = pool(planners, 1);
        CountDownLatch blocker = block(pool);
        AtomicBoolean main = new AtomicBoolean(true);
        HydrologyRuntime runtime = runtime(planners, pool, main::get);
        assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(20 * 32, 20 * 32));
        main.set(false);
        AtomicReference<ChunkWaterRaster> demanded = new AtomicReference<>();
        Thread worker = new Thread(() -> demanded.set(runtime.raster(0, 0)));
        worker.start();
        await(() -> worker.getState() == Thread.State.WAITING, "the demanded raster to wait for its plans");
        blocker.countDown();
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertNotNull(demanded.get());
        await(() -> planners.windowOrder().size() == 18, "the snapshot raster's windows");
        List<WindowKey> order = planners.windowOrder();
        for (int index = 0; index < 18; index++) {
            int centre = index < 9 ? 0 : 20;
            WindowKey key = order.get(index);
            assertTrue(index + " " + order, Math.abs(key.wx() - centre) <= 1 && Math.abs(key.wz() - centre) <= 1);
        }
        main.set(true);
        await(() -> !runtime.rasterSnapshot(20 * 32, 20 * 32).provisional(), "the snapshot raster to land");
    }

    @Test(timeout = 180_000L)
    public void demandedRasterPromotesTheSnapshotSlotsOfTheSameChunk() throws Exception {
        FakePlanners planners = new FakePlanners(settings(0, false));
        ExecutorService pool = pool(planners, 1);
        CountDownLatch blocker = block(pool);
        AtomicBoolean main = new AtomicBoolean(true);
        HydrologyRuntime runtime = runtime(planners, pool, main::get);
        assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(0, 0));
        runtime.prepareWindowsAround(40 * 32, 40 * 32, 40 * 32, 40 * 32, 0);
        main.set(false);
        AtomicReference<ChunkWaterRaster> demanded = new AtomicReference<>();
        Thread worker = new Thread(() -> demanded.set(runtime.raster(0, 0)));
        worker.start();
        await(() -> worker.getState() == Thread.State.WAITING, "the demanded raster to wait for the snapshot's plans");
        blocker.countDown();
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertEquals(WaterRole.POND, demanded.get().role(8, 8));
        await(() -> planners.windowOrder().size() == 10, "the prefetched window");
        List<WindowKey> order = planners.windowOrder();
        for (int index = 0; index < 9; index++) {
            WindowKey key = order.get(index);
            assertTrue(index + " " + order, Math.abs(key.wx()) <= 1 && Math.abs(key.wz()) <= 1);
        }
        assertEquals(new WindowKey(40, 40), order.get(9));
        assertEquals(1, planners.windowCalls(new WindowKey(0, 0)));
    }

    @Test(timeout = 180_000L)
    public void snapshotFailuresBackOffForThirtySecondsWithBoundedLogs() throws Exception {
        try (EngineFixture.Environment environment = EngineFixture.environment()) {
            FakePlanners planners = new FakePlanners(settings(0, false));
            planners.failing.set(true);
            ExecutorService pool = pool(planners, 1);
            CountDownLatch blocker = block(pool);
            HydrologyRuntime runtime = runtime(planners, pool, () -> true);
            for (int chunk = 0; chunk < 20; chunk++) {
                assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(chunk, 0));
            }
            blocker.countDown();
            settle(planners, pool, 9);
            assertEquals(1, planners.windowCalls(new WindowKey(0, 0)));
            verify(environment.platform, times(16)).reportError(anyString(), any(Throwable.class));

            planners.ticker.addAndGet(TimeUnit.SECONDS.toNanos(29L));
            for (int chunk = 0; chunk < 20; chunk++) {
                assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(chunk, 0));
            }
            pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals(1, planners.windowCalls(new WindowKey(0, 0)));
            verify(environment.platform, times(16)).reportError(anyString(), any(Throwable.class));

            planners.ticker.addAndGet(TimeUnit.SECONDS.toNanos(2L));
            assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(0, 0));
            settle(planners, pool, 10);
            assertEquals(2, planners.windowCalls(new WindowKey(0, 0)));
            verify(environment.platform, times(17)).reportError(anyString(), any(Throwable.class));
            verify(environment.platform).log(eq(LogLevel.WARN), argThat((String message) -> message.contains("4 more chunks")));
        }
    }

    @Test(timeout = 180_000L)
    public void demandedRasterReplansAndThrowsDuringSnapshotBackoff() throws Exception {
        try (EngineFixture.Environment environment = EngineFixture.environment()) {
            FakePlanners planners = new FakePlanners(settings(0, false));
            planners.failing.set(true);
            ExecutorService pool = pool(planners, 1);
            AtomicBoolean main = new AtomicBoolean(true);
            HydrologyRuntime runtime = runtime(planners, pool, main::get);
            assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(0, 0));
            settle(planners, pool, 9);
            main.set(false);
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    runtime.raster(0, 0);
                    fail("A demanded raster must fail while its plan fails");
                } catch (IllegalStateException expected) {
                    assertTrue(chain(expected), chain(expected).contains("window planner exploded"));
                }
            }
            assertEquals(4, planners.windowCalls(new WindowKey(0, 0)));
            verify(environment.platform, times(1)).reportError(anyString(), any(Throwable.class));
            main.set(true);
            assertSame(ChunkWaterRaster.PROVISIONAL, runtime.rasterSnapshot(0, 0));
            pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertEquals(4, planners.windowCalls(new WindowKey(0, 0)));
            planners.failing.set(false);
            main.set(false);
            ChunkWaterRaster recovered = runtime.raster(0, 0);
            assertEquals(70, recovered.waterHead(8, 8));
            main.set(true);
            assertSame(recovered, runtime.rasterSnapshot(0, 0));
        }
    }

    @Test(timeout = 180_000L)
    public void demandedRasterOutranksNewerPrefetch() throws Exception {
        FakePlanners planners = new FakePlanners(settings(0, false));
        ExecutorService pool = pool(planners, 1);
        CountDownLatch blocker = block(pool);
        HydrologyRuntime runtime = runtime(planners, pool, () -> false);
        AtomicReference<ChunkWaterRaster> result = new AtomicReference<>();
        Thread worker = new Thread(() -> result.set(runtime.raster(0, 0)));
        worker.start();
        await(() -> worker.getState() == Thread.State.WAITING, "the raster to wait for its plans");
        runtime.prepareWindowsAround(40 * 32, 40 * 32, 40 * 32, 40 * 32, 0);
        blocker.countDown();
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertNotNull(result.get());
        await(() -> planners.windowOrder().size() == 10, "the prefetched window");
        List<WindowKey> order = planners.windowOrder();
        for (int index = 0; index < 9; index++) {
            WindowKey key = order.get(index);
            assertTrue(index + " " + order, Math.abs(key.wx()) <= 1 && Math.abs(key.wz()) <= 1);
        }
        assertEquals(new WindowKey(40, 40), order.get(9));
    }

    @Test(timeout = 180_000L)
    public void cancellationPassesThroughPlannedRaster() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        CountDownLatch gate = new CountDownLatch(1);
        planners.gate = gate;
        HydrologyRuntime runtime = runtime(planners, () -> false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                runtime.raster(0, 0);
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        worker.start();
        await(() -> worker.getState() == Thread.State.WAITING, "the raster to wait for its plans");
        runtime.close();
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        gate.countDown();
        assertTrue(String.valueOf(failure.get()), failure.get() instanceof CancellationException);
    }

    @Test(timeout = 180_000L)
    public void nearestFeatureRefusesMainThreadAndBoundsItsSearch() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        ExecutorService pool = pool(planners, 4);
        AtomicBoolean main = new AtomicBoolean(true);
        HydrologyRuntime runtime = runtime(planners, pool, main::get);
        try {
            runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 64);
            fail("A feature search on the main thread must refuse");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("main thread"));
        }
        main.set(false);
        pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(0, planners.windowOrder().size());
        assertEquals(0, planners.regionArrivals.get());

        assertEquals(Optional.empty(), runtime.nearestFeature(10_000, 10_000, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 64));
        assertEquals(25, planners.windowOrder().size());

        CountDownLatch regions = new CountDownLatch(1);
        planners.regionGate = regions;
        int before = planners.regionArrivals.get();
        AtomicReference<Optional<HydrologyFeatureRef>> lake = new AtomicReference<>();
        Thread search = new Thread(() -> lake.set(runtime.nearestFeature(-20_000, -20_000,
                HydrologyFeatureQuery.of(HydrologyFeatureType.LAKE), 64)));
        search.start();
        await(() -> planners.regionArrivals.get() - before >= 4, "every lake region to plan at once");
        regions.countDown();
        search.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
        assertEquals(Optional.empty(), lake.get());
    }

    @Test(timeout = 180_000L)
    public void preparePregenerationRejectsTooManyRegions() throws Exception {
        FakePlanners planners = new FakePlanners(settings());
        ExecutorService pool = pool(planners, 1);
        HydrologyRuntime runtime = runtime(planners, pool, () -> false);
        List<long[]> order = new ArrayList<>();
        for (int index = 0; index < 4097; index++) {
            order.add(new long[]{index, 0L});
        }
        try {
            runtime.openPregeneration().prepare(order);
            fail("More than 4096 pregeneration regions must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("4096"));
        }
        pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(0, planners.windowOrder().size());
        assertEquals(0, planners.regionArrivals.get());
    }

    @Test
    public void biomeFluidPoliciesAreCheckedWhenTheEngineLoads() {
        IrisBiome plain = new IrisBiome();
        IrisBiome flooded = new IrisBiome();
        flooded.setHydrologyPolicy(new IrisHydrologyPolicy().setFluid("water"));
        IrisBiome magma = new IrisBiome();
        magma.setHydrologyPolicy(new IrisHydrologyPolicy().setFluid("magma"));

        assertFalse(HydrologyRuntime.requireBiomeFluids(List.of(plain), settings()));
        assertTrue(HydrologyRuntime.requireBiomeFluids(List.of(plain, flooded), settings()));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> HydrologyRuntime.requireBiomeFluids(List.of(plain, magma), settings()));
        assertTrue(failure.getMessage(), failure.getMessage().contains("'magma'"));
    }

    @Test
    public void createStaysDisabledWithoutHydrology() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisDimension dimension = new IrisDimension();
        when(engine.getDimension()).thenReturn(dimension);
        assertDisabled(HydrologyRuntime.create(engine, complex));
        dimension.setHydrology(new IrisHydrology().setEnabled(false));
        HydrologyRuntime disabled = HydrologyRuntime.create(engine, complex);
        disabled.persistUnder(new PreparedHydrologyCacheIdentity("pack"));
        disabled.openPlanStore();
        assertDisabled(disabled);
        verifyNoInteractions(complex);
        verify(engine, never()).getComplex();
    }

    @Test
    public void zeroDensitySkipsRuntimePlanningAfterValidatingSettings() {
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisDimension dimension = new IrisDimension();
        dimension.setHydrology(new IrisHydrology().setEnabled(true).setDensity(0D));
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getSeedManager()).thenReturn(new SeedManager(1337L));
        when(engine.getHeight()).thenReturn(384);

        assertDisabled(HydrologyRuntime.create(engine, complex));
        verifyNoInteractions(complex);
        verify(engine, never()).getPlatformHooks();

        dimension.getHydrology().setDensity(-1D);
        assertThrows(IllegalArgumentException.class, () -> HydrologyRuntime.create(engine, complex));
    }

    @Test(timeout = 15_000L)
    public void nestedPreviewScopesStayNonblockingAndRestoreDemandSampling() throws Exception {
        try (FakeHydrology water = new FakeHydrology(() -> false, (x, z) -> 60, 40, 63)) {
            HydrologyRuntime runtime = spy(water.runtime);
            water.hold();
            try (HydrologyRuntime.PreviewScope outer = runtime.preview()) {
                assertTrue(runtime.raster(0, 0).provisional());
                try (HydrologyRuntime.PreviewScope inner = runtime.preview()) {
                    assertTrue(runtime.raster(1, 0).provisional());
                }
                assertTrue(runtime.raster(0, 1).provisional());
            }
            water.release();
            clearInvocations(runtime);

            assertFalse(runtime.raster(400, 400).provisional());
            verify(runtime, never()).rasterPreview(anyInt(), anyInt());
        }
    }

    @Test(timeout = 300_000L)
    public void createSavesPlansUnderTheWorldFolder() throws Exception {
        Path worldFolder = temporaryFolder.newFolder("world").toPath();
        Path root = worldFolder.resolve("iris").resolve("hydrology").resolve("main");
        Path older = Files.createDirectories(root.resolve("older"));
        Path oldest = Files.createDirectories(root.resolve("oldest"));
        Files.writeString(older.resolve("w-0-0.bin.gz"), "stale");
        Files.writeString(oldest.resolve("w-0-0.bin.gz"), "stale");
        Files.setLastModifiedTime(older, FileTime.fromMillis(2_000_000L));
        Files.setLastModifiedTime(oldest, FileTime.fromMillis(1_000_000L));
        try (EngineFixture.Environment ignored = EngineFixture.environment();
             EngineFixture fixture = new EngineFixture(EngineFixture.pack(temporaryFolder.newFolder().toPath(), false))) {
            IrisHydrology config = new IrisHydrology().setEnabled(true).setPrefetchRings(0);
            fixture.dimension.setHydrology(config);
            Engine engine = engine(fixture, worldFolder);
            long fingerprint = HydrologySettings.compile(config, new SeedManager(EngineFixture.SEED).getBodies(),
                    fixture.dimension.getFluidHeight(), 0, EngineFixture.HEIGHT).fingerprint();
            PreparedHydrologyCacheIdentity identity = new PreparedHydrologyCacheIdentity("pack");
            String scope = identity.planScope(new SeedManager(EngineFixture.SEED).getBodies(), fingerprint);
            HydrologyRuntime runtime = HydrologyRuntime.create(engine, fixture.complex);
            try {
                assertTrue(runtime.enabled());
                assertFalse(Files.exists(root.resolve(scope)));
                runtime.persistUnder(identity);
                runtime.openPlanStore();
                assertTrue(Files.isDirectory(root.resolve(scope)));
                ChunkWaterRaster raster = runtime.raster(40, -12);
                assertFalse(raster.provisional());
                assertSame(raster, runtime.rasterSnapshot(40, -12));
            } finally {
                runtime.close();
            }
            assertTrue(planFiles(root.resolve(scope), "w-") >= 9);
            assertTrue(planFiles(root.resolve(scope), "r-") >= 4);
            assertTrue(Files.exists(older.resolve("w-0-0.bin.gz")));
            assertFalse(Files.exists(oldest));
        }
    }

    @Test(timeout = 180_000L)
    public void createKeepsPlansInMemoryAndWarnsWhenTheWorldCannotStoreThem() throws Exception {
        try (EngineFixture.Environment environment = EngineFixture.environment();
             EngineFixture fixture = new EngineFixture(EngineFixture.pack(temporaryFolder.newFolder().toPath(), false))) {
            fixture.dimension.setHydrology(new IrisHydrology().setEnabled(true));
            HydrologyRuntime runtime = HydrologyRuntime.create(fixture.engine, fixture.complex);
            try {
                assertTrue(runtime.enabled());
                runtime.persistUnder(new PreparedHydrologyCacheIdentity("pack"));
                runtime.openPlanStore();
                verify(environment.platform).log(eq(LogLevel.WARN),
                        argThat((String message) -> message.contains("fixture-world") && message.contains("stay in memory")));
            } finally {
                runtime.close();
            }
        }
        FakePlanners planners = new FakePlanners(settings(1, true));
        assertEquals(70, runtime(planners, () -> false).raster(0, 0).waterHead(8, 8));
    }

    @Test(timeout = 300_000L)
    public void createKeepsPlansInMemoryAndReportsWhenThePlanDirectoryCannotBeOpened() throws Exception {
        Path worldFolder = temporaryFolder.newFolder("blocked-world").toPath();
        Files.writeString(worldFolder.resolve("iris"), "not a directory");
        try (EngineFixture.Environment environment = EngineFixture.environment();
             EngineFixture fixture = new EngineFixture(EngineFixture.pack(temporaryFolder.newFolder().toPath(), false))) {
            fixture.dimension.setHydrology(new IrisHydrology().setEnabled(true).setPrefetchRings(0));
            HydrologyRuntime runtime = HydrologyRuntime.create(engine(fixture, worldFolder), fixture.complex);
            try {
                runtime.persistUnder(new PreparedHydrologyCacheIdentity("pack"));
                runtime.openPlanStore();
                verify(environment.platform).reportError(
                        argThat((String message) -> message.contains("hydrology-world") && message.contains("cannot be saved")),
                        any(UncheckedIOException.class));
                assertFalse(runtime.raster(40, -12).provisional());
            } finally {
                runtime.close();
            }
            assertTrue(Files.isRegularFile(worldFolder.resolve("iris")));
        }
    }

    private static Engine engine(EngineFixture fixture, Path worldFolder) {
        Engine engine = mock(Engine.class, withSettings().stubOnly());
        IrisWorld world = IrisWorld.builder().name("hydrology-world").worldFolder(worldFolder.toFile()).minHeight(-64).maxHeight(320).build();
        when(engine.getDimension()).thenReturn(fixture.dimension);
        when(engine.getSeedManager()).thenReturn(new SeedManager(EngineFixture.SEED));
        when(engine.getHeight()).thenReturn(EngineFixture.HEIGHT);
        when(engine.getPlatformHooks()).thenReturn(fixture.hooks);
        when(engine.getWorld()).thenReturn(world);
        return engine;
    }

    private static long planFiles(Path directory, String prefix) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(file -> file.getFileName().toString().startsWith(prefix)).count();
        }
    }

    private static void assertDisabled(HydrologyRuntime runtime) {
        assertFalse(runtime.enabled());
        assertSame(ChunkWaterRaster.EMPTY, runtime.raster(0, 0));
        assertSame(ChunkWaterRaster.EMPTY, runtime.rasterSnapshot(0, 0));
        assertSame(ChunkWaterRaster.EMPTY, runtime.rasterAt(0, 0));
        assertEquals(Optional.empty(), runtime.nearestFeature(0, 0, HydrologyFeatureQuery.of(HydrologyFeatureType.POND), 64));
        assertTrue(runtime.prepareWindowsAround(0, 0, 0, 0, 1).isDone());
        try (HydrologyPregeneration pregeneration = runtime.openPregeneration()) {
            pregeneration.prepare(List.of(new long[]{0L, 0L}));
        }
        assertSame(ChunkWaterRaster.EMPTY, runtime.rasterPreview(0, 0));
        assertEquals(HydrologyPlanPreview.EMPTY, runtime.planPreview(0, 0, 511, 511));
        runtime.close();
    }

    private static void settle(FakePlanners planners, ExecutorService pool, int windows) throws Exception {
        await(() -> planners.windowOrder().size() >= windows, windows + " planned windows");
        pool.submit(() -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static final class RefusingPool extends AbstractExecutorService {
        private final ExecutorService delegate;
        private final AtomicBoolean refusing = new AtomicBoolean();

        private RefusingPool(ExecutorService delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            if (refusing.get()) {
                throw new RejectedExecutionException("refusing pool");
            }
            delegate.execute(command);
        }

        @Override
        public void shutdown() {
            delegate.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return delegate.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return delegate.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return delegate.isTerminated();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return delegate.awaitTermination(timeout, unit);
        }
    }

    private static CountDownLatch block(ExecutorService pool) {
        CountDownLatch blocker = new CountDownLatch(1);
        pool.execute(() -> {
            try {
                blocker.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
            }
        });
        return blocker;
    }

    private HydrologyRuntime runtime(FakePlanners planners, BooleanSupplier mainThread) {
        return runtime(planners, pool(planners, 4), mainThread);
    }

    private ExecutorService pool(FakePlanners planners, int threads) {
        ExecutorService pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "fake-hydrology-" + planners.threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        pools.add(pool);
        return pool;
    }

    private HydrologyRuntime runtime(FakePlanners planners, ExecutorService pool, BooleanSupplier mainThread) {
        HydrologyPlanCache cache = new HydrologyPlanCache(planners.settings, planners::region, planners::window, pool, mainThread);
        HydrologyRuntime runtime = new HydrologyRuntime(planners.settings, cache, planners::natural, mainThread, planners.ticker::get);
        runtimes.add(runtime);
        return runtime;
    }

    private static HydrologySettings settings() {
        return settings(1, false);
    }

    private static HydrologySettings settings(int prefetchRings, boolean persist) {
        return HydrologySettings.compile(new IrisHydrology().setEnabled(true).setPersist(persist).setPrefetchRings(prefetchRings),
                EngineFixture.SEED, 40, 0, 384);
    }

    private static String chain(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append(" | ");
        }
        return messages.toString();
    }

    private static void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("Timed out waiting for: " + description);
            }
            Thread.sleep(10L);
        }
    }

    private static Pond pond(long id, PondKind kind, int level, int originX, int originZ, int nodes) {
        long[] bits = new long[(nodes * nodes + 63) >>> 6];
        for (int index = 0; index < nodes * nodes; index++) {
            bits[index >>> 6] |= 1L << index;
        }
        PondMask mask = new PondMask(originX, originZ, 4, nodes, nodes, bits);
        int centre = (nodes - 1) * 2;
        return new Pond(id, kind, level, level - 6, originX + nodes * 4, originZ, level + 1f, mask, 0L, 0, "water",
                originX + centre, originZ + centre);
    }

    private static final class FakePlanners {
        private final HydrologySettings settings;
        private final AtomicInteger naturalSamples = new AtomicInteger();
        private final AtomicInteger threads = new AtomicInteger();
        private final AtomicBoolean failing = new AtomicBoolean();
        private final Map<WindowKey, AtomicInteger> windowCalls = new ConcurrentHashMap<>();
        private final Map<String, Boolean> plannerThreads = new ConcurrentHashMap<>();
        private final List<WindowKey> windowOrder = Collections.synchronizedList(new ArrayList<>());
        private final Map<String, AtomicInteger> naturalByThread = new ConcurrentHashMap<>();
        private final AtomicInteger originSamples = new AtomicInteger();
        private final AtomicInteger regionArrivals = new AtomicInteger();
        private final AtomicLong ticker = new AtomicLong();
        private volatile CountDownLatch gate;
        private volatile CountDownLatch naturalGate;
        private volatile CountDownLatch regionGate;
        private volatile RegionKey failingRegion;
        private final AtomicInteger regionFailures = new AtomicInteger();

        private FakePlanners(HydrologySettings settings) {
            this.settings = settings;
        }

        int natural(int x, int z) {
            naturalSamples.incrementAndGet();
            naturalByThread.computeIfAbsent(Thread.currentThread().getName(), ignored -> new AtomicInteger()).incrementAndGet();
            if (x == 0 && z == 0) {
                originSamples.incrementAndGet();
            }
            hold(naturalGate);
            return NATURAL;
        }

        int naturalSamplesOn(Thread thread) {
            AtomicInteger samples = naturalByThread.get(thread.getName());
            return samples == null ? 0 : samples.get();
        }

        RegionPlan region(RegionKey key) {
            plannerThreads.put(Thread.currentThread().getName(), Boolean.TRUE);
            regionArrivals.incrementAndGet();
            hold(regionGate);
            if (key.equals(failingRegion)) {
                regionFailures.incrementAndGet();
                throw new IllegalStateException("region planner exploded");
            }
            List<Pond> lakes = key.equals(new RegionKey(0, 0)) ? List.of(pond(LAKE, PondKind.LAKE, 90, 2500, 2500, 6)) : List.of();
            return new RegionPlan(key, List.of(), lakes);
        }

        WindowPlan window(WindowKey key, List<RegionPlan> plans) {
            plannerThreads.put(Thread.currentThread().getName(), Boolean.TRUE);
            windowCalls.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
            windowOrder.add(key);
            pass();
            if (key.equals(new WindowKey(0, 0))) {
                if (failing.get()) {
                    throw new IllegalStateException("window planner exploded");
                }
                return new WindowPlan(key, List.of(river()), List.of(pond(POND, PondKind.NATURAL, 70, 2, 2, 8),
                        pond(POND + 1, PondKind.SEEDED, 70, 296, 296, 3)), List.of());
            }
            if (key.equals(new WindowKey(1, 0))) {
                return new WindowPlan(key, List.of(), List.of(), List.of(inlet()));
            }
            if (key.equals(new WindowKey(3, 0))) {
                return new WindowPlan(key, List.of(), List.of(pond(FAR_POND, PondKind.NATURAL, 75, 1700, 100, 3)), List.of());
            }
            return new WindowPlan(key, List.of(), List.of(), List.of());
        }

        private void pass() {
            hold(gate);
        }

        private static void hold(CountDownLatch latch) {
            if (latch == null) {
                return;
            }
            try {
                if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Planning gate never opened.");
                }
            } catch (InterruptedException interruption) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Planning interrupted.");
            }
        }

        private static SeaInlet inlet() {
            int stations = 13;
            float[] xs = new float[stations];
            float[] zs = new float[stations];
            float[] halfWidth = new float[stations];
            int[] floor = new int[stations];
            int[] ceiling = new int[stations];
            for (int station = 0; station < stations; station++) {
                xs[station] = 992f + station * 8f;
                zs[station] = 400f;
                halfWidth[station] = 3f;
                floor[station] = 37;
                ceiling[station] = 45;
            }
            return new SeaInlet(INLET, xs, zs, halfWidth, 40, floor, ceiling, 1088, 400, 6);
        }

        private Reach river() {
            int points = 11;
            float[] xs = new float[points];
            float[] zs = new float[points];
            int[] grade = new int[points];
            float[] halfWidth = new float[points];
            float[] depth = new float[points];
            ReachStyle[] style = new ReachStyle[points];
            for (int index = 0; index < points; index++) {
                xs[index] = 1000f + index * 10f;
                zs[index] = 200.5f;
                grade[index] = index <= 5 ? 80 - index : 70;
                halfWidth[index] = 3f;
                depth[index] = 2f;
                style[index] = index == 5 ? ReachStyle.WATERFALL : ReachStyle.RUN;
            }
            return new Reach(RIVER, settings.tiers().get(1), 0L, xs, zs, grade, halfWidth, depth, style, 30000f);
        }

        int windowCalls(WindowKey key) {
            AtomicInteger calls = windowCalls.get(key);
            return calls == null ? 0 : calls.get();
        }

        List<WindowKey> windowOrder() {
            synchronized (windowOrder) {
                return new ArrayList<>(windowOrder);
            }
        }

        List<String> plannerThreads() {
            return new ArrayList<>(plannerThreads.keySet());
        }
    }
}
