package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.raster.ChunkRasterizer;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PlanKeyTest {
    @Test
    public void containingFloorsNegativeCoordinates() {
        assertEquals(new WindowKey(-1, 0), WindowKey.containing(-1, 0));
        assertEquals(new RegionKey(0, 0), WindowKey.containing(511, 512).region());
        assertEquals(new WindowKey(0, 1), WindowKey.containing(511, 512));
        assertEquals(new WindowKey(-1, -2), WindowKey.containing(-512, -513));
        assertEquals(new RegionKey(-1, -1), RegionKey.containing(-1, -4096));
        assertEquals(new RegionKey(-1, -2), RegionKey.containing(-4096, -4097));
        assertEquals(new RegionKey(-1, 0), WindowKey.containing(-1, 4095).region());
        assertEquals(new RegionKey(-1, 1), new WindowKey(-8, 8).region());
        assertEquals(new RegionKey(-2, 0), new WindowKey(-9, 7).region());
    }

    @Test
    public void windowRegionMatchesRegionContainingAnyBlock() {
        Random random = new Random(1337L);
        for (int sample = 0; sample < 10_000; sample++) {
            int blockX = random.nextInt(60_000_000) - 30_000_000;
            int blockZ = random.nextInt(60_000_000) - 30_000_000;
            assertEquals(RegionKey.containing(blockX, blockZ), WindowKey.containing(blockX, blockZ).region());
        }
    }

    @Test
    public void regionCoverIsSharedByWindowsAndChunks() {
        assertEquals(List.of(new RegionKey(-1, 0), new RegionKey(0, 0), new RegionKey(-1, 1), new RegionKey(0, 1)),
                RegionKey.covering(-1, 0, 0, 4096));
        assertEquals(RegionKey.covering(-RegionKey.LAKE_REACH, -RegionKey.LAKE_REACH, 15 + RegionKey.LAKE_REACH, 15 + RegionKey.LAKE_REACH),
                RegionKey.lakeOwners(0, 0, 15, 15));
        assertEquals(2304, RegionKey.LAKE_REACH);
        Random random = new Random(1337L);
        for (int sample = 0; sample < 2_000; sample++) {
            int chunkX = random.nextInt(200_000) - 100_000;
            int chunkZ = random.nextInt(200_000) - 100_000;
            List<RegionKey> chunk = ChunkRasterizer.lakeRegions(chunkX, chunkZ);
            List<RegionKey> window = WindowPlanner.lakeRegions(WindowKey.containing(chunkX * 16, chunkZ * 16));
            assertTrue(chunk.size() >= 4 && chunk.size() <= 9);
            assertTrue("chunk " + chunkX + "," + chunkZ + " lake regions sit inside its window's", window.containsAll(chunk));
        }
    }
}
