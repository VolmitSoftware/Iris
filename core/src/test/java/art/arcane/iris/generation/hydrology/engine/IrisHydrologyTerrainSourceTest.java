package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.GeneratorEvaluations;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyWetness;
import art.arcane.iris.generation.hydrology.grid.Basin;
import art.arcane.iris.generation.hydrology.grid.BasinExtractor;
import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.HydrologyTerrainSource;
import art.arcane.iris.generation.hydrology.grid.PriorityFlood;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.water.CupSet;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondPlanner;
import art.arcane.iris.generation.stream.ChunkFillableDoubleStream2D;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IrisHydrologyTerrainSourceTest {
    private static final int CLUSTERS = 16;
    private static final int COLUMNS = CLUSTERS * 256;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ExecutorService pool;

    @After
    public void shutDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    public void naturalHeightsMatchHeightStreamBitExact() throws Exception {
        pool = Executors.newFixedThreadPool(4);
        int[] xs = new int[COLUMNS];
        int[] zs = new int[COLUMNS];
        columns(xs, zs);
        Path shapedPack = EngineFixture.pack(temporaryFolder.newFolder().toPath());
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture sampled = new EngineFixture(shapedPack);
             EngineFixture reference = new EngineFixture(shapedPack)) {
            HydrologySettings settings = settings(sampled);
            long[] before = sampled.complex.bakeCensus();
            HydrologySampleBuffer pooled = new HydrologySampleBuffer(COLUMNS);
            new IrisHydrologyTerrainSource(sampled.complex, sampled.dimension, settings, pool, 4, HydrologyBinding.UNBOUND).sample(xs, zs, pooled, false);
            HydrologySampleBuffer serial = new HydrologySampleBuffer(COLUMNS);
            new IrisHydrologyTerrainSource(sampled.complex, sampled.dimension, settings, null, 1, HydrologyBinding.UNBOUND).sample(xs, zs, serial, false);
            HydrologySampleBuffer base = new HydrologySampleBuffer(COLUMNS);
            new IrisHydrologyTerrainSource(sampled.complex, sampled.dimension, settings, pool, 4, HydrologyBinding.UNBOUND).sample(xs, zs, base, true);
            assertLatticeFillsMatchScalar(sampled, reference, settings);
            long[] after = sampled.complex.bakeCensus();
            assertEquals(before[BakePlane.NATURAL_HEIGHT.ordinal()], after[BakePlane.NATURAL_HEIGHT.ordinal()]);
            assertEquals(before[BakePlane.BASE_HEIGHT.ordinal()], after[BakePlane.BASE_HEIGHT.ordinal()]);
            assertSameSamples(serial, pooled, COLUMNS);
            int shaped = assertMatchesBakedChunks(reference, xs, zs, pooled, base);
            assertTrue("Terrain3D shaped only " + shaped + " sampled columns", shaped > COLUMNS / 50);
        }
        Path flatPack = EngineFixture.pack(temporaryFolder.newFolder().toPath(), false);
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture sampled = new EngineFixture(flatPack);
             EngineFixture reference = new EngineFixture(flatPack)) {
            HydrologySettings settings = settings(sampled);
            GeneratorEvaluations.Snapshot before = GeneratorEvaluations.snapshot();
            HydrologySampleBuffer natural = new HydrologySampleBuffer(COLUMNS);
            new IrisHydrologyTerrainSource(sampled.complex, sampled.dimension, settings, pool, 4, HydrologyBinding.UNBOUND).sample(xs, zs, natural, false);
            HydrologySampleBuffer base = new HydrologySampleBuffer(COLUMNS);
            new IrisHydrologyTerrainSource(sampled.complex, sampled.dimension, settings, pool, 4, HydrologyBinding.UNBOUND).sample(xs, zs, base, true);
            GeneratorEvaluations.Snapshot spent = GeneratorEvaluations.snapshot().minus(before);
            assertTrue(String.valueOf(spent), spent.batchedEvaluations() > 0L);
            assertEquals(String.valueOf(spent), 0L, spent.scalarEvaluations());
            assertEquals(0, assertMatchesBakedChunks(reference, xs, zs, natural, base));
        }
    }

    @Test
    public void policyInheritsFromBiomeThenRegionThenDimension() throws Exception {
        Path pack = EngineFixture.pack(temporaryFolder.newFolder().toPath());
        int[] xs = new int[COLUMNS];
        int[] zs = new int[COLUMNS];
        columns(xs, zs);
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture fixture = new EngineFixture(pack)) {
            HydrologySettings settings = settings(fixture);
            HydrologySampleBuffer samples = new HydrologySampleBuffer(COLUMNS);
            new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND).sample(xs, zs, samples, false);
            Set<String> pairs = new HashSet<>();
            int ocean = 0;
            for (int index = 0; index < COLUMNS; index++) {
                int x = xs[index];
                int z = zs[index];
                String biome = fixture.complex.getBaseBiomeStream().get(x, z).getLoadKey();
                String region = fixture.complex.getRegionStream().get(x, z).getLoadKey();
                boolean alpha = region.equals("alpha");
                boolean meadow = biome.equals("meadow");
                String at = biome + "@" + region + " " + x + "," + z;
                pairs.add(biome + "@" + region);
                assertEquals(at, meadow ? 2f : 0.5f, samples.rainfall()[index], 0f);
                assertEquals(at, alpha ? 3f : 1f, samples.pondDensity()[index], 0f);
                int permission = (alpha ? 0 : HydrologySampleBuffer.ALLOW_STREAMS) | (meadow ? 0 : HydrologySampleBuffer.ALLOW_PONDS);
                assertEquals(at, permission, samples.permission()[index]);
                assertEquals(at, alpha ? "water" : null, samples.fluidProfile()[index]);
                boolean sea = fixture.complex.getBridgeStream().get(x, z) == InferredType.SEA;
                assertEquals(at, sea && Math.round(samples.height()[index]) < settings.seaLevel(), samples.ocean()[index]);
                if (samples.ocean()[index]) {
                    ocean++;
                }
            }
            assertTrue("sampled pairs " + pairs, pairs.contains("meadow@alpha") && pairs.contains("meadow@beta")
                    && pairs.contains("spires@beta"));
            assertTrue("ocean columns " + ocean, ocean > 0 && ocean < COLUMNS);
            assertUnknownFluidRejected(fixture, settings, xs, zs);
        }
    }

    @Test
    public void climateZonesDryOutRainfallAndPondsOutsideOptedOutBiomes() throws Exception {
        Path pack = EngineFixture.pack(temporaryFolder.newFolder().toPath());
        int[] xs = new int[COLUMNS];
        int[] zs = new int[COLUMNS];
        columns(xs, zs);
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture fixture = new EngineFixture(pack)) {
            IrisHydrology config = new IrisHydrology().setEnabled(true).setPersist(false);
            config.getClimate().setEnabled(true).setDryness(0.5D).setTransition(0D).getStyle().setZoom(4D);
            HydrologySettings settings = HydrologySettings.compile(config, EngineFixture.SEED, fixture.dimension.getFluidHeight(), 0,
                    EngineFixture.HEIGHT);
            fixture.data.getBiomeLoader().load("meadow").getHydrologyPolicy().setClimateZones(false);
            IrisHydrologyTerrainSource source = new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1,
                    HydrologyBinding.UNBOUND);
            HydrologySampleBuffer samples = new HydrologySampleBuffer(COLUMNS);
            source.sample(xs, zs, samples, false);
            int dry = 0;
            int wet = 0;
            int kept = 0;
            for (int index = 0; index < COLUMNS; index++) {
                int x = xs[index];
                int z = zs[index];
                boolean meadow = fixture.complex.getBaseBiomeStream().get(x, z).getLoadKey().equals("meadow");
                boolean alpha = fixture.complex.getRegionStream().get(x, z).getLoadKey().equals("alpha");
                String at = x + "," + z;
                assertEquals(at, alpha ? "water" : null, source.channelFluid(x, z));
                assertEquals(at, !meadow, samples.climateZoned()[index]);
                if (meadow) {
                    assertEquals(at, 2f, samples.rainfall()[index], 0f);
                    assertEquals(at, alpha ? 3f : 1f, samples.pondDensity()[index], 0f);
                    assertEquals(at, 1f, samples.wetness()[index], 0f);
                    kept++;
                    continue;
                }
                boolean zoneDry = samples.rainfall()[index] == 0f;
                assertEquals(at, zoneDry ? 0f : 0.5f, samples.rainfall()[index], 0f);
                assertEquals(at, zoneDry ? 0f : alpha ? 3f : 1f, samples.pondDensity()[index], 0f);
                assertEquals(at, zoneDry ? 0f : 1f, samples.wetness()[index], 0f);
                if (zoneDry) {
                    dry++;
                } else {
                    wet++;
                }
            }
            assertTrue("dry columns " + dry + ", wet columns " + wet + ", opted-out columns " + kept,
                    dry > COLUMNS / 20 && wet > COLUMNS / 20 && kept > 0);
        }
    }

    @Test
    public void wetnessClampKeepsSwampsWet() throws Exception {
        Path pack = childPack("");
        int[][] grid = childGrid();
        int[] xs = grid[0];
        int[] zs = grid[1];
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture fixture = new EngineFixture(pack)) {
            HydrologySettings settings = climateSettings(fixture);
            HydrologySampleBuffer zoned = new HydrologySampleBuffer(xs.length);
            new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND).sample(xs, zs, zoned, false);
            fixture.data.getBiomeLoader().load("meadow").getHydrologyPolicy().setWetness(new IrisHydrologyWetness().setMin(0.7D));
            fixture.data.getRegionLoader().load("beta").setHydrologyPolicy(new IrisHydrologyPolicy().setWetness(new IrisHydrologyWetness().setMax(0.35D)));
            HydrologySampleBuffer clamped = new HydrologySampleBuffer(xs.length);
            new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND).sample(xs, zs, clamped, false);
            HydrologySampleBuffer unzoned = new HydrologySampleBuffer(xs.length);
            new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, lavaSettings(fixture), null, 1, HydrologyBinding.UNBOUND)
                    .sample(xs, zs, unzoned, false);
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < xs.length; index++) {
                int x = xs[index];
                int z = zs[index];
                String chain = chain(fixture, x, z);
                String leaf = chain.split("<")[0];
                boolean alpha = fixture.complex.getRegionStream().get(x, z).getLoadKey().equals("alpha");
                boolean swamp = leaf.equals("meadow") || leaf.equals("glade");
                boolean zoneDry = zoned.rainfall()[index] == 0f;
                float rainfall = swamp ? 2f : 0.5f;
                float ponds = chain.contains("peaks") ? 5f : alpha ? 3f : 1f;
                String at = chain + "@" + (alpha ? "alpha" : "beta") + " " + x + "," + z;
                assertEquals(at, zoneDry ? 0f : rainfall, zoned.rainfall()[index], 0f);
                float wet = Math.clamp(zoneDry ? 0f : 1f, swamp ? 0.7f : 0f, swamp || alpha ? 1f : 0.35f);
                assertEquals(at, rainfall * wet, clamped.rainfall()[index], 0f);
                assertEquals(at, ponds * wet, clamped.pondDensity()[index], 0f);
                assertEquals(at, wet, clamped.wetness()[index], 0f);
                assertTrue(at, clamped.climateZoned()[index]);
                assertEquals(at, rainfall, unzoned.rainfall()[index], 0f);
                assertEquals(at, ponds, unzoned.pondDensity()[index], 0f);
                assertEquals(at, 1f, unzoned.wetness()[index], 0f);
                assertFalse(at, unzoned.climateZoned()[index]);
                seen.add(leaf + "@" + (alpha ? "alpha" : "beta") + (zoneDry ? " dry" : " wet"));
            }
            assertTrue("sampled " + seen, seen.containsAll(List.of("meadow@alpha dry", "glade@beta dry", "meadow@beta wet", "spires@beta wet",
                    "spires@beta dry", "peaks@alpha wet", "peaks@alpha dry")));
            assertInvertedWetnessRejected(fixture, settings, xs, zs);
        }
    }

    @Test
    public void wetnessMinKeepsADryZonePondFromDrawingDown() throws Exception {
        Path pack = childPack("");
        int[][] grid = childGrid();
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture fixture = new EngineFixture(pack)) {
            IrisHydrology config = new IrisHydrology().setEnabled(true).setPersist(false);
            config.getFluids().add(new IrisFluidProfile().setName("lava").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:lava")));
            config.getClimate().setEnabled(true).setDryness(0.5D).setTransition(0D).setDryRainfall(0.15D).getStyle().setZoom(4D);
            config.getPonds().setFreeboard(new KList<>(1, 1));
            config.getPonds().getSeeded().setEnabled(false);
            HydrologySettings settings = HydrologySettings.compile(config, EngineFixture.SEED, fixture.dimension.getFluidHeight(), 0,
                    EngineFixture.HEIGHT);
            IrisBiome spires = fixture.data.getBiomeLoader().load("spires");
            spires.setHydrologyPolicy(new IrisHydrologyPolicy().setPondDensity(16D));
            int[] column = dryZoneColumn(fixture, settings, grid, "spires");
            Pond dry = floorPond(fixture, settings, column);
            spires.setHydrologyPolicy(new IrisHydrologyPolicy().setPondDensity(16D).setWetness(new IrisHydrologyWetness().setMin(0.7D)));
            Pond swamp = floorPond(fixture, settings, column);
            spires.setHydrologyPolicy(new IrisHydrologyPolicy().setPondDensity(16D).setWetness(new IrisHydrologyWetness().setMin(1D)));
            Pond kept = floorPond(fixture, settings, column);
            int drawdown = settings.ponds().climateDrawdown();
            assertEquals((int) Math.floor(kept.spillHeight()) - 1, kept.level());
            assertEquals(kept.level() - Math.round(drawdown * 0.3f), swamp.level());
            assertEquals(kept.level() - Math.round(drawdown * 0.85f), dry.level());
        }
    }

    private static int[] dryZoneColumn(EngineFixture fixture, HydrologySettings settings, int[][] grid, String biome) {
        HydrologySampleBuffer samples = new HydrologySampleBuffer(grid[0].length);
        new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND)
                .sample(grid[0], grid[1], samples, false);
        for (int index = 0; index < grid[0].length; index++) {
            if (samples.rainfall()[index] < 0.1f && fixture.complex.getBaseBiomeStream().get(grid[0][index], grid[1][index]).getLoadKey().equals(biome)) {
                return new int[]{grid[0][index], grid[1][index]};
            }
        }
        throw new AssertionError("No sampled " + biome + " column lies in a dry zone");
    }

    private static Pond floorPond(EngineFixture fixture, HydrologySettings settings, int[] column) {
        HydrologySampleBuffer floor = new HydrologySampleBuffer(1);
        new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND)
                .sample(new int[]{column[0]}, new int[]{column[1]}, floor, false);
        FloorBowl bowl = new FloorBowl(settings, floor);
        int span = 64;
        HydrologySampleBuffer samples = new HydrologySampleBuffer(span * span);
        new HeightLattice(bowl, 8, false, 1 << 16).fill(0, 0, span, span, samples);
        DemGrid grid = new DemGrid(0, 0, 8, span, span);
        System.arraycopy(samples.height(), 0, grid.values(), 0, span * span);
        PondPlanner planner = new PondPlanner(settings);
        CupSet cups = planner.subtractCups(grid, new WindowKey(0, 0), samples, new BitSet());
        BitSet seeds = new BitSet(span * span);
        for (int j = 0; j < span; j++) {
            for (int i = 0; i < span; i++) {
                if (i == 0 || j == 0 || i == span - 1 || j == span - 1) {
                    seeds.set(grid.index(i, j));
                }
            }
        }
        float[] filled = PriorityFlood.fill(grid, seeds, settings.drainage().fillEpsilon());
        List<Basin> basins = BasinExtractor.basins(grid, filled, 0.5f, settings.seaLevel());
        List<Pond> ponds = planner.ponds(grid, filled, basins, List.of(), new HeightLattice(bowl, 4, false, 1 << 18), cups);
        assertEquals("ponds " + ponds, 1, ponds.size());
        return ponds.getFirst();
    }

    @Test
    public void policyMultipliersOutsideZeroToSixteenAreRejected() throws Exception {
        Path pack = EngineFixture.pack(temporaryFolder.newFolder().toPath());
        int[] xs = new int[COLUMNS];
        int[] zs = new int[COLUMNS];
        columns(xs, zs);
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture fixture = new EngineFixture(pack)) {
            HydrologySettings settings = settings(fixture);
            int[] column = spiresColumn(fixture, xs, zs);
            IrisBiome spires = fixture.data.getBiomeLoader().load("spires");
            spires.setHydrologyPolicy(new IrisHydrologyPolicy().setPondDensity(17D));
            assertPolicyRejected(fixture, settings, column,
                    "Hydrology policy pondDensity for biome spires in region beta must be a finite value from 0 to 16, got 17.0");
            spires.setHydrologyPolicy(new IrisHydrologyPolicy().setRainfall(16.5D).setPondDensity(16D));
            assertPolicyRejected(fixture, settings, column,
                    "Hydrology policy rainfall for biome spires in region beta must be a finite value from 0 to 16, got 16.5");
            spires.setHydrologyPolicy(new IrisHydrologyPolicy().setRainfall(16D).setPondDensity(16D));
            HydrologySampleBuffer bounds = new HydrologySampleBuffer(1);
            new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND)
                    .sample(new int[]{column[0]}, new int[]{column[1]}, bounds, false);
            assertEquals(16f, bounds.rainfall()[0], 0f);
            assertEquals(16f, bounds.pondDensity()[0], 0f);
        }
    }

    private static int[] spiresColumn(EngineFixture fixture, int[] xs, int[] zs) {
        for (int index = 0; index < xs.length; index++) {
            if (fixture.complex.getBaseBiomeStream().get(xs[index], zs[index]).getLoadKey().equals("spires")) {
                return new int[]{xs[index], zs[index]};
            }
        }
        throw new AssertionError("No sampled column is spires");
    }

    private static void assertPolicyRejected(EngineFixture fixture, HydrologySettings settings, int[] column, String message) {
        try {
            new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND)
                    .sample(new int[]{column[0]}, new int[]{column[1]}, new HydrologySampleBuffer(1), false);
            fail("Expected rejection: " + message);
        } catch (IllegalArgumentException expected) {
            assertEquals(message, expected.getMessage());
        }
    }

    private static void assertInvertedWetnessRejected(EngineFixture fixture, HydrologySettings settings, int[] xs, int[] zs) {
        fixture.data.getBiomeLoader().load("meadow").getHydrologyPolicy().setWetness(new IrisHydrologyWetness().setMin(0.8D).setMax(0.3D));
        for (int index = 0; index < xs.length; index++) {
            if (!fixture.complex.getBaseBiomeStream().get(xs[index], zs[index]).getLoadKey().equals("meadow")) {
                continue;
            }
            try {
                new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND)
                        .sample(new int[]{xs[index]}, new int[]{zs[index]}, new HydrologySampleBuffer(1), false);
                fail("A wetness min above its max must be rejected");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("wetness") && expected.getMessage().contains("biome meadow"));
            }
            return;
        }
        fail("No sampled column is meadow");
    }

    @Test
    public void childBiomesInheritTheParentPolicy() throws Exception {
        Path pack = childPack("");
        int[][] grid = childGrid();
        int[] xs = grid[0];
        int[] zs = grid[1];
        pool = Executors.newFixedThreadPool(4);
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture fixture = new EngineFixture(pack)) {
            HydrologySettings settings = lavaSettings(fixture);
            IrisHydrologyTerrainSource source = new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1,
                    HydrologyBinding.UNBOUND);
            HydrologySampleBuffer samples = new HydrologySampleBuffer(xs.length);
            source.sample(xs, zs, samples, false);
            HydrologySampleBuffer pooled = new HydrologySampleBuffer(xs.length);
            new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, pool, 4, HydrologyBinding.UNBOUND).sample(xs, zs, pooled, false);
            assertSameSamples(samples, pooled, xs.length);
            Map<String, Integer> chains = new HashMap<>();
            for (int index = 0; index < xs.length; index++) {
                int x = xs[index];
                int z = zs[index];
                String chain = chain(fixture, x, z);
                boolean alpha = fixture.complex.getRegionStream().get(x, z).getLoadKey().equals("alpha");
                String at = chain + "@" + (alpha ? "alpha" : "beta") + " " + x + "," + z;
                chains.merge(chain + "@" + (alpha ? "alpha" : "beta"), 1, Integer::sum);
                assertChildPolicy(at, source, samples, new int[]{x, z, index}, alpha, chain.contains("peaks"), chain.contains("meadow"));
            }
            assertTrue("sampled chains " + chains, chains.containsKey("meadow<peaks@alpha") && chains.containsKey("glade<meadow<peaks@alpha")
                    && chains.containsKey("meadow@alpha") && chains.containsKey("meadow@beta") && chains.containsKey("glade<meadow@beta")
                    && chains.containsKey("peaks@alpha"));
        }
    }

    @Test
    public void childCyclesResolveTheirParentsWithoutDrift() throws Exception {
        assertCycleResolves(",\"children\":[\"glade\"]", false);
        assertCycleResolves(",\"children\":[\"meadow\"],\"childShrinkFactor\":3.5", true);
    }

    private void assertCycleResolves(String gladeExtra, boolean repeats) throws Exception {
        Path pack = childPack(gladeExtra);
        int[][] grid = childGrid();
        int[] xs = grid[0];
        int[] zs = grid[1];
        try (EngineFixture.Environment ignored = EngineFixture.environment(); EngineFixture fixture = new EngineFixture(pack)) {
            IrisHydrologyTerrainSource source = new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, lavaSettings(fixture), null, 1,
                    HydrologyBinding.UNBOUND);
            HydrologySampleBuffer samples = new HydrologySampleBuffer(xs.length);
            source.sample(xs, zs, samples, false);
            int glades = 0;
            int repeated = 0;
            for (int index = 0; index < xs.length; index++) {
                int x = xs[index];
                int z = zs[index];
                IrisBiome leaf = fixture.complex.getBaseBiomeStream().get(x, z);
                IrisBiome root = root(fixture, x, z);
                List<IrisBiome> lineage = fixture.complex.baseBiomeLineage(x, z);
                String at = gladeExtra + " " + root.getLoadKey() + ">" + leaf.getLoadKey() + " " + x + "," + z;
                assertSame(at, leaf, lineage.getFirst());
                assertSame(at, root, lineage.getLast());
                assertTrue(at, lineage.size() <= 5);
                Set<String> keys = new HashSet<>();
                for (int link = 0; link < lineage.size(); link++) {
                    keys.add(lineage.get(link).getLoadKey());
                    if (link > 0) {
                        assertTrue(at, lineage.get(link).getChildren().contains(lineage.get(link - 1).getLoadKey()));
                        assertTrue(at, lineage.get(link) != lineage.get(link - 1));
                    }
                }
                repeated += keys.size() < lineage.size() ? 1 : 0;
                glades += leaf.getLoadKey().equals("glade") ? 1 : 0;
                boolean alpha = fixture.complex.getRegionStream().get(x, z).getLoadKey().equals("alpha");
                boolean meadow = leaf.getLoadKey().equals("meadow") || leaf.getLoadKey().equals("glade");
                assertChildPolicy(at, source, samples, new int[]{x, z, index}, alpha, root.getLoadKey().equals("peaks"), meadow);
            }
            assertTrue(gladeExtra + " glade columns " + glades, glades > 0);
            assertTrue(gladeExtra + " columns whose chain repeats a biome " + repeated, repeats == repeated > 0);
        }
    }

    private static void assertChildPolicy(String at, IrisHydrologyTerrainSource source, HydrologySampleBuffer samples, int[] column, boolean alpha,
                                          boolean peaks, boolean meadow) {
        int index = column[2];
        assertEquals(at, meadow ? 2f : 0.5f, samples.rainfall()[index], 0f);
        assertEquals(at, peaks ? 5f : alpha ? 3f : 1f, samples.pondDensity()[index], 0f);
        int permission = (peaks || !alpha ? HydrologySampleBuffer.ALLOW_STREAMS : 0) | (meadow ? 0 : HydrologySampleBuffer.ALLOW_PONDS);
        assertEquals(at, permission, samples.permission()[index]);
        String fluid = peaks ? "lava" : alpha ? "water" : null;
        assertEquals(at, fluid, samples.fluidProfile()[index]);
        assertEquals(at, fluid, source.channelFluid(column[0], column[1]));
    }

    private Path childPack(String gladeExtra) throws Exception {
        Path pack = EngineFixture.pack(temporaryFolder.newFolder().toPath(), false);
        EngineFixture.extend(pack, "peaks", ",\"hydrologyPolicy\":{\"pondDensity\":5,\"streams\":\"ALLOW\",\"fluid\":\"lava\"}");
        EngineFixture.extend(pack, "meadow", ",\"children\":[\"glade\"],\"childShrinkFactor\":2.5");
        EngineFixture.biome(pack, "glade", "[{\"generator\":\"plain\",\"min\":4,\"max\":10}]", gladeExtra);
        return pack;
    }

    private static int[][] childGrid() {
        int side = 96;
        int[][] grid = new int[2][side * side];
        for (int index = 0; index < side * side; index++) {
            grid[0][index] = (index % side) * 9 - 432;
            grid[1][index] = (index / side) * 9 - 432;
        }
        return grid;
    }

    private static HydrologySettings lavaSettings(EngineFixture fixture) {
        IrisHydrology config = new IrisHydrology().setEnabled(true).setPersist(false);
        config.getFluids().add(new IrisFluidProfile().setName("lava").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:lava")));
        return HydrologySettings.compile(config, EngineFixture.SEED, fixture.dimension.getFluidHeight(), 0, EngineFixture.HEIGHT);
    }

    private static HydrologySettings climateSettings(EngineFixture fixture) {
        IrisHydrology config = new IrisHydrology().setEnabled(true).setPersist(false);
        config.getFluids().add(new IrisFluidProfile().setName("lava").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:lava")));
        config.getClimate().setEnabled(true).setDryness(0.5D).setTransition(0D).getStyle().setZoom(4D);
        return HydrologySettings.compile(config, EngineFixture.SEED, fixture.dimension.getFluidHeight(), 0, EngineFixture.HEIGHT);
    }

    private static IrisBiome root(EngineFixture fixture, int x, int z) {
        boolean sea = fixture.complex.getBridgeStream().get(x, z) == InferredType.SEA;
        return (sea ? fixture.complex.getSeaBiomeStream() : fixture.complex.getLandBiomeStream()).get(x, z);
    }

    private static String chain(EngineFixture fixture, int x, int z) {
        IrisBiome leaf = fixture.complex.getBaseBiomeStream().get(x, z);
        IrisBiome root = root(fixture, x, z);
        String pair = root.getLoadKey() + ">" + leaf.getLoadKey();
        return switch (pair) {
            case "peaks>meadow" -> "meadow<peaks";
            case "peaks>glade" -> "glade<meadow<peaks";
            case "meadow>glade" -> "glade<meadow";
            default -> {
                assertEquals("a biome outside the child chains at " + x + "," + z, root.getLoadKey(), leaf.getLoadKey());
                yield leaf.getLoadKey();
            }
        };
    }

    private static void assertUnknownFluidRejected(EngineFixture fixture, HydrologySettings settings, int[] xs, int[] zs) {
        IrisRegion beta = fixture.data.getRegionLoader().load("beta");
        beta.setHydrologyPolicy(new IrisHydrologyPolicy().setFluid("slime"));
        for (int index = 0; index < xs.length; index++) {
            if (fixture.complex.getRegionStream().get(xs[index], zs[index]) != beta) {
                continue;
            }
            try {
                new IrisHydrologyTerrainSource(fixture.complex, fixture.dimension, settings, null, 1, HydrologyBinding.UNBOUND)
                        .sample(new int[]{xs[index]}, new int[]{zs[index]}, new HydrologySampleBuffer(1), false);
                fail("A policy naming an unknown fluid must be rejected");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("'slime'"));
            }
            return;
        }
        fail("No sampled column lies in region beta");
    }

    private void assertLatticeFillsMatchScalar(EngineFixture sampled, EngineFixture reference, HydrologySettings settings) {
        IrisHydrologyTerrainSource source = new IrisHydrologyTerrainSource(sampled.complex, sampled.dimension, settings, pool, 4, HydrologyBinding.UNBOUND);
        HeightLattice fine = new HeightLattice(source, 4, false, 1 << 16);
        HydrologySampleBuffer rect = new HydrologySampleBuffer(32 * 32);
        fine.fill(-600, 400, 32, 32, rect);
        assertMatchesScalar(reference, "fine rect", rect, nodes(-600, 400, 32, 1, 4), false);
        HydrologySampleBuffer strided = new HydrologySampleBuffer(16 * 16);
        fine.fill(-300, 600, 16, 16, 6, strided);
        assertMatchesScalar(reference, "fine strided", strided, nodes(-300, 600, 16, 6, 4), false);
        HeightLattice coarse = new HeightLattice(source, 64, true, 1 << 12);
        HydrologySampleBuffer coarseBase = new HydrologySampleBuffer(16 * 16);
        coarse.fill(-8, -8, 16, 16, coarseBase);
        assertMatchesScalar(reference, "coarse base", coarseBase, nodes(-8, -8, 16, 1, 64), true);
        int[] scatteredX = {-77, 13, 250, -301, 4};
        int[] scatteredZ = {91, -40, 7, 333, -512};
        HydrologySampleBuffer scattered = new HydrologySampleBuffer(scatteredX.length);
        fine.fill(scatteredX, scatteredZ, scattered);
        int[][] scatteredBlocks = new int[2][scatteredX.length];
        for (int index = 0; index < scatteredX.length; index++) {
            scatteredBlocks[0][index] = scatteredX[index] * 4;
            scatteredBlocks[1][index] = scatteredZ[index] * 4;
        }
        assertMatchesScalar(reference, "fine scattered", scattered, scatteredBlocks, false);
    }

    private static int[][] nodes(int cellX0, int cellZ0, int side, int stride, int cell) {
        int[][] blocks = new int[2][side * side];
        for (int index = 0; index < side * side; index++) {
            blocks[0][index] = (cellX0 + (index % side) * stride) * cell;
            blocks[1][index] = (cellZ0 + (index / side) * stride) * cell;
        }
        return blocks;
    }

    private static void assertMatchesScalar(EngineFixture reference, String label, HydrologySampleBuffer samples, int[][] blocks,
                                            boolean baseOnly) {
        ProceduralStream<Double> stream = baseOnly ? reference.complex.getBaseTerrainHeightStream() : reference.complex.getNaturalHeightStream();
        ChunkBakeCache.enterSparse();
        try {
            for (int index = 0; index < blocks[0].length; index++) {
                double expected = stream.getDouble(blocks[0][index], blocks[1][index]);
                assertEquals(label + " " + blocks[0][index] + "," + blocks[1][index], Float.floatToRawIntBits((float) expected),
                        Float.floatToRawIntBits(samples.height()[index]));
            }
        } finally {
            ChunkBakeCache.exitSparse();
        }
    }

    private static int assertMatchesBakedChunks(EngineFixture reference, int[] xs, int[] zs, HydrologySampleBuffer natural,
                                                HydrologySampleBuffer base) {
        Map<Long, double[]> naturalChunks = new HashMap<>();
        Map<Long, double[]> baseChunks = new HashMap<>();
        ChunkFillableDoubleStream2D naturalStream = (ChunkFillableDoubleStream2D) reference.complex.getNaturalHeightStream();
        ChunkFillableDoubleStream2D baseStream = (ChunkFillableDoubleStream2D) reference.complex.getBaseTerrainHeightStream();
        int shaped = 0;
        for (int index = 0; index < xs.length; index++) {
            int column = ((zs[index] & 15) << 4) | (xs[index] & 15);
            double naturalHeight = chunk(naturalChunks, naturalStream, xs[index], zs[index])[column];
            double baseHeight = chunk(baseChunks, baseStream, xs[index], zs[index])[column];
            assertEquals("natural " + xs[index] + "," + zs[index], Float.floatToRawIntBits((float) naturalHeight),
                    Float.floatToRawIntBits(natural.height()[index]));
            assertEquals("base " + xs[index] + "," + zs[index], Float.floatToRawIntBits((float) baseHeight),
                    Float.floatToRawIntBits(base.height()[index]));
            if (Math.abs(naturalHeight - baseHeight) > 1D) {
                shaped++;
            }
        }
        return shaped;
    }

    private static void assertSameSamples(HydrologySampleBuffer expected, HydrologySampleBuffer actual, int count) {
        for (int index = 0; index < count; index++) {
            String at = "sample " + index;
            assertEquals(at, Float.floatToRawIntBits(expected.height()[index]), Float.floatToRawIntBits(actual.height()[index]));
            assertEquals(at, expected.ocean()[index], actual.ocean()[index]);
            assertEquals(at, Float.floatToRawIntBits(expected.rainfall()[index]), Float.floatToRawIntBits(actual.rainfall()[index]));
            assertEquals(at, Float.floatToRawIntBits(expected.pondDensity()[index]), Float.floatToRawIntBits(actual.pondDensity()[index]));
            assertEquals(at, Float.floatToRawIntBits(expected.wetness()[index]), Float.floatToRawIntBits(actual.wetness()[index]));
            assertEquals(at, expected.climateZoned()[index], actual.climateZoned()[index]);
            assertEquals(at, expected.permission()[index], actual.permission()[index]);
            assertEquals(at, expected.fluidProfile()[index], actual.fluidProfile()[index]);
        }
    }

    private static HydrologySettings settings(EngineFixture fixture) {
        return HydrologySettings.compile(new IrisHydrology().setEnabled(true).setPersist(false), EngineFixture.SEED,
                fixture.dimension.getFluidHeight(), 0, EngineFixture.HEIGHT);
    }

    private static void columns(int[] xs, int[] zs) {
        int index = 0;
        for (int cluster = 0; cluster < CLUSTERS; cluster++) {
            int originX = (cluster % 4) * 1248 - 2000 + 8;
            int originZ = (cluster / 4) * 1312 - 2400 + 8;
            for (int column = 0; column < 256; column++) {
                xs[index] = originX + (column & 15);
                zs[index] = originZ + (column >> 4);
                index++;
            }
        }
    }

    private static final class FloorBowl implements HydrologyTerrainSource {
        private final HydrologySettings settings;
        private final HydrologySampleBuffer floor;

        private FloorBowl(HydrologySettings settings, HydrologySampleBuffer floor) {
            this.settings = settings;
            this.floor = floor;
        }

        @Override
        public void sample(int[] xs, int[] zs, HydrologySampleBuffer out, boolean baseOnly) {
            for (int index = 0; index < xs.length; index++) {
                double ratio = Math.hypot(xs[index] - 256D, zs[index] - 256D) / 60D;
                out.height()[index] = (float) (ratio < 1D ? 200D + 12D * ratio * ratio : 212D - 6D * (ratio - 1D));
                out.ocean()[index] = false;
                out.rainfall()[index] = floor.rainfall()[0];
                out.pondDensity()[index] = floor.pondDensity()[0];
                out.wetness()[index] = floor.wetness()[0];
                out.climateZoned()[index] = floor.climateZoned()[0];
                out.permission()[index] = floor.permission()[0];
                out.fluidProfile()[index] = floor.fluidProfile()[0];
            }
        }

        @Override
        public int seaLevel() {
            return settings.seaLevel();
        }

        @Override
        public int minY() {
            return settings.minY();
        }

        @Override
        public int maxY() {
            return settings.maxY();
        }
    }

    private static double[] chunk(Map<Long, double[]> chunks, ChunkFillableDoubleStream2D stream, int x, int z) {
        long key = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        return chunks.computeIfAbsent(key, ignored -> {
            double[] values = new double[256];
            stream.fillChunkDoubles(x & ~15, z & ~15, values);
            return values;
        });
    }
}
