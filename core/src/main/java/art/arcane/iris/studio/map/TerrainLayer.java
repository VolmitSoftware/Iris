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

/**
 * Terrain as the 3D noise view draws it: its columns and their block faces, for any hole finer terrain cuts in it.
 */
record TerrainLayer(NoiseTerrain terrain, BlockFaces faces) {
    /**
     * @param floorY how far down sides on the terrain's edge or next to a hole reach, so they meet the terrain beside it
     *               whatever its height
     */
    static TerrainLayer of(NoiseTerrain terrain, int floorY) {
        int reachedFloorY = terrain.isEmpty() ? floorY : Math.min(floorY, terrain.baseY());
        return new TerrainLayer(terrain, BlockFaces.of(terrain, reachedFloorY));
    }
}
