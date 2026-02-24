package art.arcane.iris.core.pregenerator;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.Position2;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

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
