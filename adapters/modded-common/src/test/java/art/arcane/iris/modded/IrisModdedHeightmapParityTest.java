package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedHeightmaps;

import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisModdedHeightmapParityTest {
    @Test
    public void terrainHeightmapEncodesPaperRelativeSurfaceHeights() {
        int height = 384;
        long[] rawData = ModdedHeightmaps.terrainRawData(height,
                (x, z) -> x + z * 16 + 1);
        SimpleBitStorage storage = new SimpleBitStorage(Mth.ceillog2(height + 1), 256, rawData);

        assertEquals(1, storage.get(0));
        assertEquals(16, storage.get(15));
        assertEquals(17, storage.get(16));
        assertEquals(256, storage.get(255));
    }

    @Test
    public void terrainHeightmapClampsToDimensionStorageRange() {
        int height = 384;
        long[] rawData = ModdedHeightmaps.terrainRawData(height,
                (x, z) -> x == 0 ? -20 : 900);
        SimpleBitStorage storage = new SimpleBitStorage(Mth.ceillog2(height + 1), 256, rawData);

        assertEquals(0, storage.get(0));
        assertEquals(height, storage.get(1));
    }
}
