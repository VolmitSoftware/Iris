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

import com.volmit.block.BlockTurn;
import com.volmit.geometry.BlockPosition;

/**
 * How a structure's assembly turns a piece: around the vertical axis by whole quarter turns, in the direction Iris's
 * object rotation turns (north becomes west).
 *
 * @param yDegrees degrees around Y; a multiple of 90
 */
public record PieceTurn(int yDegrees) {
    public static final PieceTurn NONE = new PieceTurn(0);

    private static final int QUARTER_TURN_DEGREES = 90;
    private static final int QUARTER_TURNS_PER_CIRCLE = 4;

    public PieceTurn {
        if (yDegrees % QUARTER_TURN_DEGREES != 0) {
            throw new IllegalArgumentException("A piece turns by whole quarter turns, not " + yDegrees + " degrees");
        }
    }

    public static PieceTurn aroundY(int yDegrees) {
        return new PieceTurn(yDegrees);
    }

    /** The quarter turns this turn makes, 0 to 3. */
    public int quarterTurns() {
        return Math.floorMod(yDegrees / QUARTER_TURN_DEGREES, QUARTER_TURNS_PER_CIRCLE);
    }

    /** The directions this turn turns, for the facing, axis and connections of block states. */
    public BlockTurn blockTurn() {
        return (x, y, z) -> switch (quarterTurns()) {
            case 1 -> new BlockTurn.Direction(z, y, -x);
            case 2 -> new BlockTurn.Direction(-x, y, -z);
            case 3 -> new BlockTurn.Direction(-z, y, x);
            default -> new BlockTurn.Direction(x, y, z);
        };
    }

    /** {@code position} turned around the origin. */
    public BlockPosition rotate(BlockPosition position) {
        return switch (quarterTurns()) {
            case 1 -> new BlockPosition(position.z(), position.y(), -position.x());
            case 2 -> new BlockPosition(-position.x(), position.y(), -position.z());
            case 3 -> new BlockPosition(-position.z(), position.y(), position.x());
            default -> position;
        };
    }
}
