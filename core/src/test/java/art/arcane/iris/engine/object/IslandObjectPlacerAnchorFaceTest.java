package art.arcane.iris.engine.object;

import art.arcane.iris.engine.mantle.components.IslandObjectPlacer;
import art.arcane.iris.engine.mantle.MantleWriter;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IslandObjectPlacerAnchorFaceTest {

    @Test
    public void islandMaskOwnsSurfaceSupportAboveRootTerrainAndInsideRootOpenings() {
        FloatingIslandSample sample = sampleWithBottomAt(100, 0);
        MantleWriter writer = mock(MantleWriter.class);
        when(writer.isSurfaceSolid(0, 109, 0)).thenReturn(false);
        when(writer.isCarved(0, 109, 0)).thenReturn(true);
        IslandObjectPlacer placer = IslandObjectPlacer.top(writer, (x, z) -> sample, null, 109);

        assertTrue(placer.isSurfaceSolid(0, 109, 0));
        assertFalse(placer.isCarved(0, 109, 0));
        assertFalse(placer.isSurfaceSolid(0, 108, 0));
        assertTrue(placer.isCarved(0, 108, 0));
        assertFalse(placer.isSurfaceSolid(0, 110, 0));
    }

    private FloatingIslandSample sampleWithBottomAt(int baseY, int bottomOffset) {
        boolean[] mask = new boolean[10];
        mask[bottomOffset] = true;
        mask[9] = true;
        return FloatingIslandSample.constructForTest(baseY, 10, 9, 2, mask);
    }

    @Test
    public void bottomFace_getHighest_inFootprint_returnsSampleBottomY() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = IslandObjectPlacer.bottom(null, sampleProvider(samples), null, 100);

        int result = placer.getHighest(0, 0, null);
        assertEquals(100, result);
    }

    @Test
    public void bottomFace_getHighest_offFootprint_returnsChunkMinBottomY() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = IslandObjectPlacer.bottom(null, sampleProvider(samples), null, 100);

        int result = placer.getHighest(15, 15, null);
        assertEquals(100, result);
    }

    @Test
    public void bottomFace_set_aboveAnchor_dropsWrite() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = IslandObjectPlacer.bottom(null, sampleProvider(samples), null, 100);

        assertEquals(false, placer.canWriteObjectBlock(0, 101, 0));
        placer.set(0, 101, 0, null);
    }

    @Test
    public void bottomFace_canWriteObjectBlock_allowsBelowAnchor() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = IslandObjectPlacer.bottom(null, sampleProvider(samples), null, 100);

        assertEquals(true, placer.canWriteObjectBlock(0, 99, 0));
    }

    @Test
    public void bottomFace_canWriteObjectBlock_blocksAnchorAndAbove() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = IslandObjectPlacer.bottom(null, sampleProvider(samples), null, 100);

        assertEquals(false, placer.canWriteObjectBlock(0, 100, 0));
        assertEquals(false, placer.canWriteObjectBlock(0, 101, 0));
    }

    @Test
    public void topFace_existingConstructor_dropsBelowAnchor_noRegression() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = IslandObjectPlacer.top(null, sampleProvider(samples), null, 105);

        assertEquals(false, placer.canWriteObjectBlock(1, 104, 0));
        placer.set(1, 104, 0, null);
    }

    @Test
    public void topFace_allowsSupportedNeighborChunkWrite() {
        FloatingIslandSample sample = sampleWithBottomAt(100, 0);
        IslandObjectPlacer placer = IslandObjectPlacer.top(null,
                (x, z) -> z == 0 && (x == 15 || x == 16) ? sample : null, null, 109);

        assertEquals(true, placer.canWriteObjectBlock(16, 110, 0));
    }

    @Test
    public void topFace_rejectsUnsupportedWorldColumnBeyondOverhang() {
        FloatingIslandSample sample = sampleWithBottomAt(100, 0);
        IslandObjectPlacer placer = IslandObjectPlacer.top(null,
                (x, z) -> x == 15 && z == 0 ? sample : null, null, 109);

        assertEquals(false, placer.canWriteObjectBlock(18, 110, 0));
    }

    private IslandObjectPlacer.SampleProvider sampleProvider(FloatingIslandSample[] samples) {
        return (x, z) -> {
            if (x < 0 || x >= 16 || z < 0 || z >= 16) {
                return null;
            }
            return samples[(z << 4) | x];
        };
    }
}
