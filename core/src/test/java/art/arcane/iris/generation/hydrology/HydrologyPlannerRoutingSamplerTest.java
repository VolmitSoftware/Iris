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
