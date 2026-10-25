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

package art.arcane.iris.studio.map.components;

/**
 * What a flat map tile shows.
 */
public enum TileType {
    BIOME, BIOME_LAND, BIOME_SEA, REGION, REGION_PREVIEW, CAVE_LAND, HEIGHT, RIVER;

    public boolean isRegionView() {
        return this == REGION || this == REGION_PREVIEW;
    }

    /**
     * Whether the view can draw the noise that picks what it shows: the regions, or the surface biomes.
     */
    public boolean drawsPickNoise() {
        return isRegionView() || this == BIOME;
    }
}
