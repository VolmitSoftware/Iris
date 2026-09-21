package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class HydrologyPlannerRoutingSamplerTest {
    @Test
    public void coarseLatticeUsesOnlyTheRoutingBatch() {
        AtomicInteger batchCalls = new AtomicInteger();
        AtomicInteger detailCalls = new AtomicInteger();
        HydrologyTerrainSampler detailSampler = (int x, int z) -> {
            detailCalls.incrementAndGet();
            return HydrologyTerrainSample.ocean(62, "ocean");
        };
        HydrologyRoutingTerrainSampler routingSampler = new HydrologyRoutingTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                batchCalls.incrementAndGet();
                int width = request.width();
                HydrologyTerrainSample[] samples = new HydrologyTerrainSample[width * width];
                Arrays.fill(samples, HydrologyTerrainSample.ocean(62, "ocean"));
                return samples;
            }

            @Override
            public NaturalClassification classifyNatural(int blockX, int blockZ) {
                return NaturalClassification.OCEAN;
            }
        };
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(
                17L,
                settings,
                detailSampler,
                routingSampler,
                HydrologyGeometrySampler.deterministic(detailSampler),
                -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(detailSampler, settings.seaLevel(), -4096, 4096)
        );

        planner.plan(new HydrologyTileKey(-1, 1));

        assertEquals(1, batchCalls.get());
        assertEquals(0, detailCalls.get());
    }

    @Test
    public void malformedRoutingBatchIsRejected() {
        HydrologyTerrainSampler detailSampler = (int x, int z) -> HydrologyTerrainSample.ocean(62, "ocean");
        HydrologyRoutingTerrainSampler routingSampler = new HydrologyRoutingTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                int width = request.width();
                return new HydrologyTerrainSample[width * width - 1];
            }

            @Override
            public NaturalClassification classifyNatural(int blockX, int blockZ) {
                return NaturalClassification.OCEAN;
            }
        };
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(
                19L,
                settings,
                detailSampler,
                routingSampler,
                HydrologyGeometrySampler.deterministic(detailSampler),
                -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(detailSampler, settings.seaLevel(), -4096, 4096)
        );

        assertThrows(IllegalStateException.class, () -> planner.plan(new HydrologyTileKey(0, 0)));
    }

    @Test
    public void landHeightMemoizationRetainsMissingSamplesOnlyWithinThePlanningScope() {
        AtomicInteger heightCalls = new AtomicInteger();
        AtomicInteger failedCalls = new AtomicInteger();
        HydrologyNaturalTerrainSampler natural = new HydrologyNaturalTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                throw new AssertionError("Bank heights do not need a routing grid");
            }

            @Override
            public NaturalClassification classifyNatural(int x, int z) {
                throw new AssertionError("Bank height provider already classifies land");
            }

            @Override
            public HydrologyTerrainSample sampleBasis(int x, int z) {
                return switch (x) {
                    case 10 -> HydrologyTerrainSample.openLand(87, 0D, "land");
                    case 11 -> HydrologyTerrainSample.ocean(60, "ocean");
                    default -> null;
                };
            }

            @Override
            public double sampleLandHeight(int x, int z) {
                heightCalls.incrementAndGet();
                if (x == 4 && failedCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("Transient height failure");
                }
                return switch (x) {
                    case 0 -> Double.NaN;
                    case 1 -> 0D;
                    case 2 -> Integer.MIN_VALUE;
                    default -> 83D;
                };
            }
        };
        HydrologyTerrainSampler detail = (x, z) -> HydrologyTerrainSample.openLand(90, 0D, "land");
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(23L, settings, detail, natural,
                HydrologyGeometrySampler.deterministic(detail), -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(detail, settings.seaLevel(), -4096, 4096));
        planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());
        try {
            for (int pass = 0; pass < 3; pass++) {
                assertEquals(Double.NaN, planner.sampleLandHeight(0, -17), 0D);
                assertEquals(0D, planner.sampleLandHeight(1, -17), 0D);
                assertEquals(Integer.MIN_VALUE, planner.sampleLandHeight(2, -17), 0D);
                assertEquals(83D, planner.sampleLandHeight(3, -17), 0D);
            }
            assertEquals(4, heightCalls.get());
            planner.sampleBasis(10, -17);
            planner.sampleBasisWithoutSlope(11, -17);
            planner.sampleBasisWithoutSlope(12, -17);
            assertEquals(87D, planner.sampleLandHeight(10, -17), 0D);
            assertEquals(Double.NaN, planner.sampleLandHeight(11, -17), 0D);
            assertEquals(Double.NaN, planner.sampleLandHeight(12, -17), 0D);
            assertEquals(4, heightCalls.get());
            assertThrows(IllegalStateException.class, () -> planner.sampleLandHeight(4, -17));
            assertEquals(83D, planner.sampleLandHeight(4, -17), 0D);
            assertEquals(83D, planner.sampleLandHeight(4, -17), 0D);
            assertEquals(6, heightCalls.get());
            HydrologyLandHeightCache shared = planner.planningSamples.get().fallbackLandHeights();
            for (int trial = 0; trial < 3; trial++) {
                planner.planningSamples.set(new HydrologyPlanner.PlanningSamples(shared));
                assertEquals(Double.NaN, planner.sampleLandHeight(0, -17), 0D);
                assertEquals(0D, planner.sampleLandHeight(1, -17), 0D);
                assertEquals(Integer.MIN_VALUE, planner.sampleLandHeight(2, -17), 0D);
                assertEquals(83D, planner.sampleLandHeight(3, -17), 0D);
                assertEquals(83D, planner.sampleLandHeight(4, -17), 0D);
            }
            assertEquals(6, heightCalls.get());
            planner.planningSamples.set(new HydrologyPlanner.PlanningSamples());
            assertEquals(Double.NaN, planner.sampleLandHeight(0, -17), 0D);
            assertEquals(7, heightCalls.get());
        } finally {
            planner.planningSamples.remove();
        }
        assertEquals(Double.NaN, planner.sampleLandHeight(0, -17), 0D);
        assertEquals(Double.NaN, planner.sampleLandHeight(0, -17), 0D);
        assertEquals(9, heightCalls.get());
    }

    @Test
    public void oceanClassificationBypassesNaturalBasisSampling() {
        AtomicInteger basisCalls = new AtomicInteger();
        HydrologyTerrainSampler detailSampler = (int x, int z) -> HydrologyTerrainSample.openLand(96, 0D, "land");
        HydrologyNaturalTerrainSampler routingSampler = new HydrologyNaturalTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                int width = request.width();
                HydrologyTerrainSample[] samples = new HydrologyTerrainSample[width * width];
                Arrays.fill(samples, HydrologyTerrainSample.openLand(96, 0D, "land"));
                return samples;
            }

            @Override
            public NaturalClassification classifyNatural(int blockX, int blockZ) {
                return NaturalClassification.OCEAN;
            }

            @Override
            public HydrologyTerrainSample sampleBasis(int blockX, int blockZ) {
                basisCalls.incrementAndGet();
                throw new IllegalStateException("Ocean basis must not be sampled");
            }
        };
        HydrologyPlannerSettings settings = HydrologyPlannerSettings.defaults();
        HydrologyPlanner planner = new HydrologyPlanner(
                23L,
                settings,
                detailSampler,
                routingSampler,
                HydrologyGeometrySampler.deterministic(detailSampler),
                -4096,
                footprint -> new HydrologyTerrainCaveVoxelView(detailSampler, settings.seaLevel(), -4096, 4096)
        );

        planner.plan(new HydrologyTileKey(0, 0));

        assertEquals(0, basisCalls.get());
    }
}
