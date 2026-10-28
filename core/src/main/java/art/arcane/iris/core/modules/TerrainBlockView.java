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

package art.arcane.iris.core.modules;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.placement.PlacementTerrain;
import com.volmit.world.BlockView;

/** The blocks a placement's terrain holds before the placement's writes, within the placement area. */
final class TerrainBlockView implements BlockView {
    private final PlacementTerrain terrain;
    private final BlockBox bounds;

    TerrainBlockView(PlacementTerrain terrain, BlockBox bounds) {
        this.terrain = terrain;
        this.bounds = bounds;
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    public BlockState get(int worldX, int y, int worldZ) {
        if (!bounds.contains(worldX, y, worldZ)) {
            throw new IndexOutOfBoundsException("Block " + worldX + ", " + y + ", " + worldZ + " is outside the placement area " + bounds);
        }
        return terrain.get(worldX, y, worldZ);
    }
}
