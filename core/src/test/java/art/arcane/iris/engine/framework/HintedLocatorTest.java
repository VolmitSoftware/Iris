package art.arcane.iris.engine.framework;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.placement.ObjectDeclarations;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import art.arcane.iris.engine.mantle.components.ObjectModule;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.stream.interpolation.Interpolated;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.Position2;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HintedLocatorTest {
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
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        ModuleResourceFixture.serve(data);
        ModuleResourceFixture.serveModules(data, new ObjectModule());
        ModuleResourceFixture.serveColumnSettings(data);
        when(engine.getGeneration()).thenReturn(new EngineGeneration(engine));
        stubReachableBiomes();
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

    private static ObjectPlacement placing(String objectKey) {
        return ObjectPlacement.builder().place(List.of(objectKey)).build();
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

    private void stubReachableBiomes(IrisBiome... biomes) {
        when(dimension.getReachableBiomes(engine)).thenReturn(new KList<>(biomes));
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
    public void objectPlanPrunesToHostBiomes() {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("host_biome"));
        IrisBiome host = biome("host_biome");
        ObjectDeclarations.declare(host, placing("trees/oak"));
        stubBiome(host);
        stubReachableBiomes(host);
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
        ObjectDeclarations.declare(regionA, placing("ruins/tower"));
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
        stubReachableBiomes(biome("plains"));
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

    @Test
    public void findLocatesObjectThroughCoarseCascade() throws Exception {
        IrisRegion regionA = region("reg_a");
        regionA.setLandBiomes(keys("host_biome"));
        IrisBiome host = biome("host_biome");
        ObjectDeclarations.declare(host, placing("trees/oak"));
        stubBiome(host);
        stubReachableBiomes(host);
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

        Locator<art.arcane.iris.engine.object.prefab.IrisObject> locator = Locator.object("trees/oak");
        Position2 result = locator.find(engine, new Position2(0, 0), 60_000, (Integer count) -> {
        }).get();

        assertNotNull(result);
        assertTrue(result.getX() >= 64);
    }
}
