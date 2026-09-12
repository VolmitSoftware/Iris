package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.ChunkGenerationSemantics;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.world.history.GenerationSemanticIndex;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.Position2;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HintedLocatorTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static boolean boundPlatform;

    @BeforeClass
    public static void bindPlatform() throws Exception {
        if (IrisPlatforms.isBound()) {
            return;
        }

        File dataDirectory = Files.createTempDirectory("iris-hinted-locator-test").toFile();
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.dataFile(any(String[].class))).thenAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            File file = dataDirectory;
            for (Object argument : arguments) {
                file = new File(file, String.valueOf(argument));
            }
            return file;
        });
        IrisPlatforms.bind(platform);
        boundPlatform = true;
    }

    @AfterClass
    public static void unbindPlatform() {
        if (boundPlatform) {
            IrisPlatforms.unbind();
            boundPlatform = false;
        }
    }

    private Engine engine;
    private IrisDimension dimension;
    private IrisComplex complex;
    private IrisData data;
    private ResourceLoader<IrisBiome> biomeLoader;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() throws Exception {
        engine = mock(Engine.class);
        dimension = mock(IrisDimension.class);
        complex = mock(IrisComplex.class);
        data = mock(IrisData.class);
        biomeLoader = (ResourceLoader<IrisBiome>) mock(ResourceLoader.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getData()).thenReturn(data);
        when(engine.getFocus()).thenReturn(null);
        when(engine.getFocusRegion()).thenReturn(null);
        when(engine.isClosed()).thenReturn(false);
        when(engine.acquireGenerationLease(any(String.class))).thenReturn(GenerationSessionLease.noop());
        when(complex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(true);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>());
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    private static IrisRegion region(String key) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        return region;
    }

    private static KList<String> keys(String... values) {
        KList<String> list = new KList<>();
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private static <T> ProceduralStream<T> constantStream(T value) {
        return ProceduralStream.of((x, z) -> value, Interpolated.of(a -> 0D, a -> value));
    }

    private static <T> ProceduralStream<T> positionalStream(BiFunction<Double, Double, T> function, T fallback) {
        return ProceduralStream.of(function::apply, Interpolated.of(a -> 0D, a -> fallback));
    }

    private void stubRegions(IrisRegion... regions) {
        KList<IrisRegion> list = new KList<>();
        KList<String> regionKeys = new KList<>();
        for (IrisRegion region : regions) {
            list.add(region);
            regionKeys.add(region.getLoadKey());
        }
        when(dimension.getAllRegions(engine)).thenReturn(list);
        when(dimension.getRegions()).thenReturn(regionKeys);
    }

    private void stubBiome(IrisBiome biome) {
        when(biomeLoader.load(biome.getLoadKey())).thenReturn(biome);
    }

    @Test
    public void appendRingZeroAddsOnlyOrigin() {
        KList<Position2> batch = new KList<>();
        HintedLocator.appendRing(batch, new Position2(7, -3), 0, 4);
        assertEquals(1, batch.size());
        assertEquals(new Position2(7, -3), batch.get(0));
    }

    @Test
    public void appendRingProducesFullPerimeterAtStride() {
        KList<Position2> batch = new KList<>();
        HintedLocator.appendRing(batch, new Position2(0, 0), 3, 4);
        assertEquals(24, batch.size());
        Set<Position2> unique = new HashSet<>(batch);
        assertEquals(24, unique.size());
        for (Position2 cell : batch) {
            int chebyshev = Math.max(Math.abs(cell.getX()), Math.abs(cell.getZ()));
            assertEquals(12, chebyshev);
            assertEquals(0, cell.getX() % 4);
            assertEquals(0, cell.getZ() % 4);
        }
    }

    @Test
    public void appendRingsAtStrideOneTileThePlaneWithoutDuplicates() {
        Set<Position2> all = new HashSet<>();
        int total = 0;
        for (int ring = 0; ring <= 5; ring++) {
            KList<Position2> batch = new KList<>();
            HintedLocator.appendRing(batch, new Position2(2, 2), ring, 1);
            total += batch.size();
            all.addAll(batch);
        }
        assertEquals(121, total);
        assertEquals(121, all.size());
    }

    @Test
    public void biomePlanFindsChildBiomeUnderLandRoot() {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("root_land"));
        IrisBiome root = biome("root_land");
        root.setChildren(keys("target"));
        IrisBiome target = biome("target");
        stubBiome(root);
        stubBiome(target);
        stubRegions(regionA);

        IrisRegion foreign = region("reg_other");
        when(complex.getRegionStream()).thenReturn(positionalStream((x, z) -> x < 0D ? foreign : regionA, regionA));
        when(complex.getLandBiomeStream()).thenReturn(constantStream(root));
        when(complex.getSeaBiomeStream()).thenReturn(constantStream(biome("sea_root")));
        when(complex.getShoreBiomeStream()).thenReturn(constantStream(biome("shore_root")));

        HintedLocator.SearchPlan plan = HintedLocator.biomePlan(engine, "target");

        assertTrue(plan.isPossible());
        assertNotNull(plan.getCoarse());
        assertNull(plan.getExactOverride());
        assertTrue(plan.getCoarse().test(100, 100));
        assertFalse(plan.getCoarse().test(-100, 100));
    }

    @Test
    public void biomePlanIsImpossibleWhenBiomeIsUnreferenced() {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("root_land"));
        stubBiome(biome("root_land"));
        stubRegions(regionA);

        HintedLocator.SearchPlan plan = HintedLocator.biomePlan(engine, "missing");

        assertFalse(plan.isPossible());
    }

    @Test
    public void biomePlanFindsPolicyOnlyReachableBiome() throws Exception {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("plains"));

        IrisBiome plains = biome("plains");
        IrisBiome riverChild = biome("river_child");
        stubBiome(plains);
        stubBiome(riverChild);
        stubRegions(regionA);

        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>(riverChild));
        when(complex.getRegionStream()).thenReturn(constantStream(regionA));
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            return x >= 1024 ? riverChild : plains;
        });

        HintedLocator.SearchPlan plan = HintedLocator.biomePlan(engine, "river_child");
        assertTrue(plan.isPossible());
        assertNull(plan.getCoarse());

        Locator<IrisBiome> locator = Locator.surfaceBiome("river_child");
        Position2 result = locator.find(engine, new Position2(0, 0), 60_000, (Integer count) -> {
        }).get();

        assertNotNull(result);
        assertTrue(((result.getX() << 4) + 8) >= 1024);
    }

    @Test
    public void surfaceBiomeLocatorFindsNarrowAcceptedBiomeAwayFromChunkCenter() {
        IrisBiome riverShore = biome("river_shore");
        IrisBiome plains = biome("plains");
        IrisHydrologyRuntime hydrology = mock(IrisHydrologyRuntime.class);
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenReturn(plains);
        when(complex.getHydrologyRuntime()).thenReturn(hydrology);
        when(hydrology.hasAcceptedSurfaceBiomeInChunk("river_shore", 0, 0)).thenReturn(true);

        assertTrue(Locator.chunkContainsSurfaceBiome(
                engine,
                new Position2(0, 0),
                riverShore.getLoadKey()
        ));
        assertFalse(Locator.chunkContainsSurfaceBiome(
                engine,
                new Position2(1, 0),
                riverShore.getLoadKey()
        ));
    }

    @Test
    public void biomePlanFallsBackToCaveStreamForCaveOnlyBiome() {
        IrisRegion regionA = region("reg_a");
        regionA.setCaveBiomes(keys("cavey"));
        IrisBiome cavey = biome("cavey");
        stubBiome(cavey);
        stubRegions(regionA);

        when(complex.getRegionStream()).thenReturn(constantStream(regionA));
        when(complex.getCaveBiomeStream()).thenReturn(positionalStream((x, z) -> x >= 512D ? cavey : biome("other_cave"), cavey));

        HintedLocator.SearchPlan plan = HintedLocator.biomePlan(engine, "cavey");

        assertTrue(plan.isPossible());
        assertNotNull(plan.getExactOverride());
        assertTrue(plan.getCoarse().test(600, 0));
        assertFalse(plan.getCoarse().test(0, 0));

        when(engine.getCaveBiome(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            return x >= 512 ? cavey : biome("other_cave");
        });
        assertTrue(plan.getExactOverride().matches(engine, new Position2(40, 0)));
        assertFalse(plan.getExactOverride().matches(engine, new Position2(0, 0)));
    }

    @Test
    public void biomePlanIsUnprunedInFocusMode() {
        when(engine.getFocus()).thenReturn(biome("focus"));

        HintedLocator.SearchPlan plan = HintedLocator.biomePlan(engine, "anything");

        assertTrue(plan.isPossible());
        assertNull(plan.getCoarse());
        assertEquals(1, plan.getStrideChunks());
    }

    @Test
    public void biomePlanIsUnprunedForStackLayerBiome() {
        DimensionStackContext stackContext = mock(DimensionStackContext.class);
        IrisBiome stacked = biome("stacked");
        when(engine.getDimensionStackContext()).thenReturn(stackContext);
        when(engine.getAllBiomes()).thenReturn(new KList<>(stacked));

        HintedLocator.SearchPlan plan = HintedLocator.biomePlan(engine, "stacked");

        assertTrue(plan.isPossible());
        assertNull(plan.getCoarse());
        assertEquals(1, plan.getStrideChunks());
    }

    @Test
    public void regionPlanMatchesOnlyTargetRegionCells() {
        IrisRegion regionA = region("reg_a");
        IrisRegion regionB = region("reg_b");
        stubRegions(regionA, regionB);

        when(complex.getRegionStream()).thenReturn(positionalStream((x, z) -> x >= 4096D ? regionB : regionA, regionA));

        HintedLocator.SearchPlan plan = HintedLocator.regionPlan(engine, "reg_b");

        assertTrue(plan.isPossible());
        assertTrue(plan.getStrideChunks() > 1);
        assertTrue(plan.getCoarse().test(5000, 0));
        assertFalse(plan.getCoarse().test(0, 0));
    }

    @Test
    public void regionPlanIsImpossibleForUnknownRegion() {
        stubRegions(region("reg_a"));

        HintedLocator.SearchPlan plan = HintedLocator.regionPlan(engine, "reg_missing");

        assertFalse(plan.isPossible());
    }

    @Test
    public void regionPlanIsUnprunedForStackLayerRegion() {
        DimensionStackContext stackContext = mock(DimensionStackContext.class);
        DimensionTerrainContext terrainContext = mock(DimensionTerrainContext.class);
        IrisDimension stackedDimension = mock(IrisDimension.class);
        IrisRegion stackedRegion = region("stacked_region");
        when(engine.getDimensionStackContext()).thenReturn(stackContext);
        when(stackContext.getLayersTopToBottom()).thenReturn(List.of(terrainContext));
        when(terrainContext.getDimension()).thenReturn(stackedDimension);
        when(stackedDimension.getAllRegions(terrainContext)).thenReturn(new KList<>(stackedRegion));

        HintedLocator.SearchPlan plan = HintedLocator.regionPlan(engine, "stacked_region");

        assertTrue(plan.isPossible());
        assertNull(plan.getCoarse());
        assertEquals(1, plan.getStrideChunks());
    }

    @Test
    public void objectPlanPrunesToHostBiomes() {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("host_biome"));
        IrisBiome host = biome("host_biome");
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(keys("trees/oak"));
        KList<IrisObjectPlacement> placements = new KList<>();
        placements.add(placement);
        host.setObjects(placements);
        stubBiome(host);
        stubRegions(regionA);

        when(complex.getRegionStream()).thenReturn(constantStream(regionA));
        when(complex.getLandBiomeStream()).thenReturn(positionalStream((x, z) -> x >= 1024D ? host : biome("plains"), host));
        when(complex.getSeaBiomeStream()).thenReturn(constantStream(biome("sea_root")));
        when(complex.getShoreBiomeStream()).thenReturn(constantStream(biome("shore_root")));

        HintedLocator.SearchPlan plan = HintedLocator.objectPlan(engine, "trees/oak");

        assertTrue(plan.isPossible());
        assertEquals(1, plan.getStrideChunks());
        assertTrue(plan.getCoarse().test(2000, 0));
        assertFalse(plan.getCoarse().test(0, 0));
    }

    @Test
    public void objectPlanAcceptsRegionLevelPlacements() {
        IrisRegion regionA = region("reg_a");
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(keys("ruins/tower"));
        KList<IrisObjectPlacement> placements = new KList<>();
        placements.add(placement);
        regionA.setObjects(placements);
        stubRegions(regionA);

        when(complex.getRegionStream()).thenReturn(constantStream(regionA));
        when(complex.getLandBiomeStream()).thenReturn(constantStream(biome("plains")));
        when(complex.getSeaBiomeStream()).thenReturn(constantStream(biome("sea_root")));
        when(complex.getShoreBiomeStream()).thenReturn(constantStream(biome("shore_root")));

        HintedLocator.SearchPlan plan = HintedLocator.objectPlan(engine, "ruins/tower");

        assertTrue(plan.isPossible());
        assertTrue(plan.getCoarse().test(0, 0));
    }

    @Test
    public void objectPlanIsImpossibleForUnplacedObject() {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("plains"));
        stubBiome(biome("plains"));
        stubRegions(regionA);

        HintedLocator.SearchPlan plan = HintedLocator.objectPlan(engine, "missing/object");

        assertFalse(plan.isPossible());
    }

    @Test
    public void findLocatesDistantBiomeBandWithoutChunkScan() throws Exception {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("target"));
        IrisBiome target = biome("target");
        IrisBiome plains = biome("plains");
        stubBiome(target);
        stubRegions(regionA);

        when(complex.getRegionStream()).thenReturn(constantStream(regionA));
        when(complex.getLandBiomeStream()).thenReturn(positionalStream((x, z) -> x >= 4096D ? target : plains, plains));
        when(complex.getSeaBiomeStream()).thenReturn(constantStream(biome("sea_root")));
        when(complex.getShoreBiomeStream()).thenReturn(constantStream(biome("shore_root")));
        when(engine.getSurfaceBiome(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            return x >= 4096 ? target : plains;
        });

        Locator<IrisBiome> locator = Locator.surfaceBiome("target");
        AtomicInteger checks = new AtomicInteger();
        Position2 result = locator.find(engine, new Position2(0, 0), 60_000, checks::set).get();

        assertNotNull(result);
        assertTrue(((result.getX() << 4) + 8) >= 4096);
    }

    @Test
    public void findReturnsNullFastForImpossibleTarget() throws Exception {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("plains"));
        stubBiome(biome("plains"));
        stubRegions(regionA);

        Locator<IrisBiome> locator = Locator.surfaceBiome("missing");
        long start = System.currentTimeMillis();
        Position2 result = locator.find(engine, new Position2(0, 0), 120_000, (Integer count) -> {
        }).get();
        long elapsed = System.currentTimeMillis() - start;

        assertNull(result);
        assertTrue(elapsed < 10_000);
    }

    @Test
    public void locatorMergesRecordedAndActiveCandidatesByDistance() throws Exception {
        Position2 origin = new Position2(0, 0);
        Position2 activeChunk = new Position2(1, 0);
        Locator<String> locator = new Locator<>() {
            @Override
            public boolean matches(Engine ignoredEngine, Position2 chunk) {
                return activeChunk.equals(chunk);
            }

            @Override
            public SearchCandidate nearestRecordedCandidate(
                    Engine ignoredEngine,
                    Position2 ignoredOrigin,
                    int ignoredRadius
            ) {
                return SearchCandidate.atChunkCenter(new Position2(20, 0));
            }
        };

        Position2 result = locator.find(engine, origin, 60_000, ignored -> { }).get();

        assertEquals(activeChunk, result);
    }

    @Test
    public void impossibleTargetWithNoHistoricalFallbackReturnsWithoutScanning() throws Exception {
        IrisEngine routedEngine = mock(IrisEngine.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(routedEngine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(routedEngine.acquireGenerationLease(any(String.class))).thenReturn(GenerationSessionLease.noop());
        when(router.history()).thenReturn(history);
        when(history.hasHistoricalFallbackInSquare(anyInt(), anyInt(), anyInt())).thenReturn(false);
        Locator<String> locator = new HintedLocator<>((ignoredEngine, chunk) -> {
            throw new AssertionError("Impossible target must not enter procedural search.");
        }, ignoredEngine -> HintedLocator.SearchPlan.impossible());

        assertNull(locator.find(routedEngine, new Position2(0, 0), 60_000, ignored -> { })
                .get(3, TimeUnit.SECONDS));
    }

    @Test
    public void hintedLocatorMergesRecordedAndActiveCandidatesByDistance() throws Exception {
        Position2 origin = new Position2(0, 0);
        Position2 activeChunk = new Position2(1, 0);
        Locator<String> exact = new Locator<>() {
            @Override
            public boolean matches(Engine ignoredEngine, Position2 chunk) {
                return activeChunk.equals(chunk);
            }

            @Override
            public SearchCandidate nearestRecordedCandidate(
                    Engine ignoredEngine,
                    Position2 ignoredOrigin,
                    int ignoredRadius
            ) {
                return SearchCandidate.atChunkCenter(new Position2(20, 0));
            }
        };
        Locator<String> locator = new HintedLocator<>(
                exact,
                ignoredEngine -> HintedLocator.SearchPlan.unpruned()
        );

        Position2 result = locator.find(engine, origin, 60_000, ignored -> { }).get();

        assertEquals(activeChunk, result);
    }

    @Test
    public void exactBlockPositionsDecideStructureCandidateDistance() {
        Position2 origin = new Position2(0, 0);
        Locator.SearchCandidate fartherRecorded = new Locator.SearchCandidate(
                new Position2(1, 0),
                31,
                8
        );
        Locator.SearchCandidate nearerActive = new Locator.SearchCandidate(
                new Position2(1, 0),
                16,
                8
        );

        assertEquals(nearerActive, Locator.nearer(origin, fartherRecorded, nearerActive));
    }

    @Test
    public void plainLocatorChecksLaterRingsThatCanContainACloserMatch() throws Exception {
        Position2 corner = new Position2(-40, -40);
        Position2 nearer = new Position2(50, 0);
        Locator<String> locator = (ignoredEngine, chunk) -> chunk.equals(corner) || chunk.equals(nearer);

        assertEquals(nearer, locator.find(engine, new Position2(0, 0), 10_000, ignored -> { })
                .get(15, TimeUnit.SECONDS));
    }

    @Test
    public void fineLocatorChoosesTheSameNearestTieRegardlessOfWorkerOrder() throws Exception {
        Set<Position2> matches = Set.of(new Position2(-12, 0), new Position2(0, -12),
                new Position2(0, 12), new Position2(12, 0));
        Locator<String> locator = new HintedLocator<>((ignoredEngine, chunk) -> matches.contains(chunk),
                ignoredEngine -> HintedLocator.SearchPlan.unpruned());

        assertEquals(new Position2(-12, 0), locator.find(engine, new Position2(0, 0), 10_000, ignored -> { })
                .get(15, TimeUnit.SECONDS));
    }

    @Test
    public void coarseRefinementRanksMatchesFromTheSearchOrigin() throws Exception {
        Locator<String> locator = new HintedLocator<>(
                (ignoredEngine, chunk) -> chunk.equals(new Position2(8, 0)) || chunk.equals(new Position2(5, 0)),
                ignoredEngine -> HintedLocator.SearchPlan.of((x, z) -> x == 136 && z == 8, 4, null));

        assertEquals(new Position2(5, 0), locator.find(engine, new Position2(0, 0), 10_000, ignored -> { })
                .get(15, TimeUnit.SECONDS));
    }

    @Test
    public void ringCoordinatesNeverWrapAcrossTheBlockCoordinateLimit() {
        KList<Position2> cells = new KList<>();
        int maximumChunk = Integer.MAX_VALUE >> 4;
        HintedLocator.appendRing(cells, new Position2(maximumChunk, maximumChunk), 1, 1);

        assertEquals(Set.of(new Position2(maximumChunk - 1, maximumChunk - 1),
                new Position2(maximumChunk - 1, maximumChunk),
                new Position2(maximumChunk, maximumChunk - 1)), new HashSet<>(cells));
    }

    @Test
    public void transitionGatePreventsProceduralLocatorEvaluation() {
        AtomicInteger evaluations = new AtomicInteger();
        HistoryAwareLocator<String> locator = new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.REGION,
                "iris:temperate",
                (ignoredEngine, ignoredChunk) -> {
                    evaluations.incrementAndGet();
                    return true;
                }
        );
        when(complex.allowsNewGenerationChunk(3, 4)).thenReturn(false);

        assertFalse(locator.matchesForSearch(engine, new Position2(3, 4)));
        assertEquals(0, evaluations.get());
    }

    @Test
    public void semanticRecordsAndOwnershipSelectTheOnlyTruthfulLocatorSource() throws Exception {
        IrisEngine routedEngine = mock(IrisEngine.class);
        IrisComplex routedComplex = mock(IrisComplex.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        AtomicInteger scopeDepth = new AtomicInteger();
        AtomicInteger evaluations = new AtomicInteger();
        ChunkGenerationSemantics negative = semantics(1, "iris:other");
        ChunkGenerationSemantics positive = semantics(2, "iris:target");
        HistoryAwareLocator<String> locator = new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.REGION,
                "iris:target",
                (ignoredEngine, chunk) -> {
                    evaluations.incrementAndGet();
                    return scopeDepth.get() > 0 && (chunk.getX() == 0 || chunk.getX() == 3);
                }
        );

        when(routedEngine.getComplex()).thenReturn(routedComplex);
        when(routedEngine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(router.history()).thenReturn(history);
        when(history.semantics(anyInt(), anyInt())).thenAnswer(invocation -> switch (
                invocation.<Integer>getArgument(0)
        ) {
            case 1 -> Optional.of(negative);
            case 2 -> Optional.of(positive);
            default -> Optional.empty();
        });
        when(history.isHistoricallyOwned(anyInt(), anyInt())).thenAnswer(invocation -> {
            int chunkX = invocation.getArgument(0);
            return chunkX >= 0 && chunkX <= 2;
        });
        when(history.isActiveUnowned(anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == 3);
        when(routedComplex.allowsNewGenerationChunk(anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == 3);
        when(routedEngine.openGenerationHistoryCoordinateScope(anyInt(), anyInt())).thenAnswer(invocation -> {
            GenerationHistoryRuntimeRouter.CoordinateScope scope =
                    mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
            scopeDepth.incrementAndGet();
            doAnswer(close -> {
                scopeDepth.decrementAndGet();
                return null;
            }).when(scope).close();
            return scope;
        });

        assertTrue(locator.matches(routedEngine, new Position2(0, 0)));
        assertTrue(locator.matchesForSearch(routedEngine, new Position2(0, 0)));
        assertFalse(locator.matches(routedEngine, new Position2(1, 0)));
        assertFalse(locator.matchesForSearch(routedEngine, new Position2(1, 0)));
        assertTrue(locator.matches(routedEngine, new Position2(2, 0)));
        assertFalse(locator.matchesForSearch(routedEngine, new Position2(2, 0)));
        assertTrue(locator.matches(routedEngine, new Position2(3, 0)));
        assertTrue(locator.matchesForSearch(routedEngine, new Position2(3, 0)));
        assertEquals(4, evaluations.get());
        assertEquals(0, scopeDepth.get());
    }

    @Test
    public void historicalFallbackBypassesCurrentPackCoarseHints() throws Exception {
        IrisEngine routedEngine = mock(IrisEngine.class);
        IrisComplex routedComplex = mock(IrisComplex.class);
        GenerationHistory history = mock(GenerationHistory.class);
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        AtomicInteger scopeDepth = new AtomicInteger();
        HistoryAwareLocator<String> exact = new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.REGION,
                "iris:old_only",
                (ignoredEngine, chunk) -> chunk.equals(new Position2(1, 0)) && scopeDepth.get() > 0
        );
        Locator<String> locator = new HintedLocator<>(
                exact,
                ignoredEngine -> HintedLocator.SearchPlan.of((blockX, blockZ) -> false, 4, null)
        );

        when(routedEngine.getComplex()).thenReturn(routedComplex);
        when(routedEngine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(routedEngine.isClosed()).thenReturn(false);
        when(routedEngine.acquireGenerationLease(any(String.class))).thenReturn(GenerationSessionLease.noop());
        when(router.history()).thenReturn(history);
        when(history.findRecorded(any())).thenReturn(Optional.empty());
        when(history.semantics(anyInt(), anyInt())).thenReturn(Optional.empty());
        when(history.isHistoricallyOwned(anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == 1
                        && invocation.<Integer>getArgument(1) == 0);
        when(history.hasHistoricalFallbackInSquare(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                Math.abs((long) invocation.<Integer>getArgument(0) - 1L) <= invocation.<Integer>getArgument(2)
                        && Math.abs((long) invocation.<Integer>getArgument(1)) <= invocation.<Integer>getArgument(2));
        when(history.isActiveUnowned(anyInt(), anyInt())).thenReturn(false);
        when(routedComplex.allowsNewGenerationChunk(anyInt(), anyInt())).thenReturn(false);
        when(routedEngine.openGenerationHistoryCoordinateScope(anyInt(), anyInt())).thenAnswer(invocation -> {
            GenerationHistoryRuntimeRouter.CoordinateScope scope =
                    mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
            scopeDepth.incrementAndGet();
            doAnswer(close -> {
                scopeDepth.decrementAndGet();
                return null;
            }).when(scope).close();
            return scope;
        });

        Position2 result = locator.find(
                routedEngine,
                new Position2(0, 0),
                60_000,
                ignored -> { }
        ).get(10, TimeUnit.SECONDS);

        assertEquals(new Position2(1, 0), result);
        assertEquals(0, scopeDepth.get());
    }

    @Test
    public void startingAnotherSearchDoesNotCancelTheActiveRequest() throws Exception {
        CountDownLatch planning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Position2 origin = new Position2(4, -7);
        Locator<String> firstLocator = new HintedLocator<>((ignoredEngine, chunk) -> chunk.equals(origin), ignoredEngine -> {
            planning.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return HintedLocator.SearchPlan.impossible();
            }
            return HintedLocator.SearchPlan.unpruned();
        });
        Locator<String> secondLocator = new HintedLocator<>((ignoredEngine, chunk) -> false,
                ignoredEngine -> HintedLocator.SearchPlan.impossible());

        Future<Position2> first = firstLocator.find(engine, origin, 60_000, (Integer count) -> {
        });
        assertTrue(planning.await(10, TimeUnit.SECONDS));
        Future<Position2> second = secondLocator.find(engine, origin, 60_000, (Integer count) -> {
        });
        release.countDown();

        assertNull(second.get(10, TimeUnit.SECONDS));
        assertEquals(origin, first.get(10, TimeUnit.SECONDS));
    }

    private static ChunkGenerationSemantics semantics(int chunkX, String regionKey) {
        return ChunkGenerationSemantics.builder(chunkX, 0, 1L)
                .addRegion(regionKey)
                .seal()
                .build();
    }

    @Test
    public void findLocatesObjectThroughCoarseCascade() throws Exception {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("host_biome"));
        IrisBiome host = biome("host_biome");
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(keys("trees/oak"));
        KList<IrisObjectPlacement> placements = new KList<>();
        placements.add(placement);
        host.setObjects(placements);
        stubBiome(host);
        stubRegions(regionA);

        when(complex.getRegionStream()).thenReturn(constantStream(regionA));
        when(complex.getLandBiomeStream()).thenReturn(positionalStream((x, z) -> x >= 1024D ? host : biome("plains"), host));
        when(complex.getSeaBiomeStream()).thenReturn(constantStream(biome("sea_root")));
        when(complex.getShoreBiomeStream()).thenReturn(constantStream(biome("shore_root")));
        when(engine.getObjectsAt(anyInt(), anyInt())).thenAnswer(invocation -> {
            int chunkX = invocation.getArgument(0);
            Set<String> found = new HashSet<>();
            if (chunkX >= 64) {
                found.add("trees/oak");
            }
            return found;
        });

        Locator<art.arcane.iris.structure.object.IrisObject> locator = Locator.object("trees/oak");
        Position2 result = locator.find(engine, new Position2(0, 0), 60_000, (Integer count) -> {
        }).get();

        assertNotNull(result);
        assertTrue(result.getX() >= 64);
    }
}
