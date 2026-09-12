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
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisCeilingDecorator extends IrisEngineDecorator {
    private final RNG partRNG;

    public IrisCeilingDecorator(Engine engine) {
        super(engine, "Ceiling", IrisDecorationPart.CEILING);
        this.partRNG = new RNG(DecoratorCore.partSeed(getSeed(), IrisDecorationPart.CEILING));
    }

    @BlockCoordinates
    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, int height, int max) {
        decorate(x, z, realX, realX1, realX_1, realZ, realZ1, realZ_1, data, biome, biome.getInferredType(), height, max);
    }

    @BlockCoordinates
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         Hunk<PlatformBlockState> data, IrisBiome biome, InferredType inferredType, int height, int max) {
        boolean caveSkipFluid = IrisSurfaceDecorator.skipsFluid(inferredType);
        RNG rng = getRNG(realX, realZ);
        IrisDecorator decorator = DecoratorCore.pickDecorator(biome, getPart(), partRNG, rng, getData(), realX, realZ);

        if (decorator == null) {
            return;
        }

        if (!decorator.isStacking()) {
            if (caveSkipFluid) {
                PlatformBlockState state = data.get(x, height, z);
                if (B.isFluid(state)) {
                    return;
                }
            }
            DecoratorCore.placeSingleAt(decorator, x, z, realX, height, realZ, data, rng, getData(), true, getEngine().getMantle());
            return;
        }

        DecoratorCore.PlaceOpts opts = DecoratorCore.SCRATCH_OPTS.get();
        opts.reset();
        opts.caveSkipFluid = caveSkipFluid;
        DecoratorCore.placeStackDown(decorator, x, z, realX, realZ, height, 0, data, rng, getData(), max, opts, getEngine().getMantle());
    }
}
