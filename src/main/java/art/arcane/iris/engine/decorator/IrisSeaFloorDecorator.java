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
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisSeaFloorDecorator extends IrisEngineDecorator {
    public IrisSeaFloorDecorator(Engine engine) {
        super(engine, "Sea Floor", DecorationPart.SEA_FLOOR);
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, int height, int max) {
        RNG rng = getRNG(realX, realZ);
        ResolvedDecorator decorator = DecoratorCore.pickDecorator(decorators.decorators(biome, getPart()), rng, realX, realZ, decorators.log());

        if (decorator == null) {
            return;
        }

        if (!decorator.isStacking()) {
            if (decorator.slopeBlocks(() -> getComplex().getSlopeStream().get(realX, realZ))) {
                return;
            }
            if (height >= 0 && height < getEngine().getHeight()) {
                data.set(x, height, z, decorator.blockAt(biome, realX, height, realZ));
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
            data.set(x, height, z, decorator.topBlockAt(biome, realX, height, realZ));
            return;
        }

        int engineHeight = getEngine().getHeight();
        for (int i = 0; i < stack; i++) {
            int h = height + i;
            if (h > max || h > engineHeight) {
                continue;
            }
            double threshold = ((double) i) / (stack - 1);
            data.set(x, h, z, threshold >= decorator.topThreshold()
                    ? decorator.topBlockAt(biome, realX, h, realZ)
                    : decorator.blockAt(biome, realX, h, realZ));
        }
    }
}
