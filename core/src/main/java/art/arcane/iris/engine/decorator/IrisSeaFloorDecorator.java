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
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisSeaFloorDecorator extends IrisEngineDecorator {
    private final RNG partRNG;

    public IrisSeaFloorDecorator(Engine engine) {
        super(engine, "Sea Floor", IrisDecorationPart.SEA_FLOOR);
        this.partRNG = new RNG(DecoratorCore.partSeed(getSeed(), IrisDecorationPart.SEA_FLOOR));
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, int height, int max) {
        int limit = Math.min(max, Math.min(getEngine().getHeight(), data.getHeight()));
        if (height < 0 || height >= limit) {
            return;
        }
        RNG rng = getRNG(realX, realZ);
        IrisDecorator decorator = DecoratorCore.pickDecorator(biome, getPart(), partRNG, rng, getData(), realX, realZ);

        if (decorator == null) {
            return;
        }

        if (!decorator.isStacking()) {
            if (!decorator.isForcePlace() && !decorator.getSlopeCondition().isDefault()
                    && !decorator.getSlopeCondition().isValid(getComplex().getSlopeStream().get(realX, realZ))) {
                return;
            }
            PlatformBlockState block = decorator.getBlockData100(biome, rng, realX, height, realZ, getData());
            if (block != null) {
                data.set(x, height, z, block);
            }
            return;
        }

        int stack = Math.min(limit - height,
                DecoratorCore.computeStack(decorator, rng, realX, realZ, getData(), max - height));
        for (int i = 0; i < stack; i++) {
            int h = height + i;
            double threshold = stack == 1 ? 1.0 : ((double) i) / (stack - 1);
            PlatformBlockState block = threshold >= decorator.getTopThreshold()
                    ? decorator.getBlockDataForTop(biome, rng, realX, h, realZ, getData())
                    : decorator.getBlockData100(biome, rng, realX, h, realZ, getData());
            if (block == null) {
                break;
            }
            data.set(x, h, z, block);
        }
    }
}
