package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import art.arcane.iris.engine.framework.structure.assembly.PieceTurn;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StructureCarvingFootprintTest {
    @Test
    public void preservesIrregularFootprintInsteadOfFillingItsBoundingBox() {
        Piece piece = piece(10, 40, 20, PieceTurn.NONE,
                block(0, 0, 0), block(1, 0, 0), block(2, 0, 0),
                block(0, 0, 1), block(0, 0, 2));

        StructureCarvingFootprint footprint = footprintOf(pieces(piece), 0);

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
        Piece piece = piece(0, 0, 0, PieceTurn.NONE,
                block(0, 10, 0), block(0, 12, 0),
                block(4, 30, 0), block(4, 35, 0));

        StructureCarvingFootprint footprint = footprintOf(pieces(piece), 2);
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
        Piece rotated = piece(10, 20, 30, PieceTurn.aroundY(90),
                block(2, 1, 0));
        Piece overlapping = piece(10, 5, 28, PieceTurn.NONE,
                block(0, 3, 0), block(0, 9, 0));

        StructureCarvingFootprint footprint = footprintOf(
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
        Piece piece = piece(
                -8, 30, 11, PieceTurn.NONE, sourceBlocks);

        StructureCarvingFootprint footprint = footprintOf(pieces(piece), 3);

        assertNotNull(footprint);
        for (int z = footprint.minZ(); z <= footprint.maxZ(); z++) {
            for (int x = footprint.minX(); x <= footprint.maxX(); x++) {
                long expected = Long.MAX_VALUE;
                for (int[] source : sourceBlocks) {
                    long deltaX = (long) x - (piece.assembled.x() + source[0]);
                    long deltaZ = (long) z - (piece.assembled.z() + source[2]);
                    expected = Math.min(expected, deltaX * deltaX + deltaZ * deltaZ);
                }
                assertEquals(expected, footprint.distanceSquaredAt(x, z));
            }
        }
    }

    @Test
    public void returnsNullForEmptyInvalidOrOversizedInputs() {
        assertNull(footprintOf(null, 0));
        assertNull(footprintOf(new ArrayList<>(), 0));

        List<Piece> invalidPieces = new ArrayList<>();
        invalidPieces.add(null);
        invalidPieces.add(new Piece(piece(0, 0, 0, PieceTurn.NONE).assembled, null));
        assertNull(footprintOf(invalidPieces, 0));

        Piece piece = piece(0, 0, 0, PieceTurn.NONE, block(0, 0, 0));
        assertNull(footprintOf(pieces(piece), 2, 24));
        Piece overflowing = piece(
                Integer.MAX_VALUE, 0, 0, PieceTurn.NONE, block(1, 0, 0));
        assertNull(footprintOf(pieces(overflowing), 0));

        try {
            footprintOf(pieces(piece), -1);
            fail("Expected negative padding to fail");
        } catch (IllegalArgumentException e) {
            assertEquals("padding must be non-negative", e.getMessage());
        }
    }

    @Test
    public void includesTheEntirePaddingBoundary() {
        Piece piece = piece(7, 14, -4, PieceTurn.NONE, block(0, 0, 0));

        StructureCarvingFootprint footprint = footprintOf(pieces(piece), 2, 25);

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
        Piece piece = new Piece(new AssembledPiece(new ResourceId("test", "piece"), 10, 20, 30, PieceTurn.NONE,
                10, 20, 30, 18, 24, 38), List.of(
                new StructurePieceColumns.PieceBlock(new BlockPosition(0, 0, 0), false),
                new StructurePieceColumns.PieceBlock(new BlockPosition(8, 4, 8), true)));

        StructureCarvingFootprint footprint = footprintOf(pieces(piece), 0);

        assertNotNull(footprint);
        assertEquals(10, footprint.minX());
        assertEquals(10, footprint.maxX());
        assertEquals(30, footprint.minZ());
        assertEquals(30, footprint.maxZ());
    }

    private record Piece(AssembledPiece assembled, List<StructurePieceColumns.PieceBlock> blocks) {
    }

    private static StructureCarvingFootprint footprintOf(List<Piece> pieces, int padding) {
        return footprintOf(pieces, padding, StructureCarvingFootprint.DEFAULT_MAX_CELLS);
    }

    private static StructureCarvingFootprint footprintOf(List<Piece> pieces, int padding, int maxCells) {
        Map<AssembledPiece, List<StructurePieceColumns.PieceBlock>> blocksOf = new IdentityHashMap<>();
        List<AssembledPiece> assembled = pieces == null ? null : new ArrayList<>();
        if (pieces != null) {
            for (Piece piece : pieces) {
                if (piece == null) {
                    assembled.add(null);
                    continue;
                }
                assembled.add(piece.assembled);
                blocksOf.put(piece.assembled, piece.blocks);
            }
        }
        return StructureCarvingFootprint.fromColumns(
                sink -> StructurePieceColumns.emit(assembled, blocksOf::get, sink), padding, maxCells);
    }

    private static Piece piece(int x, int y, int z, PieceTurn turn, int[]... blocks) {
        List<StructurePieceColumns.PieceBlock> pieceBlocks = new ArrayList<>();
        for (int[] block : blocks) {
            pieceBlocks.add(new StructurePieceColumns.PieceBlock(new BlockPosition(block[0], block[1], block[2]), false));
        }
        return new Piece(new AssembledPiece(new ResourceId("test", "piece"), x, y, z, turn, x, y, z, x, y, z), pieceBlocks);
    }

    private static List<Piece> pieces(Piece... pieces) {
        return new ArrayList<>(List.of(pieces));
    }

    private static int[] block(int x, int y, int z) {
        return new int[]{x, y, z};
    }
}
