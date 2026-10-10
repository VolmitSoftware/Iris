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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisShoreLineDecorator extends IrisEngineDecorator {
    public IrisShoreLineDecorator(Engine engine) {
        super(engine, "Shore Line", DecorationPart.SHORE_LINE);
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, int height, int max) {
        if (height != getDimension().getFluidHeight()) {
            return;
        }

        double complexFluidHeight = getComplex().getFluidHeight();
        ProceduralStream<Double> heightStream = getComplex().getHeightStream();
        if (Math.round(heightStream.get(realX1, realZ)) >= complexFluidHeight
                && Math.round(heightStream.get(realX_1, realZ)) >= complexFluidHeight
                && Math.round(heightStream.get(realX, realZ1)) >= complexFluidHeight
                && Math.round(heightStream.get(realX, realZ_1)) >= complexFluidHeight) {
            return;
        }

        RNG rng = getRNG(realX, realZ);
        ResolvedDecorator decorator = DecoratorCore.pickDecorator(decorators.decorators(biome, getPart()), rng, realX, realZ, decorators.log());

        if (decorator == null) {
            return;
        }

        if (decorator.slopeBlocks(() -> getComplex().getSlopeStream().get(realX, realZ))) {
            return;
        }

        PlatformBlockState support = data.get(x, height, z);
        if (support == null || !support.isSolid()) {
            return;
        }

        if (!decorator.isStacking()) {
            int targetY = height + 1;
            if (targetY >= data.getHeight()
                    || !DecoratorCore.canReplaceStackTarget(data.get(x, targetY, z), false)) {
                return;
            }
            PlatformBlockState block = decorator.blockAt(biome, realX, height, realZ);
            if (block != null && DecoratorCore.isValidShorelineSupport(decorator, block, support)) {
                data.set(x, targetY, z, block);
            }
            return;
        }

        int stack = decorator.stackHeight(realX, realZ);
        if (decorator.isScaleStack()) {
            stack = (int) Math.ceil((double) (max - height) * ((double) stack / 100));
        } else {
            stack = Math.min(max - height, stack);
        }

        if (stack == 1) {
            int targetY = height + 1;
            if (targetY >= data.getHeight() || !DecoratorCore.canReplaceStackTarget(data.get(x, targetY, z), false)) {
                return;
            }

            PlatformBlockState block = decorator.topBlockAt(biome, realX, height, realZ);
            if (block != null && DecoratorCore.isValidShorelineSupport(decorator, block, support)) {
                data.set(x, targetY, z, block);
            }
            return;
        }

        for (int i = 0; i < stack; i++) {
            int h = height + i;
            int targetY = h + 1;
            if (targetY >= data.getHeight()
                    || !DecoratorCore.canReplaceStackTarget(data.get(x, targetY, z), false)) {
                break;
            }
            double threshold = ((double) i) / (stack - 1);
            PlatformBlockState block = threshold >= decorator.topThreshold()
                    ? decorator.topBlockAt(biome, realX, h, realZ)
                    : decorator.blockAt(biome, realX, h, realZ);
            if (block == null) {
                break;
            }
            if (i == 0 && !DecoratorCore.isValidShorelineSupport(decorator, block, support)) {
                break;
            }
            data.set(x, targetY, z, block);
        }
    }
}
