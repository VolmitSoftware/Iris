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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDecorationPart;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.PointedDripstone;

public class IrisCeilingDecorator extends IrisEngineDecorator {
    public IrisCeilingDecorator(Engine engine) {
        super(engine, "Ceiling", IrisDecorationPart.CEILING);
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1, Hunk<BlockData> data, IrisBiome biome, int height, int max) {
        RNG rng = getRNG(realX, realZ);
        IrisDecorator decorator = getDecorator(rng, biome, realX, realZ);

        if (decorator != null) {
            if (!decorator.isStacking()) {
                data.set(x, height, z, fixFaces(decorator.getBlockData100(biome, rng, realX, height, realZ, getData()), data, x, z, realX, height, realZ));
            } else {
                int stack = decorator.getHeight(rng, realX, realZ, getData());
                if (decorator.isScaleStack()) {
                    stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.getAbsoluteMaxStack());
                } else {
                    stack = Math.min(max, stack);
                }

                if (stack == 1) {
                    data.set(x, height, z, decorator.getBlockDataForTop(biome, rng, realX, height, realZ, getData()));
                    return;
                }

                for (int i = 0; i < stack; i++) {
                    int h = height - i;
                    if (h < getEngine().getMinHeight()) {
                        continue;
                    }

                    double threshold = (((double) i) / (double) (stack - 1));

                    BlockData bd = threshold >= decorator.getTopThreshold() ?
                            decorator.getBlockDataForTop(biome, rng, realX, h, realZ, getData()) :
                            decorator.getBlockData100(biome, rng, realX, h, realZ, getData());

                    if (bd instanceof PointedDripstone) {
                        PointedDripstone.Thickness th = PointedDripstone.Thickness.BASE;

                        if (stack == 2) {
                            th = PointedDripstone.Thickness.FRUSTUM;

                            if (i == stack - 1) {
                                th = PointedDripstone.Thickness.TIP;
                            }
                        } else {
                            if (i == stack - 1) {
                                th = PointedDripstone.Thickness.TIP;
                            } else if (i == stack - 2) {
                                th = PointedDripstone.Thickness.FRUSTUM;
                            }
                        }


                        bd = Material.POINTED_DRIPSTONE.createBlockData();
                        ((PointedDripstone) bd).setThickness(th);
                        ((PointedDripstone) bd).setVerticalDirection(BlockFace.DOWN);
                    }

                    data.set(x, h, z, bd);
                }
            }
        }
    }
}
