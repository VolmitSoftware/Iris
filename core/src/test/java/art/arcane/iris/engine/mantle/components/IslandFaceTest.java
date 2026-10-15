package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSamples;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IslandFaceTest {
    private static final int WORLD_HEIGHT = 320;

    private FloatingIslandSample sampleWithBottomAt(int baseY, int bottomOffset) {
        boolean[] mask = new boolean[10];
        mask[bottomOffset] = true;
        mask[9] = true;
        return FloatingIslandSamples.of(baseY, 10, 9, 2, mask);
    }

    @Test
    public void bottomFace_getHighest_inFootprint_returnsSampleBottomY() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandFace face = IslandFace.bottom(sampleProvider(samples), null, 100, WORLD_HEIGHT);

        int result = face.faceY(0, 0);
        assertEquals(100, result);
    }

    @Test
    public void bottomFace_getHighest_offFootprint_returnsChunkMinBottomY() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandFace face = IslandFace.bottom(sampleProvider(samples), null, 100, WORLD_HEIGHT);

        int result = face.faceY(15, 15);
        assertEquals(100, result);
    }

    @Test
    public void bottomFace_set_aboveAnchor_dropsWrite() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandFace face = IslandFace.bottom(sampleProvider(samples), null, 100, WORLD_HEIGHT);

        assertEquals(false, face.writes(0, 101, 0));
    }

    @Test
    public void bottomFace_canWriteObjectBlock_allowsBelowAnchor() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandFace face = IslandFace.bottom(sampleProvider(samples), null, 100, WORLD_HEIGHT);

        assertEquals(true, face.writes(0, 99, 0));
    }

    @Test
    public void bottomFace_canWriteObjectBlock_blocksAnchorAndAbove() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandFace face = IslandFace.bottom(sampleProvider(samples), null, 100, WORLD_HEIGHT);

        assertEquals(false, face.writes(0, 100, 0));
        assertEquals(false, face.writes(0, 101, 0));
    }

    @Test
    public void topFace_existingConstructor_dropsBelowAnchor_noRegression() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandFace face = IslandFace.top(sampleProvider(samples), null, 105, WORLD_HEIGHT);

        assertEquals(false, face.writes(1, 104, 0));
    }

    @Test
    public void topFace_allowsSupportedNeighborChunkWrite() {
        FloatingIslandSample sample = sampleWithBottomAt(100, 0);
        IslandFace face = IslandFace.top((x, z) -> z == 0 && (x == 15 || x == 16) ? sample : null, null, 109, WORLD_HEIGHT);

        assertEquals(true, face.writes(16, 110, 0));
    }

    @Test
    public void topFace_rejectsUnsupportedWorldColumnBeyondOverhang() {
        FloatingIslandSample sample = sampleWithBottomAt(100, 0);
        IslandFace face = IslandFace.top((x, z) -> x == 15 && z == 0 ? sample : null, null, 109, WORLD_HEIGHT);

        assertEquals(false, face.writes(18, 110, 0));
    }

    private IslandFace.SampleProvider sampleProvider(FloatingIslandSample[] samples) {
        return (x, z) -> {
            if (x < 0 || x >= 16 || z < 0 || z >= 16) {
                return null;
            }
            return samples[(z << 4) | x];
        };
    }
}
