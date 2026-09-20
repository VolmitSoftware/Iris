package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.structure.StructureCarvingFootprint;
import art.arcane.volmlib.util.structure.StructureCarvingFootprint.ColumnSink;

final class StructurePieceColumns {
    private static final int DEFAULT_MAX_CELLS = 1_048_576;

    private StructurePieceColumns() {
    }

    static StructureCarvingFootprint from(KList<PlacedStructurePiece> pieces, int padding) {
        return from(pieces, padding, DEFAULT_MAX_CELLS);
    }

    static StructureCarvingFootprint from(KList<PlacedStructurePiece> pieces, int padding, int maxCells) {
        return StructureCarvingFootprint.fromColumns(sink -> emitPieceColumns(pieces, sink), padding, maxCells);
    }

    private static boolean emitPieceColumns(KList<PlacedStructurePiece> pieces, ColumnSink sink) {
        if (pieces == null) {
            return true;
        }
        for (PlacedStructurePiece piece : pieces) {
            if (piece == null) {
                continue;
            }
            IrisObject object = piece.getObject();
            IrisObjectRotation rotation = piece.getRotation();
            if (object == null || rotation == null || object.getBlocks() == null) {
                continue;
            }
            for (IrisBlockVector local : object.getBlocks().keys()) {
                if (local == null || B.isAir(object.getBlocks().get(local))) {
                    continue;
                }
                IrisBlockVector rotated = rotation.rotate(local.clone());
                long worldX = (long) piece.getX() + rotated.getBlockX();
                long worldY = (long) piece.getY() + rotated.getBlockY();
                long worldZ = (long) piece.getZ() + rotated.getBlockZ();
                if (!fitsInteger(worldX) || !fitsInteger(worldY) || !fitsInteger(worldZ)) {
                    return false;
                }
                sink.column((int) worldX, (int) worldZ, (int) worldY, (int) worldY);
            }
        }
        return true;
    }

    private static boolean fitsInteger(long value) {
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }
}
