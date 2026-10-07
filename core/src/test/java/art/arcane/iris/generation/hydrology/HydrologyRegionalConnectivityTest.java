package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void separatedIntervalsCanConnectAroundAnAdjacentWaterfall() {
        HydrologyRegionalConnectivity water = flatChannel();
        water.addFluidColumn(1, 0, 67, 70);
        water.addFluidColumn(1, 1, 60, 70);
        assertTrue(water.connected(course(), ocean(), SETTINGS));
    }

    @Test
    public void anIsolatedUpperIntervalStillRejectsTheCourse() {
        HydrologyRegionalConnectivity water = flatChannel();
        water.addFluidColumn(1, 0, 67, 70);
        water.addFluidColumn(1, 1, 60, 63);
        assertFalse(water.connected(course(), ocean(), SETTINGS));
    }

    @Test
    public void aHigherReceivingIntervalCanJoinTheOceanAboveAnotherInterval() {
        HydrologyRegionalConnectivity water = flatChannel();
        water.addFluidColumn(2, 0, 40, 45);
        water.addFluidColumn(2, 1, 40, 63);
        assertTrue(water.connected(course(), ocean(), SETTINGS));
    }

    @Test
    public void intervalConnectivityMatchesVoxelFloodFillRegardlessOfInsertionOrder() {
        Random random = new Random(438761L);
        int connected = 0;
        int disconnected = 0;
        for (int trial = 0; trial < 250; trial++) {
            ArrayList<int[]> intervals = new ArrayList<>();
            boolean[][][] voxels = new boolean[3][3][13];
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    intervals.add(new int[]{x, z, 60, 63});
                    if (x == 2 && z == 0) {
                        continue;
                    }
                    if (random.nextBoolean()) {
                        int bed = 64 + random.nextInt(3);
                        intervals.add(new int[]{x, z, bed, bed + 1 + random.nextInt(3)});
                    }
                    if (random.nextBoolean()) {
                        intervals.add(new int[]{x, z, 60, 68 + random.nextInt(3)});
                    }
                }
            }
            for (int[] interval : intervals) {
                for (int y = interval[2] + 1; y <= interval[3]; y++) {
                    voxels[interval[0]][interval[1]][y - 58] = true;
                }
            }
            boolean expected = voxelConnected(voxels);
            if (expected) {
                connected++;
            } else {
                disconnected++;
            }
            for (int order = 0; order < 2; order++) {
                Collections.shuffle(intervals, random);
                HydrologyRegionalConnectivity water = new HydrologyRegionalConnectivity();
                for (int[] interval : intervals) {
                    water.addFluidColumn(interval[0], interval[1], interval[2], interval[3]);
                }
                assertEquals("Trial " + trial + ", order " + order, expected,
                        water.connected(course(), ocean(), SETTINGS));
            }
        }
        assertTrue(connected > 0);
        assertTrue(disconnected > 0);
    }

    private static boolean voxelConnected(boolean[][][] voxels) {
        int height = voxels[0][0].length;
        int[] queue = new int[3 * 3 * height];
        boolean[] visited = new boolean[queue.length];
        int total = 0;
        int first = -1;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 0; y < height; y++) {
                    if (voxels[x][z][y]) {
                        total++;
                        first = (x * 3 + z) * height + y;
                    }
                }
            }
        }
        if (first < 0) {
            return false;
        }
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        int read = 0;
        int write = 1;
        queue[0] = first;
        visited[first] = true;
        while (read < write) {
            int current = queue[read++];
            int y = current % height;
            int z = current / height % 3;
            int x = current / height / 3;
            for (int[] offset : offsets) {
                int nextX = x + offset[0];
                int nextZ = z + offset[1];
                int nextY = y + offset[2];
                if (nextX < 0 || nextX >= 3 || nextZ < 0 || nextZ >= 3 || nextY < 0 || nextY >= height
                        || !voxels[nextX][nextZ][nextY]) {
                    continue;
                }
                int next = (nextX * 3 + nextZ) * height + nextY;
                if (!visited[next]) {
                    visited[next] = true;
                    queue[write++] = next;
                }
            }
        }
        return write == total;
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
