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

package art.arcane.iris.engine.framework.structure.assembly;

import com.volmit.resource.ResourceId;

import java.util.Objects;

/**
 * One piece of an assembled structure: the pack object, where its origin lands, how it is turned and the blocks it covers.
 * Heights are world Y, and the bounds are inclusive.
 */
public record AssembledPiece(ResourceId object, int x, int y, int z, PieceTurn turn,
                             int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public AssembledPiece {
        Objects.requireNonNull(object, "object");
        Objects.requireNonNull(turn, "turn");
    }

    /** This piece moved up by {@code shiftY} blocks. */
    public AssembledPiece shiftedUp(int shiftY) {
        return new AssembledPiece(object, x, y + shiftY, z, turn, minX, minY + shiftY, minZ, maxX, maxY + shiftY, maxZ);
    }
}
