package art.arcane.iris.structure.nativegen;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureVolumeTest {
    private static final NativeStructureVolume PIECE =
            new NativeStructureVolume("minecraft:village_plains", 10, 64, 20, 25, 78, 40);

    @Test
    public void rectQueriesIgnoreTheVerticalAxis() {
        assertTrue(PIECE.intersectsRect(0, 0, 10, 20));
        assertTrue(PIECE.intersectsRect(25, 40, 60, 60));
        assertFalse(PIECE.intersectsRect(26, 20, 60, 40));
        assertFalse(PIECE.intersectsRect(10, 41, 25, 60));
    }

    @Test
    public void boxQueriesSeparateStackedVolumes() {
        assertTrue(PIECE.intersects(0, 60, 0, 40, 70, 50));
        assertFalse(PIECE.intersects(0, 0, 0, 40, 63, 50));
        assertFalse(PIECE.intersects(0, 79, 0, 40, 200, 50));
    }

    @Test
    public void containmentIsInclusiveOnEveryFace() {
        assertTrue(PIECE.contains(10, 64, 20));
        assertTrue(PIECE.contains(25, 78, 40));
        assertFalse(PIECE.contains(9, 64, 20));
        assertFalse(PIECE.contains(25, 79, 40));
    }

    @Test
    public void marginWidensContainmentSymmetrically() {
        assertFalse(PIECE.containsWithin(8, 64, 20, 1));
        assertTrue(PIECE.containsWithin(8, 64, 20, 2));
        assertTrue(PIECE.containsWithin(25, 80, 42, 2));
    }

    @Test
    public void factoryNormalizesCornerOrder() {
        NativeStructureVolume normalized = NativeStructureVolume.of("test:piece", 25, 78, 40, 10, 64, 20);

        assertEquals(PIECE.minX(), normalized.minX());
        assertEquals(PIECE.minY(), normalized.minY());
        assertEquals(PIECE.minZ(), normalized.minZ());
        assertEquals(PIECE.maxX(), normalized.maxX());
        assertEquals(PIECE.maxY(), normalized.maxY());
        assertEquals(PIECE.maxZ(), normalized.maxZ());
    }
}
