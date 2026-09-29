package art.arcane.iris.generation.hydrology;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HydrologyChunkOccupancyTest {
    @Test
    public void occupiesExactlyTheChunksHoldingFootprintColumnsAtSignedCoordinates() {
        int[][] columns = {{0, 0}, {15, 15}, {-1, -1}, {-17, 40}, {1000, -2000}, {-4097, 4096}};
        HydrologyChunkOccupancy occupancy = HydrologyChunkOccupancy.of(footprint(columns));
        HashSet<Long> expected = new HashSet<>();
        for (int[] column : columns) {
            expected.add(RiverFootprint.pack(Math.floorDiv(column[0], 16), Math.floorDiv(column[1], 16)));
        }
        int occupied = 0;
        for (int chunkZ = -300; chunkZ <= 300; chunkZ++) {
            for (int chunkX = -300; chunkX <= 300; chunkX++) {
                boolean holds = occupancy.occupies(chunkX, chunkZ);
                assertEquals(expected.contains(RiverFootprint.pack(chunkX, chunkZ)), holds);
                occupied += holds ? 1 : 0;
            }
        }
        assertEquals(expected.size(), occupied);
        assertFalse(occupancy.occupies(Integer.MAX_VALUE >> 4, Integer.MIN_VALUE >> 4));
    }

    @Test
    public void footprintsWithoutColumnsOccupyNothing() {
        assertSame(HydrologyChunkOccupancy.EMPTY, HydrologyChunkOccupancy.of(RiverFootprint.empty()));
        assertSame(HydrologyChunkOccupancy.EMPTY, HydrologyChunkOccupancy.of(null));
        assertFalse(HydrologyChunkOccupancy.EMPTY.occupies(0, 0));
        assertTrue(HydrologyChunkOccupancy.of(footprint(new int[][]{{3, 4}})).occupies(0, 0));
    }

    private static RiverFootprint footprint(int[][] columns) {
        Map<Long, HydrologyColumnSample> samples = new HashMap<>();
        for (int[] column : columns) {
            samples.put(RiverFootprint.pack(column[0], column[1]),
                    new HydrologyColumnSample(column[0], column[1], 80, 63, false, "parent", List.of()));
        }
        return new RiverFootprint(samples);
    }
}
