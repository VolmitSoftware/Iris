package art.arcane.iris.engine.mantle.components;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructurePlacingOverboreTest {
    @Test
    public void ordinaryMarkersAreSeparatedFromStructureMarkers() {
        assertTrue(StructurePlacing.isOrdinaryObjectMarker("trees/oak@42"));
        assertFalse(StructurePlacing.isOrdinaryObjectMarker(
                "@iris-structure:v1:dHJlZXMvb2Fr:42:bWluZWNyYWZ0Om1hbnNpb24"));
        assertFalse(StructurePlacing.isOrdinaryObjectMarker("not-a-marker"));
    }

    @Test
    public void collidingObjectSearchCoversTheWholeMarkerOwnedObject() {
        StructurePlacing.ObjectMarkerBounds bounds = StructurePlacing.markerSearchBounds(
                20, 64, -8, 21, 66, -7, 7, 12, 384);

        assertEquals(14, bounds.minX());
        assertEquals(53, bounds.minY());
        assertEquals(-14, bounds.minZ());
        assertEquals(27, bounds.maxX());
        assertEquals(77, bounds.maxY());
        assertEquals(-1, bounds.maxZ());
    }

    @Test
    public void collidingObjectSearchClampsToTheWorldHeight() {
        StructurePlacing.ObjectMarkerBounds bounds = StructurePlacing.markerSearchBounds(
                0, 2, 0, 0, 379, 0, 1, 12, 384);

        assertEquals(0, bounds.minY());
        assertEquals(383, bounds.maxY());
    }
}
