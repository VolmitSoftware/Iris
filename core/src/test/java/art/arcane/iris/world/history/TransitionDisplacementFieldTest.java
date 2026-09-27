package art.arcane.iris.world.history;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransitionDisplacementFieldTest extends GenerationHistorySupport {
    private static final BoundaryColumnGeometry.Voxel STONE = new BoundaryColumnGeometry.Voxel(
            "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
    private static final BoundaryColumnGeometry.Voxel AIR = new BoundaryColumnGeometry.Voxel(
            "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
    private static final BoundaryColumnGeometry.Voxel WATER = new BoundaryColumnGeometry.Voxel(
            "minecraft:water[level=0]", BoundaryColumnGeometry.Phase.FLUID, "minecraft:water[level=0]", false);

    @Test
    public void matchesFlatSeamAndReturnsToNativeTerrainInBothDirections() throws Exception {
        for (int historical : new int[]{24, 104}) {
            int nativeHeight = historical == 24 ? 104 : 24;
            TransitionDisplacementField field = field(historical, -1, (x, z) -> nativeHeight);
            assertEquals(historical, field.height(0, 8, nativeHeight), 0.0000001D);
            double previous = historical;
            for (int x = 1; x < 32; x++) {
                double height = field.height(x, 8, nativeHeight);
                assertTrue(historical < nativeHeight ? height >= previous - 0.0000001D : height <= previous + 0.0000001D);
                previous = height;
            }
            assertEquals(nativeHeight, field.height(31, 8, nativeHeight), 0D);
            assertEquals(nativeHeight, field.height(64, 8, nativeHeight), 0D);
            assertEquals(nativeHeight, field.height(-1, 8, nativeHeight), 0D);
        }
    }

    @Test
    public void preservesNewReliefAtFullAmplitudeInsideTheBand() throws Exception {
        TransitionDisplacementField field = field(72, -1, (x, z) -> 104D);
        for (int x = 0; x < 32; x++) {
            double relief = 4D * Math.sin(x / 2D);
            assertEquals(relief, field.height(x, 8, 104D + relief) - field.height(x, 8, 104D), 0.0000001D);
        }
    }

    @Test
    public void unchangedSurfaceDoesNotMoveAndChangedWaterHasNoMissingFluidSentinel() throws Exception {
        TransitionDisplacementField field = field(24, 40, (x, z) -> 24D);
        double previous = 40D;
        for (int x = 0; x < 32; x++) {
            assertEquals(24D, field.height(x, 8, 24D), 0D);
            double fluid = field.fluidHeight(x, 8, 48D);
            assertTrue(fluid >= previous - 0.0000001D);
            assertTrue(fluid >= 40D && fluid <= 48D);
            previous = fluid;
        }
        assertEquals(40D, field.fluidHeight(0, 8, 48D), 0.0000001D);
        assertEquals(48D, field.fluidHeight(31, 8, 48D), 0D);
        assertEquals("minecraft:water[level=0]", field.fluidStateKey(0, 8));
        assertNull(field.fluidStateKey(31, 8));
    }

    @Test
    public void dryHighGroundDoesNotRaiseTheReplacementSeaLevel() throws Exception {
        TransitionDisplacementField field = field(104, -1, (x, z) -> 24D);
        for (int x = 0; x < 32; x++) {
            assertEquals(40D, field.fluidHeight(x, 8, 40D), 0.0000001D);
            assertNull(field.fluidStateKey(x, 8));
        }
    }

    @Test
    public void coldConcurrentQueriesShareTheSameFieldAndWarmQueriesDoNotResampleAnchors() throws Exception {
        AtomicInteger samples = new AtomicInteger();
        TransitionDisplacementField field = field(72, -1, (x, z) -> {
            samples.incrementAndGet();
            return 104D;
        });
        ExecutorService workers = Executors.newFixedThreadPool(4);
        CyclicBarrier start = new CyclicBarrier(4);
        List<Future<Double>> heights = new ArrayList<>();
        try {
            for (int index = 0; index < 4; index++) {
                heights.add(workers.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return field.height(0, 8, 104D);
                }));
            }
            for (Future<Double> height : heights) {
                assertEquals(72D, height.get(5, TimeUnit.SECONDS), 0.0000001D);
            }
            int coldSamples = samples.get();
            assertTrue(coldSamples > 0);
            for (int x = 0; x < 16; x++) {
                field.height(x, 8, 104D);
            }
            assertEquals(coldSamples, samples.get());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private TransitionDisplacementField field(int ground, int fluid, TransitionDisplacementField.HeightSource source)
            throws Exception {
        GenerationBoundary boundary = GenerationBoundary.freeze("boundary", List.of(
                new GenerationBoundary.ChunkCoordinate(-1, 0)));
        SavedTerrainChunk terrain = SavedTerrainChunk.captureBoundary(-1, 0, 0, 128, "minecraft:noise",
                new SavedTerrainChunk.VoxelSource() {
                    @Override
                    public BoundaryColumnGeometry.Voxel voxel(int x, int y, int z) {
                        return y <= ground ? STONE : y <= fluid ? WATER : AIR;
                    }

                    @Override
                    public String biome(int x, int y, int z) {
                        return "minecraft:plains";
                    }
                });
        TerrainBoundarySignatureStore store = new TerrainBoundarySignatureStore(temporaryFolder.newFolder().toPath());
        TerrainBoundarySignatureStore.Snapshot snapshot = store.publish(2L, boundary, terrain::column);
        TransitionGenerationPlan plan = new TransitionGenerationPlan(new TransitionGenerationPlan.Specification(
                2L, "old", "new", GenerationTransition.CURRENT_ALGORITHM_VERSION, 32, boundary.identity(), snapshot.identity()),
                boundary, snapshot);
        return new TransitionDisplacementField(plan, new TransitionDisplacementField.Sources(source, 128));
    }
}
