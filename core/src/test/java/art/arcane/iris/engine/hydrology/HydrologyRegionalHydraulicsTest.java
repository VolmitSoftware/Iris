package art.arcane.iris.engine.hydrology;

import art.arcane.iris.engine.hydrology.policy.SurfaceRiverPolicy;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class HydrologyRegionalHydraulicsTest {
    @Test
    public void inletAllowanceLeavesTheOrdinaryChannelLimitUnchanged() {
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(
                HydrologyRegionalPlannerTest.settings(false, 1));
        HydrologyTerrainSample terrain = HydrologyTerrainSample.openLand(100, 0D, "land");

        assertEquals(86, hydraulics.minimumHead(terrain));
        assertEquals(70, hydraulics.inletMinimumHead(terrain));
        assertEquals(86, hydraulics.minimumHead(terrain));
        assertEquals(63, hydraulics.inletMinimumHead(HydrologyTerrainSample.openLand(65, 0D, "land")));
    }

    @Test
    public void inletAllowanceRetainsDepthAndIncisionMultipliers() {
        HydrologyRegionalHydraulics hydraulics = new HydrologyRegionalHydraulics(
                HydrologyRegionalPlannerTest.settings(false, 1));
        HydrologyTerrainSample terrain = terrain(10, 2D, 0.5D);

        assertEquals(119, hydraulics.minimumHead(terrain));
        assertEquals(108, hydraulics.inletMinimumHead(terrain));
    }

    private static HydrologyTerrainSample terrain(int incision, double depthMultiplier, double incisionMultiplier) {
        return new HydrologyTerrainSample(120, 0D, false, false, 80, 82,
                true, true, true, false, false, false, 0D, 1D, 1D, 1D, depthMultiplier, incisionMultiplier, 1D, 1D,
                "land", "land", "land", "land", "land", "land", List.of("default"), List.of(),
                Double.NaN, null, Double.NaN, true,
                new SurfaceRiverPolicy("limited", null, null, null, null, null, null, incision));
    }
}
