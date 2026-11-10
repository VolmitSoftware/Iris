package art.arcane.iris.core.pregenerator;

import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class PregenTaskChunkCountTest {
    @Test
    public void countMatchesTraversalAcrossChunkAndRegionBoundaries() {
        int[][] areas = {
                {0, 0, 1, 1},
                {0, 0, 16, 32},
                {17, -17, 15, 33},
                {-16, -32, 16, 16},
                {-513, 511, 32, 17},
                {512, -512, 512, 256},
                {PregenTask.MAX_WORLD_BLOCK - 32, 0, 32, 1},
                {0, -PregenTask.MAX_WORLD_BLOCK + 32, 1, 32}
        };
        for (int[] area : areas) {
            PregenTask task = PregenTask.builder()
                    .center(new Position2(area[0], area[1]))
                    .radiusX(area[2])
                    .radiusZ(area[3])
                    .build();
            AtomicLong visited = new AtomicLong();

            task.iterateAllChunks((x, z) -> visited.incrementAndGet());

            assertEquals("center=" + area[0] + "," + area[1], visited.get(), task.chunkCount());
        }
    }

    @Test(timeout = 2_000L)
    public void entireWorldCountUsesLongArithmeticWithoutTraversal() {
        PregenTask task = PregenTask.builder()
                .radiusX(PregenTask.MAX_WORLD_BLOCK)
                .radiusZ(PregenTask.MAX_WORLD_BLOCK)
                .build();

        assertEquals(14_062_492_500_001L, task.chunkCount());
    }
}
