package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.iris.spi.PlatformBlockState;
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
        PlatformBlockState storage = mock(PlatformBlockState.class);
        PlatformBlockState solid = mock(PlatformBlockState.class);
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
        PlatformBlockState solid = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
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
        IrisStructureComponent.requireAppliedPieces(resolvedWithOnePiece(NativeStructureSuppression.NONE),
                4, -3, 1);
    }

    @Test
    public void emptyResolvedPiecesFailForReplacement() {
        IrisStructureLocator.ResolvedPlacement resolved = resolvedPlacement(
                NativeStructureSuppression.REPLACE_SOURCE, new KList<>());

        assertReplacementFailure(
                () -> IrisStructureComponent.resolvedPiecesOrNull(resolved, 4, -3),
                "placement application received no assembled pieces");
    }

    @Test
    public void rejectedAppliedPieceFailsForReplacement() {
        IrisStructureLocator.ResolvedPlacement resolved = resolvedWithOnePiece(
                NativeStructureSuppression.REPLACE_SOURCE);

        assertReplacementFailure(
                () -> IrisStructureComponent.requireAppliedPieces(resolved, 4, -3, 1),
                "object placement rejected 1 of 1 assembled piece(s)");
    }

    private IrisStructureLocator.ResolvedPlacement resolvedWithOnePiece(
            NativeStructureSuppression suppression
    ) {
        KList<PlacedStructurePiece> pieces = new KList<>();
        pieces.add(mock(PlacedStructurePiece.class));
        return resolvedPlacement(suppression, pieces);
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

    private void assertReplacementFailure(Runnable operation, String expectedMessage) {
        try {
            operation.run();
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedMessage));
            return;
        }
        throw new AssertionError("Expected strict native replacement failure containing '" + expectedMessage + "'");
    }
}
