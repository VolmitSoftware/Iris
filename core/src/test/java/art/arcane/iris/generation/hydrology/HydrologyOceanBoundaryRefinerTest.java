package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HydrologyOceanBoundaryRefinerTest {
    @Test
    public void firstOceanUsesOneFullSampleAtTheImmediatelyPreviousLandPoint() {
        List<HydrologyPoint> probes = new ArrayList<>();
        List<HydrologyPoint> detailSamples = new ArrayList<>();
        List<HydrologyPoint> crossing = crossing(0, 4, -7);
        HydrologyTerrainSample land = HydrologyTerrainSample.openLand(91, 5D, "parent");
        HydrologyRoutingTerrainSampler routingSampler = routingSampler(
                probes,
                3,
                HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN
        );
        HydrologyTerrainSampler terrainSampler = (int x, int z) -> {
            detailSamples.add(new HydrologyPoint(x, 0, z));
            return land;
        };

        HydrologyOceanBoundaryRefiner.Result result = HydrologyOceanBoundaryRefiner.refine(
                crossing,
                terrainSampler,
                routingSampler,
                63
        );

        assertEquals(List.of(point(1, -7), point(2, -7), point(3, -7)), probes);
        assertEquals(List.of(point(2, -7)), detailSamples);
        assertEquals(new HydrologyPoint(2, 90, -7), result.landwardPoint());
        assertEquals(new HydrologyPoint(3, 90, -7), result.oceanPoint());
        assertEquals(land, result.landwardTerrain());
    }

    @Test
    public void immediateOceanResamplesTheStartingLandPoint() {
        List<HydrologyPoint> detailSamples = new ArrayList<>();
        HydrologyTerrainSample land = HydrologyTerrainSample.openLand(88, 1D, "parent");
        HydrologyTerrainSampler terrainSampler = (int x, int z) -> {
            detailSamples.add(new HydrologyPoint(x, 0, z));
            return land;
        };

        HydrologyOceanBoundaryRefiner.Result result = HydrologyOceanBoundaryRefiner.refine(
                crossing(-2, 1, 9),
                terrainSampler,
                routingSampler(new ArrayList<>(), -1, HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN),
                63
        );

        assertEquals(List.of(point(-2, 9)), detailSamples);
        assertEquals(new HydrologyPoint(-2, 90, 9), result.landwardPoint());
        assertEquals(new HydrologyPoint(-1, 90, 9), result.oceanPoint());
    }

    @Test
    public void unavailableClassificationAbortsWithoutFullTerrainSampling() {
        List<HydrologyPoint> detailSamples = new ArrayList<>();
        HydrologyTerrainSampler terrainSampler = (int x, int z) -> {
            detailSamples.add(new HydrologyPoint(x, 0, z));
            return HydrologyTerrainSample.openLand(90, 0D, "parent");
        };

        HydrologyOceanBoundaryRefiner.Result result = HydrologyOceanBoundaryRefiner.refine(
                crossing(0, 3, 0),
                terrainSampler,
                routingSampler(new ArrayList<>(), 2, HydrologyRoutingTerrainSampler.NaturalClassification.UNAVAILABLE),
                63
        );

        assertNull(result);
        assertEquals(List.of(), detailSamples);
    }

    @Test
    public void noOceanDoesNotRequestFullTerrain() {
        List<HydrologyPoint> detailSamples = new ArrayList<>();
        HydrologyTerrainSampler terrainSampler = (int x, int z) -> {
            detailSamples.add(new HydrologyPoint(x, 0, z));
            return HydrologyTerrainSample.openLand(90, 0D, "parent");
        };

        HydrologyOceanBoundaryRefiner.Result result = HydrologyOceanBoundaryRefiner.refine(
                crossing(-3, 0, 2),
                terrainSampler,
                routingSampler(new ArrayList<>(), Integer.MAX_VALUE, HydrologyRoutingTerrainSampler.NaturalClassification.LAND),
                63
        );

        assertNull(result);
        assertEquals(List.of(), detailSamples);
    }

    @Test
    public void floodedCoastRetreatsToPhysicalShoreAndKeepsKnownOceanEndpoint() {
        HydrologyTerrainSampler terrain = (x, z) -> HydrologyTerrainSample.openLand(x < 2 ? 306 : 282, 0D, "coast");
        HydrologyOceanBoundaryRefiner.Result result = HydrologyOceanBoundaryRefiner.refine(
                crossing(0, 6, 0), terrain,
                routingSampler(new ArrayList<>(), 5, HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN), 306);

        assertEquals(1, result.landwardPoint().x());
        assertEquals(306, result.landwardTerrain().naturalHeight());
        assertEquals(5, result.oceanPoint().x());
    }

    @Test
    public void aCompletelySubmergedCoastEdgeHasNoLandwardEndpoint() {
        assertNull(HydrologyOceanBoundaryRefiner.refine(crossing(0, 5, 0),
                (x, z) -> HydrologyTerrainSample.openLand(282, 0D, "flooded"),
                routingSampler(new ArrayList<>(), 5, HydrologyRoutingTerrainSampler.NaturalClassification.OCEAN), 306));
    }

    private HydrologyRoutingTerrainSampler routingSampler(
            List<HydrologyPoint> probes,
            int classifiedX,
            HydrologyRoutingTerrainSampler.NaturalClassification classification
    ) {
        return new HydrologyRoutingTerrainSampler() {
            @Override
            public HydrologyTerrainSample[] sampleGrid(GridRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NaturalClassification classifyNatural(int blockX, int blockZ) {
                probes.add(new HydrologyPoint(blockX, 0, blockZ));
                return blockX == classifiedX ? classification : NaturalClassification.LAND;
            }
        };
    }

    private List<HydrologyPoint> crossing(int minimumX, int maximumX, int z) {
        ArrayList<HydrologyPoint> crossing = new ArrayList<>();
        for (int x = minimumX; x <= maximumX; x++) {
            crossing.add(new HydrologyPoint(x, 90, z));
        }
        return List.copyOf(crossing);
    }

    private HydrologyPoint point(int x, int z) {
        return new HydrologyPoint(x, 0, z);
    }
}
