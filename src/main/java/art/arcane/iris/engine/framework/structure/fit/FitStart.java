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

import art.arcane.iris.builtin.structure.StructureTerrain;
import art.arcane.iris.engine.framework.structure.NativeStructureFit;
import com.volmit.vanilla.DecorationStep;
import com.volmit.vanilla.StructureFit;
import com.volmit.vanilla.TerrainAdaptation;

import java.util.ArrayList;
import java.util.List;

/** A start the platform places in the chunk, with the fit {@code iris:structure} gives it, its pieces in world heights. */
final class FitStart {
    private final StructureFit.Start start;
    private final NativeStructureFit fit;
    private final List<FitPiece> pieces;

    FitStart(StructureFit.Start start, NativeStructureFit fit, int bottomY) {
        this.start = start;
        this.fit = fit;
        List<FitPiece> converted = new ArrayList<>(start.pieces().size());
        for (StructureFit.Piece piece : start.pieces()) {
            converted.add(new FitPiece(piece, bottomY));
        }
        this.pieces = List.copyOf(converted);
    }

    StructureFit.Start start() {
        return start;
    }

    String structureId() {
        return start.structure().toString();
    }

    NativeStructureFit fit() {
        return fit;
    }

    StructureTerrain terrain() {
        return fit.resolvedTerrain();
    }

    TerrainAdaptation adaptation() {
        return start.adaptation();
    }

    DecorationStep step() {
        return start.step();
    }

    int chunkX() {
        return start.chunkX();
    }

    int chunkZ() {
        return start.chunkZ();
    }

    List<FitPiece> pieces() {
        return pieces;
    }

    /** The box around every piece of the start. */
    Box contentBounds() {
        Box bounds = pieces.getFirst().box();
        for (FitPiece piece : pieces) {
            bounds = bounds.encapsulating(piece.box());
        }
        return bounds;
    }

    Object key() {
        return start.key();
    }
}
