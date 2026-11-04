package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisStructureComponentMarkerTest {
    @Test
    public void markerFilterAcceptsOnlyStorageContainers() {
        NativeBlockState storage = mock(NativeBlockState.class);
        NativeBlockState solid = mock(NativeBlockState.class);
        when(storage.isStorageChest()).thenReturn(true);
        when(solid.isStorageChest()).thenReturn(false);

        assertTrue(IrisStructureComponent.shouldWriteStructureMarker(storage));
        assertFalse(IrisStructureComponent.shouldWriteStructureMarker(solid));
        assertFalse(IrisStructureComponent.shouldWriteStructureMarker(null));
    }

    @Test
    public void placementIdIsStableAndCoordinateSensitive() {
        int first = IrisStructureComponent.structurePlacementId("structures/village", "pieces/house", 20, -14, 35);
        int repeated = IrisStructureComponent.structurePlacementId("structures/village", "pieces/house", 20, -14, 35);
        int moved = IrisStructureComponent.structurePlacementId("structures/village", "pieces/house", 21, -14, 35);

        assertEquals(first, repeated);
        assertNotEquals(first, moved);
    }

    @Test
    public void treeCollisionUsesOnlyNonAirStructureBlocks() {
        NativeBlockState solid = mock(NativeBlockState.class);
        NativeBlockState air = mock(NativeBlockState.class);
        when(solid.isAir()).thenReturn(false);
        when(air.isAir()).thenReturn(true);

        assertTrue(IrisStructureComponent.isOccupiedStructureState(solid));
        assertFalse(IrisStructureComponent.isOccupiedStructureState(air));
        assertFalse(IrisStructureComponent.isOccupiedStructureState(null));
    }

    @Test
    public void emptyResolvedPiecesRemainSkippableForNonReplacement() {
        IrisStructureLocator.ResolvedPlacement resolved = resolvedPlacement(
                NativeStructureSuppression.NONE, new KList<>());

        assertNull(IrisStructureComponent.resolvedPiecesOrNull(resolved, 4, -3));
    }

    @Test
    public void emptyResolvedPiecesAreSkippedForReplacement() {
        IrisStructureLocator.ResolvedPlacement resolved = resolvedPlacement(
                NativeStructureSuppression.REPLACE_SOURCE, new KList<>());

        assertNull(IrisStructureComponent.resolvedPiecesOrNull(resolved, 4, -3));
    }

    private IrisStructureLocator.ResolvedPlacement resolvedPlacement(
            NativeStructureSuppression suppression,
            KList<PlacedStructurePiece> pieces
    ) {
        IrisStructurePlacement placement = new IrisStructurePlacement().setNativeSuppression(suppression);
        placement.getStructures().add("test:city");
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test:city");
        return new IrisStructureLocator.ResolvedPlacement(
                placement, "test:city", structure, pieces, new RNG(1L), 0, 64, 0, false);
    }
}
