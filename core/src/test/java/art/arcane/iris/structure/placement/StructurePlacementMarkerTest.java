package art.arcane.iris.structure.placement;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StructurePlacementMarkerTest {
    @Test
    public void structureMarkerRoundTripsDelimiterSafeKeys() {
        String objectKey = "pieces/room@east:alpha/樹";
        String structureKey = "structures/village@night:beta/城";

        String encoded = StructurePlacementMarker.encodeStructure(objectKey, 918273, structureKey);
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode(encoded);

        assertTrue(encoded.startsWith("@iris-structure:v1:"));
        assertEquals(objectKey, decoded.objectKey());
        assertEquals(918273, decoded.placementId());
        assertEquals(structureKey, decoded.structureKey());
        assertTrue(decoded.structureAware());
    }

    @Test
    public void legacyMarkerDecodesWithoutChangingFields() {
        StructurePlacementMarker.Decoded decoded = StructurePlacementMarker.decode("objects/oak_tree@42");

        assertEquals("objects/oak_tree", decoded.objectKey());
        assertEquals(42, decoded.placementId());
        assertNull(decoded.structureKey());
        assertFalse(decoded.structureAware());
    }

    @Test
    public void malformedAndUnknownMarkersFailSafely() {
        assertNull(StructurePlacementMarker.decode(null));
        assertNull(StructurePlacementMarker.decode(""));
        assertNull(StructurePlacementMarker.decode("objects/oak_tree"));
        assertNull(StructurePlacementMarker.decode("objects/oak_tree@not-an-id"));
        assertNull(StructurePlacementMarker.decode("objects/oak_tree@42@extra"));
        assertNull(StructurePlacementMarker.decode("@iris-structure:v2:b2JqZWN0:42:c3RydWN0dXJl"));
        assertNull(StructurePlacementMarker.decode("@iris-structure:v1:not%base64:42:c3RydWN0dXJl"));
        assertNull(StructurePlacementMarker.decode("@iris-structure:v1:b2JqZWN0:42:"));
    }
}
