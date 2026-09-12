package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StructureCarvingFootprintTest {
    @Test
    public void preservesIrregularFootprintInsteadOfFillingItsBoundingBox() {
        PlacedStructurePiece piece = piece(10, 40, 20, IrisObjectRotation.of(0, 0, 0),
                block(0, 0, 0), block(1, 0, 0), block(2, 0, 0),
                block(0, 0, 1), block(0, 0, 2));

        StructureCarvingFootprint footprint = StructureCarvingFootprint.from(pieces(piece), 0);

        assertNotNull(footprint);
        assertEquals(10, footprint.minX());
        assertEquals(12, footprint.maxX());
        assertEquals(20, footprint.minZ());
        assertEquals(22, footprint.maxZ());
        assertEquals(3, footprint.width());
        assertEquals(3, footprint.depth());
        assertEquals(0L, footprint.distanceSquaredAt(12, 20));
        assertEquals(4L, footprint.distanceSquaredAt(12, 22));
    }

    @Test
    public void reportsExactDistanceAndNearestColumnHeights() {
        PlacedStructurePiece piece = piece(0, 0, 0, IrisObjectRotation.of(0, 0, 0),
                block(0, 10, 0), block(0, 12, 0),
                block(4, 30, 0), block(4, 35, 0));

        StructureCarvingFootprint footprint = StructureCarvingFootprint.from(pieces(piece), 2);
        StructureCarvingFootprint.Column nearestRight = footprint.sample(3, 2);
        StructureCarvingFootprint.Column tied = footprint.sample(2, 0);

        assertNotNull(nearestRight);
        assertEquals(5L, nearestRight.distanceSquared());
        assertEquals(30, nearestRight.sourceMinY());
        assertEquals(35, nearestRight.sourceMaxY());
        assertNotNull(tied);
        assertEquals(4L, tied.distanceSquared());
        assertEquals(10, tied.sourceMinY());
        assertEquals(12, tied.sourceMaxY());
    }

    @Test
    public void rotatesAndMergesOverlappingColumns() {
        PlacedStructurePiece rotated = piece(10, 20, 30, IrisObjectRotation.of(0, 90, 0),
                block(2, 1, 0));
        PlacedStructurePiece overlapping = piece(10, 5, 28, IrisObjectRotation.of(0, 0, 0),
                block(0, 3, 0), block(0, 9, 0));

        StructureCarvingFootprint footprint = StructureCarvingFootprint.from(
                pieces(rotated, overlapping), 0);

        assertNotNull(footprint);
        assertEquals(10, footprint.minX());
        assertEquals(10, footprint.maxX());
        assertEquals(28, footprint.minZ());
        assertEquals(28, footprint.maxZ());
        assertEquals(0L, footprint.distanceSquaredAt(10, 28));
        assertEquals(8, footprint.sourceMinYAt(10, 28));
        assertEquals(21, footprint.sourceMaxYAt(10, 28));
    }

    @Test
    public void matchesBruteForceSquaredDistancesAcrossSparseColumns() {
        int[][] sourceBlocks = new int[][]{
                block(0, 2, 0), block(3, 4, 1), block(1, 6, 4), block(5, 8, 5)
        };
        PlacedStructurePiece piece = piece(
                -8, 30, 11, IrisObjectRotation.of(0, 0, 0), sourceBlocks);

        StructureCarvingFootprint footprint = StructureCarvingFootprint.from(pieces(piece), 3);

        assertNotNull(footprint);
        for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
            for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
                long expected = Long.MAX_VALUE;
                for (int[] source : sourceBlocks) {
                    long deltaX = (long) x - (piece.getX() + source[0]);
                    long deltaZ = (long) z - (piece.getZ() + source[2]);
                    expected = Math.min(expected, deltaX * deltaX + deltaZ * deltaZ);
                }
                assertEquals(expected, footprint.distanceSquaredAt(x, z));
            }
        }
    }

    @Test
    public void returnsNullForEmptyInvalidOrOversizedInputs() {
        assertNull(StructureCarvingFootprint.from(null, 0));
        assertNull(StructureCarvingFootprint.from(new KList<>(), 0));

        KList<PlacedStructurePiece> invalidPieces = new KList<>();
        invalidPieces.add((PlacedStructurePiece) null);
        invalidPieces.add(new PlacedStructurePiece(
                null, null, 0, 0, 0, null, 0, 0, 0, 0, 0, 0));
        assertNull(StructureCarvingFootprint.from(invalidPieces, 0));

        PlacedStructurePiece piece = piece(0, 0, 0, IrisObjectRotation.of(0, 0, 0), block(0, 0, 0));
        assertNull(StructureCarvingFootprint.from(pieces(piece), 2, 24));
        PlacedStructurePiece overflowing = piece(
                Integer.MAX_VALUE, 0, 0, IrisObjectRotation.of(0, 0, 0), block(1, 0, 0));
        assertNull(StructureCarvingFootprint.from(pieces(overflowing), 0));

        try {
            StructureCarvingFootprint.from(pieces(piece), -1);
            fail("Expected negative padding to fail");
        } catch (IllegalArgumentException e) {
            assertEquals("padding must be non-negative", e.getMessage());
        }
    }

    @Test
    public void includesTheEntirePaddingBoundary() {
        PlacedStructurePiece piece = piece(7, 14, -4, IrisObjectRotation.of(0, 0, 0), block(0, 0, 0));

        StructureCarvingFootprint footprint = StructureCarvingFootprint.from(pieces(piece), 2, 25);

        assertNotNull(footprint);
        assertEquals(5, footprint.minX());
        assertEquals(9, footprint.maxX());
        assertEquals(-6, footprint.minZ());
        assertEquals(-2, footprint.maxZ());
        assertEquals(5, footprint.width());
        assertEquals(5, footprint.depth());
        assertTrue(footprint.contains(5, -6));
        assertTrue(footprint.contains(9, -2));
        assertFalse(footprint.contains(4, -6));
        assertEquals(8L, footprint.distanceSquaredAt(5, -6));
        assertNull(footprint.sample(4, -6));
    }

    @Test
    public void explicitAirDoesNotExpandTheExteriorFootprint() {
        IrisObject object = new IrisObject(1, 1, 1);
        PlatformBlockState solid = mock(PlatformBlockState.class);
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        object.getBlocks().put(new IrisBlockVector(0, 0, 0), solid);
        object.getBlocks().put(new IrisBlockVector(8, 4, 8), air);
        PlacedStructurePiece piece = new PlacedStructurePiece(
                null, object, 10, 20, 30, IrisObjectRotation.of(0, 0, 0),
                10, 20, 30, 18, 24, 38);

        StructureCarvingFootprint footprint = StructureCarvingFootprint.from(pieces(piece), 0);

        assertNotNull(footprint);
        assertEquals(10, footprint.minX());
        assertEquals(10, footprint.maxX());
        assertEquals(30, footprint.minZ());
        assertEquals(30, footprint.maxZ());
    }

    private static PlacedStructurePiece piece(int x, int y, int z, IrisObjectRotation rotation,
                                              int[]... blocks) {
        IrisObject object = new IrisObject(1, 1, 1);
        PlatformBlockState state = mock(PlatformBlockState.class);
        for (int[] block : blocks) {
            object.getBlocks().put(new IrisBlockVector(block[0], block[1], block[2]), state);
        }
        return new PlacedStructurePiece(
                null, object, x, y, z, rotation, x, y, z, x, y, z);
    }

    private static KList<PlacedStructurePiece> pieces(PlacedStructurePiece... pieces) {
        KList<PlacedStructurePiece> result = new KList<>();
        for (PlacedStructurePiece piece : pieces) {
            result.add(piece);
        }
        return result;
    }

    private static int[] block(int x, int y, int z) {
        return new int[]{x, y, z};
    }
}
