/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.engine.framework.structure.fit;

import com.volmit.vanilla.PieceKind;
import com.volmit.vanilla.StructureFit;
import com.volmit.vanilla.TemplateCells;
import com.volmit.world.BlockPosition;

import java.util.ArrayList;
import java.util.List;

/** A piece of a start in world heights with inclusive bounds. */
final class FitPiece {
    private final StructureFit.Piece piece;
    private final Box box;
    private final int bottomY;

    FitPiece(StructureFit.Piece piece, int bottomY) {
        this.piece = piece;
        this.box = Box.of(piece.box(), bottomY);
        this.bottomY = bottomY;
    }

    Box box() {
        return box;
    }

    PieceKind kind() {
        return piece.kind();
    }

    boolean isJigsaw() {
        return piece.kind() != PieceKind.FIXED;
    }

    boolean isRigid() {
        return piece.kind() == PieceKind.RIGID;
    }

    /** Whether the piece keeps its shape over the terrain: every piece built in code, and rigid jigsaw pieces. */
    boolean isRigidOrFixed() {
        return piece.kind() != PieceKind.TERRAIN_MATCHING;
    }

    int groundLevelDelta() {
        return piece.groundLevelDelta();
    }

    /** The world height of the piece's ground level. */
    int groundY() {
        return box.minY() + piece.groundLevelDelta();
    }

    /** The junctions, their Y the world height of the ground at their source. */
    List<BlockPosition> junctions() {
        List<BlockPosition> junctions = new ArrayList<>(piece.junctions().size());
        for (BlockPosition junction : piece.junctions()) {
            junctions.add(new BlockPosition(junction.x(), junction.y() + bottomY, junction.z()));
        }
        return junctions;
    }

    TemplateCells cells(Box region) {
        return piece.cells(region.toApi(bottomY));
    }

    /** The cell's world height. */
    int cellY(TemplateCells cells, int cell) {
        return cells.y(cell) + bottomY;
    }

    void columns(StructureFit.ColumnVisitor visitor) {
        piece.columns((x, z, minY, maxY) -> visitor.column(x, z, minY + bottomY, maxY + bottomY));
    }

    /** Legacy template air inside {@code clip}, in world heights. */
    List<BlockPosition> legacyAir(Box clip) {
        List<BlockPosition> air = piece.legacyAir(clip.toApi(bottomY));
        List<BlockPosition> world = new ArrayList<>(air.size());
        for (BlockPosition position : air) {
            world.add(new BlockPosition(position.x(), position.y() + bottomY, position.z()));
        }
        return world;
    }
}
