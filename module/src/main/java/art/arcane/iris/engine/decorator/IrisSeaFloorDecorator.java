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

public class IrisSeaFloorDecorator extends IrisEngineDecorator {
    public IrisSeaFloorDecorator(DecorationScene scene) {
        super(scene, DecorationPart.SEA_FLOOR);
    }

    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         DecoratedBiome biome, int height, int max) {
        RNG rng = getRNG(realX, realZ);
        ResolvedDecorator decorator = DecoratorCore.pickDecorator(decorators.decorators(biome, getPart()), rng, realX, realZ, decorators.log());

        if (decorator == null) {
            return;
        }

        var data = scene.blocks();
        if (!decorator.isStacking()) {
            if (decorator.slopeBlocks(() -> scene.slopeAt(realX, realZ))) {
                return;
            }
            if (height >= 0 && height < scene.worldHeight()) {
                place(x, height, z, decorator.blockAt(realX, height, realZ));
            }
            return;
        }

        int stack = decorator.stackHeight(realX, realZ);
        if (decorator.isScaleStack()) {
            stack = (int) Math.ceil((double) (max - height) * ((double) stack / 100));
        } else {
            stack = Math.min(stack, max - height);
        }

        if (stack == 1) {
            place(x, height, z, decorator.topBlockAt(realX, height, realZ));
            return;
        }

        int engineHeight = scene.worldHeight();
        for (int i = 0; i < stack; i++) {
            int h = height + i;
            if (h > max || h > engineHeight) {
                continue;
            }
            double threshold = ((double) i) / (stack - 1);
            place(x, h, z, threshold >= decorator.topThreshold()
                    ? decorator.topBlockAt(realX, h, realZ)
                    : decorator.blockAt(realX, h, realZ));
        }
    }

    /** A block the decorator has none of leaves the water as it is. */
    private void place(int x, int y, int z, BlockState block) {
        if (block != null && y < scene.worldHeight()) {
            scene.blocks().set(x, y, z, block);
        }
    }
}
