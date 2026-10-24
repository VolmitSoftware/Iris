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

package art.arcane.iris.engine.mantle.components;

import com.volmit.geometry.BlockPosition;

/**
 * How Iris turns an object it hangs under an island: upside down about X, then a quarter turn about Y of {@code yRotation}
 * degrees (0, 90, 180 or 270), in the object's own block positions.
 */
record UndersideTurn(int yRotation) {
    private static final int FULL_TURN_DEGREES = 360;

    BlockPosition turn(int x, int y, int z) {
        int flippedY = -y;
        int flippedZ = -z;
        return switch (Math.floorMod(yRotation, FULL_TURN_DEGREES)) {
            case 90 -> new BlockPosition(flippedZ, flippedY, -x);
            case 180 -> new BlockPosition(-x, flippedY, -flippedZ);
            case 270 -> new BlockPosition(-flippedZ, flippedY, x);
            default -> new BlockPosition(x, flippedY, flippedZ);
        };
    }
}
