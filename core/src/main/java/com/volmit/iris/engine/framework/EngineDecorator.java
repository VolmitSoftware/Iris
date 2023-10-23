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

package com.volmit.iris.engine.framework;

import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import org.bukkit.block.data.BlockData;

public interface EngineDecorator extends EngineComponent {

    @BlockCoordinates
    void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max);

    @BlockCoordinates
    default void decorate(int x, int z, int realX, int realZ, Hunk<BlockData> data, IrisBiome biome, int height, int max) {
        decorate(x, z, realX, realX, realX, realZ, realZ, realZ, data, biome, height, max);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    default boolean canGoOn(BlockData decorant, BlockData atop) {
        if (atop == null || B.isAir(atop)) {
            return false;
        }

        return B.canPlaceOnto(decorant.getMaterial(), atop.getMaterial());
    }
}
