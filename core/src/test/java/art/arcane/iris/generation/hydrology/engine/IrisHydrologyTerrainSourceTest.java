package art.arcane.iris.generation.hydrology.engine;

import art.arcane.iris.generation.bake.BakePlane;
import art.arcane.iris.generation.bake.ChunkBakeCache;
import art.arcane.iris.generation.bake.GeneratorEvaluations;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.stream.ChunkFillableDoubleStream2D;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
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
        HeightLattice window = new HeightLattice(new FineLatticeSource(fine, source), 8, false, 1 << 16);
        HydrologySampleBuffer strided = new HydrologySampleBuffer(16 * 16);
        window.fill(-150, 300, 16, 16, 3, strided);
        assertMatchesScalar(reference, "window strided", strided, nodes(-150, 300, 16, 3, 8), false);
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

    private static double[] chunk(Map<Long, double[]> chunks, ChunkFillableDoubleStream2D stream, int x, int z) {
        long key = ((long) (x >> 4) << 32) | ((z >> 4) & 0xFFFFFFFFL);
        return chunks.computeIfAbsent(key, ignored -> {
            double[] values = new double[256];
            stream.fillChunkDoubles(x & ~15, z & ~15, values);
            return values;
        });
    }
}
