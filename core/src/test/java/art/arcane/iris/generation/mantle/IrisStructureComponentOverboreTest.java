package art.arcane.iris.generation.mantle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisStructureComponentOverboreTest {
    @Test
    public void enlargedTreeSearchCoversPaddedScaleWithoutEnlargingShrunkTrees() {
        assertEquals(4, IrisStructureComponent.scaledMarkerSpan(4, 1D));
        assertEquals(4, IrisStructureComponent.scaledMarkerSpan(4, 0.5D));
        assertEquals(8, IrisStructureComponent.scaledMarkerSpan(4, 2D));
        assertEquals(20, IrisStructureComponent.scaledMarkerSpan(10, 2D));
    }

    @Test
    public void ordinaryMarkersAreSeparatedFromStructureMarkers() {
        assertTrue(IrisStructureComponent.isOrdinaryObjectMarker("trees/oak@42"));
        assertFalse(IrisStructureComponent.isOrdinaryObjectMarker(
                "@iris-structure:v1:dHJlZXMvb2Fr:42:bWluZWNyYWZ0Om1hbnNpb24"));
        assertFalse(IrisStructureComponent.isOrdinaryObjectMarker("not-a-marker"));
    }

    @Test
    public void collidingObjectSearchCoversTheWholeMarkerOwnedObject() {
        IrisStructureComponent.ObjectMarkerBounds bounds = IrisStructureComponent.markerSearchBounds(
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
        IrisStructureComponent.ObjectMarkerBounds bounds = IrisStructureComponent.markerSearchBounds(
                0, 2, 0, 0, 379, 0, 1, 12, 384);

        assertEquals(0, bounds.minY());
        assertEquals(383, bounds.maxY());
    }
}
