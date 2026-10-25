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

package art.arcane.iris.studio.map;

import java.util.List;
import java.util.function.Predicate;

/**
 * A tile's ground as the 3D noise view shows it, less the finer tiles inside it that are ready to fill their part. The
 * hole is cut when drawing, from the fillers that are drawable by then, so ground is never left out before what fills
 * it can be drawn.
 */
record HoledLayer(TerrainTile tile, TerrainLayer layer, List<TerrainTile> fillers) {
    TerrainHole holeAmong(Predicate<TerrainTile> drawable) {
        return TerrainHole.of(tile, fillers.stream().filter(drawable).toList());
    }

    TerrainHole holeOfAllFillers() {
        return TerrainHole.of(tile, fillers);
    }

    NoiseTerrain terrain() {
        return layer.terrain();
    }
}
