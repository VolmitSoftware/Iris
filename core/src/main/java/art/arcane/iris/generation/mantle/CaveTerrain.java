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

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.List;

/** What listed cave carving reads of the world around a chunk. */
interface CaveTerrain {
    /** The region, surface biome and cave biome that decide which caves carve at the column. */
    CavePlace placeAt(int worldX, int worldZ);

    /**
     * The cave biomes that dimension carving entries put at each column of the chunk, one band of world heights at a
     * time. Caves a band's biome lists carve only inside that band.
     */
    List<CaveBand> bandsOf(int chunkX, int chunkZ);

    /**
     * @param region       the region of the column
     * @param surfaceBiome the biome at the surface of the column
     * @param caveBiome    the cave biome the column's caves belong to: the surface biome where the cave is too shallow to
     *                     have its own, as {@code caveMinDepthBelowSurface} says
     */
    record CavePlace(IrisRegion region, IrisBiome surfaceBiome, IrisBiome caveBiome) {
    }

    /**
     * @param lowestWorldY  lowest world height of the band
     * @param highestWorldY highest world height of the band, included
     * @param biomeByColumn the cave biome in each column of the chunk indexed as {@code (localX << 4) | localZ}; null where the entry gives none
     */
    record CaveBand(int lowestWorldY, int highestWorldY, IrisBiome[] biomeByColumn) {
    }
}
