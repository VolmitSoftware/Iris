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
import com.volmit.world.BiomeKind;

public class IrisSurfaceDecorator extends IrisEngineDecorator {
    public IrisSurfaceDecorator(DecorationScene scene) {
        super(scene, DecorationPart.NONE);
    }

    protected boolean isSlopeValid(ResolvedDecorator decorator, int realX, int realZ) {
        return !decorator.slopeBlocks(() -> scene.slopeAt(realX, realZ));
    }

    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         DecoratedBiome biome, int height, int max) {
        BiomeKind kind = biome.kind();
        int fluidHeight = scene.fluidHeight();
        if (kind == BiomeKind.SHORE && height < fluidHeight) {
            return;
        }

        boolean underwater = isUnderwater(kind, height, fluidHeight);
        boolean caveSkipFluid = skipsFluid(kind);
        RNG rng = getRNG(realX, realZ);
        ResolvedDecorator decorator = DecoratorCore.pickDecorator(decorators.decorators(biome, getPart()), rng, realX, realZ, decorators.log());

        if (decorator == null || !isSlopeValid(decorator, realX, realZ)) {
            return;
        }

        if (decorator.isStacking()) {
            DecoratorCore.PlaceOpts opts = DecoratorCore.SCRATCH_OPTS.get();
            opts.reset();
            opts.underwater = underwater;
            opts.fluidHeight = fluidHeight;
            opts.caveSkipFluid = caveSkipFluid;
            DecoratorCore.placeStackUp(decorator, scene, x, z, realX, realZ, height, max, opts);
            return;
        }

        DecoratorCore.placeSurfaceSingle(decorator, scene, x, z, realX, height, realZ, underwater, caveSkipFluid);
    }

    static boolean isUnderwater(BiomeKind kind, int height, int fluidHeight) {
        return height < fluidHeight && kind != BiomeKind.CAVE;
    }

    static boolean skipsFluid(BiomeKind kind) {
        return kind == BiomeKind.CAVE;
    }
}
