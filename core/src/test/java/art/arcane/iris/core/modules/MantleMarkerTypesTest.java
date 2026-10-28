package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.blocks.ObjectMarkers;
import com.volmit.geometry.BlockPosition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** Iris keeps the marker ids the built-in modules mark with, without reading their classes. */
public class MantleMarkerTypesTest {
    private static final BlockPosition SPOT = new BlockPosition(1, 2, 3);

    @Test
    public void theTypesAreTheOnesTheBuiltInModulesMarkWith() {
        assertEquals(ObjectMarkers.TYPE, MantleMarkerTypes.PACK_MARKER);
        assertEquals(ObjectMarkers.MARKER_ATTRIBUTE, MantleMarkerTypes.PACK_MARKER_NAME);
        assertEquals(ObjectMarkers.TREE_MATERIAL, MantleMarkerTypes.TREE_MATERIAL);
        assertEquals(ObjectMarkers.BURIED_TREASURE, MantleMarkerTypes.BURIED_TREASURE);
    }

    @Test
    public void aPackMarkerGivesItsName() {
        assertEquals("spawn/zombie", MantleMarkerTypes.packMarkerName(ObjectMarkers.of(SPOT, "spawn/zombie")));
    }

    @Test
    public void aMarkerOfAnotherTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MantleMarkerTypes.packMarkerName(ObjectMarkers.treeMaterial(SPOT)));
    }
}
