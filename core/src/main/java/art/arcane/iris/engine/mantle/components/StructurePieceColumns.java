package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.framework.structure.assembly.AssembledPiece;
import com.volmit.geometry.BlockPosition;

import java.util.List;
import java.util.function.Function;

/** The columns an assembled structure occupies, for the carve footprint. */
final class StructurePieceColumns {
    private StructurePieceColumns() {
    }

    /** One block of a piece object: where it lies from the object's center, and whether it is air. */
    record PieceBlock(BlockPosition offset, boolean air) {
    }

    /**
     * Reports a column for every block of every piece that is not air, at the height the block lands, in the
     * order of the pieces; false when a block lands outside the integer range.
     */
    static boolean emit(List<AssembledPiece> pieces, Function<AssembledPiece, List<PieceBlock>> blocksOf,
                        StructureCarvingFootprint.ColumnSink sink) {
        if (pieces == null) {
            return true;
        }
        for (AssembledPiece piece : pieces) {
            if (piece == null) {
                continue;
            }
            List<PieceBlock> blocks = blocksOf.apply(piece);
            if (blocks == null) {
                continue;
            }
            for (PieceBlock block : blocks) {
                if (block.air()) {
                    continue;
                }
                BlockPosition rotated = piece.turn().rotate(block.offset());
                long worldX = (long) piece.x() + rotated.x();
                long worldY = (long) piece.y() + rotated.y();
                long worldZ = (long) piece.z() + rotated.z();
                if (worldX != (int) worldX || worldY != (int) worldY || worldZ != (int) worldZ) {
                    return false;
                }
                sink.column((int) worldX, (int) worldZ, (int) worldY, (int) worldY);
            }
        }
        return true;
    }
}
