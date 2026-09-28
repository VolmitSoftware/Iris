package art.arcane.iris.structure.nativegen;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class NativeStructureVolumeIndexKeyTest {
    @Test
    public void neighbouringChunkKeysSpreadAcrossHashBuckets() {
        IntOpenHashSet buckets = new IntOpenHashSet();
        int keys = 0;
        for (int chunkX = -64; chunkX < 64; chunkX++) {
            for (int chunkZ = -64; chunkZ < 64; chunkZ++) {
                long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
                int hash = NativeStructureVolumeIndex.runtimeChunkHash(7, chunkKey);
                buckets.add((hash ^ (hash >>> 16)) & ((1 << 15) - 1));
                keys++;
            }
        }
        assertTrue("buckets=" + buckets.size(), buckets.size() > keys / 2);
    }
}
