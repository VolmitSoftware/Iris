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
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisSurfaceDecorator extends IrisEngineDecorator {
    public IrisSurfaceDecorator(Engine engine) {
        super(engine, "Surface", DecorationPart.NONE);
    }

    protected IrisSurfaceDecorator(Engine engine, String name) {
        super(engine, name, DecorationPart.NONE);
    }

    protected boolean isSlopeValid(ResolvedDecorator decorator, int realX, int realZ) {
        return !decorator.slopeBlocks(() -> getComplex().getSlopeStream().get(realX, realZ));
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
        int fluidHeight = getDimension().getFluidHeight();
        if (inferredType == InferredType.SHORE && height < fluidHeight) {
            return;
        }

        boolean underwater = isUnderwater(inferredType, height, fluidHeight);
        boolean caveSkipFluid = skipsFluid(inferredType);
        RNG rng = getRNG(realX, realZ);
        ResolvedDecorator decorator = DecoratorCore.pickDecorator(decorators.decorators(biome, getPart()), rng, realX, realZ);

        if (decorator == null || !isSlopeValid(decorator, realX, realZ)) {
            return;
        }

        if (decorator.isStacking()) {
            DecoratorCore.PlaceOpts opts = DecoratorCore.SCRATCH_OPTS.get();
            opts.reset();
            opts.underwater = underwater;
            opts.fluidHeight = fluidHeight;
            opts.caveSkipFluid = caveSkipFluid;
            DecoratorCore.placeStackUp(decorator, x, z, realX, realZ, height, max, data, opts);
            return;
        }

        DecoratorCore.placeSurfaceSingle(decorator, x, z, realX, height, realZ,
                data, underwater, caveSkipFluid, getEngine().getMantle());
    }

    static boolean isUnderwater(InferredType inferredType, int height, int fluidHeight) {
        return height < fluidHeight && inferredType != InferredType.CAVE;
    }

    static boolean skipsFluid(InferredType inferredType) {
        return inferredType == InferredType.CAVE;
    }
}
