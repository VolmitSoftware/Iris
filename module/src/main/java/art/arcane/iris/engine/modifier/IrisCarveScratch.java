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

package art.arcane.iris.engine.modifier;

import com.volmit.resource.ResourceId;

import java.util.HashMap;
import java.util.Map;

final class IrisCarveScratch {
    final CarveColumnMask[] columnMasks = new CarveColumnMask[256];
    final CarveColumnMask[] boundaryMasks = new CarveColumnMask[256];
    final int[] surfaceHeights = new int[256];
    final CarveWallBuffer walls = new CarveWallBuffer(512);
    final Map<ResourceId, ResourceId> customBiomeCache = new HashMap<>();
    int[] upperSurfaceHeights;
    boolean customCaveBiomePresent;

    IrisCarveScratch() {
        for (int index = 0; index < columnMasks.length; index++) {
            columnMasks[index] = new CarveColumnMask();
            boundaryMasks[index] = new CarveColumnMask();
        }
    }

    int[] getOrCreateUpperSurfaceHeights() {
        if (upperSurfaceHeights == null) {
            upperSurfaceHeights = new int[256];
        }
        return upperSurfaceHeights;
    }

    void reset() {
        for (int index = 0; index < columnMasks.length; index++) {
            columnMasks[index].clear();
            boundaryMasks[index].clear();
        }
        walls.clear();
        customBiomeCache.clear();
        customCaveBiomePresent = false;
    }
}
