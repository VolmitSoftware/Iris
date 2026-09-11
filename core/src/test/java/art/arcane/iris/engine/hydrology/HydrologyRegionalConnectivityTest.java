package art.arcane.iris.engine.hydrology;

import org.junit.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HydrologyRegionalConnectivityTest {
    private static final HydrologyPlannerSettings SETTINGS = HydrologyPlannerSettings.defaults();
    @Test
    public void stackedWaterRequiresTheActualFallingIntervalToConnect() {
        HydrologyRegionalConnectivity water = new HydrologyRegionalConnectivity();
        water.addFluidColumn(0, 0, 67, 70);
        water.addFluidColumn(1, 0, 67, 70);
        water.addFluidColumn(1, 0, 60, 63);
        water.addFluidColumn(2, 0, 60, 63);
        assertFalse(water.connected(course(), ocean(), SETTINGS));
        water.addFluidColumn(1, 0, 62, 68);
        assertTrue(water.connected(course(), ocean(), SETTINGS));
    }

    @Test
    public void anOceanBiomeAtSeaLevelIsNotAReceivingWaterColumn() {
        HydrologyRegionalConnectivity water = new HydrologyRegionalConnectivity();
        for (int x = 0; x < 3; x++) {
            water.addFluidColumn(x, 0, 60, 63);
        }
        assertFalse(water.connected(course(), (x, z) -> HydrologyTerrainSample.ocean(63, "ocean"), SETTINGS));
        assertTrue(water.connected(course(), ocean(), SETTINGS));
    }

    @Test
    public void adjacentColumnsWithNoSharedWetHeightCannotConnect() {
        HydrologyRegionalConnectivity water = new HydrologyRegionalConnectivity();
        water.addFluidColumn(0, 0, 63, 66);
        water.addFluidColumn(1, 0, 60, 63);
        water.addFluidColumn(2, 0, 60, 63);
        assertFalse(water.connected(course(), ocean(), SETTINGS));
    }

    @Test
    public void aFloodedCoastalTailConnectsOwnedWaterToItsKnownOceanEndpoint() {
        HydrologyRegionalConnectivity water = flatChannel();
        HydrologyTerrainSampler sampler = floodedCoasts();

        assertTrue(water.connected(extendedCourse(false), sampler, SETTINGS));
        assertFalse(sampler.receivingWater(3, 0, 63));
    }

    @Test
    public void aDrySillInTheFloodedTailBreaksItsOceanConnection() {
        HydrologyTerrainSampler sampler = (x, z) -> x == 6
                ? HydrologyTerrainSample.openLand(63, 0D, "coast") : floodedCoasts().sample(x, z);

        assertFalse(flatChannel().connected(extendedCourse(false), sampler, SETTINGS));
    }

    @Test
    public void seaToSeaChannelsMustReachBothDeclaredCoasts() {
        HydrologyTerrainSampler sampler = floodedCoasts();
        HydrologyTerrainSampler blocked = (x, z) -> x == -4
                ? HydrologyTerrainSample.openLand(63, 0D, "coast") : sampler.sample(x, z);

        assertTrue(flatChannel().connected(extendedCourse(true), sampler, SETTINGS));
        assertFalse(flatChannel().connected(extendedCourse(true), blocked, SETTINGS));
    }

    @Test
    public void anEarlierOceanPocketCannotStandInForTheDeclaredReceivingOcean() {
        HydrologyTerrainSampler sampler = (x, z) -> x >= 3 && x != 6
                ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(72, 0D, "land");

        assertFalse(flatChannel().connected(extendedCourse(false), sampler, SETTINGS));
    }

    @Test
    public void diagonalOceanStationsNeedCardinalWetBridges() {
        HydraulicSegment channel = course().segments().getFirst();
        HydraulicSegment mouth = mouth(2L, new HydrologyPoint(2, 63, 0), new HydrologyPoint(5, 63, 3));
        RiverCourse course = new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "water", 1, List.of(), List.of(channel, mouth));
        HydrologyTerrainSampler sampler = (x, z) -> x >= 3 && x <= 5 && z == x - 2
                ? HydrologyTerrainSample.ocean(60, "ocean") : HydrologyTerrainSample.openLand(72, 0D, "land");
        HydrologyTerrainSampler bridged = (x, z) -> x >= 3 && x <= 5 && z == x - 3
                ? HydrologyTerrainSample.ocean(60, "ocean") : sampler.sample(x, z);

        assertFalse(flatChannel().connected(course, sampler, SETTINGS));
        assertTrue(flatChannel().connected(course, bridged, SETTINGS));
    }

    private static HydrologyRegionalConnectivity flatChannel() {
        HydrologyRegionalConnectivity water = new HydrologyRegionalConnectivity();
        for (int x = 0; x < 3; x++) {
            water.addFluidColumn(x, 0, 60, 63);
        }
        return water;
    }

    private static HydrologyTerrainSampler floodedCoasts() {
        return (x, z) -> x <= -8 || x >= 8 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(x < 0 || x > 2 ? 60 : 72, 0D, "coast");
    }

    private static RiverCourse extendedCourse(boolean twoCoasts) {
        HydraulicSegment channel = course().segments().getFirst();
        HydraulicSegment outflow = mouth(2L, new HydrologyPoint(2, 63, 0), new HydrologyPoint(8, 63, 0));
        List<HydraulicSegment> segments = twoCoasts ? List.of(
                mouth(6L, new HydrologyPoint(-8, 63, 0), new HydrologyPoint(0, 63, 0)), channel, outflow)
                : List.of(channel, outflow);
        return new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "water", 1, List.of(), segments);
    }

    private static HydraulicSegment mouth(long id, HydrologyPoint start, HydrologyPoint end) {
        return new HydraulicSegment(id, 1L, HydrologyFeatureType.MOUTH, 63, 63,
                4, 3, false, false, List.of(start, end), HydraulicChannelProfile.uniform(4, 3));
    }

    private static HydrologyTerrainSampler ocean() {
        return (x, z) -> x >= 3 ? HydrologyTerrainSample.ocean(60, "ocean")
                : HydrologyTerrainSample.openLand(72, 0D, "land");
    }

    private static RiverCourse course() {
        HydraulicSegment channel = new HydraulicSegment(5L, 1L, HydrologyFeatureType.SURFACE_POOL, 63, 63,
                4, 3, false, false, List.of(new HydrologyPoint(0, 63, 0), new HydrologyPoint(2, 63, 0)),
                HydraulicChannelProfile.uniform(4, 3));
        HydraulicSegment segment = new HydraulicSegment(2L, 1L, HydrologyFeatureType.MOUTH, 63, 63,
                4, 3, false, false, List.of(new HydrologyPoint(2, 63, 0), new HydrologyPoint(3, 63, 0)),
                HydraulicChannelProfile.uniform(4, 3));
        return new RiverCourse(1L, RiverCourseType.SURFACE, OptionalLong.of(3L), OptionalLong.of(4L),
                "water", 1, List.of(), List.of(channel, segment));
    }
}
