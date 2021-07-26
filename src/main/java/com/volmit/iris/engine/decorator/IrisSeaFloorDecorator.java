/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.decorator;

import com.volmit.iris.engine.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.hunk.Hunk;
import com.volmit.iris.engine.object.DecorationPart;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.util.documentation.BlockCoordinates;
import org.bukkit.block.data.BlockData;

public class IrisSeaFloorDecorator extends IrisEngineDecorator {
    public IrisSeaFloorDecorator(Engine engine) {
        super(engine, "Sea Floor", DecorationPart.SEA_FLOOR);
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max) {
        IrisDecorator decorator = getDecorator(biome, realX, realZ);

        if (decorator != null) {
            if (!decorator.isStacking()) {
                if (height >= 0 || height < getEngine().getHeight()) {
                    data.set(x, height, z, decorator.getBlockData100(biome, getRng(), realX, realZ, getData()));
                }
            } else {
                int stack = decorator.getHeight(getRng().nextParallelRNG(Cache.key(realX, realZ)), realX, realZ, getData());
                stack = Math.min(stack, max - height + 2);

                BlockData top = decorator.getBlockDataForTop(biome, getRng(), realX, realZ, getData());
                BlockData fill = decorator.getBlockData100(biome, getRng(), realX, realZ, getData());

                for (int i = 0; i < stack; i++) {
                    if (height + i > max || height + i > getEngine().getHeight()) {
                        continue;
                    }

                    double threshold = ((double) i) / (stack - 1);
                    data.set(x, height + i, z, threshold >= decorator.getTopThreshold() ? top : fill);
                }
            }
        }

    }
}
