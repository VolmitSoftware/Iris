package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleBinaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ProceduralTerrainHeightSamplerTest {
    @Test
    public void unitStepPreservesOriginalFractionalSamples() {
        AtomicInteger calls = new AtomicInteger();
        ProceduralTerrainHeightSampler sampler = new ProceduralTerrainHeightSampler((x, z) -> {
            calls.incrementAndGet();
            return terrain(x, z);
        }, 1);
        for (double x : new double[]{-17.25D, -4D, -0.5D, 0D, 1.75D, 32D}) {
            assertEquals(terrain(x, -2.25D), sampler.sample(x, -2.25D), 0D);
        }
        assertEquals(6, calls.get());
    }

    @Test
    public void denseTerrainReusesLatticeNodesInsteadOfSamplingEveryColumn() {
        AtomicInteger calls = new AtomicInteger();
        ProceduralTerrainHeightSampler sampler = new ProceduralTerrainHeightSampler((x, z) -> {
            calls.incrementAndGet();
            return terrain(x, z);
        }, 4);
        for (int x = -32; x < 32; x++) {
            for (int z = -32; z < 32; z++) {
                assertEquals(interpolated(ProceduralTerrainHeightSamplerTest::terrain, x, z, 4),
                        sampler.sample(x, z), 0D);
            }
        }
        assertTrue("Procedural sample calls: " + calls.get(), calls.get() < 400);
        assertTrue(calls.get() >= 289);
    }

    @Test
    public void neighboringRegionsRetainHeightsAcrossRepeatedSweeps() {
        AtomicInteger calls = new AtomicInteger();
        DoubleBinaryOperator linear = (x, z) -> x * 0.25D - z * 0.125D + 96D;
        ProceduralTerrainHeightSampler sampler = new ProceduralTerrainHeightSampler((x, z) -> {
            calls.incrementAndGet();
            return linear.applyAsDouble(x, z);
        }, 4);
        int uniqueNodes = 261 * 261;
        for (int sweep = 0; sweep < 2; sweep++) {
            int before = calls.get();
            for (int chunkX = -32; chunkX <= 32; chunkX++) {
                for (int chunkZ = -32; chunkZ <= 32; chunkZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                            int x = chunkX * 16 + localX;
                            int z = chunkZ * 16 + localZ;
                            assertEquals(linear.applyAsDouble(x, z), sampler.sample(x, z), 0D);
                        }
                    }
                }
            }
            int sourceCalls = calls.get() - before;
            assertTrue("Procedural sample calls in sweep " + sweep + ": " + sourceCalls,
                    sourceCalls < (sweep == 0 ? uniqueNodes * 1.05D : uniqueNodes * 0.25D));
        }
    }

    @Test
    public void allStepsPreserveNodesLinearFieldsAndSignedWorldCoordinates() {
        DoubleBinaryOperator linear = (x, z) -> x * 0.25D - z * 0.125D + 96D;
        for (int step : new int[]{2, 4, 8}) {
            ProceduralTerrainHeightSampler sampler = new ProceduralTerrainHeightSampler(linear, step);
            ProceduralTerrainHeightSampler shaped = new ProceduralTerrainHeightSampler(
                    ProceduralTerrainHeightSamplerTest::terrain, step);
            for (double x : new double[]{Integer.MIN_VALUE, -33.5D, -8D, -0.25D, 0D, 8D, 31.75D,
                    Integer.MAX_VALUE}) {
                for (double z : new double[]{-19.5D, -8D, 0D, 8D, 19.5D}) {
                    assertEquals(linear.applyAsDouble(x, z), sampler.sample(x, z), 0D);
                    assertEquals(interpolated(ProceduralTerrainHeightSamplerTest::terrain, x, z, step),
                            shaped.sample(x, z), 0D);
                }
            }
            for (int x = -32; x <= 32; x += step) {
                assertEquals(terrain(x, -8), shaped.sample(x, -8), 0D);
            }
        }
    }

    @Test
    public void concurrentQueriesRemainDeterministicAcrossCacheReplacement() throws Exception {
        ProceduralTerrainHeightSampler sampler = new ProceduralTerrainHeightSampler(
                ProceduralTerrainHeightSamplerTest::terrain, 4);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                int offset = worker * 65_537;
                tasks.add(executor.submit(() -> {
                    for (int index = 0; index < 100_000; index++) {
                        int x = index * 4 - offset;
                        int z = -index * 8 + offset;
                        assertEquals(interpolated(ProceduralTerrainHeightSamplerTest::terrain, x, z, 4),
                                sampler.sample(x, z), 0D);
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            assertTrue(sampler.cachedNodeCount() > 200_000);
            assertTrue(sampler.cachedNodeCount() <= 262_144);
            ProceduralTerrainHeightSampler other = new ProceduralTerrainHeightSampler((x, z) -> 42D, 4);
            assertEquals(42D, other.sample(0, 0), 0D);
            assertEquals(terrain(0, 0), sampler.sample(0, 0), 0D);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedNodesCanBeRetriedAndInvalidStepsAreRejected() {
        AtomicInteger calls = new AtomicInteger();
        ProceduralTerrainHeightSampler sampler = new ProceduralTerrainHeightSampler((x, z) -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("height failure");
            }
            return 72D;
        }, 4);
        assertThrows(IllegalStateException.class, () -> sampler.sample(-4D, 8D));
        assertEquals(72D, sampler.sample(-4D, 8D), 0D);
        assertEquals(2, calls.get());
        for (int step : new int[]{-1, 0, 3, 16}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ProceduralTerrainHeightSampler((x, z) -> 0D, step));
        }
    }

    @Test
    public void sampledBaseHeightsRetainThreeDimensionalLedgesAndAirGaps() {
        ProceduralTerrainHeightSampler sampler = new ProceduralTerrainHeightSampler(
                (x, z) -> 96D + (Math.floorMod((int) x, 4) == 2 ? 2D : 0D), 4);
        IrisBiome biome = new IrisBiome().setTerrain3D(new IrisTerrain3D()
                .setAmplitude(32).setHorizontalScale(64).setVerticalScale(64)
                .setMinimumSlope(0).setFluidClearance(0).setFluidFade(1));
        Terrain3DRuntime.Options options = new Terrain3DRuntime.Options(12, 256, 0, null, true, 4096);
        Terrain3DRuntime.NoiseFactory noise = (style, seed) ->
                (x, y, z) -> StrictMath.sin(y * StrictMath.PI / 16D);
        Terrain3DRuntime sampled = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> sampler.sample(x, z), (x, z) -> biome), options, noise);
        Terrain3DRuntime reference = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 96D, (x, z) -> biome), options, noise);
        for (int x = -9; x <= 9; x++) {
            Terrain3DColumn column = sampled.column(x, -2);
            assertEquals(reference.column(x, -2), column);
            assertTrue(column.shaped());
            assertTrue(column.spanCount() > 1);
            assertTrue(column.isSolid(104));
            assertFalse(column.isSolid(84));
            assertTrue(column.isSolid(60));
        }
    }

    private static double terrain(double x, double z) {
        return 96D + StrictMath.sin(x * 0.13D) * 18D + StrictMath.cos(z * 0.17D) * 12D;
    }

    private static double interpolated(DoubleBinaryOperator source, double x, double z, int step) {
        double lowerX = Math.floor(x / step) * step;
        double lowerZ = Math.floor(z / step) * step;
        double dx = (x - lowerX) / step;
        double dz = (z - lowerZ) / step;
        double northWest = source.applyAsDouble(lowerX, lowerZ);
        double north = dx == 0D ? northWest
                : northWest + (source.applyAsDouble(lowerX + step, lowerZ) - northWest) * dx;
        if (dz == 0D) {
            return north;
        }
        double southWest = source.applyAsDouble(lowerX, lowerZ + step);
        double south = dx == 0D ? southWest
                : southWest + (source.applyAsDouble(lowerX + step, lowerZ + step) - southWest) * dx;
        return north + (south - north) * dz;
    }
}
