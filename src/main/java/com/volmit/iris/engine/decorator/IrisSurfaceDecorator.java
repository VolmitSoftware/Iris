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

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.biome.InferredType;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.decoration.IrisDecorationPart;
import com.volmit.iris.engine.object.decoration.IrisDecorator;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;

public class IrisSurfaceDecorator extends IrisEngineDecorator {
    public IrisSurfaceDecorator(Engine engine) {
        super(engine, "Surface", IrisDecorationPart.NONE);
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max) {
        if (biome.getInferredType().equals(InferredType.SHORE) && height < getDimension().getFluidHeight()) {
            return;
        }

        BlockData bd, bdx;
        IrisDecorator decorator = getDecorator(biome, realX, realZ);
        bdx = data.get(x, height, z);
        boolean underwater = height < getDimension().getFluidHeight();

        if (decorator != null) {
            if (!decorator.isStacking()) {
                bd = decorator.getBlockData100(biome, getRng(), realX, height, realZ, getData());

                if (!underwater) {
                    if (!canGoOn(bd, bdx)) {
                        return;
                    }
                }

                if (bd instanceof Bisected) {
                    bd = bd.clone();
                    ((Bisected) bd).setHalf(Bisected.Half.TOP);
                    try {
                        data.set(x, height + 2, z, bd);
                    } catch (Throwable e) {
                        Iris.reportError(e);
                    }
                    bd = bd.clone();
                    ((Bisected) bd).setHalf(Bisected.Half.BOTTOM);
                }

                data.set(x, height + 1, z, bd);

            } else {
                if (height < getDimension().getFluidHeight()) {
                    max = getDimension().getFluidHeight();
                }

                int stack = decorator.getHeight(getRng().nextParallelRNG(Cache.key(realX, realZ)), realX, realZ, getData());
                if (decorator.isScaleStack()) {
                    int maxStack = max - height;
                    stack = (int) Math.ceil((double) maxStack * ((double) stack / 100));
                } else stack = Math.min(height - max, stack);

                if (stack == 1) {
                    data.set(x, height, z, decorator.getBlockDataForTop(biome, getRng(), realX, height, realZ, getData()));
                    return;
                }

                for (int i = 0; i < stack; i++) {
                    int h = height + i;
                    double threshold = ((double) i) / (stack - 1);
                    bd = threshold >= decorator.getTopThreshold() ?
                            decorator.getBlockDataForTop(biome, getRng(), realX, h, realZ, getData()) :
                            decorator.getBlockData100(biome, getRng(), realX, h, realZ, getData());

                    if (bd == null) {
                        break;
                    }

                    if (i == 0 && !underwater && !canGoOn(bd, bdx)) {
                        break;
                    }

                    if (underwater && height + 1 + i > getDimension().getFluidHeight()) {
                        break;
                    }

                    data.set(x, height + 1 + i, z, bd);
                }
            }
        }
    }
}
