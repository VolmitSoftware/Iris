package art.arcane.iris.engine.terrain;

import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisTerrain3D;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

public class Terrain3DRuntimeTest {
    @Test
    public void omittedProfilesPreserveExactHeightsWithoutCreatingNoise() {
        AtomicInteger compilations = new AtomicInteger();
        IrisBiome biome = new IrisBiome();
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 100.25D + x * 0.125D, (x, z) -> biome),
                options(true),
                (style, seed) -> {
                    compilations.incrementAndGet();
                    return (x, y, z) -> 1D;
                });

        for (int x = -20; x < 20; x++) {
            Terrain3DColumn column = runtime.column(x, 3);
            assertEquals(100.25D + x * 0.125D, column.baseHeight(), 0D);
            assertEquals(Math.round(column.baseHeight()), column.topY());
            assertEquals(1, column.spanCount());
            assertFalse(column.shaped());
        }
        assertEquals(0, compilations.get());
    }

    @Test
    public void inactiveRuntimeDoesNotResolveBiomesOrNoise() {
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 91.5D, (x, z) -> {
                    throw new AssertionError("Unexpected biome resolution");
                }), options(false), (style, seed) -> {
                    throw new AssertionError("Unexpected noise compilation");
                });

        assertFalse(runtime.active());
        assertEquals(92, runtime.column(0, 0).topY());
    }

    @Test
    public void unchangedColumnsRetainTheOriginalHeightOutsideVoxelBounds() {
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 300.25D, (x, z) -> null),
                options(false), (style, seed) -> (x, y, z) -> 0D);

        assertEquals(300.25D, runtime.column(0, 0).baseHeight(), 0D);
        assertEquals(255, runtime.column(0, 0).topY());
        assertFalse(runtime.column(0, 0).isSolid(256));
    }

    @Test
    public void densityAddsRockAndLeavesAirBelowProjectingLedges() {
        Terrain3DRuntime runtime = runtime(profile(), (x, y, z) -> StrictMath.sin(y * StrictMath.PI / 16D));
        Terrain3DColumn column = runtime.column(0, 0);

        assertTrue(column.shaped());
        assertTrue(column.topY() > Math.round(column.baseHeight()));
        assertTrue(column.spanCount() > 1);
        assertTrue(column.isSolid(104));
        assertFalse(column.isSolid(84));
        assertTrue(column.isSolid(60));
        assertEquals(-1, column.surfaceY(84));
        for (int span = 0; span < column.spanCount(); span++) {
            assertEquals(column.floor(span), column.surfaceY(column.ceiling(span)));
            assertFalse(column.isSolid(column.floor(span) + 1));
        }
    }

    @Test
    public void isolatedDensityPeakIsRemovedFromEveryColumnQuery() {
        Terrain3DRuntime runtime = runtime(profile(),
                (x, y, z) -> x == 16D && y == 112D && z == -16D ? 1D : -1D);

        for (int x = 14; x <= 18; x++) {
            for (int z = -18; z <= -14; z++) {
                Terrain3DColumn column = runtime.column(x, z);
                assertEquals(64, column.topY());
                assertEquals(1, column.spanCount());
                assertFalse(column.isSolid(112));
                assertEquals(-1, column.surfaceY(112));
                assertEquals(64, column.highestSolidY(112));
                assertEquals(64, column.nearestSurfaceY(112));
                assertTrue(column.isSolid(64));
            }
        }
    }

    @Test
    public void latticeMatchesTheSameFieldAcrossPositiveAndNegativeChunkBoundaries() {
        Terrain3DRuntime.NoiseSource noise = (x, y, z) -> (StrictMath.sin(x * StrictMath.PI / 23D)
                + StrictMath.sin(y * StrictMath.PI / 16D)
                + StrictMath.cos(z * StrictMath.PI / 29D)) / 3D;
        Terrain3DRuntime forward = runtime(profile(), noise);
        Terrain3DRuntime reverse = runtime(profile(), noise);

        for (int x = 20; x >= -20; x--) {
            reverse.column(x, -17);
        }
        for (int x = -20; x <= 20; x++) {
            Terrain3DColumn column = forward.column(x, -17);
            assertEquals(column, reverse.column(x, -17));
            for (int y = 1; y < 160; y++) {
                double density = 96.5D - y + 32D * interpolated(noise, x, y, -17);
                assertEquals("at " + x + "," + y + ",-17", density >= 0D, column.isSolid(y));
            }
        }
    }

    @Test
    public void profileContributionsFadeIntoAnOmittedNeighbour() {
        IrisBiome shaped = new IrisBiome().setTerrain3D(profile());
        IrisBiome unchanged = new IrisBiome();
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 96D, (x, z) -> x < 16 ? shaped : unchanged),
                options(true), (style, seed) -> (x, y, z) -> 1D);

        assertEquals(128, runtime.column(12, 0).topY());
        assertEquals(120, runtime.column(13, 0).topY());
        assertEquals(112, runtime.column(14, 0).topY());
        assertEquals(104, runtime.column(15, 0).topY());
        assertEquals(96, runtime.column(16, 0).topY());
        assertFalse(runtime.column(16, 0).shaped());
    }

    @Test
    public void signedFissureInterpolationPreservesNarrowCracksBetweenLatticeNodes() {
        IrisTerrain3D config = profile().setAmplitude(0).setCrackDepth(24).setCrackWidth(1)
                .setCrackScale(64);
        Terrain3DRuntime runtime = runtime(config, (x, y, z) -> (x - 2D) / 32D);

        assertEquals(72, runtime.column(2, 0).topY());
        assertEquals(96, runtime.column(0, 0).topY());
        assertEquals(96, runtime.column(4, 0).topY());
    }

    @Test
    public void waterAndSlopeGatesKeepProtectedTerrainUnchanged() {
        IrisBiome biome = new IrisBiome().setTerrain3D(profile().setMinimumSlope(0.25D));
        Terrain3DRuntime flat = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 96D, (x, z) -> biome),
                options(true), (style, seed) -> (x, y, z) -> -1D);
        assertFalse(flat.column(0, 0).shaped());

        biome.setTerrain3D(profile().setAmplitude(128).setCrackDepth(128).setFluidClearance(8));
        Terrain3DRuntime water = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 40D, (x, z) -> biome),
                new Terrain3DRuntime.Options(12, 256, 32, null, true, 4096),
                (style, seed) -> (x, y, z) -> -1D);
        assertFalse(water.column(0, 0).shaped());
        assertEquals(40, water.column(0, 0).topY());
    }

    @Test
    public void columnCacheReusesGeometryAndAnchorNoise() {
        AtomicInteger samples = new AtomicInteger();
        Terrain3DRuntime runtime = runtime(profile(), (x, y, z) -> {
            samples.incrementAndGet();
            return StrictMath.sin(y * StrictMath.PI / 16D);
        });
        Terrain3DColumn first = runtime.column(0, 0);
        int initialSamples = samples.get();
        assertSame(first, runtime.column(0, 0));
        assertEquals(initialSamples, samples.get());
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                runtime.column(x, z);
            }
        }
        assertTrue(samples.get() <= 49 * 18);
        int samplesBeforeClear = samples.get();
        runtime.clear();
        assertEquals(first, runtime.column(0, 0));
        assertTrue(samples.get() > samplesBeforeClear);
    }

    @Test
    public void concurrentAndEvictedColumnsRemainReproducible() throws Exception {
        Terrain3DRuntime.NoiseSource noise = (x, y, z) -> StrictMath.sin((x + y + z) * StrictMath.PI / 16D);
        IrisBiome biome = new IrisBiome().setTerrain3D(profile());
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 96D, (x, z) -> biome),
                new Terrain3DRuntime.Options(12, 256, 0, null, true, 16),
                (style, seed) -> noise);
        Terrain3DRuntime reference = runtime(profile(), noise);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<Terrain3DColumn>> results = new ArrayList<>();
            for (int index = 0; index < 128; index++) {
                int x = index % 32 - 16;
                results.add(executor.submit(() -> runtime.column(x, 0)));
            }
            for (int index = 0; index < results.size(); index++) {
                assertEquals(reference.column(index % 32 - 16, 0), results.get(index).get(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void repeatedColumnsKeepCoordinateAndRuntimeGeometrySeparate() {
        Terrain3DRuntime first = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 96D + x * 0.25D + z * 0.5D, (x, z) -> null),
                options(true), (style, seed) -> (x, y, z) -> 0D);
        Terrain3DRuntime second = runtime(profile(), (x, y, z) -> 1D);

        assertEquals(96D, first.column(0, 0).baseHeight(), 0D);
        assertEquals(128, second.column(0, 0).topY());
        assertEquals(96, first.column(0, 0).topY());
        assertEquals(95.75D, first.column(-1, 0).baseHeight(), 0D);
        assertEquals(95.5D, first.column(0, -1).baseHeight(), 0D);
        assertEquals(95.5D, first.column(0, -1).baseHeight(), 0D);
        assertEquals(96D, first.column(0, 0).baseHeight(), 0D);
        assertEquals(128, second.column(0, 0).topY());
    }

    @Test
    public void clearingRuntimeRefreshesTheLastColumnOnItsCallingThread() {
        AtomicInteger height = new AtomicInteger(96);
        IrisBiome biome = new IrisBiome().setTerrain3D(profile());
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> height.get(), (x, z) -> biome),
                options(true), (style, seed) -> (x, y, z) -> 0D);

        assertEquals(96, runtime.column(0, 0).topY());
        height.set(120);
        runtime.clear();

        assertEquals(120, runtime.column(0, 0).topY());
        assertTrue(runtime.column(0, 0).isSolid(110));
    }

    @Test
    public void clearingRuntimeRefreshesLastColumnsOnOtherThreads() throws Exception {
        AtomicInteger height = new AtomicInteger(96);
        IrisBiome biome = new IrisBiome().setTerrain3D(profile());
        Terrain3DRuntime runtime = new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> height.get(), (x, z) -> biome),
                options(true), (style, seed) -> (x, y, z) -> 0D);
        CountDownLatch sampled = new CountDownLatch(2);
        CountDownLatch cleared = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Terrain3DColumn>> results = new ArrayList<>();
            for (int index = 0; index < 2; index++) {
                results.add(executor.submit(() -> {
                    Terrain3DColumn before = runtime.column(0, 0);
                    sampled.countDown();
                    assertTrue(cleared.await(10, TimeUnit.SECONDS));
                    assertEquals(96, before.topY());
                    return runtime.column(0, 0);
                }));
            }
            try {
                assertTrue(sampled.await(10, TimeUnit.SECONDS));
                height.set(120);
                runtime.clear();
            } finally {
                cleared.countDown();
            }
            for (Future<Terrain3DColumn> result : results) {
                assertEquals(120, result.get(10, TimeUnit.SECONDS).topY());
            }
        }
    }

    @Test
    public void invalidCurrentFieldsAndNonfiniteNoiseFailBeforeGeometryEscapes() {
        assertThrows(IllegalArgumentException.class,
                () -> runtime(profile().setAmplitude(129), (x, y, z) -> 0D).column(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> runtime(profile().setVerticalScale(Double.NaN), (x, y, z) -> 0D).column(0, 0));
        assertThrows(IllegalStateException.class,
                () -> runtime(profile(), (x, y, z) -> Double.NaN).column(0, 0));
    }

    private static Terrain3DRuntime runtime(IrisTerrain3D profile, Terrain3DRuntime.NoiseSource noise) {
        IrisBiome biome = new IrisBiome().setTerrain3D(profile);
        return new Terrain3DRuntime(
                new Terrain3DRuntime.Sources((x, z) -> 96D, (x, z) -> biome),
                options(true), (style, seed) -> noise);
    }

    private static Terrain3DRuntime.Options options(boolean enabled) {
        return new Terrain3DRuntime.Options(12, 256, 0, null, enabled, 4096);
    }

    private static IrisTerrain3D profile() {
        return new IrisTerrain3D().setAmplitude(32).setHorizontalScale(64).setVerticalScale(64)
                .setMinimumSlope(0).setFluidClearance(0).setFluidFade(1);
    }

    private static double interpolated(Terrain3DRuntime.NoiseSource noise, int x, int y, int z) {
        int startX = Math.floorDiv(x, 4) * 4;
        int startY = Math.floorDiv(y, 4) * 4;
        int startZ = Math.floorDiv(z, 4) * 4;
        double density = 0D;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    double weightX = dx == 0 ? 1D - (x - startX) / 4D : (x - startX) / 4D;
                    double weightY = dy == 0 ? 1D - (y - startY) / 4D : (y - startY) / 4D;
                    double weightZ = dz == 0 ? 1D - (z - startZ) / 4D : (z - startZ) / 4D;
                    density += weightX * weightY * weightZ
                            * noise.sample(startX + dx * 4, startY + dy * 4, startZ + dz * 4);
                }
            }
        }
        return density;
    }
}
