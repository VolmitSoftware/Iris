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
package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;

public class IrisShoreLineDecorator extends IrisEngineDecorator {
    public IrisShoreLineDecorator(DecorationScene scene) {
        super(scene, DecorationPart.SHORE_LINE);
    }

    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         DecoratedBiome biome, int height, int max) {
        if (height != scene.fluidHeight()) {
            return;
        }

        double complexFluidHeight = scene.fluidHeight();
        if (scene.roundedHeightAt(realX1, realZ) >= complexFluidHeight
                && scene.roundedHeightAt(realX_1, realZ) >= complexFluidHeight
                && scene.roundedHeightAt(realX, realZ1) >= complexFluidHeight
                && scene.roundedHeightAt(realX, realZ_1) >= complexFluidHeight) {
            return;
        }

        RNG rng = getRNG(realX, realZ);
        ResolvedDecorator decorator = DecoratorCore.pickDecorator(decorators.decorators(biome, getPart()), rng, realX, realZ, decorators.log());

        if (decorator == null) {
            return;
        }

        if (decorator.slopeBlocks(() -> scene.slopeAt(realX, realZ))) {
            return;
        }

        var data = scene.blocks();
        BlockState support = data.get(x, height, z);
        if (support == null || !scene.isSolid(support)) {
            return;
        }

        if (!decorator.isStacking()) {
            int targetY = height + 1;
            if (targetY >= data.height()
                    || !DecoratorCore.canReplaceStackTarget(scene, data.get(x, targetY, z), false)) {
                return;
            }
            BlockState block = decorator.blockAt(realX, height, realZ);
            if (block != null && DecoratorCore.isValidShorelineSupport(scene, decorator, block, support)) {
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
            if (targetY >= data.height() || !DecoratorCore.canReplaceStackTarget(scene, data.get(x, targetY, z), false)) {
                return;
            }

            BlockState block = decorator.topBlockAt(realX, height, realZ);
            if (block != null && DecoratorCore.isValidShorelineSupport(scene, decorator, block, support)) {
                data.set(x, targetY, z, block);
            }
            return;
        }

        for (int i = 0; i < stack; i++) {
            int h = height + i;
            int targetY = h + 1;
            if (targetY >= data.height()
                    || !DecoratorCore.canReplaceStackTarget(scene, data.get(x, targetY, z), false)) {
                break;
            }
            double threshold = ((double) i) / (stack - 1);
            BlockState block = threshold >= decorator.topThreshold()
                    ? decorator.topBlockAt(realX, h, realZ)
                    : decorator.blockAt(realX, h, realZ);
            if (block == null) {
                break;
            }
            if (i == 0 && !DecoratorCore.isValidShorelineSupport(scene, decorator, block, support)) {
                break;
            }
            data.set(x, targetY, z, block);
        }
    }
}
