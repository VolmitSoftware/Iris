package art.arcane.iris.platform.bukkit.terrain;

import art.arcane.iris.api.terrain.IrisColumnField;
import art.arcane.iris.api.terrain.IrisColumnQuery;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisColumnWalkTest {
    private static final EnumSet<IrisColumnField> ANY = EnumSet.of(IrisColumnField.SURFACE_HEIGHT);

    @Test
    public void visitsExactlyTheAdvertisedColumnCount() {
        IrisColumnQuery query = IrisColumnQuery.rect(-40, -40, 39, 39, 8, ANY);
        List<long[]> visited = new ArrayList<>();

        long count = IrisColumnWalk.walk(query, (int blockX, int blockZ) -> visited.add(new long[]{blockX, blockZ}));

        assertEquals(query.columnCount(), count);
        assertEquals(query.columnCount(), visited.size());
    }

    @Test
    public void everyColumnIsStrideAlignedAndInsideTheRect() {
        IrisColumnQuery query = IrisColumnQuery.rect(-37, -21, 60, 44, 7, ANY);

        IrisColumnWalk.walk(query, (int blockX, int blockZ) -> {
            assertTrue(blockX >= query.minBlockX() && blockX <= query.maxBlockX());
            assertTrue(blockZ >= query.minBlockZ() && blockZ <= query.maxBlockZ());
            assertEquals(0, (blockX - query.minBlockX()) % query.strideBlocks());
            assertEquals(0, (blockZ - query.minBlockZ()) % query.strideBlocks());
            return true;
        });
    }

    @Test
    public void everyChunkIsVisitedOnceAsAContiguousRun() {
        IrisColumnQuery query = IrisColumnQuery.rect(-33, -33, 47, 47, 4, ANY);
        List<Long> chunkRuns = new ArrayList<>();

        IrisColumnWalk.walk(query, (int blockX, int blockZ) -> {
            long chunkKey = (((long) (blockX >> 4)) << 32) ^ ((blockZ >> 4) & 0xFFFFFFFFL);
            if (chunkRuns.isEmpty() || chunkRuns.get(chunkRuns.size() - 1) != chunkKey) {
                chunkRuns.add(chunkKey);
            }
            return true;
        });

        Set<Long> distinct = new LinkedHashSet<>(chunkRuns);
        assertEquals("a chunk must never be revisited after the walk leaves it",
                distinct.size(), chunkRuns.size());
    }

    @Test
    public void aRefusingVisitorStopsTheWalkAndReportsWhatItSaw() {
        IrisColumnQuery query = IrisColumnQuery.rect(0, 0, 63, 63, 1, ANY);
        int[] seen = new int[1];

        long count = IrisColumnWalk.walk(query, (int blockX, int blockZ) -> {
            seen[0]++;
            return seen[0] < 10;
        });

        assertEquals(9L, count);
        assertEquals(10, seen[0]);
        assertTrue(count < query.columnCount());
    }

    @Test
    public void aSingleColumnRectVisitsExactlyThatColumn() {
        IrisColumnQuery query = IrisColumnQuery.rect(-1, -1, -1, -1, 16, ANY);
        List<long[]> visited = new ArrayList<>();

        long count = IrisColumnWalk.walk(query, (int blockX, int blockZ) -> visited.add(new long[]{blockX, blockZ}));

        assertEquals(1L, count);
        assertEquals(-1L, visited.get(0)[0]);
        assertEquals(-1L, visited.get(0)[1]);
    }
}
