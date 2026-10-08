package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.PlacedStructurePiece;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureSuppression;
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

public class StructurePlacingMarkerTest {
    @Test
    public void markerFilterAcceptsOnlyStorageContainers() {
        PlatformBlockState storage = mock(PlatformBlockState.class);
        PlatformBlockState solid = mock(PlatformBlockState.class);
        when(storage.isStorageChest()).thenReturn(true);
        when(solid.isStorageChest()).thenReturn(false);

        assertTrue(StructurePlacing.shouldWriteStructureMarker(storage));
        assertFalse(StructurePlacing.shouldWriteStructureMarker(solid));
        assertFalse(StructurePlacing.shouldWriteStructureMarker(null));
    }

    @Test
    public void placementIdIsStableAndCoordinateSensitive() {
        int first = StructurePlacing.structurePlacementId("structures/village", "pieces/house", 20, -14, 35);
        int repeated = StructurePlacing.structurePlacementId("structures/village", "pieces/house", 20, -14, 35);
        int moved = StructurePlacing.structurePlacementId("structures/village", "pieces/house", 21, -14, 35);

        assertEquals(first, repeated);
        assertNotEquals(first, moved);
    }

    @Test
    public void treeCollisionUsesOnlyNonAirStructureBlocks() {
        PlatformBlockState solid = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(solid.isAir()).thenReturn(false);
        when(air.isAir()).thenReturn(true);

        assertTrue(StructurePlacing.isOccupiedStructureState(solid));
        assertFalse(StructurePlacing.isOccupiedStructureState(air));
        assertFalse(StructurePlacing.isOccupiedStructureState(null));
    }

    @Test
    public void emptyResolvedPiecesRemainSkippableForNonReplacement() {
        IrisStructureLocator.ResolvedPlacement resolved = resolvedPlacement(
                NativeStructureSuppression.NONE, new KList<>());

        assertNull(StructurePlacing.resolvedPiecesOrNull(resolved, 4, -3));
        StructurePlacing.requireAppliedPieces(resolvedWithOnePiece(NativeStructureSuppression.NONE),
                4, -3, 1);
    }

    @Test
    public void emptyResolvedPiecesFailForReplacement() {
        IrisStructureLocator.ResolvedPlacement resolved = resolvedPlacement(
                NativeStructureSuppression.REPLACE_SOURCE, new KList<>());

        assertReplacementFailure(
                () -> StructurePlacing.resolvedPiecesOrNull(resolved, 4, -3),
                "placement application received no assembled pieces");
    }

    @Test
    public void rejectedAppliedPieceFailsForReplacement() {
        IrisStructureLocator.ResolvedPlacement resolved = resolvedWithOnePiece(
                NativeStructureSuppression.REPLACE_SOURCE);

        assertReplacementFailure(
                () -> StructurePlacing.requireAppliedPieces(resolved, 4, -3, 1),
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
