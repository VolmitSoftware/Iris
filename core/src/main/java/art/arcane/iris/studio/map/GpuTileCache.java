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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The ground on the graphics card by where it lies, so ground sampled anew for a place replaces what was there, which
 * covers the place until then. Only for the GPU thread.
 */
final class GpuTileCache {
    private final LinkedHashMap<Place, GpuTile> leastRecentlyUsedFirst = new LinkedHashMap<>(16, 0.75F, true);

    GpuTile get(Place place) {
        return leastRecentlyUsedFirst.get(place);
    }

    void put(Place place, GpuTile tile) {
        leastRecentlyUsedFirst.put(place, tile);
    }

    /**
     * Deletes ground not shown whose terrain is gone from memory, as it can never be drawn again, then the least
     * recently used until the cache fits, sparing what is shown.
     */
    void forgetBeyond(long mostBytes, Set<Place> shown) {
        long bytes = 0L;
        Iterator<Map.Entry<Place, GpuTile>> oldestFirst = leastRecentlyUsedFirst.entrySet().iterator();
        while (oldestFirst.hasNext()) {
            Map.Entry<Place, GpuTile> entry = oldestFirst.next();
            if (!shown.contains(entry.getKey()) && entry.getValue().showsForgottenTerrain()) {
                entry.getValue().delete();
                oldestFirst.remove();
            } else {
                bytes += entry.getValue().bytes();
            }
        }
        oldestFirst = leastRecentlyUsedFirst.entrySet().iterator();
        while (bytes > mostBytes && oldestFirst.hasNext()) {
            Map.Entry<Place, GpuTile> entry = oldestFirst.next();
            if (!shown.contains(entry.getKey())) {
                bytes -= entry.getValue().bytes();
                entry.getValue().delete();
                oldestFirst.remove();
            }
        }
    }

    /**
     * Where a tile's ground lies, and how coarse it is.
     */
    record Place(int originX, int originZ, int step) {
        static Place of(NoiseTerrain terrain) {
            return new Place(terrain.originX(), terrain.originZ(), terrain.step());
        }

        // The same place as the tile's terrain once sampled.
        static Place of(TerrainTile tile) {
            TerrainGrid grid = tile.grid();
            return new Place(grid.originX(), grid.originZ(), grid.step());
        }
    }
}
