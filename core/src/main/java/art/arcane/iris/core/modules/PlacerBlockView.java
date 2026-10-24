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

import art.arcane.iris.engine.object.placement.IObjectPlacer;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.world.BlockView;

/**
 * The blocks an Iris object placer holds before a placement's writes, within the placement area. The placer's
 * heights count from the dimension's bottom, like IrisApi's.
 */
final class PlacerBlockView implements BlockView {
    private final IObjectPlacer placer;
    private final BlockBox bounds;
    private final PlatformBlockStates states;

    PlacerBlockView(IObjectPlacer placer, BlockBox bounds, PlatformBlockStates states) {
        this.placer = placer;
        this.bounds = bounds;
        this.states = states;
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
        return states.toApi(placer.get(worldX, y, worldZ));
    }
}
