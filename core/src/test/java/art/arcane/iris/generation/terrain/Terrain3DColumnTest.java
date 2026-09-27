package art.arcane.iris.generation.terrain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class Terrain3DColumnTest {
    @Test
    public void displacementPreservesCavitiesAndUpperShelvesWhileKeepingBedrockGrounded() {
        Terrain3DColumn original = new Terrain3DColumn(40D, 25, true, new int[]{0, 30, 36, 42, 50, 54});
        Terrain3DColumn shifted = original.displaced(10, 128);

        assertEquals(50D, shifted.baseHeight(), 0D);
        assertEquals(64, shifted.topY());
        assertEquals(35, shifted.minY());
        assertEquals(3, shifted.spanCount());
        assertTrue(shifted.isSolid(0));
        assertTrue(shifted.isSolid(40));
        assertFalse(shifted.isSolid(41));
        assertFalse(shifted.isSolid(45));
        assertTrue(shifted.isSolid(46));
        assertTrue(shifted.isSolid(52));
        assertFalse(shifted.isSolid(53));
        assertTrue(shifted.isSolid(60));
        assertEquals(54, original.topY());
        assertSame(original, original.displaced(0, 128));
    }

    @Test
    public void loweringTerrainClipsOnlySpansOutsideTheWorld() {
        Terrain3DColumn original = new Terrain3DColumn(40D, 25, true, new int[]{0, 30, 36, 42, 50, 54});
        Terrain3DColumn shifted = original.displaced(-38, 128);

        assertEquals(2, shifted.spanCount());
        assertEquals(0, shifted.ceiling(0));
        assertEquals(4, shifted.floor(0));
        assertEquals(12, shifted.ceiling(1));
        assertEquals(16, shifted.floor(1));
        assertFalse(shifted.isSolid(8));
    }

    @Test
    public void upwardDisplacementClipsTheWorldCeilingWithoutCreatingAnEmptyColumn() {
        Terrain3DColumn column = new Terrain3DColumn(40D, 25, true, new int[]{0, 30, 36, 42});
        Terrain3DColumn shifted = column.displaced(100, 128);
        assertEquals(127, shifted.topY());
        assertEquals(1, shifted.spanCount());
        assertTrue(shifted.isSolid(0));
        assertTrue(shifted.isSolid(127));
        assertEquals(0, column.displaced(-100, 128).topY());
    }
}
