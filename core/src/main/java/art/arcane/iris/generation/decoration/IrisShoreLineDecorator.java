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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisShoreLineDecorator extends IrisEngineDecorator {
    private final RNG partRNG;

    public IrisShoreLineDecorator(Engine engine) {
        super(engine, "Shore Line", IrisDecorationPart.SHORE_LINE);
        this.partRNG = new RNG(DecoratorCore.partSeed(getSeed(), IrisDecorationPart.SHORE_LINE));
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, int height, int max) {
        double localFluidHeight = getEngine().getMantle().getFluidHeight(realX, realZ);
        if (height != Math.round(localFluidHeight)) {
            return;
        }

        ProceduralStream<Double> heightStream = getComplex().getHeightStream();
        if (Math.round(heightStream.get(realX1, realZ)) >= getEngine().getMantle().getFluidHeight(realX1, realZ)
                && Math.round(heightStream.get(realX_1, realZ)) >= getEngine().getMantle().getFluidHeight(realX_1, realZ)
                && Math.round(heightStream.get(realX, realZ1)) >= getEngine().getMantle().getFluidHeight(realX, realZ1)
                && Math.round(heightStream.get(realX, realZ_1)) >= getEngine().getMantle().getFluidHeight(realX, realZ_1)) {
            return;
        }

        place(x, z, realX, realZ, data, biome, height, max);
    }

    public void decorateAcceptedShore(
            int x,
            int z,
            int realX,
            int realZ,
            Hunk<PlatformBlockState> data,
            IrisBiome biome,
            int height,
            int max
    ) {
        place(x, z, realX, realZ, data, biome, height, max);
    }

    private void place(
            int x,
            int z,
            int realX,
            int realZ,
            Hunk<PlatformBlockState> data,
            IrisBiome biome,
            int height,
            int max
    ) {
        RNG rng = getRNG(realX, realZ);
        IrisDecorator decorator = DecoratorCore.pickDecorator(biome, getPart(), partRNG, rng, getData(), realX, realZ);

        if (decorator == null) {
            return;
        }

        if (!decorator.isForcePlace() && !decorator.getSlopeCondition().isDefault()
                && !decorator.getSlopeCondition().isValid(getComplex().getSlopeStream().get(realX, realZ))) {
            return;
        }

        PlatformBlockState support = data.get(x, height, z);
        if (support == null || !support.isSolid()) {
            return;
        }

        IrisSurfaceDecorator.AquaticPlacementSnapshot aquaticSnapshot = IrisSurfaceDecorator.captureAquaticPlacement(
                decorator, getData(), data, x, z, height, max);
        if (!decorator.isStacking()) {
            int targetY = height + 1;
            if (targetY >= data.getHeight()
                    || !DecoratorCore.canReplaceStackTarget(data.get(x, targetY, z), false)) {
                return;
            }
            PlatformBlockState block = decorator.getBlockData100(biome, rng, realX, height, realZ, getData());
            if (block != null && DecoratorCore.isValidShorelineSupport(decorator, block, support)
                    && IrisSugarCane.canPlace(block, data, x, targetY, z, realX, realZ, getEngine())) {
                data.set(x, targetY, z, block);
                aquaticSnapshot.restoreIfUnsupported(data, x, z);
            }
            return;
        }

        int stack = decorator.getHeight(rng, realX, realZ, getData());
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

            PlatformBlockState block = decorator.getBlockDataForTop(biome, rng, realX, height, realZ, getData());
            if (block != null && DecoratorCore.isValidShorelineSupport(decorator, block, support)
                    && IrisSugarCane.canPlace(block, data, x, targetY, z, realX, realZ, getEngine())) {
                data.set(x, targetY, z, block);
                aquaticSnapshot.restoreIfUnsupported(data, x, z);
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
            PlatformBlockState block = threshold >= decorator.getTopThreshold()
                    ? decorator.getBlockDataForTop(biome, rng, realX, h, realZ, getData())
                    : decorator.getBlockData100(biome, rng, realX, h, realZ, getData());
            if (block == null) {
                break;
            }
            if (!IrisSugarCane.canPlace(block, data, x, targetY, z, realX, realZ, getEngine())) {
                break;
            }
            if (i == 0 && !DecoratorCore.isValidShorelineSupport(decorator, block, support)) {
                break;
            }
            data.set(x, targetY, z, block);
        }
        aquaticSnapshot.restoreIfUnsupported(data, x, z);
    }
}
