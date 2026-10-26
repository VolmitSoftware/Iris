/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object.value;

import art.arcane.iris.engine.object.annotations.Desc;

/** One of the six axis directions, named by its axis and sign as packs write it. */
@Desc("A direction object")
public enum IrisDirection {
    @Desc("0, 1, 0")
    UP_POSITIVE_Y(0, 1, 0),
    @Desc("0, -1, 0")
    DOWN_NEGATIVE_Y(0, -1, 0),
    @Desc("0, 0, -1")
    NORTH_NEGATIVE_Z(0, 0, -1),
    @Desc("0, 0, 1")
    SOUTH_POSITIVE_Z(0, 0, 1),
    @Desc("1, 0, 0")
    EAST_POSITIVE_X(1, 0, 0),
    @Desc("-1, 0, 0")
    WEST_NEGATIVE_X(-1, 0, 0);

    private final int x;
    private final int y;
    private final int z;

    IrisDirection(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
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

    public IrisDirection reverse() {
        return switch (this) {
            case DOWN_NEGATIVE_Y -> UP_POSITIVE_Y;
            case EAST_POSITIVE_X -> WEST_NEGATIVE_X;
            case NORTH_NEGATIVE_Z -> SOUTH_POSITIVE_Z;
            case SOUTH_POSITIVE_Z -> NORTH_NEGATIVE_Z;
            case UP_POSITIVE_Y -> DOWN_NEGATIVE_Y;
            case WEST_NEGATIVE_X -> EAST_POSITIVE_X;
        };
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
}
