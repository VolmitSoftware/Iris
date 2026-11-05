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

package art.arcane.iris.core.studio.map;

import java.util.function.BooleanSupplier;

/** The cells of a tile with its ring: {@code size} per side, cell (0, 0) sampling ({@code startX}, {@code startZ}). */
record TileGrid(int size, double startX, double startZ, double step, BooleanSupplier cancelled) {
    double worldX(int gridX) {
        return startX + gridX * step;
    }

    double worldZ(int gridZ) {
        return startZ + gridZ * step;
    }
}
