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

package art.arcane.iris.engine.object.structure.jigsaw;

import com.volmit.config.Desc;
import com.volmit.geometry.BlockFace;

/**
 * One of the six axis directions, named by its axis and sign as jigsaw packs write them ({@code EAST_POSITIVE_X}). The
 * constants are part of the pack format, which is why the API's {@link BlockFace} ({@code EAST}) cannot stand in for it.
 */
@Desc("A direction object")
public enum JigsawDirection {
    @Desc("0, 1, 0")
    UP_POSITIVE_Y(0, 1, 0, BlockFace.UP),
    @Desc("0, -1, 0")
    DOWN_NEGATIVE_Y(0, -1, 0, BlockFace.DOWN),
    @Desc("0, 0, -1")
    NORTH_NEGATIVE_Z(0, 0, -1, BlockFace.NORTH),
    @Desc("0, 0, 1")
    SOUTH_POSITIVE_Z(0, 0, 1, BlockFace.SOUTH),
    @Desc("1, 0, 0")
    EAST_POSITIVE_X(1, 0, 0, BlockFace.EAST),
    @Desc("-1, 0, 0")
    WEST_NEGATIVE_X(-1, 0, 0, BlockFace.WEST);

    private final int x;
    private final int y;
    private final int z;
    private final BlockFace face;

    JigsawDirection(int x, int y, int z, BlockFace face) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.face = face;
    }

    @Override
    public String toString() {
        return switch (this) {
            case DOWN_NEGATIVE_Y -> "Down";
            case EAST_POSITIVE_X -> "East";
            case NORTH_NEGATIVE_Z -> "North";
            case SOUTH_POSITIVE_Z -> "South";
            case UP_POSITIVE_Y -> "Up";
            case WEST_NEGATIVE_X -> "West";
        };
    }

    public boolean isVertical() {
        return this == DOWN_NEGATIVE_Y || this == UP_POSITIVE_Y;
    }

    public JigsawDirection reverse() {
        return switch (this) {
            case DOWN_NEGATIVE_Y -> UP_POSITIVE_Y;
            case EAST_POSITIVE_X -> WEST_NEGATIVE_X;
            case NORTH_NEGATIVE_Z -> SOUTH_POSITIVE_Z;
            case SOUTH_POSITIVE_Z -> NORTH_NEGATIVE_Z;
            case UP_POSITIVE_Y -> DOWN_NEGATIVE_Y;
            case WEST_NEGATIVE_X -> EAST_POSITIVE_X;
        };
    }

    /** This direction turned around Y by {@code quarterTurns} quarter turns, the way an object's rotation turns it (north becomes west); vertical directions stay. */
    public JigsawDirection turnedBy(int quarterTurns) {
        if (isVertical()) {
            return this;
        }
        JigsawDirection turned = this;
        for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
            turned = switch (turned) {
                case NORTH_NEGATIVE_Z -> WEST_NEGATIVE_X;
                case WEST_NEGATIVE_X -> SOUTH_POSITIVE_Z;
                case SOUTH_POSITIVE_Z -> EAST_POSITIVE_X;
                case EAST_POSITIVE_X -> NORTH_NEGATIVE_Z;
                case UP_POSITIVE_Y, DOWN_NEGATIVE_Y -> turned;
            };
        }
        return turned;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    /** The API face this direction is. */
    public BlockFace face() {
        return face;
    }
}
