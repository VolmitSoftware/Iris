package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PregenTaskInterleavedTraversalTest {
    @Test
    public void interleavedTraversalIsDeterministicAndComplete() {
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(1024)
                .radiusZ(1024)
                .build();

        KList<Long> baseline = new KList<>();
        task.iterateAllChunks((x, z) -> baseline.add(asKey(x, z)));

        KList<Long> firstInterleaved = new KList<>();
        task.iterateAllChunksInterleaved((regionX, regionZ, chunkX, chunkZ, firstChunkInRegion, lastChunkInRegion) -> {
            firstInterleaved.add(asKey(chunkX, chunkZ));
            return true;
        });

        KList<Long> secondInterleaved = new KList<>();
        task.iterateAllChunksInterleaved((regionX, regionZ, chunkX, chunkZ, firstChunkInRegion, lastChunkInRegion) -> {
            secondInterleaved.add(asKey(chunkX, chunkZ));
            return true;
        });

        assertEquals(baseline.size(), firstInterleaved.size());
        assertEquals(firstInterleaved, secondInterleaved);
        assertEquals(asSet(baseline), asSet(firstInterleaved));
    }

    @Test
    public void blockRadius352CoversExactly2025Chunks() {
        PregenTask task = PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(352)
                .radiusZ(352)
                .build();
        KList<Long> chunks = new KList<>();

        task.iterateAllChunks((x, z) -> chunks.add(asKey(x, z)));

        assertEquals(2025, chunks.size());
        assertEquals(2025, asSet(chunks).size());
    }

    @Test
    public void nonpositiveRadiusIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(0)
                .radiusZ(352)
                .build());
        assertThrows(IllegalArgumentException.class, () -> PregenTask.builder()
                .center(new Position2(0, 0))
                .radiusX(352)
                .radiusZ(-1)
                .build());
    }

    private Set<Long> asSet(KList<Long> values) {
        Set<Long> set = new HashSet<>();
        for (Long value : values) {
            set.add(value);
        }
        return set;
    }

    private long asKey(int x, int z) {
        long high = (long) x << 32;
        long low = z & 0xFFFFFFFFL;
        return high | low;
    }
}
