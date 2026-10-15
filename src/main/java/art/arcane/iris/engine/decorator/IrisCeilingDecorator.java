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

public class IrisCeilingDecorator extends IrisEngineDecorator {
    public IrisCeilingDecorator(DecorationScene scene) {
        super(scene, DecorationPart.CEILING);
    }

    @Override
    public void decorate(int x, int z, int realX, int realX1, int realX_1, int realZ, int realZ1, int realZ_1,
                         DecoratedBiome biome, int height, int max) {
        boolean caveSkipFluid = IrisSurfaceDecorator.skipsFluid(biome.kind());
        RNG rng = getRNG(realX, realZ);
        ResolvedDecorator decorator = DecoratorCore.pickDecorator(decorators.decorators(biome, getPart()), rng, realX, realZ, decorators.log());

        if (decorator == null) {
            return;
        }

        if (!decorator.isStacking()) {
            if (caveSkipFluid && scene.isFluid(scene.blocks().get(x, height, z))) {
                return;
            }
            DecoratorCore.placeSingleAt(decorator, scene, x, z, realX, height, realZ, true);
            return;
        }

        DecoratorCore.PlaceOpts opts = DecoratorCore.SCRATCH_OPTS.get();
        opts.reset();
        opts.caveSkipFluid = caveSkipFluid;
        DecoratorCore.placeStackDown(decorator, scene, x, z, realX, realZ, height, 0, max, opts);
    }
}
