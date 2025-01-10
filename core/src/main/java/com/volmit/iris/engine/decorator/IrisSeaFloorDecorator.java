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

package com.volmit.iris.engine.decorator;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDecorationPart;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;

public class IrisSeaFloorDecorator extends IrisEngineDecorator {
    public IrisSeaFloorDecorator(Engine engine) {
        super(engine, "Sea Floor", IrisDecorationPart.SEA_FLOOR);
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max) {
        IrisDecorator decorator = getDecorator(biome, realX, realZ);

        if (decorator != null) {
            var bdx = data.get(x, height - 1, z);
            if (!decorator.isStacking()) {
                var bd = decorator.getBlockData100(biome, getRng(), realX, height, realZ, getData());
                if ((!canGoOn(bd, bdx)
                        || (bd instanceof Bisected
                        ? (data.get(x, height, z).isOccluding() || data.get(x, height + 1, z).isOccluding())
                        : data.get(x, height, z).isOccluding()))
                        && !decorator.isForcePlace() && decorator.getForceBlock() == null)
                    return;

                if (!decorator.isForcePlace() && !decorator.getSlopeCondition().isDefault()
                        && !decorator.getSlopeCondition().isValid(getComplex().getSlopeStream().get(realX, realZ))) {
                    return;
                }

                if (bd instanceof Bisected) {
                    bd = bd.clone();
                    ((Bisected) bd).setHalf(Bisected.Half.TOP);
                    try {
                        data.set(x, height + 1, z, bd);
                    } catch (Throwable e) {
                        Iris.reportError(e);
                    }
                    bd = bd.clone();
                    ((Bisected) bd).setHalf(Bisected.Half.BOTTOM);
                }

                if (height >= 0 || height < getEngine().getHeight()) {
                    data.set(x, height, z, bd);
                }
            } else {
                var bd = decorator.getBlockData100(biome, getRng(), realX, height, realZ, getData());
                if (((!canGoOn(bd, bdx) || data.get(x, height, z).isOccluding()) && (!decorator.isForcePlace() && decorator.getForceBlock() == null)))
                    return;

                int stack = decorator.getHeight(getRng().nextParallelRNG(Cache.key(realX, realZ)), realX, realZ, getData());
                if (decorator.isScaleStack()) {
                    int maxStack = max - height;
                    stack = (int) Math.ceil((double) maxStack * ((double) stack / 100));
                } else stack = Math.min(stack, max - height);

                for (int i = 1; i < stack; i++) {
                    var block = data.get(x, height + i + 1, z);
                    if ((block.isOccluding()) && (!decorator.isForcePlace() && decorator.getForceBlock() == null))
                        return;
                }

                if (stack == 1) {
                    data.set(x, height, z, decorator.getBlockDataForTop(biome, getRng(), realX, height, realZ, getData()));
                    return;
                }

                for (int i = 0; i < stack; i++) {
                    int h = height + i;
                    if (h > max || h > getEngine().getHeight()) {
                        continue;
                    }

                    double threshold = ((double) i) / (stack - 1);
                    BlockData block = threshold >= decorator.getTopThreshold() ?
                            decorator.getBlockDataForTop(biome, getRng(), realX, h, realZ, getData()) :
                            decorator.getBlockData100(biome, getRng(), realX, h, realZ, getData());
                    if (block instanceof Waterlogged wblock)
                        wblock.setWaterlogged(true);

                    data.set(x, h, z, block);
                }
            }
        }
    }
}
