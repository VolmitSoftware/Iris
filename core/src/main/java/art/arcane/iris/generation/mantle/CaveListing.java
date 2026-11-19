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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cave.field.HeightBand;

import java.util.List;

/**
 * The caves that carve in one chunk, in the order they were listed: the first cave listed owns a block that several carve.
 *
 * @param caves           each listed cave with how much each column lists it
 * @param opensToSurface  whether any of the caves cuts through the surface
 */
record CaveListing(List<ListedColumns> caves, boolean opensToSurface) {
    boolean isEmpty() {
        return caves.isEmpty();
    }

    /**
     * @param cave   the cave
     * @param weight how much each column of the chunk lists the cave, from 0 to 1, indexed as {@code (localX << 4) | localZ}
     * @param band   the heights the cave is limited to, from the dimension's bottom; unlimited unless a carving band lists it
     */
    record ListedColumns(ListedCave cave, double[] weight, HeightBand band) {
    }
}
