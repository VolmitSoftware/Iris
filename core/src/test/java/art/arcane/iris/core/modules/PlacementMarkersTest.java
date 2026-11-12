package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import com.volmit.config.Level;
import com.volmit.placement.PlacementOwner;
import com.volmit.placement.PlacementRecord;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class PlacementMarkersTest {
    @Test
    public void aRecordOfAnObjectOnItsOwnIsKeyAtIdAndReadsBack() {
        IrisPackResources resources = resources();
        PlacementRecord record = PlacementRecord.of(resources.idOf("trees/oak"), 7);

        String marker = PlacementMarkers.encode(record, resources);

        assertEquals("trees/oak@7", marker);
        assertEquals(Optional.of(record), PlacementMarkers.recordOf(marker, resources));
    }

    @Test
    public void aRecordOfAStructurePieceUsesTheDelimiterSafeFormAndReadsBack() {
        IrisPackResources resources = resources();
        PlacementRecord record = PlacementRecord.ofPiece(resources.idOf("pieces/room"), 918273, resources.idOf("structures/village"));

        String marker = PlacementMarkers.encode(record, resources);

        assertEquals(PlacementMarkers.encodeStructure("pieces/room", 918273, "structures/village"), marker);
        assertEquals(Optional.of(record), PlacementMarkers.recordOf(marker, resources));
    }

    @Test
    public void theBiomeOrRegionThatPlacedAnObjectReadsBack() {
        IrisPackResources resources = resources();
        PlacementRecord fromBiome = PlacementRecord.of(resources.idOf("trees/oak"), 7)
                .ownedBy(PlacementOwner.biome(resources.idOf("forest/birch")));
        PlacementRecord pieceFromRegion = PlacementRecord.ofPiece(resources.idOf("pieces/room"), 918273, resources.idOf("structures/village"))
                .ownedBy(PlacementOwner.region(resources.idOf("plains")));

        String biomeMarker = PlacementMarkers.encode(fromBiome, resources);
        String regionMarker = PlacementMarkers.encode(pieceFromRegion, resources);

        assertEquals(Optional.of(fromBiome), PlacementMarkers.recordOf(biomeMarker, resources));
        assertEquals(Optional.of(pieceFromRegion), PlacementMarkers.recordOf(regionMarker, resources));
        assertEquals(new PlacementMarkers.OwnerKey(Level.BIOME, "forest/birch"), PlacementMarkers.decode(biomeMarker).owner());
        assertEquals("trees/oak@7", PlacementMarkers.decode(biomeMarker).placementKey());
    }

    @Test
    public void aStructurePlacedByTheDimensionReadsBack() {
        IrisPackResources resources = resources();
        PlacementRecord piece = PlacementRecord.ofPiece(resources.idOf("pieces/room"), 918273, resources.idOf("structures/ruins"))
                .ownedBy(new PlacementOwner(Level.DIMENSION, resources.idOf("natureplanet")));

        String marker = PlacementMarkers.encode(piece, resources);

        assertEquals(Optional.of(piece), PlacementMarkers.recordOf(marker, resources));
        assertEquals(new PlacementMarkers.OwnerKey(Level.DIMENSION, "natureplanet"), PlacementMarkers.decode(marker).owner());
    }

    @Test
    public void anEndThatNamesNoOwnerLeavesTheMarkerAsItWas() {
        PlacementMarkers.Decoded hashInKey = PlacementMarkers.decode("trees/oak#2@7");
        PlacementMarkers.Decoded placementOwner = PlacementMarkers.decode("trees/oak@7#placement:Zm9yZXN0");

        assertEquals("trees/oak#2", hashInKey.objectKey());
        assertNull(hashInKey.owner());
        assertNull(placementOwner);
    }

    @Test
    public void textThatNamesNoPlacementIsNoRecord() {
        IrisPackResources resources = resources();

        assertTrue(PlacementMarkers.recordOf(null, resources).isEmpty());
        assertTrue(PlacementMarkers.recordOf("", resources).isEmpty());
        assertTrue(PlacementMarkers.recordOf("objects/oak_tree", resources).isEmpty());
    }

    private static IrisPackResources resources() {
        IrisData data = mock(IrisData.class);
        ModuleResourceFixture.serve(data);
        return data.getPackResources();
    }

    @Test
    public void structureMarkerRoundTripsDelimiterSafeKeys() {
        String objectKey = "pieces/room@east:alpha/樹";
        String structureKey = "structures/village@night:beta/城";

        String encoded = PlacementMarkers.encodeStructure(objectKey, 918273, structureKey);
        PlacementMarkers.Decoded decoded = PlacementMarkers.decode(encoded);

        assertTrue(encoded.startsWith("@iris-structure:v1:"));
        assertEquals(objectKey, decoded.objectKey());
        assertEquals(918273, decoded.placementId());
        assertEquals(structureKey, decoded.structureKey());
        assertTrue(decoded.structureAware());
    }

    @Test
    public void legacyMarkerDecodesWithoutChangingFields() {
        PlacementMarkers.Decoded decoded = PlacementMarkers.decode("objects/oak_tree@42");

        assertEquals("objects/oak_tree", decoded.objectKey());
        assertEquals(42, decoded.placementId());
        assertNull(decoded.structureKey());
        assertFalse(decoded.structureAware());
    }

    @Test
    public void malformedAndUnknownMarkersFailSafely() {
        assertNull(PlacementMarkers.decode(null));
        assertNull(PlacementMarkers.decode(""));
        assertNull(PlacementMarkers.decode("objects/oak_tree"));
        assertNull(PlacementMarkers.decode("objects/oak_tree@not-an-id"));
        assertNull(PlacementMarkers.decode("objects/oak_tree@42@extra"));
        assertNull(PlacementMarkers.decode("@iris-structure:v2:b2JqZWN0:42:c3RydWN0dXJl"));
        assertNull(PlacementMarkers.decode("@iris-structure:v1:not%base64:42:c3RydWN0dXJl"));
        assertNull(PlacementMarkers.decode("@iris-structure:v1:b2JqZWN0:42:"));
    }
}
