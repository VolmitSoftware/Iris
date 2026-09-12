package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HydrologyOutletBoundaryPolicyTest {
    @Test
    public void aCoarseOceanMatchCannotHideAMismatchedExactShoreOrReceivingCell() {
        for (int mismatchedX : new int[] {127, 128}) {
            HydrologyTerrainSampler terrain = (x, z) -> sample(x >= 128, x == mismatchedX ? "other" : "area");
            HydrologyPlanner planner = new HydrologyPlanner(1L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
            HydrologySampledGrid grid = grid(terrain);
            assertTrue(grid.node(0).terrain().drainsInto(grid.node(1).terrain()));
            assertTrue(planner.outletPlanner.oceanOutletCandidates(grid, true).isEmpty());
        }
    }

    @Test
    public void aMatchingExactBoundaryKeepsItsReceivingOceanCell() {
        HydrologyTerrainSampler terrain = (x, z) -> sample(x >= 128, "area");
        HydrologyPlanner planner = new HydrologyPlanner(1L, HydrologyRegionalPlannerTest.settings(false, 1), terrain);
        List<OutletCandidate> outlets = planner.outletPlanner.oceanOutletCandidates(grid(terrain), true);

        assertFalse(outlets.isEmpty());
        for (OutletCandidate candidate : outlets) {
            assertEquals(127, candidate.outlet().landwardPoint().x());
            assertEquals(128, candidate.outlet().connectionPoint().x());
            assertTrue(terrain.sample(candidate.outlet().connectionPoint().x(), candidate.outlet().connectionPoint().z()).ocean());
        }
    }

    @Test
    public void aCoastalChannelRequiresTheOriginOceanToEnterTheLandArea() {
        HydrologyTerrainSampler terrain = (x, z) -> sample(x >= 1, x >= 1 ? "sea" : null);
        HydrologyPlanner planner = new HydrologyPlanner(1L, HydrologyRegionalPlannerTest.settings(true, 1), terrain);
        RiverOutlet outlet = new RiverOutlet(1L, HydrologyFeatureType.MOUTH, 2L,
                new HydrologyPoint(0, 63, 0), new HydrologyPoint(1, 63, 0), 63, true);

        assertTrue(terrain.sample(0, 0).drainsInto(terrain.sample(1, 0)));
        assertFalse(planner.regional.bidirectionalMouth(outlet));
        HydrologyTerrainSampler matching = (x, z) -> sample(x >= 1, "sea");
        HydrologyPlanner accepted = new HydrologyPlanner(1L, HydrologyRegionalPlannerTest.settings(true, 1), matching);
        assertTrue(accepted.regional.bidirectionalMouth(outlet));
    }

    private static HydrologySampledGrid grid(HydrologyTerrainSampler terrain) {
        ArrayList<HydrologyGridNode> nodes = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            int x = index % 2 * 256;
            int z = index / 2 * 256;
            nodes.add(new HydrologyGridNode(index, index % 2, index / 2, x, z, index + 1L, terrain.sample(x, z)));
        }
        return new HydrologySampledGrid(0, 0, 0, 0, 512, 2, 256, nodes);
    }

    private static HydrologyTerrainSample sample(boolean ocean, String confines) {
        int height = ocean ? 60 : 66;
        return new HydrologyTerrainSample(height, 0D, ocean, false, height - 32, height - 30,
                !ocean, !ocean, !ocean, false, false, false,
                0D, ocean ? 0D : 1D, 0D, 1D, 1D, 1D, 1D, 1D,
                "land", "land", "land", "land", "land", "land", List.of("default"), List.of(),
                Double.NaN, confines, Double.NaN, true, SurfaceRiverPolicy.INHERIT);
    }
}
